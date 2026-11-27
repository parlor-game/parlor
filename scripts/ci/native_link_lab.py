"""Measure one real, clean Apple Release link or preflight its task graph."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import tomllib
from datetime import datetime, timezone
from pathlib import Path

TASK = ":composeApp:linkReleaseFrameworkIosSimulatorArm64"
DEBUG_TASK = ":composeApp:linkDebugFrameworkIosSimulatorArm64"
FRAMEWORK_DIR = Path("composeApp/build/bin/iosSimulatorArm64")
FRAMEWORK_BINARY = Path("ComposeApp.framework/ComposeApp")
LINK = re.compile(r"(?m)^(?:> Task )?(:\S+:link(?:Debug|Release)Framework\w+)(?: (.*))?$")
PHASE = re.compile(r"(?m)^([A-Za-z][A-Za-z0-9]+): ([0-9]+) msec$")


def output(root: Path, *command: str) -> str:
    return subprocess.check_output(command, cwd=root, text=True).strip()


def execution_evidence(log: str, mode: str) -> dict:
    links = LINK.findall(log)
    expected_status = "SKIPPED" if mode == "plan" else ""
    if links != [(TASK, expected_status)]:
        raise RuntimeError(f"Expected exactly one {mode} link task, observed {links}")
    phase_start = log.index(f"> Task {TASK}\n") if mode == "link" else len(log)
    native = log[phase_start:]
    phases = {name: int(ms) for name, ms in PHASE.findall(native)}
    if mode == "link":
        if not re.search(r"(?m)^\s*-opt[ \t]*$", native):
            raise RuntimeError("The selected Kotlin/Native link did not run with optimization")
        if phases.get("Compiler", 0) <= 0 or "Linker" not in phases:
            raise RuntimeError("Missing real Kotlin/Native compiler or linker profile")
    return {"link_task": TASK, "mode": mode, "native_phase_ms": phases,
            "gradle_task_outcomes": {
                status: len(re.findall(rf"(?m)^> Task [^\n]+ {status}$", log))
                for status in ("UP-TO-DATE", "FROM-CACHE", "NO-SOURCE", "SKIPPED")
            }}


def task_cache_evidence(log: str, role: str) -> dict:
    links = LINK.findall(log)
    expected_status, verb = ("", "Stored") if role == "baseline" else ("FROM-CACHE", "Loaded")
    if links != [(DEBUG_TASK, expected_status)]:
        raise RuntimeError(f"Expected the Debug link to be {verb.lower()} for {role}, observed {links}")
    keys = re.findall(
        rf"(?m)^{verb} cache entry for task '{DEBUG_TASK}' with cache key ([0-9a-f]{{32}})$", log
    )
    if len(keys) != 1:
        raise RuntimeError(f"No unique task output cache key for {role}")
    return {"link_task": DEBUG_TASK, "mode": "cache-probe", "native_phase_ms": {},
            "task_cache": {"key": keys[0], "outcome": verb.lower()}}


MATCHED_FIELDS = (
    "mode", "link_task", "xcode", "macos", "architecture", "runner_image_version",
    "logical_cpus", "jdk", "kotlin_version", "gradle_distribution", "dependency_cache_match",
    "dependency_cache_exact_hit", "konan_before", "gradle_task_output_cache", "gradle_configuration_cache",
)


def compare_reports(baseline: dict, candidate: dict) -> dict:
    if (baseline.get("role") != "baseline" or candidate.get("role") != "candidate" or
            any(r.get("result") != "PASS" or r.get("schema") != 1 for r in (baseline, candidate)) or
            not baseline.get("run_id") or not baseline.get("run_attempt") or
            any(baseline.get(key) != candidate.get(key) for key in ("run_id", "run_attempt"))):
        raise RuntimeError("Expected the two successful roles from the same lab attempt")
    mismatches = [
        field for field in MATCHED_FIELDS
        if field not in baseline or field not in candidate or baseline[field] != candidate[field]
    ]
    if mismatches:
        raise RuntimeError(f"Unmatched runner, toolchain or cache conditions: {', '.join(mismatches)}")
    before, after = (r["gradle_wall_seconds"] for r in (baseline, candidate))
    phase_deltas = {
        name: candidate["native_phase_ms"][name] - duration
        for name, duration in baseline["native_phase_ms"].items()
        if name in candidate["native_phase_ms"]
    }
    report = {"schema": 1, "mode": baseline["mode"], "run_id": baseline["run_id"],
              "baseline_sha": baseline["source_sha"], "candidate_sha": candidate["source_sha"],
              "baseline_gradle_seconds": before, "candidate_gradle_seconds": after,
              "gradle_delta_seconds": after - before, "native_phase_delta_ms": phase_deltas,
              "dependency_cache_window_seconds": {
                  r["role"]: r["dependency_cache_step_window_seconds"] for r in (baseline, candidate)
              }}
    if baseline["mode"] == "cache-probe":
        producer, consumer = baseline["task_cache"], candidate["task_cache"]
        if (baseline["source_sha"] != candidate["source_sha"] or
                baseline["source_tree"] != candidate["source_tree"] or
                baseline["framework"] != candidate["framework"] or
                producer["key"] != consumer["key"] or
                (producer["outcome"], consumer["outcome"]) != ("stored", "loaded")):
            raise RuntimeError("Expected one exact-source framework restored across checkout paths")
        report.update(task_cache_key=producer["key"], framework=baseline["framework"],
                      task_cache_transfer_seconds=candidate["task_cache_transfer_seconds"])
    return report


def konan_state(root: Path, data: Path) -> dict:
    if not data.is_dir():
        return {"entries": [], "kib": 0}
    return {"entries": sorted(path.name for path in data.iterdir()),
            "kib": int(output(root, "du", "-sk", str(data)).split()[0])}


def run(args: argparse.Namespace) -> None:
    root, out = args.source.resolve(strict=True), args.report.resolve()
    if not re.fullmatch(r"[0-9a-f]{40}", args.expected_sha):
        raise RuntimeError("Expected a full source SHA")
    head = output(root, "git", "rev-parse", "HEAD")
    tree = output(root, "git", "rev-parse", "HEAD^{tree}")
    if head != args.expected_sha or output(root, "git", "status", "--porcelain"):
        raise RuntimeError("Checked-out source is dirty or does not match the requested SHA")
    if platform.system() != "Darwin" or platform.machine() != "arm64":
        raise RuntimeError("The lab requires the standard ARM64 macOS runner")
    xcode = output(root, "xcodebuild", "-version")
    if xcode != "Xcode 26.3\nBuild version 17C529":
        raise RuntimeError(f"Unexpected Xcode build: {xcode}")
    probe = args.mode == "cache-probe"
    variant = "debugFramework" if probe else "releaseFramework"
    binary = root / FRAMEWORK_DIR / variant / FRAMEWORK_BINARY
    if binary.parent.exists():
        raise RuntimeError("Framework output must be cold")
    native_data = args.native_data.resolve()
    if native_data.exists():
        raise RuntimeError("Each comparison role requires a clean Native data path")
    out.mkdir(parents=True, exist_ok=False)
    command = ["./gradlew", DEBUG_TASK if probe else TASK, "--dependency-verification=strict",
               "--no-daemon", "--no-configuration-cache", "--console=plain", f"-Pkonan.data.dir={native_data}"]
    if probe:
        command += ["--build-cache", "--info"]
    else:
        command += ["--no-build-cache", "-Pparlor.profileIosReleaseLink=true",
                    "-Pkotlin.internal.compiler.arguments.log.level=warning"]
        command += ["--dry-run"] if args.mode == "plan" else ["--profile"]
    konan_before = konan_state(root, native_data)
    start = time.monotonic_ns()
    started_at = datetime.now(timezone.utc).isoformat()
    with (out / "gradle.log").open("wb") as log:
        timeout = {"baseline": 1200, "candidate": 480}[args.role] if probe else None
        try:
            exit_code = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT,
                                       timeout=timeout, check=False).returncode
        except subprocess.TimeoutExpired:
            exit_code = 124
    seconds = (time.monotonic_ns() - start) / 1e9
    with (root / "gradle/libs.versions.toml").open("rb") as catalog:
        kotlin_version = tomllib.load(catalog)["versions"]["kotlin"]
    gradle_distribution = next(
        line.split("=", 1)[1].replace(r"\:", ":")
        for line in (root / "gradle/wrapper/gradle-wrapper.properties").read_text().splitlines()
        if line.startswith("distributionUrl=")
    )
    report = {"schema": 1, "run_id": os.environ.get("GITHUB_RUN_ID"),
              "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"), "source_sha": head,
              "source_tree": tree, "role": args.role, "mode": args.mode,
              "started_at": started_at, "finished_at": datetime.now(timezone.utc).isoformat(),
              "gradle_wall_seconds": seconds, "exit_code": exit_code,
              "xcode": xcode, "macos": platform.mac_ver()[0], "architecture": platform.machine(),
              "runner_image_version": os.environ.get("ImageVersion"),
              "logical_cpus": output(root, "sysctl", "-n", "hw.logicalcpu"),
              "jdk": output(root, "java", "--version").splitlines()[0],
              "kotlin_version": kotlin_version, "gradle_distribution": gradle_distribution,
              "dependency_cache_step_window_seconds": int(os.environ["PARLOR_LAB_CACHE_SECONDS"]),
              "dependency_cache_match": os.environ.get("PARLOR_LAB_CACHE_MATCH"),
              "dependency_cache_exact_hit": os.environ.get("PARLOR_LAB_CACHE_HIT") == "true",
              "konan_before": konan_before, "konan_after": konan_state(root, native_data),
              "gradle_task_output_cache": probe, "gradle_configuration_cache": False,
              "command": command}
    try:
        if exit_code:
            raise RuntimeError(f"Gradle exited with {exit_code}. Inspect gradle.log.")
        log = (out / "gradle.log").read_text(errors="replace")
        if probe:
            report.update(task_cache_evidence(log, args.role))
            report["task_cache_transfer_seconds"] = int(os.environ.get("PARLOR_LAB_TASK_CACHE_TRANSFER_SECONDS", "0"))
        else:
            report.update(execution_evidence(log, args.mode))
        if args.mode != "plan":
            subprocess.run(["xcrun", "lipo", "-verify_arch", "arm64", str(binary)], check=True)
            with binary.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            report["framework"] = {"bytes": binary.stat().st_size, "sha256": digest}
            for profile in (root / "build/reports/profile").glob("profile-*.html"):
                shutil.copy2(profile, out)
        report["result"] = "PASS"
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        report["result"] = "FAIL"
        report["error"] = str(error)
        raise
    finally:
        (out / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(f"{args.role} {args.mode}: {seconds:.1f}s, source {head}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    if len(sys.argv) > 1 and sys.argv[1] == "compare":
        parser.add_argument("baseline", type=Path)
        parser.add_argument("candidate", type=Path)
        args = parser.parse_args(sys.argv[2:])
        report = compare_reports(json.loads(args.baseline.read_text()), json.loads(args.candidate.read_text()))
        print(json.dumps(report, indent=2, sort_keys=True))
        return
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--native-data", type=Path, required=True)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--role", choices=("baseline", "candidate"), required=True)
    parser.add_argument("--mode", choices=("plan", "link", "cache-probe"), required=True)
    run(parser.parse_args())


if __name__ == "__main__":
    main()
