#!/usr/bin/env python3
"""Capture public Android app routes from a clean emulator using adb only."""

from __future__ import annotations

import argparse
import json
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


def display_metadata(device: str) -> dict[str, str | float]:
    size_output = adb(device, "shell", "wm", "size")
    density_output = adb(device, "shell", "wm", "density")
    size_line = next(line for line in size_output.splitlines() if "Physical size:" in line or "Override size:" in line)
    density_line = next(line for line in density_output.splitlines() if "Physical density:" in line or "Override density:" in line)
    width, height = (int(value) for value in size_line.rsplit(":", 1)[1].strip().split("x"))
    density = int(density_line.rsplit(":", 1)[1].strip())
    scale = float(adb(device, "shell", "settings", "get", "system", "font_scale").strip())
    rotation = int(adb(device, "shell", "settings", "get", "system", "user_rotation").strip())
    orientations = {0: "portrait", 1: "landscape", 2: "reverse-portrait", 3: "reverse-landscape"}
    return {
        "resolution_px": f"{width}x{height}",
        "density_dpi": density,
        "logical_viewport_dp": f"{round(width * 160 / density)}x{round(height * 160 / density)}",
        "font_scale": scale,
        "orientation": orientations.get(rotation, f"rotation-{rotation}"),
    }


def hierarchy(device: str) -> ET.Element:
    adb(device, "shell", "uiautomator", "dump", "/sdcard/window.xml")
    data = subprocess.run(["adb", "-s", device, "exec-out", "cat", "/sdcard/window.xml"],
                          check=True, capture_output=True).stdout
    return ET.fromstring(data)


def click_text(device: str, candidates: list[str], rounds: int = 8) -> str:
    size_output = adb(device, "shell", "wm", "size")
    size_line = next(line for line in size_output.splitlines() if "Physical size:" in line or "Override size:" in line)
    screen_width, screen_height = (int(value) for value in size_line.rsplit(":", 1)[1].strip().split("x"))
    center_x = screen_width // 2
    lower_y = screen_height * 9 // 10
    upper_y = screen_height // 3
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
            adb(device, "shell", "input", "swipe", str(center_x), str(lower_y), str(center_x), str(upper_y), "350")
            time.sleep(0.4)
    raise RuntimeError(f"No visible native control matched any of: {candidates}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", default="emulator-5554")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--package", default="me.parlor.android.debug")
    parser.add_argument("--activity", default="com.parlor.app.MainActivity")
    parser.add_argument("--profile", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--locale", default="en-US")
    parser.add_argument("--theme", choices=["light", "dark"], default="light")
    args = parser.parse_args()

    adb(args.device, "shell", "pm", "clear", args.package)
    adb(args.device, "shell", "cmd", "uimode", "night", "yes" if args.theme == "dark" else "no")
    adb(args.device, "shell", "am", "start", "-W", "-n", f"{args.package}/{args.activity}")
    time.sleep(5)
    if args.locale.startswith("ar"):
        click_text(args.device, ["Open settings."])
        click_text(args.device, ["العربية"])
        time.sleep(1.5)
        click_text(args.device, ["افتح مكتبة الألعاب."])
        time.sleep(1.5)
    viewport = display_metadata(args.device)
    width, height = viewport["resolution_px"].split("x")
    tag = f"{args.profile}-{args.locale}-{args.theme}-{viewport['font_scale']}x-{width}x{height}"
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / f"provenance-{args.profile}-{args.locale}-{args.theme}.json").write_text(
        json.dumps({
            "platform": "Android Emulator",
            "profile": args.profile,
            "device_model": args.model,
            "locale": "Arabic (app language override `ar`; emulator system default remains English)" if args.locale.startswith("ar") else "English (emulator system default)",
            "locale_tag": "ar" if args.locale.startswith("ar") else "en-US",
            "locale_method": "Selected the production Arabic language option in Settings through the Android accessibility tree." if args.locale.startswith("ar") else "Fresh app data on an English-default Android emulator.",
            "theme": args.theme,
            **viewport,
            "source_sha": run("git", "rev-parse", "HEAD").strip(),
            "method": "Native app installed and launched on an Android emulator; screenshot bytes from adb screencap; UI routes selected from uiautomator accessibility-tree nodes.",
        }, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    capture(args.device, args.output / f"android-{tag}-library.png")

    english_games = [
        ("whodunit", "Whodunit", ["Open Whodunit", "Whodunit"]),
        ("mafia", "Mafia", ["Open the Mafia", "Mafia"]),
        ("last-light", "Last Light", ["Open Last Light", "Last Light"]),
        ("egyptian-dominoes", "Egyptian Dominoes", ["Open Egyptian Dominoes", "Egyptian Dominoes"]),
        ("ghamza", "Ghamza", ["Open Ghamza", "Ghamza"]),
        ("word-impostor", "Word Impostor", ["Open Word Impostor", "Word Impostor"]),
    ]
    arabic_games = [
        ("whodunit", "من القاتل", ["افتح من القاتل"]),
        ("mafia", "حرامية وظباط", ["افتح لعبة حرامية وظباط"]),
        ("last-light", "الضوء الأخير", ["افتح الضوء الأخير", "العب الضوء الأخير"]),
        ("egyptian-dominoes", "الدومنة المصرية", ["افتح الدومنة المصرية"]),
        ("ghamza", "غمزة", ["افتح غمزة"]),
        ("word-impostor", "لعبة الإمبوستر", ["افتح لعبة الإمبوستر"]),
    ]
    games = arabic_games if args.locale.startswith("ar") else english_games
    for slug, title, selectors in games:
        adb(args.device, "shell", "am", "force-stop", args.package)
        adb(args.device, "shell", "am", "start", "-W", "-n", f"{args.package}/{args.activity}")
        time.sleep(3)
        selected = click_text(args.device, selectors)
        time.sleep(1)
        capture(args.device, args.output / f"android-{tag}-{slug}-mode-picker.png")
        print(f"captured game picker: {title} ({selected})")
        adb(args.device, "shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)



if __name__ == "__main__":
    main()
