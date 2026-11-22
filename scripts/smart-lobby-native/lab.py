"""Build a runner plan and manage one UI Test Actions controller per device."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import struct
import plistlib
import sys
import time
from urllib.parse import urlsplit

CONFIG = Path("scripts/smart-lobby-native/dispatch.json")
DURATION = "55"
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
        print(f"build={json.dumps([entry for entry in builds if entry['target'] == 'ios'], separators=(',', ':'))}", file=stream)
        print(f"lab={json.dumps(labs, separators=(',', ':'))}", file=stream)
        reuse_candidate = (os.environ.get("GITHUB_EVENT_NAME") == "push"
                         and json.loads(CONFIG.read_text()).get("reuse_candidate") is True)
        print(f"reuse_candidate={str(reuse_candidate).lower()}", file=stream)


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


def check_ios_entitlement():
    binary = Path(sys.argv[2]).read_bytes()
    expected = sys.argv[3]
    magic, architecture, _, _, command_count, _, _, _ = struct.unpack_from("<8I", binary)
    if (magic, architecture) != (0xfeedfacf, 0x0100000c):
        raise ValueError("Expected an arm64 Mach-O simulator executable")
    cursor = 32
    for _ in range(command_count):
        command, size = struct.unpack_from("<2I", binary, cursor)
        if command == 0x19:  # LC_SEGMENT_64
            count, = struct.unpack_from("<I", binary, cursor + 64)
            for n in range(count):
                section = cursor + 72 + n * 80
                if binary[section:section + 16].rstrip(b"\x00") != b"__entitlements":
                    continue
                _, length, offset = struct.unpack_from("<QQI", binary, section + 32)
                rights = plistlib.loads(binary[offset:offset + length].rstrip(b"\x00"))
                if rights.get("application-identifier") != expected or rights.get("keychain-access-groups") != [expected]:
                    raise ValueError("Simulator app lacks the pinned test-only Keychain entitlement")
                print(json.dumps({"application_identifier": expected, "keychain_access_groups": [expected]}))
                return
        cursor += size
    raise ValueError("Simulator app lacks embedded entitlements")



def current_wifi_addresses(devices):
    addresses = {}
    for device in devices:
        output = subprocess.run(
            ["adb", "-s", device, "shell", "ip", "-4", "addr", "show", "wlan0"],
            capture_output=True, text=True, check=True, timeout=8,
        ).stdout
        address, = re.findall(r"\binet ([0-9.]+/[0-9]+)", output)
        addresses[device] = ipaddress.IPv4Interface(address)
    if (len({entry.ip for entry in addresses.values()}) != len(devices)
            or len({entry.network for entry in addresses.values()}) != 1
            or not all(entry.ip.is_private for entry in addresses.values())):
        raise ValueError("Guest Wi-Fi addresses do not represent one shared LAN")
    return addresses


def probe_wifi(addresses):
    probes = []
    for source in addresses:
        for peer in addresses:
            if source == peer:
                continue
            reply = subprocess.run(
                ["adb", "-s", source, "shell", "ping", "-I", "wlan0", "-c", "3", "-W", "2",
                 str(addresses[peer].ip)], capture_output=True, text=True, timeout=12,
            )
            match = re.search(r"3 packets transmitted, (\d+) (?:packets )?received", reply.stdout)
            probes.append({"from": source, "to": peer, "sent": 3,
                           "received": int(match[1]) if match else 0})
    return probes


def reassociate_wifi(guests):
    for target in guests:
        for mode in ("disable", "enable"):
            subprocess.run(["adb", "-s", target, "shell", "svc", "wifi", mode], check=True, timeout=8)
            if mode == "disable":
                time.sleep(2)
    time.sleep(8)


def wifi():
    temp = Path(os.environ["RUNNER_TEMP"])
    devices = (temp / "parlor-devices").read_text().splitlines()
    evidence = temp / "parlor-proof"
    for attempt in (1, 2):
        addresses = current_wifi_addresses(devices)
        probes = probe_wifi(addresses)
        missing = sorted({probe["to"] for probe in probes if probe["received"] != 3})
        report = evidence / ("shared-wifi.json" if not missing else f"shared-wifi-incomplete-{attempt}.json")
        report.write_text(json.dumps({"addresses": {k: str(v) for k, v in addresses.items()},
                                      "icmp": probes}, indent=2) + "\n")
        if not missing:
            return
        print(f"Wi-Fi preflight attempt {attempt} missed inbound probes on {len(missing)} guests", flush=True)
        if attempt == 1:
            reassociate_wifi(missing)
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
        "UI_TEST_DEEPLINK_SCHEMES": "parlor",
        "UI_TEST_ANDROID_IME": "0",
        "UI_TEST_IOS_AXE": (temp / "parlor-axe-path").read_text().strip()
        if target == "ios" and pairing == "mixed" else "",
        "UI_TEST_LAUNCH": f"xcrun simctl launch {device} me.parlor.ios.debug"
        if target == "ios" and re.fullmatch(r"[0-9a-fA-F-]{36}", device) else "",
    }
    lifecycle = Path(os.environ["LAN_UI_DIR"]) / "lifecycle.py"
    # Lifecycle output can contain control endpoints. Send only closed-vocabulary
    # startup categories to Actions and retain raw output in the job's private dir.
    markers = (
        "timed out", "identity", "screen capture", "accessibility", "booted ios simulator",
        "mid-session", "did not report the geometry", "out of alignment", "modulenotfounderror",
        "no display", "no supported", "failed to start tunnel", "did not publish", "connection refused",
        "rate limit", "too many requests", "failed to fetch", "cloudflared", "device offline",
        "device not found", "no devices/emulators found", "cannot connect", "permission denied",
        "temporary failure", "address already in use", "screenshot", "http 429", "status 429",
    )
    for attempt in range(1, 3):
        logfile = root / f"start-{attempt}.log"
        try:
            with logfile.open("wb") as log:
                subprocess.run([sys.executable, str(lifecycle), "start"], env=environment,
                               stdout=log, stderr=subprocess.STDOUT, check=True, timeout=300)
            break
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            lines = logfile.read_text(errors="replace").lower()
            clues = ", ".join(marker for marker in markers if marker in lines)
            print(f"{target} guest {index} controller startup attempt {attempt}: "
                  f"{clues or 'no recognized diagnostic marker'}", flush=True)
            # Never retry after a session receipt could already have been issued.
            if attempt == 2 or any(root.glob("ui-session-*")):
                raise
            if target == "ios":
                # An AXe startup can fail before allocating a controller.
                # Preserve the installed simulator, then retry via its window.
                environment["UI_TEST_IOS_AXE"] = ""
            time.sleep(4 + index)
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
    if pairing == "ios":
        # Both app instances are installed and launched. One desktop controller switches
        # between their named simulator windows; multiple controllers would compete for focus.
        devices, digests = devices[:1], digests[:1]
    (temp / "parlor-connections").mkdir()
    with ThreadPoolExecutor(max_workers=min(2, len(devices))) as pool:
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
            "check-ios-entitlement": check_ios_entitlement,
            "wifi": wifi,
            "start": start,
            "hold": hold,
            "stop": lambda: stop(Path(os.environ["RUNNER_TEMP"])),
        }[operation]()
    except (KeyError, OSError, ValueError, StopIteration, subprocess.SubprocessError) as error:
        if operation in {"start", "hold"}:
            stop(Path(os.environ["RUNNER_TEMP"]))
        sys.exit(f"{operation} failed ({type(error).__name__}). Controller logs are private.")
