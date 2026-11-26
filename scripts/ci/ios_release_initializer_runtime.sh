#!/usr/bin/env bash
set -euo pipefail

# The caller has already run the full Debug suite and owns this simulator.
test "$#" -eq 1
test "$(uname -m)" = arm64
simulator_udid="$1"
evidence=build/ci-evidence
derived_data=build/xcode-release-runtime
test ! -e "$derived_data"
test ! -e "$evidence/ios-release-runtime.xcresult"
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.project.kotlin.internal.compiler.arguments.log.level=warning"
export GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.project.parlor.profileIosReleaseLink=true"
export GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.project.parlor.compareIosStaticInitializerPass=true"

target=(
  -project iosApp/iosApp.xcodeproj
  -scheme iosApp
  -configuration Release
  -sdk iphonesimulator
  -destination "platform=iOS Simulator,id=$simulator_udid"
  -destination-timeout 120
  -derivedDataPath "$derived_data"
)
xcodebuild "${target[@]}" ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build-for-testing |
  tee "$evidence/xcode-release-runtime-build.log"

grep -Fq -- '-Xdisable-phases=RemoveRedundantCallsToStaticInitializersPhase' "$evidence/xcode-release-runtime-build.log"
app="$derived_data/Build/Products/Release-iphonesimulator/Parlor.app"
test "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")" = me.parlor.ios
framework="$app/Frameworks/ComposeApp.framework/ComposeApp"
shasum -a 256 "$app/Parlor" "$framework" | tee "$evidence/ios-release-runtime-binaries.sha256"

# Use the Xcode-generated run manifest to test the exact Release product.
shopt -s nullglob
test_runs=("$derived_data"/Build/Products/*.xctestrun)
test "${#test_runs[@]}" -eq 1
/usr/bin/python3 -B - "${test_runs[0]}" "$app" >"$evidence/ios-release-runtime-target.txt" <<'PYTHON'
import plistlib
import sys
from pathlib import Path

manifest, app = map(Path, sys.argv[1:])
with manifest.open("rb") as stream:
    run = plistlib.load(stream)
if "TestConfigurations" in run:
    targets = [target for config in run["TestConfigurations"] for target in config["TestTargets"]]
else:
    targets = [target for target in run.values() if isinstance(target, dict)]
paths = [
    Path(target["UITargetAppPath"].replace("__TESTROOT__", str(manifest.parent))).resolve(strict=True)
    for target in targets
    if "UITargetAppPath" in target
]
if paths != [app.resolve(strict=True)]:
    raise SystemExit("XCTest plan does not target the fingerprinted Release application")
print(paths[0].relative_to(Path.cwd()))
PYTHON
suite=iosAppUITests/IOSAppLaunchUITests
xcodebuild test-without-building   -xctestrun "${test_runs[0]}"   -destination "platform=iOS Simulator,id=$simulator_udid"   -destination-timeout 120   -parallel-testing-enabled NO   -maximum-concurrent-test-simulator-destinations 1   -resultBundlePath "$evidence/ios-release-runtime.xcresult"   -only-testing:"$suite/testColdLaunchRendersComposeHomeWithoutUnexpectedAlert"   -only-testing:"$suite/testOriginalGamesExposeLocalAndNearbySetupInEnglish"   -only-testing:"$suite/testArabicDisplayNameAndAsciiRoomCodeThroughNativeInput" |
  tee "$evidence/xcode-release-runtime-test.log"
shasum -a 256 -c "$evidence/ios-release-runtime-binaries.sha256"
xcrun xcresulttool get test-results summary --path "$evidence/ios-release-runtime.xcresult"   >"$evidence/ios-release-runtime-summary.json"
jq -e '.passedTests == 3 and .failedTests == 0 and .skippedTests == 0'   "$evidence/ios-release-runtime-summary.json"
