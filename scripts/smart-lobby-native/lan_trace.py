"""Sample the job-owned relay and the installed Android app's TCP listener count."""
import json
import os
from pathlib import Path
import re
import subprocess
import time

BRIDGE_COUNTERS = (
    "arp", "icmp", "tcp", "mdns", "p2pkit_mdns_query", "p2pkit_mdns_response", "fault_drop", "fault_queue_full",
)


def android_listeners(app):
    if not re.fullmatch(r"[1-9][0-9]{0,7}", app):
        raise ValueError("Unavailable app process")
    # Only the verified disposable emulator and its recorded app PID are read.
    script = (
        f"test -r /proc/{app}/fd && "
        f"for fd in /proc/{app}/fd/*; do readlink \"$fd\"; done;"
        "cat /proc/net/tcp /proc/net/tcp6"
    )
    response = subprocess.run(
        ["adb", "-s", "emulator-5554", "shell", script],
        check=True, capture_output=True, text=True, timeout=8,
    ).stdout
    owned = set(re.findall(r"^socket:\[([0-9]+)\]$", response, re.MULTILINE))
    listening = set()
    for line in response.splitlines():
        columns = line.split()
        if len(columns) >= 10 and columns[3] == "0A" and columns[9] in owned:
            listening.add(columns[9])
    if "local_address" not in response or not owned:
        raise ValueError("Owned listener observation unavailable")
    return listening


def relay_sample(path):
    report = json.loads(path.read_text())
    return {
        lane: {key: report[lane].get(key, 0) for key in BRIDGE_COUNTERS}
        for lane in ("sent", "received")
    }


def main():
    root = Path(os.environ["RUNNER_TEMP"])
    target = os.environ["TARGET"]
    relay = root / "mobile-lan-preflight/traffic.json"
    ready = root / "mobile-lan/ready"
    root_adb = target == "android" and (root / "parlor-listener-root").exists()
    owns_listener = None
    pid_path = root / "parlor-proof/emulator-5554/app-pid.txt"
    while ready.is_file():
        sample = {"runner_epoch_ms": time.time_ns() // 1_000_000, "platform": target}
        try:
            sample["relay"] = relay_sample(relay)
        except (OSError, ValueError, KeyError):
            sample["relay"] = None
        if target == "android":
            sample["owned_listeners"] = None
            if root_adb:
                try:
                    listeners = android_listeners(pid_path.read_text().strip())
                    sample["owned_listeners"] = len(listeners)
                    if owns_listener is None and listeners:
                        owns_listener = listeners
                    sample["initial_listeners_retained"] = (
                        len(owns_listener & listeners) if owns_listener is not None else None
                    )
                except (OSError, subprocess.SubprocessError, ValueError):
                    pass
        sample["complete_epoch_ms"] = time.time_ns() // 1_000_000
        print(json.dumps(sample, separators=(",", ":")), flush=True)
        time.sleep(2)


if __name__ == "__main__":
    main()
