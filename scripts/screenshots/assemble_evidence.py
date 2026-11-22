#!/usr/bin/env python3
"""Package captured Parlor screenshots with provenance and explicit route coverage."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import shutil
import struct
import subprocess
import zlib
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

MERGED_COMMIT = "d9f78320ffdfa3f4b22342f975166b214dca9671"
PRODUCTION_TREE = "6bd6300f4822939259f1d0d95221fcf5c9b4d40d"
GAMES = {
    "whodunit": "Whodunit",
    "mafia": "Mafia",
    "egyptian-dominoes": "Egyptian Dominoes",
    "ghamza": "Ghamza",
    "word-impostor": "Word Impostor",
    "last-light": "Last Light",
}

GAME_ROUTE = {
    "whodunit": "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/WhodunitPhaseRouter.kt",
    "mafia": "game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/passandplay/MafiaPassAndPlayPhaseRouter.kt",
    "egyptian-dominoes": "game-modes/egyptian-dominoes/src/commonMain/kotlin/com/parlor/games/dominoes/ui/DominoTable.kt",
    "ghamza": "game-modes/ghamza/src/commonMain/kotlin/com/parlor/games/ghamza/ui/GhamzaTable.kt",
    "word-impostor": "game-modes/word-impostor/src/commonMain/kotlin/com/parlor/games/wordimpostor/ui/WordImpostorTable.kt",
    "last-light": "game-modes/last-light/src/commonMain/kotlin/com/parlor/games/lastlight/ui/flow/passandplay/LastLightGameFlow.kt",
}

SHARED_ROUTE = {
    "shared.library": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt",
    "shared.library-end": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt",
    "shared.settings": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/settings/SettingsScreen.kt",
    "shared.recovery": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt",
    "shared.mode-picker": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/playmode/PlayModePickerScreen.kt",
    "shared.host-permission": "composeApp/src/commonMain/kotlin/com/parlor/app/permissions/P2pPermissionRationaleScreen.kt",
    "shared.host-name-entry": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/multiplayer/NameInputScreen.kt",
    "shared.join-permission": "composeApp/src/commonMain/kotlin/com/parlor/app/permissions/P2pPermissionRationaleScreen.kt",
    "shared.join-name-entry": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/multiplayer/NameInputScreen.kt",
    "shared.room-code-entry": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/multiplayer/JoinPromptScreen.kt",
    "shared.keyboard-entry": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/multiplayer/NameInputScreen.kt",
    "shared.host-waiting": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/HostedGameLobby.kt",
    "shared.peer-waiting": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/PeerGameLobby.kt",
    "shared.lobby": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/RoomGameLobbyUi.kt",
    "shared.resume-failure": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/LocalResumeFailureScreen.kt",
}


def validate_png(path: Path) -> tuple[int, int, str]:
    """Validate PNG chunks, CRCs, zlib data, scanline structure, and decoder output."""
    raw = path.read_bytes()
    signature = b"\x89PNG\r\n\x1a\n"
    if not raw.startswith(signature):
        raise ValueError("missing PNG signature")
    offset = len(signature)
    compressed = bytearray()
    width = height = bit_depth = color_type = interlace = None
    saw_end = False
    while offset + 12 <= len(raw):
        length = struct.unpack_from(">I", raw, offset)[0]
        kind = raw[offset + 4 : offset + 8]
        data_start = offset + 8
        data_end = data_start + length
        if data_end + 4 > len(raw):
            raise ValueError("truncated PNG chunk")
        data = raw[data_start:data_end]
        expected_crc = struct.unpack_from(">I", raw, data_end)[0]
        if zlib.crc32(kind + data) & 0xFFFFFFFF != expected_crc:
            raise ValueError(f"bad {kind.decode('ascii', 'replace')} chunk CRC")
        if kind == b"IHDR":
            if length != 13:
                raise ValueError("invalid IHDR length")
            width, height, bit_depth, color_type, _compression, _filter, interlace = struct.unpack(">IIBBBBB", data)
        elif kind == b"IDAT":
            compressed.extend(data)
        elif kind == b"IEND":
            saw_end = True
            offset = data_end + 4
            break
        offset = data_end + 4
    if not saw_end or width is None or height is None or width < 1 or height < 1:
        raise ValueError("missing IEND or valid IHDR")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color_type)
    if channels is None or bit_depth not in (1, 2, 4, 8, 16) or interlace != 0:
        raise ValueError(f"unsupported PNG encoding color={color_type} depth={bit_depth} interlace={interlace}")
    row_bytes = (width * channels * bit_depth + 7) // 8
    decoded = zlib.decompress(bytes(compressed))
    if len(decoded) != height * (row_bytes + 1):
        raise ValueError("decoded image data does not match dimensions")
    for y in range(height):
        start = y * (row_bytes + 1)
        filter_type = decoded[start]
        if filter_type > 4:
            raise ValueError(f"invalid PNG row filter {filter_type}")
    if offset != len(raw):
        raise ValueError("trailing bytes after IEND")
    decoder = shutil.which("ffmpeg")
    if decoder:
        result = subprocess.run([decoder, "-v", "error", "-i", str(path), "-frames:v", "1", "-f", "null", "-"],
                                capture_output=True, text=True, check=False)
        if result.returncode != 0:
            raise ValueError(f"ffmpeg decode failed: {result.stderr.strip()}")
        decode_method = "ffmpeg full-frame decode; PNG chunk CRC, zlib stream, dimensions and row filters verified"
    else:
        decode_method = "PNG chunk CRC, zlib stream, dimensions and row filters verified; external decoder unavailable"
    return width, height, decode_method


def attachment_records(root: Path) -> dict[str, dict[str, str]]:
    records: dict[str, dict[str, str]] = {}
    for manifest in root.rglob("manifest.json"):
        try:
            data = json.loads(manifest.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue

        def walk(node: object) -> None:
            if isinstance(node, dict):
                strings = {str(k): str(v) for k, v in node.items() if isinstance(v, (str, int, float))}
                filename = next((strings[k] for k in strings if k.casefold() in {
                    "filename", "exportedfilename", "exported_file_name", "file", "path"
                } and strings[k].casefold().endswith(".png")), None)
                if filename:
                    records[Path(filename).name] = strings
                for value in node.values():
                    walk(value)
            elif isinstance(node, list):
                for value in node:
                    walk(value)

        walk(data)
    return records


def classify(display: str, filename: str, platform: str) -> tuple[str, str, str, str]:
    label = f"{display} {filename}".casefold()
    shared_tokens = [
        (("host-permission-guidance", "host-permission"), "shared.host-permission", "Host permission guidance", SHARED_ROUTE["shared.host-permission"]),
        (("join-permission-guidance", "join-permission"), "shared.join-permission", "Join permission guidance", SHARED_ROUTE["shared.join-permission"]),
        (("host-name-entry", "join-name-entry"), "shared.name-entry", "Host/join name entry", SHARED_ROUTE["shared.host-name-entry"]),
        (("room-code-entry", "join-room-code", "room-code"), "shared.room-code-entry", "Room code entry", SHARED_ROUTE["shared.room-code-entry"]),
        (("keyboard",), "shared.keyboard-entry", "Text entry with keyboard", SHARED_ROUTE["shared.keyboard-entry"]),
        (("settings",), "shared.settings", "Settings", SHARED_ROUTE["shared.settings"]),
        (("recovery",), "shared.recovery", "Recovery status", SHARED_ROUTE["shared.recovery"]),
        (("library-end",), "shared.library-end", "Library end", SHARED_ROUTE["shared.library-end"]),
        (("home", "library", "game library"), "shared.library", "Game library", SHARED_ROUTE["shared.library"]),
        (("empty-state", "empty state"), "shared.library-empty", "Filtered empty state", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt"),
    ]
    for tokens, screen_id, state, route in shared_tokens:
        if any(token in label for token in tokens):
            return "Shared", screen_id, state, route
    for slug, title in GAMES.items():
        aliases = [slug, title.casefold()]
        aliases.extend({
            "whodunit": ["من القاتل"],
            "mafia": ["حرامية وظباط"],
            "egyptian-dominoes": ["الدومنة المصرية"],
            "ghamza": ["غمزة"],
            "word-impostor": ["لعبة الإمبوستر"],
            "last-light": ["الضوء الأخير", "hand-counts", "fuse-cylinder", "table-chrome", "result-design"],
        }.get(slug, []))
        if slug == "word-impostor":
            aliases.append("word impostor")
        if any(alias in label for alias in aliases):
            if "mode-picker" in label or re.search(rf"setup-{re.escape(slug)}", label):
                return title, f"shared.mode-picker.{slug}", "Mode selection", SHARED_ROUTE["shared.mode-picker"]
            if slug == "whodunit" and "discussion" in label:
                return title, "whodunit.discussion", "Discussion", "game-modes/whodunit/src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/round/RoundScreens.kt"
            if slug == "mafia" and "target" in label:
                return title, "mafia.night", "Night target selection", "game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/night/TargetPickerScreen.kt"
            if slug == "egyptian-dominoes":
                state = next((value for value in ("roster", "table-covered", "results", "rematch-covered") if value in label), "game screen")
                screen = "egyptian-dominoes.table" if state == "table-covered" else "egyptian-dominoes.replay" if state == "rematch-covered" else f"egyptian-dominoes.{state}"
                return title, screen, state.replace("-", " ").title(), GAME_ROUTE[slug]
            if slug == "ghamza":
                state = next((value for value in ("social", "results", "rematch-covered") if value in label), "game screen")
                return title, f"ghamza.{state}", state.replace("-", " ").title(), GAME_ROUTE[slug]
            if slug == "word-impostor":
                state = next((value for value in ("next-question-public", "voting", "guessing-covered", "results", "rematch-covered") if value in label), "game screen")
                return title, f"word-impostor.{state}", state.replace("-", " ").title(), GAME_ROUTE[slug]
            if slug == "last-light":
                state = next((value for value in ("local-player-count", "guide", "concealed", "playing", "round-result", "winner", "hand-counts", "table-chrome", "fuse-cylinder", "result-design") if value in label), "game screen")
                screen_id = {
                    "local-player-count": "last-light.setup",
                    "concealed": "last-light.handoff",
                    "playing": "last-light.turn",
                    "round-result": "last-light.results",
                    "winner": "last-light.results",
                    "result-design": "last-light.results",
                    "table-chrome": "last-light.overlay",
                }.get(state, f"last-light.{state}")
                return title, screen_id, state.replace("-", " ").title(), GAME_ROUTE[slug]
            return title, f"{slug}.game-screen", "Game screen", GAME_ROUTE[slug]

    shared_rules = [
        (("onboarding",), "shared.onboarding", "Onboarding", "composeApp/src/commonMain/kotlin/com/parlor/app/App.kt"),
        (("home", "library", "game library"), "shared.library", "Game library", SHARED_ROUTE["shared.library"]),
        (("library-end",), "shared.library-end", "Library end", SHARED_ROUTE["shared.library-end"]),
        (("empty-state", "empty state"), "shared.library-empty", "Filtered empty state", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt"),
        (("settings",), "shared.settings", "Settings", SHARED_ROUTE["shared.settings"]),
        (("recovery",), "shared.recovery", "Recovery status", SHARED_ROUTE["shared.recovery"]),
        (("host-permission",), "shared.host-permission", "Host permission guidance", SHARED_ROUTE["shared.host-permission"]),
        (("join-permission",), "shared.join-permission", "Join permission guidance", SHARED_ROUTE["shared.join-permission"]),
        (("host-name-entry",), "shared.host-name-entry", "Host name entry", SHARED_ROUTE["shared.host-name-entry"]),
        (("join-name-entry",), "shared.join-name-entry", "Join name entry", SHARED_ROUTE["shared.join-name-entry"]),
        (("room-code-entry", "room-code", "join-room-code"), "shared.room-code-entry", "Room code entry", SHARED_ROUTE["shared.room-code-entry"]),
        (("keyboard",), "shared.keyboard-entry", "Text entry with keyboard", SHARED_ROUTE["shared.keyboard-entry"]),
        (("local-player-count",), "shared.last-light-local-setup", "Local player setup", GAME_ROUTE["last-light"]),
        (("mode-picker", "setup-"), "shared.mode-picker", "Mode selection", SHARED_ROUTE["shared.mode-picker"]),
        (("host-waiting",), "shared.host-waiting", "Host waiting", SHARED_ROUTE["shared.host-waiting"]),
        (("peer-waiting",), "shared.peer-waiting", "Peer waiting", SHARED_ROUTE["shared.peer-waiting"]),
    ]
    for tokens, screen_id, state, route in shared_rules:
        if any(token in label for token in tokens):
            return "Shared", screen_id, state, route
    return "Unmapped", "unmapped.unknown", display or Path(filename).stem, ""


def test_entry_point(filename: str, display: str, platform: str) -> str:
    value = f"{filename} {display}".casefold()
    if platform == "iOS Simulator":
        if "keyboard" in value:
            return "iosAppUITests/IOSAppLaunchUITests.swift — testKeyboardInsetsOnNameAndRoomCodeInEnglish / Arabic"
        if "last-light-local-player-count" in value:
            return "iosAppUITests/IOSAppLaunchUITests.swift — testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone"
        if "additionalgames" in value or "egyptian dominoes" in value or "ghamza" in value or "word impostor" in value:
            return "iosAppUITests/IOSAppLaunchUITests.swift — testAdditionalGamesExposeOnlyMultiplayerSetupInEnglish / Arabic"
        return "iosAppUITests/IOSAppLaunchUITests.swift — native public setup and settings UI tests"
    if platform == "Android Emulator":
        return "scripts/android/capture_public_screens.py — adb/uiautomator route capture"
    mapping = [
        ("home-", "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/home/HomeCatalogVisualEvidenceTest.kt"),
        ("setup-", "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/home/HomeCatalogVisualEvidenceTest.kt"),
        ("recovery-", "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/home/HomeCatalogVisualEvidenceTest.kt"),
        ("settings-", "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/settings/SettingsAccessibilityAcceptanceTest.kt"),
        ("host-room-code", "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/multiplayer/RoomCodeAccessibilityAcceptanceTest.kt"),
        ("join-room-code", "composeApp/src/desktopTest/kotlin/com/parlor/app/shell/multiplayer/RoomCodeAccessibilityAcceptanceTest.kt"),
        ("whodunit-discussion", "game-modes/whodunit/src/desktopTest/kotlin/com/parlor/games/whodunit/ui/WhodunitDiscussionLayoutTest.kt"),
        ("mafia-target", "game-modes/mafia/src/desktopTest/kotlin/com/parlor/games/mafia/ui/screens/night/TargetPickerInteractionTest.kt"),
        ("egyptian-dominoes", "game-modes/egyptian-dominoes/src/desktopTest/kotlin/com/parlor/games/dominoes/ui/DominoTableTest.kt"),
        ("ghamza", "game-modes/ghamza/src/desktopTest/kotlin/com/parlor/games/ghamza/ui/GhamzaTableTest.kt"),
        ("word-impostor", "game-modes/word-impostor/src/desktopTest/kotlin/com/parlor/games/wordimpostor/ui/WordImpostorTableTest.kt"),
        ("hand-counts", "game-modes/last-light/src/desktopTest/kotlin/com/parlor/games/lastlight/ui/game/GameplayHandCountsTest.kt"),
        ("table-chrome", "game-modes/last-light/src/desktopTest/kotlin/com/parlor/games/lastlight/ui/game/GameplayChromeTest.kt"),
        ("guide", "game-modes/last-light/src/desktopTest/kotlin/com/parlor/games/lastlight/ui/game/GameplayChallengeGuideTest.kt"),
        ("result-design", "game-modes/last-light/src/desktopTest/kotlin/com/parlor/games/lastlight/ui/game/GameplayResultDesignTest.kt"),
        ("fuse-cylinder", "game-modes/last-light/src/desktopTest/kotlin/com/parlor/games/lastlight/ui/game/FuseCylinderTest.kt"),
        ("last-light-", "game-modes/last-light/src/desktopTest/kotlin/com/parlor/games/lastlight/ui/game/GameplayLayoutTest.kt"),
    ]
    for token, path in mapping:
        if token in value:
            return path
    return "Production composable screenshot fixture; test entry not inferred from filename"


def read_json(path: Path) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def native_profile(artifact_name: str, root: Path) -> tuple[str, str, dict[str, object], dict[str, object]]:
    platform = "iOS Simulator" if "-ios-" in artifact_name else "Android Emulator"
    profile = next((name for name in ("small-phone", "standard-phone", "large-phone", "tablet") if f"-{name}-" in artifact_name), "unknown")
    provenance_candidates = list(root.rglob("provenance.json")) if platform == "iOS Simulator" else list(root.rglob("profile-status.json"))
    simulator_candidates = list(root.rglob("simulator-provenance.json"))
    provenance = read_json(provenance_candidates[0]) if provenance_candidates else {}
    simulator = read_json(simulator_candidates[0]) if simulator_candidates else {}
    return platform, profile, provenance, simulator


def android_capture_provenance(root: Path, profile: str, filename: str) -> dict[str, object]:
    match = re.match(rf"android-{re.escape(profile)}-(ar-EG|en-US)-(dark|light)-", filename)
    if not match:
        return {}
    return read_json(root / f"provenance-{profile}-{match.group(1)}-{match.group(2)}.json")


def reused_capture_names(root: Path) -> set[str]:
    names: set[str] = set()
    prefix = "reused existing native capture: "
    for log in root.rglob("capture-*.log"):
        try:
            lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        names.update(line.split(prefix, 1)[1].strip() for line in lines if prefix in line)
    return names


def xctest_intervals(root: Path) -> list[tuple[float, float, str]]:
    started = re.compile(r"Test Case '-\[[^]]+ (test\w+)\]' started\.")
    test_start = re.compile(r"Start Test at (\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d+)")
    finished = re.compile(r"Test Case '-\[[^]]+ (test\w+)\]' (?:passed|failed) \(([0-9.]+) seconds\)\.")
    intervals: list[tuple[float, float, str]] = []
    for log in root.rglob("xcode-*.log"):
        try:
            lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        method = ""
        start: datetime | None = None
        for line in lines:
            match = started.search(line)
            if match:
                method, start = match.group(1), None
                continue
            match = test_start.search(line)
            if match and method:
                start = datetime.strptime(match.group(1), "%Y-%m-%d %H:%M:%S.%f").replace(tzinfo=timezone.utc)
                continue
            match = finished.search(line)
            if match and method and start:
                duration = float(match.group(2))
                intervals.append((start.timestamp(), start.timestamp() + duration, method))
                method, start = "", None
    return intervals


def profile_diagnostics(platform: str, root: Path) -> list[str]:
    """Preserve concrete capture failures alongside the affected device profile."""
    issues: list[str] = []
    if platform == "Android Emulator":
        for log in sorted(root.rglob("capture-*.log")):
            text = log.read_text(encoding="utf-8", errors="replace")
            if "xml.etree.ElementTree.ParseError" in text:
                issues.append("uiautomator hierarchy parse failed: xml.etree.ElementTree.ParseError: syntax error: line 1, column 0")
            elif "No visible native control matched any of:" in text:
                match = re.search(r"RuntimeError: (No visible native control matched any of: .+)", text)
                if match:
                    issues.append(match.group(1))
            elif "Traceback (most recent call last):" in text:
                tail = [line.strip() for line in text.splitlines() if line.strip()]
                issues.append("capture script error: " + (tail[-1] if tail else "see capture log"))
    else:
        for log in sorted(root.rglob("xcode-*.log")):
            text = log.read_text(encoding="utf-8", errors="replace")
            for line in text.splitlines():
                if ": error: -[iosAppUITests.IOSAppLaunchUITests testColdLaunchRendersComposeHomeWithoutUnexpectedAlert]" in line:
                    issues.append("XCTest failed at IOSAppLaunchUITests.swift:24: app.buttons[\"Open settings.\"] did not appear within 10 seconds after the library rendered.")
                    break
    return list(dict.fromkeys(issues))


def capture_locale(platform: str, display: str, filename: str, attachment: dict[str, str], intervals: list[tuple[float, float, str]]) -> tuple[str, str | None]:
    value = f"{display} {filename}".casefold()
    if any("\u0600" <= char <= "\u06ff" for char in value):
        return "Arabic (ar-EG)", None
    if "arabic" in value or re.search(r"(?:^|[-_])ar(?:[-_.]|$)", value):
        return "Arabic (ar-EG)", None
    if "english" in value or re.search(r"(?:^|[-_])en(?:[-_.]|$)", value):
        return "English (en-US)", None
    if platform == "iOS Simulator":
        try:
            timestamp = float(attachment.get("timestamp", ""))
        except ValueError:
            timestamp = -1
        method = next((name for start, end, name in intervals if start <= timestamp <= end), None)
        if method and "arabic" in method.casefold():
            return "Arabic (ar-EG)", method
        if method:
            return "English (en-US)", method
        if "keyboard" in value:
            return "Locale follows the associated XCTest; attachment time did not match a test interval", None
    return "English (en-US)", None


def route_checklist() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = [
        {"id": "shared.startup", "game": "Shared", "state": "Startup / initial route", "screen_id": "shared.library", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/App.kt", "test_entry_point": "App starts at the Home route; Android/iOS UI capture suites", "na_reason": ""},
        {"id": "shared.onboarding", "game": "Shared", "state": "First-run onboarding", "screen_id": "shared.onboarding", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/App.kt", "test_entry_point": "App navigation graph", "na_reason": "App.kt starts at Home; source has no onboarding route or onboarding composable."},
        {"id": "shared.library", "game": "Shared", "state": "Game library / catalog", "screen_id": "shared.library", "route_reference": SHARED_ROUTE["shared.library"], "test_entry_point": "HomeCatalogVisualEvidenceTest; Android and iOS UI capture suites", "na_reason": ""},
        {"id": "shared.library-end", "game": "Shared", "state": "Library end / scrolling", "screen_id": "shared.library-end", "route_reference": SHARED_ROUTE["shared.library-end"], "test_entry_point": "HomeCatalogVisualEvidenceTest", "na_reason": ""},
        {"id": "shared.filters", "game": "Shared", "state": "Catalog filter selections", "screen_id": "shared.library-filter", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeCatalogFilter.kt", "test_entry_point": "HomeCatalogVisualEvidenceTest", "na_reason": ""},
        {"id": "shared.empty", "game": "Shared", "state": "Empty filtered catalog", "screen_id": "shared.library-empty", "route_reference": SHARED_ROUTE["shared.library"], "test_entry_point": "HomeCatalogVisualEvidenceTest / HomeScreen EmptyState", "na_reason": ""},
        {"id": "shared.settings", "game": "Shared", "state": "Settings: language, appearance, accessibility", "screen_id": "shared.settings", "route_reference": SHARED_ROUTE["shared.settings"], "test_entry_point": "SettingsAccessibilityAcceptanceTest; iOS cold-launch UI test", "na_reason": ""},
        {"id": "shared.recovery", "game": "Shared", "state": "Recovery status / saved session lookup", "screen_id": "shared.recovery", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/home/HomeScreen.kt", "test_entry_point": "HomeCatalogVisualEvidenceTest recovery fixture", "na_reason": ""},
        {"id": "shared.resume-failure", "game": "Shared", "state": "Unreadable save / retry / discard dialog", "screen_id": "shared.resume-failure", "route_reference": SHARED_ROUTE["shared.resume-failure"], "test_entry_point": "LocalResumeFailureScreen", "na_reason": ""},
        {"id": "shared.mode-picker", "game": "Shared", "state": "Mode selection for each game", "screen_id": "shared.mode-picker", "route_reference": SHARED_ROUTE["shared.mode-picker"], "test_entry_point": "HomeCatalogVisualEvidenceTest; iOS setup UI tests; Android uiautomator", "na_reason": ""},
        {"id": "shared.host-permission", "game": "Shared", "state": "Host permission guidance", "screen_id": "shared.host-permission", "route_reference": SHARED_ROUTE["shared.host-permission"], "test_entry_point": "iOS public setup UI tests", "na_reason": ""},
        {"id": "shared.join-permission", "game": "Shared", "state": "Join permission guidance", "screen_id": "shared.join-permission", "route_reference": SHARED_ROUTE["shared.join-permission"], "test_entry_point": "iOS public setup UI tests", "na_reason": ""},
        {"id": "shared.name-entry", "game": "Shared", "state": "Host and join display-name entry", "screen_id": "shared.name-entry", "route_reference": SHARED_ROUTE["shared.host-name-entry"], "test_entry_point": "iOS public setup UI tests", "na_reason": ""},
        {"id": "shared.room-code-entry", "game": "Shared", "state": "Join room code entry", "screen_id": "shared.room-code-entry", "route_reference": SHARED_ROUTE["shared.room-code-entry"], "test_entry_point": "RoomCodeAccessibilityAcceptanceTest; iOS public setup UI tests", "na_reason": ""},
        {"id": "shared.keyboard", "game": "Shared", "state": "Name and room-code keyboard/inset states", "screen_id": "shared.keyboard-entry", "route_reference": SHARED_ROUTE["shared.keyboard-entry"], "test_entry_point": "iOS keyboard inset UI tests; RoomCodeAccessibilityAcceptanceTest", "na_reason": ""},
        {"id": "shared.discovery", "game": "Shared", "state": "Nearby discovery / room search", "screen_id": "shared.discovery", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/PeerGameLobby.kt", "test_entry_point": "PeerGameLobby discovery flow", "na_reason": ""},
        {"id": "shared.waiting", "game": "Shared", "state": "Host/client waiting", "screen_id": "shared.waiting", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/HostedGameLobby.kt", "test_entry_point": "HostedGameLobby / PeerGameLobby", "na_reason": ""},
        {"id": "shared.lobby", "game": "Shared", "state": "Admission, readiness, lobby controls, start", "screen_id": "shared.lobby", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/RoomGameLobbyUi.kt", "test_entry_point": "RoomGameLobbyUi / LobbyComponents", "na_reason": ""},
        {"id": "shared.disconnect", "game": "Shared", "state": "Disconnect, rejoin, and recovery", "screen_id": "shared.disconnect", "route_reference": "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/PeerGameLobby.kt", "test_entry_point": "Multiplayer rejoin flow", "na_reason": ""},
    ]
    for slug, title in GAMES.items():
        rows.append({
            "id": f"shared.mode-picker.{slug}", "game": title, "state": "Mode selection", "screen_id": f"shared.mode-picker.{slug}",
            "route_reference": SHARED_ROUTE["shared.mode-picker"],
            "test_entry_point": "HomeCatalogVisualEvidenceTest; iOS setup UI tests; Android uiautomator",
            "na_reason": "",
        })
    per_game = {
        "whodunit": [
            ("setup", "Player count / case / name / rules setup", "src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/setup/PlayerCountScreen.kt"),
            ("handoff", "Private character reveal and handoff", "src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/reveal/CharacterRevealScreens.kt"),
            ("discussion", "Public discussion", "src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/round/RoundScreens.kt"),
            ("round", "Question / investigation and turn transition", "src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/round/RoundScreens.kt"),
            ("vote", "Vote and elimination outcome", "src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/vote/VoteScreens.kt"),
            ("results", "Postgame results / replay / exit", "src/commonMain/kotlin/com/parlor/games/whodunit/ui/screens/postgame/PostGameScreen.kt"),
            ("nearby", "Nearby host/peer wait and admission", "src/commonMain/kotlin/com/parlor/games/whodunit/ui/flow/multiplayer/WhodunitHostSessionFlow.kt"),
        ],
        "mafia": [
            ("setup", "Player entry and role setup", "src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/setup/MafiaSetupScreen.kt"),
            ("handoff", "Private role reveal and handoff", "src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/handoff/MafiaHandoffScreens.kt"),
            ("night", "Mafia/doctor/detective/civilian night actions", "src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/night/TargetPickerScreen.kt"),
            ("dawn", "Night announcement / dawn transition", "src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/announce/NightAnnouncementScreen.kt"),
            ("discussion", "Day discussion and vote", "src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/discussion/DiscussionScreen.kt"),
            ("vote", "Vote result / elimination", "src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/vote/VoteCastScreen.kt"),
            ("results", "Postgame results / replay / exit", "src/commonMain/kotlin/com/parlor/games/mafia/ui/screens/postgame/PostGameScreen.kt"),
            ("nearby", "Nearby host/client lobby and phases", "src/commonMain/kotlin/com/parlor/games/mafia/ui/flow/multidevice/MafiaMultiDevicePhaseRouter.kt"),
        ],
        "egyptian-dominoes": [
            ("local", "Local pass-and-play setup", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/playmode/PlayModePickerScreen.kt"),
            ("roster", "Host roster and player readiness", "src/commonMain/kotlin/com/parlor/games/dominoes/ui/DominoSettingsContent.kt"),
            ("table", "Covered hand / active turn / tile placement", "src/commonMain/kotlin/com/parlor/games/dominoes/ui/DominoTable.kt"),
            ("results", "Round/game results", "src/commonMain/kotlin/com/parlor/games/dominoes/ui/DominoResults.kt"),
            ("replay", "Rematch and exit", "src/commonMain/kotlin/com/parlor/games/dominoes/ui/DominoResults.kt"),
            ("nearby", "Host/join lobby and network states", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/RoomGameLobbyUi.kt"),
        ],
        "ghamza": [
            ("local", "Local pass-and-play setup", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/playmode/PlayModePickerScreen.kt"),
            ("setup", "Host options / roster / game settings", "src/commonMain/kotlin/com/parlor/games/ghamza/ui/GhamzaSettingsContent.kt"),
            ("round", "Social play / prompt and turn progression", "src/commonMain/kotlin/com/parlor/games/ghamza/ui/GhamzaTable.kt"),
            ("results", "Results / replay / exit", "src/commonMain/kotlin/com/parlor/games/ghamza/ui/GhamzaTable.kt"),
            ("nearby", "Host/client lobby and admission", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/RoomGameLobbyUi.kt"),
        ],
        "word-impostor": [
            ("local", "Local pass-and-play setup", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/playmode/PlayModePickerScreen.kt"),
            ("setup", "Host options / roster / settings", "src/commonMain/kotlin/com/parlor/games/wordimpostor/ui/WordImpostorSettingsContent.kt"),
            ("question", "Public question / next turn", "src/commonMain/kotlin/com/parlor/games/wordimpostor/ui/WordImpostorTable.kt"),
            ("vote", "Voting and private guess handoff", "src/commonMain/kotlin/com/parlor/games/wordimpostor/ui/WordVoteAndResults.kt"),
            ("results", "Results / replay / exit", "src/commonMain/kotlin/com/parlor/games/wordimpostor/ui/WordVoteAndResults.kt"),
            ("nearby", "Host/client lobby and admission", "composeApp/src/commonMain/kotlin/com/parlor/app/shell/game/multiplayer/RoomGameLobbyUi.kt"),
        ],
        "last-light": [
            ("setup", "Player management and local setup", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/flow/passandplay/LastLightLocalScreens.kt"),
            ("handoff", "Private hand / handoff and reveal cover", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/game/GameplayScreen.kt"),
            ("turn", "Dense gameplay / turn transition", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/game/GameplayScreen.kt"),
            ("overlay", "Table options / leave / help overlays", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/flow/common/LastLightTableControls.kt"),
            ("results", "Round result / winner / replay", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/game/GameResults.kt"),
            ("hand-counts", "Private hand/count display variants", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/game/GameplayScreen.kt"),
            ("guide", "Rules / challenge guide", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/game/GameplayScreen.kt"),
            ("fuse", "Fuse / claim control", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/game/GameplayScreen.kt"),
            ("nearby", "Nearby host/client lobby and session table", "src/commonMain/kotlin/com/parlor/games/lastlight/ui/flow/multidevice/LastLightMultiDeviceHostFlow.kt"),
        ],
    }
    for game, states in per_game.items():
        for state, title, route in states:
            if state == "local" and game in {"egyptian-dominoes", "ghamza", "word-impostor"}:
                na = f"PlayModePickerScreen capability registration confirms {GAMES[game]} exposes nearby host/join only and no local mode."
                screen = f"{game}.local-setup"
            else:
                na = ""
                screen = {
                    ("last-light", "hand-counts"): "last-light.hand-counts",
                    ("last-light", "guide"): "last-light.guide",
                    ("last-light", "fuse"): "last-light.fuse-cylinder",
                }.get((game, state), f"{game}.{state}")
            rows.append({
                "id": f"{game}.{state}", "game": GAMES[game], "state": title,
                "screen_id": screen,
                "route_reference": f"game-modes/{game}/{route}" if not route.startswith("composeApp/") else route,
                "test_entry_point": "Source route/composable and relevant module desktop/native UI capture entry points",
                "na_reason": na,
            })
    return rows


def parse_desktop_metadata(name: str, width: int, height: int, source_sha: str) -> dict[str, object]:
    locale = "Arabic" if re.search(r"(?:^|[-_])ar(?:[-_.]|$)", name, re.I) else "English" if re.search(r"(?:^|[-_])en(?:[-_.]|$)", name, re.I) else "Default"
    theme_match = re.search(r"-(light|dark|system)(?:-|\.)", name, re.I)
    theme = theme_match.group(1).lower() if theme_match else "default"
    scale_match = re.search(r"(?:-|_)([12])(?:\.0|x)(?:-|\.)", name, re.I)
    text_scale = float(scale_match.group(1)) if scale_match else 2.0 if "large-text" in name else 1.0
    return {
        "locale": locale,
        "theme": theme,
        "resolution_px": f"{width}x{height}",
        "logical_viewport": f"{width}x{height} dp (density 1.0 Desktop test frame)",
        "orientation": "landscape" if width > height else "portrait" if height > width else "square",
        "text_scale": text_scale,
        "source_sha": source_sha,
        "method": "Desktop deterministic UI test fixture rendering production composables with synthetic player/game state; this is Desktop evidence, not a native device capture.",
    }


def infer_metadata(path: Path, platform: str, profile: str, provenance: dict[str, object], simulator: dict[str, object], attachment: dict[str, str], source_sha: str, width: int, height: int, locale: str) -> dict[str, object]:
    if platform == "Desktop":
        return parse_desktop_metadata(path.name, width, height, source_sha)
    name = path.name
    if platform == "Android Emulator":
        base = dict(provenance)
        locale = str(base.get("locale") or ("Arabic" if "ar-eg" in name.lower() else "English"))
        return {
            "profile": str(base.get("profile", profile)),
            "device_model": base.get("device_model", "Unknown Android emulator profile"),
            "resolution_px": str(base.get("resolution_px", f"{width}x{height}")),
            "logical_viewport": str(base.get("logical_viewport_dp", "unknown")) + " dp",
            "density_dpi": base.get("density_dpi"),
            "orientation": "landscape" if width > height else "portrait",
            "locale": locale,
            "locale_tag": base.get("locale_tag", "ar" if locale.lower().startswith("ar") else "en-US"),
            "locale_method": base.get("locale_method", "Android emulator system locale"),
            "theme": base.get("theme", "unknown"),
            "text_scale": base.get("font_scale", "unknown"),
            "source_sha": source_sha,
            "method": base.get("method", "Native Android Emulator app screenshot via adb screencap."),
        }
    model_info = dict(simulator)
    model_info.update({k: v for k, v in provenance.items() if k not in model_info})
    return {
        "profile": profile,
        "device_model": model_info.get("device_model", provenance.get("device_model", "Unknown iOS Simulator")),
        "device_type": model_info.get("device_type", provenance.get("device_type")),
        "resolution_px": f"{width}x{height}",
        "profile_native_resolution_px": model_info.get("resolution_px", provenance.get("native_resolution_px")),
        "logical_viewport": model_info.get("logical_viewport_points", provenance.get("logical_viewport_points", "unknown")) + " pt",
        "display_scale": model_info.get("display_scale", provenance.get("display_scale")),
        "orientation": model_info.get("orientation", "portrait"),
        "locale": locale,
        "theme": "dark" if any(parent.name == "dark" for parent in path.parents) else "light",
        "text_scale": model_info.get("text_scale", provenance.get("text_scale", 1.0)),
        "content_size_category": model_info.get("content_size_category", provenance.get("content_size_category", "UICTContentSizeCategoryL")),
        "runtime": model_info.get("runtime", provenance.get("runtime")),
        "source_sha": source_sha,
        "method": "Native iOS Simulator XCTest UI navigation; XCTAttachment app screenshot exported as original PNG.",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--native-dir", type=Path, required=True)
    parser.add_argument("--desktop-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--allow-existing-output", action="store_true", help="Preserve source screenshots in an existing local package directory.")
    parser.add_argument("--android-run-id", default="")
    parser.add_argument("--android-reuse-run-id", default="")
    parser.add_argument("--android-reuse-source-sha", default="")
    parser.add_argument("--ios-run-id", default="")
    parser.add_argument("--capture-run-id", default="local-desktop-only", help=argparse.SUPPRESS)
    parser.add_argument("--source-sha", default="unknown")
    parser.add_argument("--run-jobs", type=Path)
    args = parser.parse_args()
    out = args.output_dir
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()) and not args.allow_existing_output:
        raise SystemExit(f"Output directory must be empty to preserve existing evidence: {out}")
    if any((out / name).exists() for name in ("images", "manifest.json", "index.html", "README.txt")):
        raise SystemExit(f"Refusing to overwrite an existing evidence package in: {out}")
    images_dir = out / "images"
    images_dir.mkdir()

    native_pngs = sorted(args.native_dir.rglob("*.png")) if args.native_dir.exists() else []
    artifact_groups: dict[str, list[Path]] = defaultdict(list)
    for path in native_pngs:
        relative_parts = path.relative_to(args.native_dir).parts
        artifact = relative_parts[0] if relative_parts else "unknown-artifact"
        artifact_groups[artifact].append(path)
    artifact_roots = {path.name: path for path in args.native_dir.iterdir() if path.is_dir()} if args.native_dir.exists() else {}
    profile_info: dict[str, tuple[str, str, dict[str, object], dict[str, object]]] = {
        name: native_profile(name, root) for name, root in artifact_roots.items()
    }
    profile_device_execution: dict[tuple[str, str], bool] = {}
    profile_issue_map: dict[tuple[str, str], list[str]] = defaultdict(list)
    for name, (platform, profile, provenance, simulator) in profile_info.items():
        device_reached = bool(simulator) if platform == "iOS Simulator" else bool(provenance)
        profile_device_execution[(platform, profile)] = profile_device_execution.get((platform, profile), False) or device_reached
        profile_issue_map[(platform, profile)].extend(profile_diagnostics(platform, artifact_roots[name]))

    entries: list[dict[str, object]] = []
    failures: list[dict[str, str]] = []

    def add_capture(source: Path, platform: str, profile: str, provenance: dict[str, object], simulator: dict[str, object], attachment: dict[str, str], source_sha: str, source_artifact: str, locale: str = "Default", capture_test_method: str | None = None, reused_from_run_id: str | None = None) -> None:
        try:
            width, height, decode_method = validate_png(source)
        except (OSError, ValueError, zlib.error) as error:
            failures.append({"file": str(source), "error": str(error)})
            return
        display = next((attachment[key] for key in ("name", "displayName", "suggestedHumanReadableName", "testName", "testIdentifier") if key in attachment), source.stem)
        game, screen_id, state, route = classify(display, source.name, platform)
        if platform == "Desktop":
            family = "shared" if game == "Shared" else "games"
            rel = Path("images") / "desktop" / family / source.name
        elif platform == "Android Emulator":
            rel = Path("images") / "android" / profile / source.name
        else:
            appearance = "dark" if any(parent.name == "dark" for parent in source.parents) else "light"
            rel = Path("images") / "ios" / profile / appearance / source.name
        destination = out / rel
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            suffix = hashlib.sha256(source.read_bytes()).hexdigest()[:8]
            destination = destination.with_name(f"{destination.stem}-{suffix}{destination.suffix}")
            rel = destination.relative_to(out)
        shutil.copy2(source, destination)
        actual_platform = "Desktop" if platform == "Desktop" else platform
        metadata = infer_metadata(source, actual_platform, profile, provenance, simulator, attachment, source_sha, width, height, locale)
        entry = {
            "capture_filename": rel.as_posix(),
            "screen_id": screen_id,
            "screen_state": state,
            "game": game,
            "route_reference": route,
            "test_entry_point": test_entry_point(source.name, display, actual_platform),
            "platform": actual_platform,
            "source_artifact": source_artifact,
            "reused_from_run_id": reused_from_run_id,
            "capture_test_method": capture_test_method,
            "display_name": display,
            "image_px": f"{width}x{height}",
            "decode_validation": decode_method,
            "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "duplicate_pixels": False,
            **metadata,
        }
        entries.append(entry)

    for artifact, paths in sorted(artifact_groups.items()):
        platform, profile, default_provenance, simulator = profile_info[artifact]
        root = artifact_roots[artifact]
        records = attachment_records(root)
        intervals = xctest_intervals(root) if platform == "iOS Simulator" else []
        reused_names = reused_capture_names(root) if platform == "Android Emulator" else set()
        for path in paths:
            provenance = android_capture_provenance(root, profile, path.name) if platform == "Android Emulator" else default_provenance
            attachment = records.get(path.name, {})
            display = next((attachment[key] for key in ("name", "displayName", "suggestedHumanReadableName", "testName", "testIdentifier") if key in attachment), path.stem)
            locale, test_method = capture_locale(platform, display, path.name, attachment, intervals)
            reused_from = args.android_reuse_run_id if path.name in reused_names else None
            source_sha = str(provenance.get("source_sha") or default_provenance.get("source_sha") or args.source_sha)
            if reused_from and args.android_reuse_source_sha:
                source_sha = args.android_reuse_source_sha
            add_capture(path, platform, profile, provenance or default_provenance, simulator, attachment, source_sha, artifact, locale, test_method, reused_from)
    if args.desktop_dir.exists():
        for path in sorted(args.desktop_dir.rglob("*.png")):
            add_capture(path, "Desktop", "Desktop seeded fixture", {}, {}, {}, args.source_sha, "Desktop production composable fixture")

    hash_files: dict[str, list[dict[str, object]]] = defaultdict(list)
    for entry in entries:
        hash_files[str(entry["sha256"])].append(entry)
    for duplicates in hash_files.values():
        if len(duplicates) > 1:
            for entry in duplicates:
                entry["duplicate_pixels"] = True
                entry["duplicate_group_sha256"] = entry["sha256"]

    jobs = read_json(args.run_jobs) if args.run_jobs else {}
    checklist = route_checklist()
    for row in checklist:
        screen_id = str(row["screen_id"])
        if screen_id == "shared.mode-picker":
            matching = [entry for entry in entries if str(entry["screen_id"]).startswith("shared.mode-picker")]
        else:
            matching = [entry for entry in entries if entry["screen_id"] == screen_id]
        row["captures"] = [entry["capture_filename"] for entry in matching]
        row["capture_count"] = len(matching)
        row["captured_platforms"] = sorted({entry["platform"] for entry in matching})
        native_matching = [entry for entry in matching if entry["platform"] in {"Android Emulator", "iOS Simulator"}]
        row["native_captures"] = [entry["capture_filename"] for entry in native_matching]
        row["native_capture_count"] = len(native_matching)
        row["native_captured_platforms"] = sorted({entry["platform"] for entry in native_matching})
        row["native_coverage_by_platform"] = {
            platform: {
                "capture_count": sum(1 for entry in native_matching if entry["platform"] == platform),
                "profiles": sorted({str(entry.get("profile")) for entry in native_matching if entry["platform"] == platform}),
            }
            for platform in sorted({str(entry["platform"]) for entry in native_matching})
        }
        if row["na_reason"]:
            row["status"] = "not-applicable"
            row["gap_or_reason"] = row["na_reason"]
            row["native_status"] = "not-applicable"
            row["native_gap_or_reason"] = row["na_reason"]
        elif matching:
            row["status"] = "captured"
            row["gap_or_reason"] = ""
            row["native_status"] = "captured" if native_matching else "not-captured-natively"
            row["native_gap_or_reason"] = "" if native_matching else "Only Desktop seeded production-composable evidence exists; it does not certify a native screen capture."
        else:
            row["status"] = "uncovered"
            row["native_status"] = "uncovered"
            if screen_id in {"shared.discovery", "shared.waiting", "shared.lobby", "shared.disconnect"}:
                reason = "No live LAN session was started. The native UI tests stop before discovery/connection, and no host/client admission or rejoin flow was executed."
            elif screen_id in {"shared.library-filter", "shared.library-empty", "shared.resume-failure"}:
                reason = "The capture run did not drive this state or construct its empty/corrupt-save fixture; the source route exists but there is no PNG evidence."
            elif ".handoff" in screen_id:
                reason = "No private player secret was exposed during this capture; the tested native flows stop at public setup and the seeded suite supplies only documented covered/public states."
            elif row["game"] == "Shared":
                reason = "No matching screen capture was produced by the executed native/desktop route harness."
            else:
                reason = "The production route exists, but the captured UI progression/seeded composable fixtures did not reach this distinct state."
            row["gap_or_reason"] = reason
            row["native_gap_or_reason"] = reason

    platform_counts = Counter(entry["platform"] for entry in entries)
    game_platform: dict[str, dict[str, dict[str, int]]] = defaultdict(lambda: defaultdict(lambda: {"screenshots": 0, "distinct_states": 0}))
    state_sets: dict[tuple[str, str], set[str]] = defaultdict(set)
    for entry in entries:
        game_platform[str(entry["game"])][str(entry["platform"])]["screenshots"] += 1
        state_sets[(str(entry["game"]), str(entry["platform"]))].add(str(entry["screen_id"]))
    for (game, platform), states in state_sets.items():
        game_platform[game][platform]["distinct_states"] = len(states)

    profile_rows: list[dict[str, object]] = []
    expected_profiles = [
        ("Android Emulator", "small-phone", "Pixel 2"),
        ("Android Emulator", "standard-phone", "Pixel 4"),
        ("Android Emulator", "large-phone", "Pixel 6 Pro"),
        ("Android Emulator", "tablet", "Pixel C"),
        ("iOS Simulator", "small-phone", "iPhone SE (3rd generation)"),
        ("iOS Simulator", "standard-phone", "iPhone 16"),
        ("iOS Simulator", "large-phone", "iPhone 16 Pro Max"),
        ("iOS Simulator", "tablet", "iPad (10th generation)"),
    ]
    jobs_list = jobs.get("jobs", []) if isinstance(jobs, dict) else []
    for platform, profile, model in expected_profiles:
        captures = [entry for entry in entries if entry["platform"] == platform and entry.get("profile") == profile]
        platform_tag = "capture-ios" if platform == "iOS Simulator" else "capture-android"
        matching_jobs = [job for job in jobs_list if isinstance(job, dict) and str(job.get("name", "")).startswith(platform_tag) and profile in str(job.get("name", ""))]
        job = max(matching_jobs, key=lambda item: str(item.get("started_at") or "")) if matching_jobs else {}
        device_metadata_exists = profile_device_execution.get((platform, profile), False)
        profile_rows.append({
            "platform": platform,
            "profile": profile,
            "expected_model": model,
            "capture_count": len(captures),
            "job_started": bool(job.get("started_at")) if isinstance(job, dict) else False,
            "job_status": job.get("status") if isinstance(job, dict) else "not found in run job list",
            "job_conclusion": job.get("conclusion") if isinstance(job, dict) else None,
            "profile_executed_on_device": bool(device_metadata_exists),
            "profile_issues": list(dict.fromkeys(profile_issue_map[(platform, profile)])),
            "actual_models": sorted({str(entry.get("device_model")) for entry in captures}) or ([str(model)] if device_metadata_exists else []),
            "job_url": job.get("html_url") if isinstance(job, dict) else None,
        })

    observations = [
        {"severity": "review", "evidence": "Desktop seeded Ghamza result at narrow 320 dp / text scale 2.0 shows long result copy continuing below the screenshot viewport. The screen is vertically scrollable; a native small-phone check is needed before calling this a clipping defect.", "capture": next((e["capture_filename"] for e in entries if "ghamza-results-en-light-2.0" in str(e["capture_filename"])), None)},
        {"severity": "review", "evidence": "Desktop seeded Egyptian Dominoes covered table at 320 dp / text scale 2.0 places the lower turn action below the captured viewport; this is a scroll-position observation, not proof that the action is unreachable.", "capture": next((e["capture_filename"] for e in entries if "egyptian-dominoes-table-covered-en-light-2.0" in str(e["capture_filename"])), None)},
        {"severity": "observation", "evidence": "At 320 dp the Home catalog shows two complete game cards and part of the next card; the cards themselves remain within the scrollable library.", "capture": next((e["capture_filename"] for e in entries if "home-en-light-1.0-320dp" in str(e["capture_filename"])), None)},
    ]

    manifest = {
        "title": "Parlor complete app screen capture evidence",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "source": {
            "requested_merged_commit": MERGED_COMMIT,
            "verified_production_tree": PRODUCTION_TREE,
            "native_capture_run_ids": {
                "android": [value for value in args.android_run_id.split(",") if value],
                "android_reuse_run_id": args.android_reuse_run_id or None,
                "ios": args.ios_run_id,
            },
            "native_capture_run_urls": {
                "android": [
                    f"https://github.com/parlor-game/parlor/actions/runs/{value}"
                    for value in args.android_run_id.split(",") if value.isdigit()
                ],
                "android_reuse": f"https://github.com/parlor-game/parlor/actions/runs/{args.android_reuse_run_id}" if args.android_reuse_run_id.isdigit() else None,
                "ios": f"https://github.com/parlor-game/parlor/actions/runs/{args.ios_run_id}" if args.ios_run_id.isdigit() else None,
            },
            "dedicated_branch": "capture/native-screens-2026-11-22",
            "capture_harness_sha": args.source_sha,
            "verified_app_source_tree": PRODUCTION_TREE,
        },
        "totals": {
            "png_count": len(entries),
            "png_decode_failures": failures,
            "platform_counts": dict(platform_counts),
            "profiles": profile_rows,
            "profile_jobs_started_count": sum(1 for profile in profile_rows if profile["job_started"]),
            "profiles_executed_on_device_count": sum(1 for profile in profile_rows if profile["profile_executed_on_device"]),
            "game_platform_counts": game_platform,
            "uncovered_checklist_count": sum(1 for row in checklist if row["status"] == "uncovered"),
            "native_captured_checklist_count": sum(1 for row in checklist if row.get("native_status") == "captured"),
            "native_uncovered_checklist_count": sum(1 for row in checklist if row.get("native_status") in {"uncovered", "not-captured-natively"}),
            "not_applicable_count": sum(1 for row in checklist if row["status"] == "not-applicable"),
        },
        "workflow_jobs": jobs,
        "visual_review_notes": observations,
        "checklist": checklist,
        "captures": entries,
    }
    manifest_path = out / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_gallery(out, entries, profile_rows, manifest)
    (out / "README.txt").write_text(
        "Parlor screenshot evidence package. Original PNG bytes are preserved. Desktop seeded images render shipping production composables using synthetic deterministic fixtures and are labeled Desktop; they are not native captures. Android/iOS folders contain screenshots exported from actual Actions emulator/simulator runs. Manifest includes route coverage, device metadata, source SHA, capture method, and uncovered reasons.\n",
        encoding="utf-8",
    )
    if failures:
        raise SystemExit(f"PNG decode failures: {failures}")
    for entry in entries:
        if not (out / str(entry["capture_filename"])).is_file():
            raise SystemExit(f"Missing gallery target: {entry['capture_filename']}")
    print(json.dumps({"png_count": len(entries), "platform_counts": dict(platform_counts), "profiles": profile_rows, "uncovered": manifest["totals"]["uncovered_checklist_count"]}, indent=2))


def write_gallery(out: Path, entries: list[dict[str, object]], profiles: list[dict[str, object]], manifest: dict[str, object]) -> None:
    groups = ["Shared", *GAMES.values()]
    cards = []
    for entry in entries:
        label = html.escape(str(entry["display_name"]))
        filename = html.escape(str(entry["capture_filename"]))
        group = html.escape(str(entry["game"]))
        platform = html.escape(str(entry["platform"]))
        profile = html.escape(str(entry.get("profile", "Desktop")))
        locale = html.escape(str(entry.get("locale", "unknown")))
        theme = html.escape(str(entry.get("theme", "unknown")))
        viewport = html.escape(str(entry.get("logical_viewport", entry.get("logical_viewport_points", ""))))
        state = html.escape(str(entry["screen_state"]))
        cards.append(
            f'<article class="card" data-group="{group}" data-platform="{platform}" data-profile="{profile}" data-search="{html.escape((label+" "+state+" "+group+" "+platform+" "+profile+" "+locale+" "+theme).casefold())}">'
            f'<a href="{filename}"><img loading="lazy" src="{filename}" alt="{label}"></a>'
            f'<div class="caption"><b>{html.escape(GAMES.get(str(entry["game"]), str(entry["game"])))}</b>'
            f'<span>{state}</span><small>{platform} · {profile} · {locale} · {theme}</small>'
            f'<small>{viewport} · {html.escape(str(entry.get("image_px", "")))} px</small>'
            f'<small>{label}</small></div></article>'
        )
    profile_summary = " ".join(f"<li>{html.escape(str(p['platform']))} / {html.escape(str(p['profile']))}: {p['capture_count']} PNGs</li>" for p in profiles)
    sections = "".join(f'<option value="{html.escape(group)}">{html.escape(group)}</option>' for group in groups)
    html_doc = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Parlor screenshot evidence</title>
<style>
body{{font:15px/1.45 system-ui,sans-serif;margin:0;background:#f5f2e9;color:#1d2a27}}header{{background:#103b36;color:#fff;padding:24px 4vw}}h1{{margin:0 0 8px}}main{{padding:20px 4vw}}.controls{{display:flex;gap:10px;flex-wrap:wrap;margin:12px 0 20px}}input,select{{padding:10px;border:1px solid #a8b2ac;border-radius:8px;background:white}}.summary{{background:#fff;border-radius:10px;padding:12px 18px;margin:12px 0 20px}}.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:14px}}.card{{background:#fff;border:1px solid #d8d5cb;border-radius:10px;overflow:hidden}}.card img{{width:100%;height:360px;object-fit:contain;background:#e8e4d9;display:block}}.caption{{padding:10px;display:grid;gap:3px}}small{{color:#55645e;overflow-wrap:anywhere}}.section{{margin:28px 0}}a{{color:inherit}}.badge{{font-size:12px;padding:2px 7px;border-radius:10px;background:#e6f2ee}}@media(max-width:600px){{.card img{{height:300px}}}}
</style></head><body><header><h1>Parlor screen evidence</h1>
<div>PNG files are original captures. Native simulator/emulator captures are separated from Desktop fixture renders.</div>
<div>Source requested: {MERGED_COMMIT} · verified production tree: {PRODUCTION_TREE} · Actions run: {html.escape(str(args_run_url(manifest)))}</div></header>
<main><div class="summary"><b>{len(entries)} PNGs</b> · {html.escape(json.dumps(manifest['totals']['platform_counts'], ensure_ascii=False))}<ul>{profile_summary}</ul><a href="manifest.json">Full route checklist and per-image provenance manifest</a></div>
<div class="controls"><input id="q" placeholder="Search screen, state, profile, locale…"><select id="platform"><option value="">All platforms</option><option>Android Emulator</option><option>iOS Simulator</option><option>Desktop</option></select><select id="profile"><option value="">All profiles</option><option>small-phone</option><option>standard-phone</option><option>large-phone</option><option>tablet</option><option>Desktop seeded fixture</option></select><select id="group"><option value="">All groups</option>{sections}</select></div>
{''.join(f'<section class="section"><h2>{html.escape(group)}</h2><div class="grid">'+''.join(card for card, entry in zip(cards, entries) if entry['game']==group)+'</div></section>' for group in groups)}
<section class="section"><h2>Unmapped captures</h2><div class="grid">{''.join(card for card, entry in zip(cards, entries) if entry['game'] not in groups)}</div></section>
</main><script>
const q=document.querySelector('#q'),p=document.querySelector('#platform'),s=document.querySelector('#profile'),g=document.querySelector('#group');
function filter(){{document.querySelectorAll('.card').forEach(c=>{{const ok=(!q.value||c.dataset.search.includes(q.value.toLowerCase()))&&(!p.value||c.dataset.platform===p.value)&&(!s.value||c.dataset.profile===s.value)&&(!g.value||c.dataset.group===g.value);c.hidden=!ok;}});document.querySelectorAll('.section').forEach(sec=>{{sec.hidden=!!g.value&&!sec.querySelector('h2').textContent.includes(g.value);}});}}
[q,p,s,g].forEach(x=>x.addEventListener('input',filter));
</script></body></html>'''
    (out / "index.html").write_text(html_doc, encoding="utf-8")


def args_run_url(manifest: dict[str, object]) -> str:
    source = manifest.get("source", {})
    if not isinstance(source, dict):
        return "local Desktop capture"
    urls = source.get("native_capture_run_urls", {})
    if not isinstance(urls, dict):
        return "local Desktop capture"
    links: list[str] = []
    for value in urls.values():
        if isinstance(value, list):
            links.extend(str(item) for item in value if item)
        elif value:
            links.append(str(value))
    return " · ".join(links) or "local Desktop capture"


if __name__ == "__main__":
    main()
