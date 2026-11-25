"""Compare managed API 35 failures against the same APKs on one cold emulator."""

import json
import re

from scripts.android import run_resume_process_acceptance as native


def main() -> int:
    output = native.ROOT / "build/ci-evidence/android-release-cold-probe"
    device = "android_managed_device"
    sdk, avd, config, source = native.preflight(output, device=device)
    apks = native.ROOT / "composeApp/build/outputs/apk"
    app, = (apks / "release").glob("*.apk")
    tests, = (apks / "androidTest/release").glob("*.apk")
    output.mkdir(mode=0o700, exist_ok=False)
    result = {
        "kind": "COLD_BOOT_DIAGNOSTIC_ONLY",
        "source": source,
        "apk_sha256": {"app": native.digest(app), "instrumentation": native.digest(tests)},
        "test_result": "INCOMPLETE",
    }
    try:
        app_id = config["applications"]["android"]["store_application_id"]
        component = app_id + ".test/android.test.InstrumentationTestRunner"
        with native.owned_emulator(sdk, avd, output, config["toolchains"][device]) as (adb, _):
            for options, apk in (((), app), (("-t",), tests)):
                native.contract.require(adb("install", *options, str(apk), timeout=120).strip().endswith("Success"),
                                        "Installing the existing Release APK pair failed")
            instrumentation = adb("shell", "am", "instrument", "-w", component, timeout=120)
            result["test_result"] = "PASS" if (
                re.search(r"(?m)^OK \(3 tests\)\s*$", instrumentation)
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
