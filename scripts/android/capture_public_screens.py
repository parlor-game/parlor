#!/usr/bin/env python3
"""Capture public Android app routes from a clean emulator using adb only."""

from __future__ import annotations

import argparse
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def run(*args: str, check: bool = True) -> str:
    result = subprocess.run(args, check=check, capture_output=True, text=True)
    return result.stdout


def adb(device: str, *args: str) -> str:
    return run("adb", "-s", device, *args)


def capture(device: str, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    image = subprocess.run(["adb", "-s", device, "exec-out", "screencap", "-p"],
                           check=True, capture_output=True).stdout
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("adb returned a non-PNG screenshot")
    output.write_bytes(image)


def hierarchy(device: str) -> ET.Element:
    adb(device, "shell", "uiautomator", "dump", "/sdcard/window.xml")
    data = subprocess.run(["adb", "-s", device, "exec-out", "cat", "/sdcard/window.xml"],
                          check=True, capture_output=True).stdout
    return ET.fromstring(data)


def click_text(device: str, candidates: list[str], rounds: int = 8) -> str:
    for attempt in range(rounds):
        root = hierarchy(device)
        parents: dict[ET.Element, ET.Element] = {}
        for parent in root.iter():
            for child in parent:
                parents[child] = parent
        for node in root.iter("node"):
            label = " ".join((node.attrib.get("text", ""), node.attrib.get("content-desc", "")))
            if any(candidate.casefold() in label.casefold() for candidate in candidates):
                target = node
                while target is not root and target.attrib.get("clickable") != "true":
                    target = parents.get(target, root)
                bounds = target.attrib.get("bounds", "")
                if bounds:
                    left_top, right_bottom = bounds.split("][")
                    left, top = map(int, left_top.removeprefix("[").split(","))
                    right, bottom = map(int, right_bottom.removesuffix("]").split(","))
                    adb(device, "shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                    time.sleep(1.2)
                    return label.strip()
        if attempt + 1 < rounds:
            adb(device, "shell", "input", "swipe", "540", "1950", "540", "700", "350")
            time.sleep(0.4)
    raise RuntimeError(f"No visible native control matched any of: {candidates}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", default="emulator-5554")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--package", default="me.parlor.android.debug")
    parser.add_argument("--activity", default="com.parlor.app.MainActivity")
    args = parser.parse_args()

    adb(args.device, "shell", "pm", "clear", args.package)
    adb(args.device, "shell", "am", "start", "-W", "-n", f"{args.package}/{args.activity}")
    time.sleep(5)
    capture(args.device, args.output / "android-library-en-light-1.0-1080x2400.png")

    games = [
        ("whodunit", "Whodunit", ["Open Whodunit", "Whodunit"]),
        ("mafia", "Mafia", ["Open the Mafia", "Mafia"]),
        ("last-light", "Last Light", ["Open Last Light", "Last Light"]),
        ("egyptian-dominoes", "Egyptian Dominoes", ["Open Egyptian Dominoes", "Egyptian Dominoes"]),
        ("ghamza", "Ghamza", ["Open Ghamza", "Ghamza"]),
        ("word-impostor", "Word Impostor", ["Open Word Impostor", "Word Impostor"]),
    ]
    for slug, title, selectors in games:
        adb(args.device, "shell", "am", "force-stop", args.package)
        adb(args.device, "shell", "am", "start", "-W", "-n", f"{args.package}/{args.activity}")
        time.sleep(3)
        selected = click_text(args.device, selectors)
        time.sleep(1)
        capture(args.device, args.output / f"android-{slug}-mode-picker-en-light-1.0-1080x2400.png")
        print(f"captured game picker: {title} ({selected})")
        adb(args.device, "shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)


if __name__ == "__main__":
    main()
