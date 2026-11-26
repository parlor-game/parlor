"""Compare managed Android failures against the same APKs on one cold emulator."""

import argparse
import json
import re

from scripts.android import run_resume_process_acceptance as native


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=("release", "debug"), default="release")
    variant = parser.parse_args().variant
    device, app_id_key, test_count = {
        "release": ("android_managed_device", "store_application_id", 3),
        "debug": ("android_navigation_device", "debug_application_id", 35),
    }[variant]
    output = native.ROOT / f"build/ci-evidence/android-{variant}-cold-probe"
    sdk, avd, config, source = native.preflight(output, device=device)
    apks = native.ROOT / "composeApp/build/outputs/apk"
    app, = (apks / variant).glob("*.apk")
    tests, = (apks / "androidTest" / variant).glob("*.apk")
    output.mkdir(mode=0o700, exist_ok=False)
    result = {
        "kind": "COLD_BOOT_DIAGNOSTIC_ONLY",
        "source": source,
        "apk_sha256": {"app": native.digest(app), "instrumentation": native.digest(tests)},
        "test_result": "INCOMPLETE",
    }
    try:
        app_id = config["applications"]["android"][app_id_key]
        component = app_id + ".test/android.test.InstrumentationTestRunner"
        with native.owned_emulator(sdk, avd, output, config["toolchains"][device]) as (adb, _):
            for options, apk in (((), app), (("-t",), tests)):
                native.contract.require(adb("install", *options, str(apk), timeout=120).strip().endswith("Success"),
                                        "Installing the existing APK pair failed")
            instrumentation = adb("shell", "am", "instrument", "-w", component, timeout=300)
            result["test_result"] = "PASS" if (
                re.search(rf"(?m)^OK \({test_count} tests\)\s*$", instrumentation)
                and re.search(r"(?m)^INSTRUMENTATION_CODE: -1\s*$", instrumentation)
                and "FAILURES!!!" not in instrumentation
            ) else "FAIL"
    except (RuntimeError, OSError) as error:
        result["error_type"] = type(error).__name__
    finally:
        (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    return 0 if result["test_result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
