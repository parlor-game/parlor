"""Build a runner plan and manage one UI Test Actions controller per device."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.parse import urlsplit

CONFIG = Path("scripts/smart-lobby-native/dispatch.json")
DURATION = "60"
CONTROLLER_PIN = "0c818c5d72e8c30c59bd1287c330a40392bf8e89"


def configuration():
    if os.environ.get("GITHUB_EVENT_NAME") == "push":
        payload = json.loads(CONFIG.read_text())
        pairing, digests = payload["pairing"], payload["token_digests"]
    else:
        pairing = os.environ["PAIRING"]
        digests = json.loads(os.environ["DIGESTS"])
    if pairing not in {"android", "ios", "mixed"} or not isinstance(digests, list):
        raise ValueError("Invalid pairing or credential digest array")
    counts = {2} if pairing == "mixed" else {2, 3, 5, 6}
    if (
        len(digests) not in counts
        or any(not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest)
               for digest in digests)
        or len(digests) != len(set(digests))
    ):
        raise ValueError("Provide a distinct SHA-256 digest per requested device")
    return pairing, digests


def plan():
    pairing, digests = configuration()
    targets = ("android", "ios") if pairing == "mixed" else (pairing,)
    builds = [{"target": target, "runner": runner} for target, runner in
              (("android", "ubuntu-24.04"), ("ios", "macos-26")) if target in targets]
    labs = [dict(entry, pairing=pairing, count=1 if pairing == "mixed" else len(digests))
            for entry in builds]
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as stream:
        print(f"build={json.dumps(builds, separators=(',', ':'))}", file=stream)
        print(f"lab={json.dumps(labs, separators=(',', ':'))}", file=stream)
        reuse_android = (pairing == "android" and os.environ.get("GITHUB_EVENT_NAME") == "push"
                         and json.loads(CONFIG.read_text()).get("reuse_android") is True)
        print(f"reuse_android={str(reuse_android).lower()}", file=stream)


def manifest():
    root = Path(sys.argv[2])
    if not (root / "Info.plist").is_file():
        raise ValueError("Missing application bundle")
    files = {}
    for path in sorted(root.rglob("*")):
        entry = path.relative_to(root).as_posix()
        if path.is_symlink():
            files[entry] = {"link": os.readlink(path)}
        elif path.is_file():
            with path.open("rb") as stream:
                files[entry] = hashlib.file_digest(stream, "sha256").hexdigest()
    print(json.dumps(files, indent=2))


def wifi():
    temp = Path(os.environ["RUNNER_TEMP"])
    devices = (temp / "parlor-devices").read_text().splitlines()
    report = temp / "parlor-proof" / "shared-wifi.json"
    addresses = {}
    for device in devices:
        output = (report.parent / device / "wlan0.txt").read_text()
        address, = re.findall(r"\binet ([0-9.]+/[0-9]+)", output)
        addresses[device] = ipaddress.IPv4Interface(address)
    if (len({entry.ip for entry in addresses.values()}) != len(devices)
            or len({entry.network for entry in addresses.values()}) != 1
            or not all(entry.ip.is_private for entry in addresses.values())):
        raise ValueError("Guest Wi-Fi addresses do not represent one shared LAN")
    probes = []
    for source in devices:
        for peer in devices:
            if source == peer:
                continue
            reply = subprocess.run(
                ["adb", "-s", source, "shell", "ping", "-I", "wlan0", "-c", "3", "-W", "2",
                 str(addresses[peer].ip)], capture_output=True, text=True, timeout=12,
            )
            match = re.search(r"3 packets transmitted, (\d+) (?:packets )?received", reply.stdout)
            probes.append({"from": source, "to": peer, "sent": 3,
                           "received": int(match[1]) if match else 0})
    report.write_text(json.dumps({"addresses": {k: str(v) for k, v in addresses.items()},
                                  "icmp": probes}, indent=2) + "\n")
    if any(probe["received"] != 3 for probe in probes):
        raise ValueError("Guest-to-guest Wi-Fi probes did not all succeed")


def directories(temp):
    root = temp / "parlor-controllers"
    return sorted(root.glob("device-*"))


def stop(temp):
    for root in directories(temp):
        for control in root.glob("ui-session-*"):
            (control / "stop").touch()


def start_one(temp, index, device, digest, pairing):
    target = os.environ["TARGET"]
    root = temp / "parlor-controllers" / f"device-{index}"
    root.mkdir(mode=0o700, parents=True)
    package = "me.parlor.android.debug" if target == "android" else "me.parlor.ios.debug"
    environment = {
        **os.environ,
        "RUNNER_TEMP": str(root),
        "GITHUB_OUTPUT": str(root / "output"),
        "GITHUB_STEP_SUMMARY": str(root / "summary"),
        "UI_TEST_TARGET": target,
        "UI_TEST_DEVICE": device,
        "UI_TEST_TOKEN_SHA256": digest,
        "UI_TEST_DURATION": DURATION,
        "UI_TEST_STARTUP_TIMEOUT": "90",
        "UI_TEST_TRANSPORT": "cloudflare-quick",
        "UI_TEST_APP_IDS": package,
        "UI_TEST_ANDROID_IME": "0",
        "UI_TEST_IOS_AXE": str((temp / "parlor-axe-path").read_text().strip())
        if target == "ios" and pairing == "ios" else "",
    }
    lifecycle = Path(os.environ["LAN_UI_DIR"]) / "lifecycle.py"
    # Lifecycle startup can log control endpoints. Keep its output outside uploaded paths.
    with (root / "start.log").open("wb") as log:
        subprocess.run([sys.executable, str(lifecycle), "start"], env=environment,
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=220)
    connection, = root.glob("ui-session-*/connection.json")
    data = json.loads(connection.read_text())
    origin = urlsplit(data["url"])
    if (data["target"] != target or data["transport"] != "cloudflare-quick"
            or origin.scheme != "https"
            or not re.fullmatch(r"[a-z0-9-]+\.trycloudflare\.com", origin.netloc)
            or origin.path not in {"", "/"} or origin.query or origin.fragment
            or not re.fullmatch(r"[a-zA-Z0-9_-]+", data["session_id"])):
        raise ValueError("Unexpected UI connection format")
    # Publish only bearer-free connection fields. The caller keeps all raw tokens.
    safe = {key: data[key] for key in ("url", "target", "transport", "session_id", "expires_unix")}
    safe.update(device=device, device_index=index, controller_pin=CONTROLLER_PIN)
    (temp / "parlor-connections" / f"{target}-{index}.json").write_text(json.dumps(safe, indent=2))
    # Start the lease immediately. The public artifact upload runs in the next step.
    environment.update(UI_TEST_CONNECTION_FILE=str(connection), UI_TEST_PUBLISH_OUTCOME="success")
    with (root / "hold.log").open("wb") as log:
        subprocess.Popen([sys.executable, str(lifecycle), "hold"], env=environment,
                         stdout=log, stderr=subprocess.STDOUT, start_new_session=True)


def start():
    pairing, digests = configuration()
    temp = Path(os.environ["RUNNER_TEMP"])
    target = os.environ["TARGET"]
    devices = (temp / "parlor-devices").read_text().splitlines()
    if pairing == "mixed":
        digests = [digests[0 if target == "android" else 1]]
    if len(devices) != len(digests) or len(set(devices)) != len(devices):
        raise ValueError("Independent device allocations do not match the operator roster")
    (temp / "parlor-connections").mkdir()
    with ThreadPoolExecutor(max_workers=len(devices)) as pool:
        jobs = [pool.submit(start_one, temp, index, device, digest, pairing)
                for index, (device, digest) in enumerate(zip(devices, digests), 1)]
        for job in jobs:
            job.result()
    print(f"Opened {len(devices)} independent {target} UI controllers", flush=True)


def hold():
    temp = Path(os.environ["RUNNER_TEMP"])
    outcomes = [next(root.glob("ui-session-*")) / "outcome" for root in directories(temp)]
    if not outcomes:
        raise ValueError("No UI controllers are available")
    deadline = time.monotonic() + int(DURATION) * 60 + 45
    while time.monotonic() < deadline:
        statuses = [path.read_text().strip() if path.exists() else None for path in outcomes]
        if any(status not in {None, "success"} for status in statuses):
            raise ValueError("A UI controller exited before successful teardown")
        if all(status == "success" for status in statuses):
            print(f"Closed {len(outcomes)} UI controllers")
            return
        time.sleep(2)
    raise TimeoutError("The controllers did not finish within the bounded session window")


if __name__ == "__main__":
    operation = sys.argv[1]
    try:
        {
            "plan": plan,
            "pairing": lambda: print(configuration()[0]),
            "manifest": manifest,
            "wifi": wifi,
            "start": start,
            "hold": hold,
            "stop": lambda: stop(Path(os.environ["RUNNER_TEMP"])),
        }[operation]()
    except (KeyError, OSError, ValueError, StopIteration, subprocess.SubprocessError) as error:
        if operation in {"start", "hold"}:
            stop(Path(os.environ["RUNNER_TEMP"]))
        sys.exit(f"{operation} failed ({type(error).__name__}). Controller logs are private.")
