"""Probe the standard 14 GB Intel runner before building a separate Intel app."""
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import time


def run(*args, timeout=30):
    return subprocess.run(args, capture_output=True, text=True, check=True, timeout=timeout).stdout.strip()


def simctl(*args, timeout=30):
    return run("/usr/bin/xcrun", "simctl", *args, timeout=timeout)


def owned_simulator(seat, runtime):
    device = simctl("create", f"Parlor Intel probe {seat}",
                    "com.apple.CoreSimulator.SimDeviceType.iPhone-16e", runtime, timeout=90)
    if not re.fullmatch(r"[0-9A-Fa-f-]{36}", device):
        raise RuntimeError("Unexpected test device identifier")
    return device


def springboard_pid(device):
    table = simctl("spawn", device, "launchctl", "list", timeout=12)
    match = re.search(r"^([0-9]+)\s+\S+\s+com\.apple\.SpringBoard$", table, re.M)
    return int(match[1]) if match else None


def boot(device):
    started = time.monotonic()
    deadline = started + 210
    try:
        simctl("boot", device, timeout=75)
    except subprocess.SubprocessError as error:
        return {"ready": False, "stage": "boot_request", "error": type(error).__name__,
                "elapsed_seconds": round(time.monotonic() - started, 3)}
    while time.monotonic() < deadline:
        try:
            pid = springboard_pid(device)
        except subprocess.SubprocessError:
            pid = None
        if pid is not None:
            return {"ready": True, "springboard_pid": pid,
                    "elapsed_seconds": round(time.monotonic() - started, 3)}
        time.sleep(3)
    return {"ready": False, "stage": "springboard", "elapsed_seconds": round(time.monotonic() - started, 3)}


def main():
    temporary = Path(os.environ["RUNNER_TEMP"])
    keep_booted = sys.argv[1:] == ["--keep-booted"]
    if sys.argv[1:] and not keep_booted:
        raise ValueError("Unknown probe argument")
    proof = temporary / "parlor-proof" if keep_booted else temporary
    proof.mkdir(exist_ok=True)
    path = proof / "native-intel-simulator-capacity.json"
    summary = {"architecture": platform.machine(), "parlor_app_built": False, "seats": [],
               "strategy": "wait for the first SpringBoard before starting the second simulator"}
    devices = []
    retained = False
    try:
        if summary["architecture"] != "x86_64":
            raise RuntimeError("Expected the independent Intel test allocation")
        summary["physical_memory_bytes"] = int(run("sysctl", "-n", "hw.memsize"))
        summary["xcode"] = run("xcodebuild", "-version").splitlines()
        if "Build version 17C529" not in summary["xcode"]:
            raise RuntimeError("The qualified Xcode build is unavailable on the candidate runner")
        # Run 45281706309 confirmed this runtime on the exact Xcode/runner
        # combination. Requiring it at create avoids a second inventory pass.
        runtime = "com.apple.CoreSimulator.SimRuntime.iOS-26-2"
        summary["runtime"] = runtime
        for seat in (1, 2):
            try:
                device = owned_simulator(seat, runtime)
            except subprocess.SubprocessError as error:
                summary["seats"].append({"ready": False, "stage": "create", "error": type(error).__name__})
                break
            devices.append(device)
            state = boot(device)
            summary["seats"].append(state)
            if not state["ready"]:
                break
        if len(summary["seats"]) == 2 and all(seat["ready"] for seat in summary["seats"]):
            with ThreadPoolExecutor(max_workers=2) as pool:
                summary["concurrent_pids"] = list(pool.map(springboard_pid, devices))
        observed = summary.get("concurrent_pids", [])
        if len(set(observed)) != 2 or None in observed:
            raise RuntimeError("Two live SpringBoards were not observed together")
        if keep_booted:
            for device in devices:
                services = simctl("spawn", device, "launchctl", "list", timeout=12)
                (proof / f"simulator-{device}-services.txt").write_text(services + "\n")
            (temporary / "parlor-devices").write_text("\n".join(devices) + "\n")
            retained = True
    finally:
        path.write_text(json.dumps(summary, indent=2) + "\n")
        if not retained:
            for device in devices:
                try:
                    simctl("delete", device, timeout=40)
                except subprocess.SubprocessError:
                    pass


if __name__ == "__main__":
    main()
