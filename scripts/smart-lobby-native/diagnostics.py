"""Capture synchronized, allowlisted transport events from independently installed apps."""
from collections import deque
import datetime as dt
import json
import os
from pathlib import Path
import re
import select
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
STREAM_EVENTS = EVENTS | {"capture_unavailable"}
REASONS = frozenset("""
    none transport native_permission native_no_route native_refused native_timeout native_dns
    permission wrong_room wrong_game approval_expired stale_room room_full session_started
    incompatible_protocol unauthorized name_in_use rate_limit payload_limit wrong_direction
    malformed invalid_action stale_revision sequence_gap session_ended unknown_command
    disconnected lifecycle internal pinned_host_absent advertisement_scope_mismatch
    host_acceptor_active host_acceptor_stopped host_kit_not_running
""".split())
REASON_CODES = {reason: code for code, reason in enumerate(sorted(REASONS))}
ANDROID_LINE = re.compile(r"^(?P<epoch>[0-9]{9,11}\.[0-9]+)\s+.*?\bParlorP2p\s*:\s*(?P<message>seq=.*)$")
APPLE_LINE = re.compile(r"\bParlorP2p\s+(?P<message>seq=.*)$")
IOS_DEBUG_BUNDLE = "me.parlor.ios.debug"
CRASH_TOKEN = re.compile(r"[A-Z][A-Z0-9_]{0,47}")
CRASH_UUID = re.compile(r"[a-fA-F0-9-]{36}")
KOTLIN_EXCEPTION_CLASS = r"[A-Za-z_][A-Za-z0-9_.$]{0,199}"
UNHANDLED_KOTLIN = re.compile(
    rf"^(?:Uncaught|Unhandled) Kotlin exception:[ \t]*({KOTLIN_EXCEPTION_CLASS})(?=$|[: \t])", re.M,
)
PRINTED_THROWABLE = re.compile(rf"^({KOTLIN_EXCEPTION_CLASS})(?::[^\n]*)?\n[ \t]+at ", re.M)
PARLOR_STACK_SYMBOL = re.compile(r"\bkfun:(com\.parlor\.[A-Za-z0-9_.$]+(?:#[A-Za-z0-9_.$<>-]+)?)")


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
        retained_minutes = int(os.environ.get("PARLOR_UI_DURATION", "80")) + 5
        cmd = guest_command(platform, device) + ["log", "show", "--style", "ndjson", "--last", f"{retained_minutes}m",
                                               "--info", "--debug", "--predicate",
                                               'eventMessage CONTAINS "ParlorP2p seq="']
    return [event for line in shell_output(cmd, 80).splitlines()
            if (event := parse_event(line, platform)) is not None]


def safe_native_frames(callstack, images):
    frames = []
    for frame in callstack[:24]:
        image_index, offset = frame.get("imageIndex"), frame.get("imageOffset")
        if type(image_index) is not int or not 0 <= image_index < len(images) or type(offset) is not int:
            continue
        uuid = images[image_index].get("uuid", "")
        if isinstance(uuid, str) and CRASH_UUID.fullmatch(uuid):
            frames.append({"image_uuid": uuid, "image_offset": offset})
    return frames


def summarize_ios_crash(contents, device):
    header, report = contents.split("\n", 1)
    metadata = json.loads(header)
    crash = json.loads(report)
    if (metadata.get("bug_type") != "309" or metadata.get("platform") not in (None, 7)
            or metadata.get("bundleID") != IOS_DEBUG_BUNDLE
            or crash.get("bundleInfo", {}).get("CFBundleIdentifier") != IOS_DEBUG_BUNDLE
            or f"/devices/{device.lower()}/" not in crash.get("procPath", "").lower()):
        return None
    output = {}
    try:
        stamp = re.sub(r" (?=[+-][0-9]{4}$)", "", crash["captureTime"])
        output["capture_utc"] = dt.datetime.fromisoformat(stamp).astimezone(dt.timezone.utc).isoformat()
    except (TypeError, ValueError, KeyError):
        pass
    for source, keys in (("exception", ("type", "signal")), ("termination", ("namespace", "code"))):
        for key in keys:
            value = crash.get(source, {}).get(key)
            if type(value) is int or isinstance(value, str) and CRASH_TOKEN.fullmatch(value):
                output[f"{source}_{key}"] = value
    threads = crash.get("threads", [])
    images = crash.get("usedImages", [])
    thread_index = crash.get("faultingThread")
    if type(thread_index) is int and 0 <= thread_index < len(threads):
        output["faulting_frames"] = safe_native_frames(threads[thread_index].get("frames", []), images)
    if report_frames := crash.get("lastExceptionBacktrace"):
        output["exception_frames"] = safe_native_frames(report_frames, images)
    return output


def ios_crash_reports(device):
    logs = Path.home() / "Library" / "Logs" / "DiagnosticReports"
    since = time.time() - (int(os.environ.get("PARLOR_UI_DURATION", "80")) + 25) * 60
    candidates = []
    for path in logs.glob("*.ips"):
        try:
            info = path.stat()
        except OSError:
            continue
        if info.st_mtime >= since and info.st_size <= 2_000_000 and not path.is_symlink():
            candidates.append((info.st_mtime, path))
    reports = []
    for _, path in sorted(candidates, reverse=True)[:80]:
        try:
            summary = summarize_ios_crash(path.read_text(), device)
        except (OSError, ValueError, TypeError, AttributeError, KeyError):
            continue
        if summary is not None:
            reports.append(summary)
            if len(reports) == 4:
                break
    return reports


def ios_kotlin_failure(process_logs):
    streams = {}
    failures = []
    for stream in ("stderr", "stdout"):
        path = process_logs / stream
        if not path.is_file() or path.is_symlink():
            continue
        with path.open("rb") as log:
            log.seek(max(0, path.stat().st_size - 131_072))
            streams[stream] = log.read(131_072).decode(errors="replace")
    for stream, recent in streams.items():
        for match in list(UNHANDLED_KOTLIN.finditer(recent))[-4:]:
            excerpt = "\n".join(recent[match.end():].splitlines()[1:49])
            failures.append({"exception_class": match[1], "stream": stream,
                             "app_symbols": PARLOR_STACK_SYMBOL.findall(excerpt)[:20]})
    # Kotlin/Native prints the exception prefix to stdout and its stack to stderr.
    if not failures and "Uncaught Kotlin exception:" in streams.get("stdout", ""):
        stderr = streams.get("stderr", "")
        if match := PRINTED_THROWABLE.search(stderr):
            excerpt = "\n".join(stderr[match.start():].splitlines()[1:49])
            failures.append({"exception_class": match[1], "stream": "stderr",
                             "app_symbols": PARLOR_STACK_SYMBOL.findall(excerpt)[:20]})
    return failures


def stream_events(platform, device, destination, outcome):
    guest = guest_command(platform, device)
    if platform == "android":
        command = ["adb", "-s", device, "logcat", "-T", "1", "-v", "epoch", "-s", "ParlorP2p:I", "*:S"]
    else:
        command = guest + [
            "log", "stream", "--style", "ndjson", "--level", "debug", "--predicate",
            'eventMessage CONTAINS "ParlorP2p seq="',
        ]
    recent = deque(maxlen=80)
    pending = b""
    temporary = destination.with_suffix(".tmp")
    with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as source:
        try:
            while not outcome.exists():
                if not select.select([source.stdout], [], [], 1)[0]:
                    continue
                data = os.read(source.stdout.fileno(), 4096)
                if not data:
                    if not outcome.exists():
                        recent.append({"event": "capture_unavailable", "level": "warning"})
                        temporary.write_text("".join(json.dumps(event) + "\n" for event in recent))
                        temporary.replace(destination)
                    break
                lines = (pending + data).split(b"\n")
                pending = lines.pop()
                if len(pending) > 65536:
                    pending = b""
                added = False
                for line in lines:
                    if event := parse_event(line.decode(errors="replace"), platform):
                        recent.append({"event": event["event"], "epoch_ms": event["guest_epoch_ms"],
                                       "code": REASON_CODES[event["reason"]],
                                       "level": "error" if event["result"] in {"failure", "timeout"} else "info"})
                        added = True
                if added:
                    temporary.write_text("".join(json.dumps(event) + "\n" for event in recent))
                    temporary.replace(destination)
        finally:
            if source.poll() is None:
                source.terminate()
                try:
                    source.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    source.kill()


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
            if platform == "ios":
                reports = ios_crash_reports(device)
                (proof / "ios-process-reports.json").write_text(json.dumps(reports, indent=2) + "\n")
                failures = ios_kotlin_failure(temp / "parlor-process-logs" / device)
                (proof / "ios-kotlin-failures.json").write_text(json.dumps(failures, indent=2) + "\n")
                print(f"Retained {len(reports)} redacted crash summaries for this iOS debug app.")
            try:
                events = read_events(platform, device)
            except (OSError, subprocess.SubprocessError, ValueError) as error:
                (proof / "diagnostics-status.json").write_text(json.dumps({"error": type(error).__name__}) + "\n")
                continue
            (proof / "diagnostics.jsonl").write_text("".join(json.dumps(event) + "\n" for event in events))
            (proof / "diagnostics-status.json").write_text(json.dumps({"event_count": len(events)}) + "\n")
            print(f"Retained {len(events)} bounded ParlorP2p events on {platform}.")


if __name__ == "__main__":
    if len(sys.argv) == 6 and sys.argv[1] == "stream":
        stream_events(sys.argv[2], sys.argv[3], Path(sys.argv[4]), Path(sys.argv[5]))
    elif len(sys.argv) == 2 and sys.argv[1] in {"clock-before", "capture"}:
        main(sys.argv[1])
    else:
        raise SystemExit("Use clock-before, capture or stream")
