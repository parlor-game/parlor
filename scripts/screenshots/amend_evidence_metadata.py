#!/usr/bin/env python3
"""Add verified post-capture XCTest and device-size review notes without recapturing images."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path


SMALL_IOS_ISSUE = (
    "XCTest at IOSAppLaunchUITests.swift:267: "
    "testLastLightLocalPlayerFieldsScrollWithNextAndDismissWithDone — "
    'XCTAssertEqual failed: ("XCTWaiterResult(rawValue: 2)") is not equal to '
    '("XCTWaiterResult(rawValue: 1)") - The full typed value must reach the actual Compose field.'
)


def capture_for(manifest: dict[str, object], platform: str, profile: str, screen_id: str, locale_prefix: str | None = None) -> str:
    captures = manifest.get("captures", [])
    if not isinstance(captures, list):
        raise ValueError("manifest captures must be a list")
    for capture in captures:
        if not isinstance(capture, dict):
            continue
        if capture.get("platform") != platform or capture.get("profile") != profile or capture.get("screen_id") != screen_id:
            continue
        if locale_prefix and not str(capture.get("locale", "")).startswith(locale_prefix):
            continue
        return str(capture["capture_filename"])
    raise ValueError(f"No matching capture for {platform}/{profile}/{screen_id}/{locale_prefix or '*'}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--source-run-id", required=True)
    parser.add_argument("--amendment-sha", required=True)
    args = parser.parse_args()

    root = args.root
    manifest_path = root / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    profiles = manifest["totals"]["profiles"]
    small_profile = next(
        row for row in profiles
        if row.get("platform") == "iOS Simulator" and row.get("profile") == "small-phone"
    )
    issues = small_profile.setdefault("profile_issues", [])
    if SMALL_IOS_ISSUE not in issues:
        issues.append(SMALL_IOS_ISSUE)

    reviewed = [
        {
            "severity": "review",
            "evidence": "Pixel 2 small-phone library is a two-column scrollable catalog. Two cards are fully visible in the initial viewport; the next row continues below the fold. No unreachable content or clipping is established by this first-screen capture.",
            "capture": capture_for(manifest, "Android Emulator", "small-phone", "shared.library"),
        },
        {
            "severity": "review",
            "evidence": "Pixel C tablet library was captured in landscape at 1280x900 dp and presents three columns. Visible cards and fixed bottom navigation fit in the captured viewport; deeper scroll states were not captured on this device.",
            "capture": capture_for(manifest, "Android Emulator", "tablet", "shared.library"),
        },
        {
            "severity": "review",
            "evidence": "The iPhone SE 375x667 pt Arabic name-entry keyboard capture keeps the primary continue action visible above the keyboard. No keyboard overlap is visible in this captured state.",
            "capture": capture_for(manifest, "iOS Simulator", "small-phone", "shared.keyboard-entry", "Arabic"),
        },
        {
            "severity": "metadata discrepancy",
            "evidence": "iPhone 16 simulator provenance reports 1179x2556 px, while the original screenshot PNGs decode to 1178x2556 px. Both values are preserved per image; screenshots were not resized.",
            "capture": capture_for(manifest, "iOS Simulator", "standard-phone", "shared.library"),
        },
        {
            "severity": "coverage gap",
            "evidence": "Dense game screens, private handoffs, overlays, and results have Desktop seeded production-composable fixtures where listed in the route checklist. Their native small/standard/large/tablet responsiveness was not established because the native UI suites did not progress into those game states.",
            "capture": None,
        },
    ]
    notes = manifest.setdefault("visual_review_notes", [])
    known = {item.get("evidence") for item in notes if isinstance(item, dict)}
    notes.extend(item for item in reviewed if item["evidence"] not in known)
    manifest["metadata_amendment"] = {
        "amended_at_utc": datetime.now(timezone.utc).isoformat(),
        "source_aggregate_run_id": args.source_run_id,
        "amendment_source_sha": args.amendment_sha,
        "screenshots_changed": False,
        "reason": "Added the second iPhone SE XCTest failure recorded in the original simulator log and documented visual review across actual device-size screenshots.",
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report_lines = [
        "# Native device-size review",
        "",
        "These observations use original screenshots from the Actions Android emulators and iOS simulators. They are visual review notes, not accessibility certification or a live-network test.",
        "",
    ]
    for item in reviewed:
        report_lines.extend([f"## {item['severity'].title()}", "", item["evidence"]])
        if item["capture"]:
            report_lines.append(f"\nEvidence: [{Path(item['capture']).name}]({item['capture']})")
        report_lines.append("")
    (root / "SIZE_REVIEW.md").write_text("\n".join(report_lines).rstrip() + "\n", encoding="utf-8")

    readme = root / "README.txt"
    text = readme.read_text(encoding="utf-8") if readme.exists() else ""
    if "SIZE_REVIEW.md" not in text:
        readme.write_text(text.rstrip() + "\nDevice-size visual review and verified XCTest details: SIZE_REVIEW.md.\n", encoding="utf-8")
    index = root / "index.html"
    html = index.read_text(encoding="utf-8")
    marker = '<a href="manifest.json">Full route checklist and per-image provenance manifest</a>'
    if marker in html and 'href="SIZE_REVIEW.md"' not in html:
        html = html.replace(marker, marker + ' · <a href="SIZE_REVIEW.md">Device-size review notes</a>')
        index.write_text(html, encoding="utf-8")

    captures = manifest.get("captures", [])
    missing = [
        str(capture.get("capture_filename"))
        for capture in captures
        if isinstance(capture, dict) and not (root / str(capture.get("capture_filename"))).is_file()
    ]
    if missing:
        raise SystemExit(f"Missing original screenshot files after metadata amendment: {missing[:5]}")
    print(json.dumps({
        "png_count": manifest["totals"]["png_count"],
        "small_ios_issues": len(issues),
        "size_review_notes": len(reviewed),
        "all_capture_links_exist": True,
    }, indent=2))


if __name__ == "__main__":
    main()
