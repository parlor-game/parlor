"""Capture synchronized, allowlisted transport events from independently installed apps."""
import datetime as dt
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys
import time

EXPORT = re.compile(
    r"seq=(?P<sequence>[0-9]+) elapsed_ms=(?P<elapsed_ms>[0-9]+)"
    r" event=(?P<event>[a-z_]+) role=(?P<role>none|host|peer)"
    r" result=(?P<result>none|success|failure|timeout|cancelled|rejected|duplicate)"
    r" reason=(?P<reason>[a-z_]+)"
    r" count=(?P<count>none|zero|one|two_to_four|five_to_eight|nine_to_seventeen|over_limit)$"
)
EVENTS = frozenset("""
    session_create_started session_create_succeeded session_create_failed
    discovery_started discovery_candidates discovery_attempted discovery_finished
    reconnect_browse_sample host_advertisement_sample
    lan_connection_preparing lan_connection_waiting lan_connection_ready
    lan_connection_failed lan_connection_cancelled connection_secure connection_closed
    admission_requested admission_reserved admission_committed admission_rolled_back
    admission_rejected protocol_rejected command_sent command_received command_accepted
    command_rejected command_duplicate snapshot_sent snapshot_received lifecycle_suspended
    lifecycle_resume_started lifecycle_resumed lifecycle_expired credential_invalidated
    credential_invalidation_failed cleanup_started cleanup_completed cleanup_failed frame_dropped
    peer_rate_limited
""".split())
REASONS = frozenset("""
    none transport native_permission native_no_route native_refused native_timeout native_dns
    permission wrong_room wrong_game approval_expired stale_room room_full session_started
    incompatible_protocol unauthorized name_in_use rate_limit payload_limit wrong_direction
    malformed invalid_action stale_revision sequence_gap session_ended unknown_command
    disconnected lifecycle internal pinned_host_absent advertisement_scope_mismatch
    host_acceptor_active host_acceptor_stopped host_kit_not_running
""".split())
ANDROID_LINE = re.compile(r"^(?P<epoch>[0-9]{9,11}\.[0-9]+)\s+.*?\bParlorP2p\s*:\s*(?P<message>seq=.*)$")
APPLE_LINE = re.compile(r"\bParlorP2p\s+(?P<message>seq=.*)$")


def parse_event(line, platform):
    if platform == "android":
        raw = ANDROID_LINE.match(line.strip())
        if not raw:
            return None
        epoch = float(raw["epoch"])
    else:
        try:
            entry = json.loads(line)
            raw = APPLE_LINE.search(entry["eventMessage"])
            if not raw:
                return None
            epoch = dt.datetime.fromisoformat(entry["timestamp"]).timestamp()
        except (KeyError, ValueError, TypeError):
            return None
    fields = EXPORT.fullmatch(raw["message"])
    if not fields or fields["event"] not in EVENTS or fields["reason"] not in REASONS:
        return None
    event = fields.groupdict()
    for name in ("sequence", "elapsed_ms"):
        event[name] = int(event[name])
    return {"guest_epoch_ms": round(epoch * 1000), **event}


def shell_output(args, timeout):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=True).stdout


def guest_command(platform, device):
    if platform == "android" and re.fullmatch(r"emulator-[0-9]+", device):
        return ["adb", "-s", device, "shell"]
    if platform == "ios" and re.fullmatch(r"[0-9a-fA-F-]{36}", device):
        return ["xcrun", "simctl", "spawn", device]
    raise ValueError("Invalid job-owned guest")


def prepare_ios_clock(temp):
    binary = temp / "parlor-ios-clock"
    if binary.is_file():
        return binary
    sdk = shell_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], 20).strip()
    source = Path(__file__).with_name("clock_probe.c")
    shell_output(["xcrun", "--sdk", "iphonesimulator", "clang", "-std=c11", "-Wall", "-Wextra",
                  "-Werror", "-target", "arm64-apple-ios16.0-simulator", "-isysroot", sdk,
                  str(source), "-o", str(binary)], 30)
    shell_output(["codesign", "--force", "--sign", "-", str(binary)], 15)
    return binary


def clock(platform, device):
    command = guest_command(platform, device)
    if platform == "android":
        command += ["date", "+%s%3N"]
    else:
        command.append(str(prepare_ios_clock(Path(os.environ["RUNNER_TEMP"]))))
    samples = []
    for _ in range(3):
        before_ms = time.time_ns() // 1_000_000
        guest = shell_output(command, 15).strip()
        after_ms = time.time_ns() // 1_000_000
        if not re.fullmatch(r"[0-9]{13}", guest):
            raise ValueError("Invalid guest clock result")
        guest_ms = int(guest)
        samples.append({"runner_midpoint_epoch_ms": (before_ms + after_ms) // 2,
                        "guest_epoch_ms": guest_ms,
                        "offset_ms": guest_ms - (before_ms + after_ms) // 2,
                        "roundtrip_ms": after_ms - before_ms})
    return {"samples": samples, "median_offset_ms": int(statistics.median(v["offset_ms"] for v in samples)),
            "guest_resolution_ms": 1}


def read_events(platform, device):
    if platform == "android":
        cmd = ["adb", "-s", device, "logcat", "-d", "-v", "epoch", "-s", "ParlorP2p:I", "*:S"]
    else:
        cmd = guest_command(platform, device) + ["log", "show", "--style", "ndjson", "--last", "85m",
                                               "--info", "--debug", "--predicate",
                                               'eventMessage CONTAINS "ParlorP2p seq="']
    return [event for line in shell_output(cmd, 80).splitlines()
            if (event := parse_event(line, platform)) is not None]


def main(operation):
    temp = Path(os.environ["RUNNER_TEMP"])
    platform = os.environ["TARGET"]
    devices = (temp / "parlor-devices").read_text().splitlines()
    for device in devices:
        proof = temp / "parlor-proof" / device
        proof.mkdir(exist_ok=True)
        try:
            result = clock(platform, device)
        except (OSError, subprocess.SubprocessError, ValueError) as error:
            result = {"error": type(error).__name__}
        (proof / f"{operation}.json").write_text(json.dumps(result, indent=2) + "\n")
        if operation == "capture":
            try:
                events = read_events(platform, device)
            except (OSError, subprocess.SubprocessError, ValueError) as error:
                (proof / "diagnostics-status.json").write_text(json.dumps({"error": type(error).__name__}) + "\n")
                continue
            (proof / "diagnostics.jsonl").write_text("".join(json.dumps(event) + "\n" for event in events))
            (proof / "diagnostics-status.json").write_text(json.dumps({"event_count": len(events)}) + "\n")
            print(f"Retained {len(events)} bounded ParlorP2p events on {platform}.")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {"clock-before", "capture"}:
        raise SystemExit("Use clock-before or capture")
    main(sys.argv[1])
