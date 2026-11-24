"""Capture two public UI profiles from the Android release APK installed by CI."""

import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

DEVICE = "emulator-5554"
PACKAGE = os.getenv("PARLOR_APP_ID", "me.parlor.android")
ACTIVITY = f"{PACKAGE}/com.parlor.app.MainActivity"
GAMES = (
    ("whodunit", "Whodunit", "من القاتل", True),
    ("mafia", "Mafia", "حرامية وظباط", True),
    ("last-light", "Last Light", "الضوء الأخير", True),
    ("egyptian-dominoes", "Egyptian Dominoes", "الدومنة المصرية", False),
    ("ghamza", "Ghamza", "غمزة", False),
    ("word-impostor", "Word Impostor", "الإمبوستر", False),
)


def adb(*args):
    return subprocess.check_output(("adb", "-s", DEVICE, *args), timeout=45)


def tree():
    for _ in range(4):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/parlor-review.xml")
            return ET.fromstring(adb("exec-out", "cat", "/sdcard/parlor-review.xml"))
        except (ET.ParseError, subprocess.CalledProcessError):
            time.sleep(0.6)
    raise RuntimeError("Android did not return a valid public accessibility tree")


def label(node):
    text = " ".join((node.get("text", ""), node.get("content-desc", "")))
    # Player names are isolated for bidirectional display in the real UI.
    return text.translate(dict.fromkeys(range(0x2066, 0x206A))).strip().casefold()


def find(target, *, width, height, scroll=False, click=False, max_tap_y=None):
    target = target.casefold()
    for _ in range(8):
        root = tree()
        parents = {child: parent for parent in root.iter() for child in parent}
        hits = [node for node in root.iter("node")
                if target in label(node)]
        if hits:
            exact = [node for node in hits if label(node) == target]
            if not click:
                return True
            for node in exact + hits:
                while node in parents and node.get("clickable") != "true":
                    node = parents[node]
                if node.get("clickable") != "true" or node.get("enabled") != "true":
                    continue
                bounds = [int(v) for v in re.findall(r"\d+", node.get("bounds", ""))]
                if len(bounds) != 4:
                    continue
                left, top, right, bottom = bounds
                if right <= left or bottom <= top:
                    continue
                center_y = (top + bottom) // 2
                if max_tap_y is not None and center_y >= max_tap_y:
                    continue
                adb("shell", "input", "tap", str((left + right) // 2), str(center_y))
                time.sleep(0.5)
                return True
        if scroll:
            adb("shell", "input", "swipe", str(2 * width // 3), str(3 * height // 4),
                str(2 * width // 3), str(height // 3), "280")
        time.sleep(0.5)
    return False


def screenshot(path):
    data = adb("exec-out", "screencap", "-p")
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Native screen capture did not return PNG bytes")
    path.write_bytes(data)


def launch():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def profile(output, arabic, only_game=None):
    width, height, density, scale = (720, 1280, 360, "2.0") if arabic else (1080, 2280, 440, "1.0")
    name = "ar-dark-320dp-2x" if arabic else "en-light-393dp-1x"
    adb("shell", "wm", "size", f"{width}x{height}")
    adb("shell", "wm", "density", str(density))
    adb("shell", "settings", "put", "system", "font_scale", scale)
    adb("shell", "cmd", "uimode", "night", "yes" if arabic else "no")
    adb("shell", "pm", "clear", PACKAGE)
    launch()

    def tap(target, scroll=False, max_tap_y=None):
        if not find(target, width=width, height=height, scroll=scroll, click=True, max_tap_y=max_tap_y):
            raise RuntimeError(f"Expected enabled native control: {target}")

    def expect(target, scroll=False):
        if not find(target, width=width, height=height, scroll=scroll):
            raise RuntimeError(f"Expected visible native label: {target}")

    if arabic:
        tap("Open settings.")
        tap("العربية", scroll=True)
        tap("افتح مكتبة الألعاب.")
    expect("مكتبة الألعاب" if arabic else "Game library")
    screenshot(output / f"{name}-library.png")
    back_label = "العودة إلى الشاشة الرئيسية." if arabic else "Return to the home screen."
    rows = []
    for slug, english, arabic_title, local in GAMES:
        if only_game and slug != only_game:
            continue
        print(f"Capturing {name} / {slug}", flush=True)
        try:
            if only_game is None:
                launch()
            catalog_label = {
                "whodunit": "افتح من القاتل واختر طريقة اللعب.",
                "word-impostor": "لعبة الإمبوستر",
            }.get(slug, arabic_title)
            # The enlarged catalog can expose only part of the first card above
            # fixed navigation. Scroll until its center is inside the catalog.
            safe_catalog_bottom = 3 * height // 4 if arabic else None
            tap(catalog_label if arabic else english, scroll=True, max_tap_y=safe_catalog_bottom)
            expect(back_label)
            expect(arabic_title if arabic else english)
            screenshot(output / f"{name}-{slug}-picker.png")
            if local:
                tap("ابدأ جلسة تمرير ولعب" if arabic else "Start a pass-and-play", scroll=True)
                if any(back_label in node.get("content-desc", "") for node in tree().iter("node")):
                    raise RuntimeError("Local mode tap left the game picker visible")
                if arabic and slug == "whodunit":
                    expect("اختر لغز الليلة.")
                    expect("English")
                screenshot(output / f"{name}-{slug}-local-entry.png")
            else:
                expect("افتح غرفة" if arabic else "Open a room", scroll=True)
                expect("أدخل رمز المضيف" if arabic else "Enter the host", scroll=True)
            rows.append({"game": slug, "picker": True, "local_entry_checked": local})
        except (RuntimeError, subprocess.SubprocessError) as exc:
            rows.append({"game": slug, "failure": str(exc)})
            screenshot(output / f"{name}-{slug}-failure.png")
    return {"profile": name, "pixels": [width, height], "density": density, "font_scale": scale, "routes": rows}


def play_last_light(output):
    width, height, density = 1080, 2280, 440
    adb("shell", "wm", "size", f"{width}x{height}")
    adb("shell", "wm", "density", str(density))
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "cmd", "uimode", "night", "no")
    adb("shell", "pm", "clear", PACKAGE)
    launch()
    params = {"width": width, "height": height}
    stages = []

    def tap(target, scroll=False, max_tap_y=None):
        if not find(target, **params, click=True, scroll=scroll, max_tap_y=max_tap_y):
            raise RuntimeError(f"Missing interactive control: {target}")

    def expect(target):
        if not find(target, **params):
            raise RuntimeError(f"Missing public state: {target}")

    def handoff(exclude=None):
        for _ in range(8):
            labels = [label(node) for node in tree().iter("node")]
            for name in ("AlphaQA", "BetaQA"):
                if name != exclude and any(f"pass the device to {name.casefold()}" in text for text in labels):
                    if any(re.search(r"\bcard [1-9] of [1-9]", text) for text in labels):
                        raise RuntimeError("Private card exposed in the handoff accessibility tree")
                    return name
            time.sleep(0.7)
        raise RuntimeError("Expected covered handoff to the active seat")

    try:
        tap("Open Last Light and choose local or nearby play", scroll=True, max_tap_y=3 * height // 4)
        tap("Start a pass-and-play session on this device.", scroll=True)
        expect("Gather around one device")
        for field, name in enumerate(("AlphaQA", "BetaQA"), 1):
            tap(f"Player {field}", scroll=True)
            adb("shell", "input", "text", name)
            expect(name)
            if b"mInputShown=true" in adb("shell", "dumpsys", "input_method"):
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
        tap("Start match", scroll=True)
        # A two-seat match must eliminate a player by its eleventh challenge.
        # Each challenge consumes one of the two fixed six-step fuses.
        for round_number in range(1, 12):
            first = handoff()
            screenshot(output / f"last-light-round-{round_number:02}-first-covered.png")
            stages.append(f"Round {round_number}: covered handoff to {first}")
            tap(f"I am {first}")
            tap("Show hand", scroll=True)
            tap("Card 1 of", scroll=True)
            tap("Play 1 card", scroll=True)

            second = handoff(exclude=first)
            screenshot(output / f"last-light-round-{round_number:02}-second-covered.png")
            stages.append(f"Round {round_number}: {first} played one card. Covered handoff to {second}.")
            tap(f"I am {second}")
            tap(f"Challenge {first}", scroll=True)

            for _ in range(8):
                labels = [label(node) for node in tree().iter("node")]
                if any("last light standing" in text for text in labels):
                    for player in (first, second):
                        if any(f"{player.casefold()} wins." in text for text in labels):
                            screenshot(output / "last-light-terminal-winner.png")
                            stages.append(f"Round {round_number}: {player} wins.")
                            return
                verdict = next((value for value in ("the claim was true.", "bluff caught.")
                                if any(value in text for text in labels)), None)
                if verdict:
                    screenshot(output / f"last-light-round-{round_number:02}-public-result.png")
                    stages.append(f"Round {round_number}: {second} challenged. Public result: {verdict}")
                    tap("Next round", scroll=True)
                    break
                time.sleep(0.7)
            else:
                raise RuntimeError("No public round verdict or terminal winner appeared")
        raise RuntimeError("Two-seat match did not reach a winner within eleven rounds")
    finally:
        (output / "last-light-stages.json").write_text(json.dumps(stages, indent=2) + "\n")


def main():
    output = Path(sys.argv[1])
    if len(sys.argv) == 3 and sys.argv[2] == "--last-light-match":
        play_last_light(output)
        return
    profiles = []
    only_game = sys.argv[2] if len(sys.argv) == 3 else None
    for locale in ((True,) if only_game else (False, True)):
        try:
            profiles.append(profile(output, locale, only_game))
        except (RuntimeError, subprocess.SubprocessError) as exc:
            profiles.append({"locale": "ar" if locale else "en", "failure": str(exc)})
    (output / "coverage.json").write_text(json.dumps(profiles, indent=2, ensure_ascii=False) + "\n")
    if any("failure" in record for case in profiles for record in (case, *case.get("routes", []))):
        sys.exit("One or more native visual actions failed. See the captured screenshots and coverage receipt.")


if __name__ == "__main__":
    main()
