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
import time
from datetime import datetime, timezone
from pathlib import Path

TASK = ":composeApp:linkReleaseFrameworkIosSimulatorArm64"
FRAMEWORK = Path("composeApp/build/bin/iosSimulatorArm64/releaseFramework/ComposeApp.framework/ComposeApp")
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
    if (root / FRAMEWORK).parent.exists():
        raise RuntimeError("Framework output must be cold")
    out.mkdir(parents=True, exist_ok=False)
    command = ["./gradlew", TASK, "--dependency-verification=strict", "--no-daemon",
               "--no-build-cache", "--no-configuration-cache", "--console=plain",
               "-Pparlor.profileIosReleaseLink=true",
               "-Pkotlin.internal.compiler.arguments.log.level=warning"]
    command += ["--dry-run"] if args.mode == "plan" else ["--profile"]
    start = time.monotonic_ns()
    started_at = datetime.now(timezone.utc).isoformat()
    with (out / "gradle.log").open("wb") as log:
        build = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT, check=False)
    seconds = (time.monotonic_ns() - start) / 1e9
    report = {"schema": 1, "run_id": os.environ.get("GITHUB_RUN_ID"),
              "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"), "source_sha": head,
              "source_tree": tree, "role": args.role, "mode": args.mode,
              "started_at": started_at, "finished_at": datetime.now(timezone.utc).isoformat(),
              "gradle_wall_seconds": seconds, "exit_code": build.returncode,
              "xcode": xcode, "macos": platform.mac_ver()[0], "architecture": platform.machine(),
              "runner_image_version": os.environ.get("ImageVersion"),
              "logical_cpus": output(root, "sysctl", "-n", "hw.logicalcpu"),
              "jdk": output(root, "java", "--version").splitlines()[0],
              "dependency_cache_step_window_seconds": int(os.environ["PARLOR_LAB_CACHE_SECONDS"]),
              "dependency_cache_match": os.environ.get("PARLOR_LAB_CACHE_MATCH"),
              "dependency_cache_exact_hit": os.environ.get("PARLOR_LAB_CACHE_HIT") == "true",
              "gradle_task_output_cache": False, "gradle_configuration_cache": False,
              "command": command}
    try:
        if build.returncode:
            raise RuntimeError(f"Gradle exited with {build.returncode}. Inspect gradle.log.")
        report.update(execution_evidence((out / "gradle.log").read_text(errors="replace"), args.mode))
        if args.mode == "link":
            binary = root / FRAMEWORK
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
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--role", choices=("baseline", "candidate"), required=True)
    parser.add_argument("--mode", choices=("plan", "link"), required=True)
    run(parser.parse_args())


if __name__ == "__main__":
    main()
