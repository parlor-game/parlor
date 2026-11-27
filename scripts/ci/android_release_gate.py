"""Bind every Android verification lane to a clean checkout and retained reports."""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

from scripts.ci import verification_hygiene as hygiene
from scripts.ci.apple_release_gate import read_json

LANES = ("common-and-package", "installed-runtime", "process-restart")
FIELDS = {"lane", "source_sha", "source_tree", "run_id", "run_attempt",
          "evidence_id", "evidence_sha256", "cleanup_id", "cleanup_sha256"}


def identifier(value: object) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[1-9][0-9]{0,19}", value) is not None


def lane_identity(root: Path, prefix: Path, task: dict, lane: str, cleanup_upload: dict) -> dict:
    if task.get("GITHUB_JOB") != "desktop-android" or lane not in LANES:
        raise RuntimeError("Unknown Android verification lane")
    source = hygiene.source_identity(root)
    claim = read_json(Path(str(prefix) + "-ownership.json"))
    cleanup = read_json(Path(str(prefix) + "-cleanup.json"))
    absence = cleanup.get("android_emulator_absence", {})
    if (claim.get("task") != task or any(claim.get(key) != value for key, value in source.items()) or
            cleanup.get("task") != task or cleanup.get("result") != "PASS" or
            cleanup.get("errors") != [] or cleanup.get("retained") != [] or
            cleanup.get("preparation_outcome") != "success" or cleanup.get("gradle_stop", {}).get("exit_code") != 0 or
            absence.get("result") != "PASS" or
            absence.get("source") != {key: source[key] for key in ("head", "tree")} or
            hygiene.git(root, "status", "--porcelain").strip()):
        raise RuntimeError("Android lane source or final cleanup is not qualified")
    evidence = cleanup.get("upload", {})
    if not all(hygiene.uploaded_evidence_is_retained(upload) for upload in (evidence, cleanup_upload)):
        raise RuntimeError("Android lane evidence or cleanup was not retained")
    return dict(lane=lane, source_sha=source["head"], source_tree=source["tree"],
                run_id=task["GITHUB_RUN_ID"], run_attempt=task["GITHUB_RUN_ATTEMPT"],
                evidence_id=evidence["artifact_id"], evidence_sha256=evidence["artifact_digest"],
                cleanup_id=cleanup_upload["artifact_id"], cleanup_sha256=cleanup_upload["artifact_digest"])


def verify_latest_jobs(response: dict, run_id: str, attempt: str) -> None:
    """Matrix-level needs may hide a cancelled member. Check each last execution."""
    jobs = response.get("jobs")
    if not isinstance(jobs, list) or response.get("total_count") != len(jobs) or len(jobs) > 100:
        raise RuntimeError("Incomplete Android workflow jobs response")
    expected = {f"Android verification ({lane})" for lane in LANES}
    completed = set()
    for job in jobs:
        name = job.get("name")
        if name in expected:
            if (name in completed or str(job.get("run_id")) != run_id or
                    str(job.get("run_attempt")) != attempt or
                    job.get("status") != "completed" or job.get("conclusion") != "success"):
                raise RuntimeError("Android verification lane has no successful latest job: " + name)
            completed.add(name)
    if completed != expected:
        raise RuntimeError("Android workflow is missing a required individual job")


def aggregate(receipts: Path, result: str, head: str, tree: str, run_id: str, attempt: str) -> dict:
    if result != "success" or not identifier(run_id) or not identifier(attempt):
        raise RuntimeError("Android verification producers did not pass")
    lanes: dict[str, dict] = {}
    seen: set[tuple[str, str]] = set()
    for directory in sorted(receipts.iterdir()):
        if not directory.is_dir() or directory.is_symlink():
            raise RuntimeError("Android lane certificate is not a directory")
        path = directory / "lane.json"
        if set(directory.iterdir()) != {path}:
            raise RuntimeError("Expected one Android lane certificate")
        value = read_json(path)
        if set(value) != FIELDS:
            raise RuntimeError("Incomplete Android lane custody")
        lane, producer_attempt = value["lane"], value["run_attempt"]
        if (lane not in LANES or not identifier(producer_attempt) or int(producer_attempt) > int(attempt) or
                directory.name != f"android-lane-certificate-{run_id}-{producer_attempt}-{lane}" or
                value["source_sha"] != head or value["source_tree"] != tree or value["run_id"] != run_id or
                (lane, producer_attempt) in seen or
                not all(identifier(value[field]) for field in ("evidence_id", "cleanup_id")) or
                not all(isinstance(value[field], str) and re.fullmatch(r"[0-9a-f]{64}", value[field])
                        for field in ("evidence_sha256", "cleanup_sha256"))):
            raise RuntimeError("Invalid or foreign Android lane certificate")
        seen.add((lane, producer_attempt))
        if lane not in lanes or int(producer_attempt) > int(lanes[lane]["run_attempt"]):
            lanes[lane] = value
    if set(lanes) != set(LANES) or len({receipt[key] for receipt in lanes.values()
                                       for key in ("evidence_id", "cleanup_id")}) != 2 * len(LANES):
        raise RuntimeError("Missing or reused Android lane evidence")
    return dict(schema=1, result="PASS", source_sha=head, source_tree=tree, run_id=run_id,
                run_attempt=attempt, lanes=lanes)


def main() -> int:
    env = os.environ
    destination = Path(env["RUNNER_TEMP"])
    if sys.argv[1:] == ["lane"]:
        root, prefix, task = hygiene.context()
        upload = dict(outcome=env["PARLOR_ANDROID_CLEANUP_OUTCOME"],
                      artifact_id=env["PARLOR_ANDROID_CLEANUP_ID"],
                      artifact_digest=env["PARLOR_ANDROID_CLEANUP_DIGEST"])
        value = lane_identity(root, prefix, task, env["PARLOR_ANDROID_LANE"], upload)
        destination = destination / "lane.json"
    elif sys.argv[1:] == ["aggregate"] and env.get("GITHUB_JOB") == "android-release":
        root = Path(env["GITHUB_WORKSPACE"]).resolve(strict=True)
        source = hygiene.source_identity(root)
        if hygiene.git(root, "status", "--porcelain").strip() or source["head"] != env["GITHUB_SHA"]:
            raise RuntimeError("Android aggregate checkout is not the clean triggering source")
        verify_latest_jobs(read_json(destination / "parlor-android-jobs.json"),
                           env["GITHUB_RUN_ID"], env["GITHUB_RUN_ATTEMPT"])
        value = aggregate(destination / "parlor-android-lanes", env["PARLOR_ANDROID_PRODUCER_RESULT"],
                          source["head"], source["tree"], env["GITHUB_RUN_ID"], env["GITHUB_RUN_ATTEMPT"])
        destination = destination / "parlor-android-release.json"
    else:
        raise RuntimeError("Expected Android lane or aggregate")
    hygiene.write_new(destination, value)
    print(json.dumps(value))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
