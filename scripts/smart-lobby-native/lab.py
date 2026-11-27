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
DURATION = os.environ.get("PARLOR_UI_DURATION", "80")
CONTROLLER_PIN = "0c818c5d72e8c30c59bd1287c330a40392bf8e89"
RETAINED_CANDIDATE = "e4e2b73a6286212c9f8c19ec3e421238fbbf4deb"
DUAL_IOS_SOURCE = "0438fc6ff53780e87fa111f68352b15dd44a3ec3"
DUAL_IOS_PRODUCER = 45121464371
PARTY_NIGHT_SOURCE = "3154ed26fe18831c5b5a08f1bfa31faa353cbda0"
PARTY_NIGHT_PRODUCER = 45343614595
PRESETS_CANDIDATE = "0b13c7892fd84dae800a095985a6db6b321a2281"
PLAYLIST_EDIT_CANDIDATE = "eddc1b1f773be8499e05464e08e2b51937f3957a"
STANDINGS_CANDIDATE = "dec09061e310b84054a6bebb23f9532e8e66c398"
PARTY_INTEGRATION_CANDIDATE = "a357b16cdf4034b22ffbbc8968526363f8c530cd"


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
    payload = json.loads(CONFIG.read_text()) if os.environ.get("GITHUB_EVENT_NAME") == "push" else {}
    candidate = payload.get("candidate", RETAINED_CANDIDATE)
    observe_reconnect = payload.get("observe_reconnect") is True
    reuse_candidate = payload.get("reuse_candidate") is True
    staged_ios_pair = payload.get("staged_ios_pair") is True
    extended_match = payload.get("extended_match") is True
    if not isinstance(candidate, str) or not re.fullmatch(r"[0-9a-f]{40}", candidate):
        raise ValueError("Candidate must be an exact commit SHA")
    rules_restore_android = payload.get("rules_restore_android") is True
    if rules_restore_android and (
        candidate != "758fa763168c643df8d86267cc1d0b7f0d1322f4"
        or pairing != "android" or len(digests) != 5 or reuse_candidate or observe_reconnect
    ):
        raise ValueError("The rules restore trial requires five Android installs of the exact candidate")
    reuse_presets_android = payload.get("reuse_exact_presets_android") is True
    if reuse_presets_android and (
        candidate != PRESETS_CANDIDATE or pairing != "android" or len(digests) != 2 or not reuse_candidate
    ):
        raise ValueError("The saved presets fallback requires the verified retained APK and two Android installs")
    reuse_party_night = reuse_candidate and candidate == PARTY_NIGHT_SOURCE
    reuse_playlist_edit = (
        candidate == PLAYLIST_EDIT_CANDIDATE and pairing == "mixed"
        and reuse_candidate and observe_reconnect and not extended_match
    )
    visible_ios = payload.get("visible_ios_window") is True
    if reuse_party_night and (pairing != "mixed" or not observe_reconnect or extended_match):
        raise ValueError("The retained Party Night builds require the mixed reconnect topology")
    fresh_playlist = (
        candidate in {PRESETS_CANDIDATE, PLAYLIST_EDIT_CANDIDATE, STANDINGS_CANDIDATE}
        and pairing == "mixed" and not reuse_candidate and observe_reconnect
    )
    reuse_standings_android = payload.get("reuse_standings_android") is True
    standings_mixed = fresh_playlist and visible_ios
    standings_android_pair = (
        pairing == "android" and len(digests) == 2 and reuse_candidate and not observe_reconnect and not visible_ios
    )
    if reuse_standings_android and (
        candidate != STANDINGS_CANDIDATE or not (standings_mixed or standings_android_pair)
    ):
        raise ValueError("The retained standings APK requires the matching mixed or two-Android trial")
    exact_pr355 = (
        candidate == "d2a55eb5f4880798b6e07377df249e370dd2ff11"
        and pairing == "mixed" and observe_reconnect
    )
    exact_pr356 = (
        candidate == "08c09f72f267b3fa7d83c381d813d1297e59f3c4"
        and pairing == "mixed" and observe_reconnect
    )
    build_only = payload.get("build_only") is True
    party_integration_packages = (
        build_only and candidate == PARTY_INTEGRATION_CANDIDATE and pairing == "mixed"
        and not reuse_candidate and not observe_reconnect
    )
    if build_only and (not (exact_pr356 or party_integration_packages) or reuse_candidate):
        raise ValueError("Package-only qualification requires a pinned mixed-platform candidate")
    if visible_ios and not (
        (reuse_party_night or reuse_playlist_edit or fresh_playlist or exact_pr355 or exact_pr356) and payload.get("require_native_ios") is False
    ):
        raise ValueError("The visible Simulator controller requires a pinned mixed-platform trial")
    if extended_match and not fresh_playlist and (pairing != "mixed" or not reuse_candidate or
                           not observe_reconnect or candidate != DUAL_IOS_SOURCE):
        raise ValueError("The extended match requires the exact retained mixed-platform builds")
    if observe_reconnect and not (fresh_playlist or reuse_playlist_edit or exact_pr355 or exact_pr356) and (pairing != "mixed" or reuse_candidate != (extended_match or reuse_party_night) or
                              candidate == RETAINED_CANDIDATE):
        raise ValueError("Reconnect observations require a verified mixed pair")
    if staged_ios_pair and (pairing != "ios" or len(digests) != 2 or not reuse_candidate or
                            observe_reconnect or candidate != DUAL_IOS_SOURCE):
        raise ValueError("The staged simulator pair requires the pinned two-iOS build")
    if candidate != RETAINED_CANDIDATE and not (
        observe_reconnect or staged_ios_pair or reuse_presets_android or rules_restore_android or reuse_standings_android
        or party_integration_packages
    ):
        raise ValueError("An alternate candidate is only used for the reconnect experiment")
    targets = ("android", "ios") if pairing == "mixed" else (pairing,)
    ios_runner = "macos-15" if reuse_standings_android else "macos-26"
    runners = {"android": "ubuntu-24.04", "ios": ios_runner}
    builds = [
        {"target": target, "runner": runners[target]} for target in targets
        if target == "ios" or ((observe_reconnect or rules_restore_android or party_integration_packages) and not reuse_standings_android)
    ]
    labs = [
        {"target": target, "runner": runners[target], "pairing": pairing, "count": 1 if pairing == "mixed" else len(digests)}
        for target in targets
    ]
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as stream:
        print(f"build={json.dumps(builds, separators=(',', ':'))}", file=stream)
        print(f"lab={json.dumps(labs, separators=(',', ':'))}", file=stream)
        print(f"reuse_standings_android={str(reuse_standings_android).lower()}", file=stream)
        print(f"rules_restore_android={str(rules_restore_android).lower()}", file=stream)
        print(f"reuse_presets_android={str(reuse_presets_android).lower()}", file=stream)
        print(f"reuse_candidate={str(reuse_candidate).lower()}", file=stream)
        print(f"observe_reconnect={str(observe_reconnect).lower()}", file=stream)
        print(f"staged_ios_pair={str(staged_ios_pair).lower()}", file=stream)
        print(f"extended_match={str(extended_match).lower()}", file=stream)
        print(f"ios_retained_run={DUAL_IOS_PRODUCER if staged_ios_pair else 45082889009}", file=stream)
        print(f"retained_party_night_run={PARTY_NIGHT_PRODUCER if reuse_party_night else ''}", file=stream)
        print(f"build_only={str(build_only).lower()}", file=stream)
        print(f"reuse_pr356={str(exact_pr356 and reuse_candidate).lower()}", file=stream)
        print(f"reuse_playlist_edit={str(reuse_playlist_edit).lower()}", file=stream)
        print(f"reuse_pr355={str(exact_pr355 and reuse_candidate).lower()}", file=stream)
        print(f"visible_ios_window={str(visible_ios).lower()}", file=stream)
        print(f"candidate={candidate}", file=stream)


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


def wait_for_wifi_addresses(devices, seconds=35):
    deadline = time.monotonic() + seconds
    while True:
        try:
            return current_wifi_addresses(devices)
        except (ValueError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as error:
            # DHCP and adb can take a moment to settle after reassociation.
            if time.monotonic() >= deadline:
                raise ValueError("Distinct guest Wi-Fi addresses did not settle") from error
            time.sleep(2)


def probe_wifi(addresses):
    probes = []
    for source in addresses:
        for peer in addresses:
            if source == peer:
                continue
            probe = {"from": source, "to": peer, "sent": 3, "received": 0}
            try:
                reply = subprocess.run(
                    ["adb", "-s", source, "shell", "ping", "-I", "wlan0", "-c", "3", "-W", "2",
                     str(addresses[peer].ip)], capture_output=True, text=True, timeout=12,
                )
            except subprocess.TimeoutExpired:
                # A silent adb probe is a missed direction. Keep the evidence
                # and let the existing single reassociation handle it.
                probe["status"] = "ADB_TIMEOUT"
            else:
                match = re.search(r"3 packets transmitted, (\d+) (?:packets )?received", reply.stdout)
                probe["received"] = int(match[1]) if match else 0
                probe["status"] = "COMPLETE" if match else "NO_PING_SUMMARY"
            probes.append(probe)
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
        addresses = wait_for_wifi_addresses(devices)
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
        for control in root.glob("attempt-*/ui-session-*"):
            (control / "stop").touch()


def start_one(temp, index, device, digest, pairing):
    target = os.environ["TARGET"]
    root = temp / "parlor-controllers" / f"device-{index}"
    root.mkdir(mode=0o700, parents=True)
    package = "me.parlor.android.debug" if target == "android" else "me.parlor.ios.debug"
    environment = {
        **os.environ,
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
        if target == "ios" and os.environ.get("VISIBLE_IOS_WINDOW") != "true" else "",
        "UI_TEST_LAUNCH": f"xcrun simctl launch {device} me.parlor.ios.debug"
        if target == "ios" and re.fullmatch(r"[0-9a-fA-F-]{36}", device) else "",
    }
    lifecycle = Path(os.environ["LAN_UI_DIR"]) / "lifecycle.py"
    if target == "android" and pairing == "mixed" and os.environ.get("LAN_FAULTS") == "1":
        environment["UI_TEST_NETWORK_CONTROL"] = str(temp / "mobile-lan/control/link.sock")
        environment["PARLOR_NETWORK_CONTROL_ROOT"] = str(temp)
    visual_fallback = os.environ.get("GITHUB_EVENT_NAME") != "push" or not json.loads(CONFIG.read_text()).get(
        "require_native_ios", False
    )
    # Lifecycle output can contain control endpoints. Send only closed-vocabulary
    # startup categories to Actions and retain raw output in the job's private dir.
    markers = (
        "timed out", "identity", "screen capture", "accessibility", "booted ios simulator",
        "mid-session", "did not report the geometry", "out of alignment", "modulenotfounderror",
        "no display", "no supported", "failed to start tunnel", "did not publish", "connection refused",
        "rate limit", "too many requests", "failed to fetch", "cloudflared", "device offline",
        "device not found", "no devices/emulators found", "cannot connect", "permission denied",
        "temporary failure", "address already in use", "screenshot", "http 429", "status 429",
        "ui server exited with status", "could not confirm cleanup", "startup was cancelled",
    )
    for attempt in range(1, 3):
        attempt_root = root / f"attempt-{attempt}"
        attempt_root.mkdir(mode=0o700)
        environment.update(RUNNER_TEMP=str(attempt_root), GITHUB_OUTPUT=str(attempt_root / "output"),
                           GITHUB_STEP_SUMMARY=str(attempt_root / "summary"))
        logfile = attempt_root / "start.log"
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
            # The upstream lifecycle creates its directory before startup.
            # A failed attempt is retryable only after it has stopped and
            # never published a connection document.
            sessions = list(attempt_root.glob("ui-session-*"))
            if attempt == 2 or any((s / "connection.json").exists() or not (s / "outcome").exists()
                                   for s in sessions):
                raise
            if target == "ios" and pairing == "mixed" and visual_fallback and any(
                marker in lines for marker in ("axe", "screen capture", "accessibility", "out of alignment")
            ):
                environment["UI_TEST_IOS_AXE"] = ""
            time.sleep(4 + index)
    connection, = attempt_root.glob("ui-session-*/connection.json")
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
    with ThreadPoolExecutor(max_workers=1 if pairing == "ios" else min(2, len(devices))) as pool:
        jobs = [pool.submit(start_one, temp, index, device, digest, pairing)
                for index, (device, digest) in enumerate(zip(devices, digests), 1)]
        for job in jobs:
            job.result()
    print(f"Opened {len(devices)} independent {target} UI controllers", flush=True)


def hold():
    temp = Path(os.environ["RUNNER_TEMP"])
    outcomes = []
    for root in directories(temp):
        connection, = root.glob("attempt-*/ui-session-*/connection.json")
        outcomes.append(connection.with_name("outcome"))
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
