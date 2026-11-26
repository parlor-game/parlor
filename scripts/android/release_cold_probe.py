"""Compare managed Android failures against the same APKs on one cold emulator."""

import argparse
from collections import Counter
import json
import re
import time

from scripts.android import run_resume_process_acceptance as native


def completed_tests(trace: str) -> Counter[str]:
    completed: Counter[str] = Counter()
    fields = {}
    for line in trace.splitlines():
        field = re.fullmatch(r"INSTRUMENTATION_STATUS: (class|test)=(.+)", line)
        if field:
            fields[field[1]] = field[2]
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            if line == "INSTRUMENTATION_STATUS_CODE: 0" and "class" in fields and "test" in fields:
                completed[f"{fields['class']}.{fields['test']}"] += 1
            fields.clear()
    return completed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=("release", "debug"), default="release")
    parser.add_argument("--trace-timing", action="store_true", help="Retain raw API 34 timing diagnostics")
    parser.add_argument("--explicit-roster", action="store_true", help="Measure explicit API 34 test class discovery")
    args = parser.parse_args()
    variant = args.variant
    if args.trace_timing and variant != "debug":
        parser.error("--trace-timing is restricted to the API 34 diagnostic")
    if args.explicit_roster and not args.trace_timing:
        parser.error("--explicit-roster requires the API 34 trace")
    device, app_id_key, test_count = {
        "release": ("android_managed_device", "store_application_id", 3),
        "debug": ("android_navigation_device", "debug_application_id", 35),
    }[variant]
    roster = None
    selection = ()
    if args.explicit_roster:
        roster = (native.ROOT / "scripts/android/api34-baseline-roster.txt").read_text().splitlines()
        native.contract.require(len(roster) == len(set(roster)) == test_count and
                                all(re.fullmatch(r"com\.parlor\.app(?:\.\w+)+\.test\w+", name) for name in roster),
                                "Invalid proven API 34 method set")
        classes = list(dict.fromkeys(name.rsplit(".", 1)[0] for name in roster))
        selection = ("-e", "class", ",".join(classes))
    output = native.ROOT / f"build/ci-evidence/android-{variant}-cold-probe"
    sdk, avd, config, source = native.preflight(output, device=device)
    apks = native.ROOT / "composeApp/build/outputs/apk"
    app, = (apks / variant).glob("*.apk")
    tests, = (apks / "androidTest" / variant).glob("*.apk")
    output.mkdir(mode=0o700, exist_ok=False)
    timings: dict[str, float] = {}
    result = {
        "kind": "COLD_BOOT_DIAGNOSTIC_ONLY",
        "source": source,
        "apk_sha256": {"app": native.digest(app), "instrumentation": native.digest(tests)},
        "timings_seconds": timings,
        "test_selection": "explicit_classes" if roster else "runner_discovery",
        "test_result": "INCOMPLETE",
    }
    probe_started = time.monotonic()
    try:
        app_id = config["applications"]["android"][app_id_key]
        component = app_id + ".test/android.test.InstrumentationTestRunner"
        with native.owned_emulator(sdk, avd, output, config["toolchains"][device]) as (adb, _):
            ready = time.monotonic()
            timings["provision_and_boot"] = ready - probe_started
            for options, apk in (((), app), (("-t",), tests)):
                native.contract.require(adb("install", *options, str(apk), timeout=120).strip().endswith("Success"),
                                        "Installing the existing APK pair failed")
            installed = time.monotonic()
            timings["install"] = installed - ready
            try:
                if args.trace_timing:
                    # Bound the measurement beyond the observed 7m29s managed run.
                    instrumentation = adb("shell", "am", "instrument", "-w", "-r", *selection, component,
                                          timeout=540, evidence=output / "instrumentation.txt")
                else:
                    instrumentation = adb("shell", "am", "instrument", "-w", component, timeout=300)
            finally:
                timings["instrumentation"] = time.monotonic() - installed
        result["test_result"] = "PASS" if (
            re.search(rf"(?m)^OK \({test_count} tests\)\s*$", instrumentation)
            and re.search(r"(?m)^INSTRUMENTATION_CODE: -1\s*$", instrumentation)
            and "FAILURES!!!" not in instrumentation
            and (roster is None or completed_tests(instrumentation) == Counter(roster))
        ) else "FAIL"
    except (RuntimeError, OSError) as error:
        result["error_type"] = type(error).__name__
    finally:
        timings["through_cleanup"] = time.monotonic() - probe_started
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    return 0 if result["test_result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
