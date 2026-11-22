#!/usr/bin/env bash
set -euo pipefail
work="$RUNNER_TEMP"
package="$work/parlor-package"
proof="$work/parlor-proof"
devices="$work/parlor-devices"
bundle=me.parlor.ios.debug
apk_id=me.parlor.android.debug
activity=com.parlor.app.MainActivity
verification="$GITHUB_WORKSPACE/scripts/smart-lobby-native/lab.py"
export DEVELOPER_DIR=/Applications/Xcode_26.3.app/Contents/Developer

build() {
  mkdir -p "$package"
  cd "$GITHUB_WORKSPACE/candidate"
  [[ "$(git rev-parse HEAD)" == "$CANDIDATE" ]]
  [[ -z "$(git status --porcelain --untracked-files=no)" ]]
  git rev-parse HEAD HEAD^{tree} > "$package/source.txt"
  if [[ "$TARGET" == android ]]; then
    ./gradlew :composeApp:assembleDebug --dependency-verification=strict --no-configuration-cache
    cp composeApp/build/outputs/apk/debug/composeApp-debug.apk "$package/parlor.apk"
    (cd "$package" && sha256sum parlor.apk > SHA256SUMS)
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "build-tools;36.0.0"
    "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "$package/parlor.apk" > "$package/signature.txt"
  else
    [[ "$(uname -m)" == arm64 ]]
    xcodebuild -version | tee "$package/xcode.txt"
    grep -Fxq 'Build version 17C529' "$package/xcode.txt"
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
      -derivedDataPath "$work/parlor-xcode" \
      ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
    app="$work/parlor-xcode/Build/Products/Debug-iphonesimulator/Parlor.app"
    if [[ ! -d "$app" ]]; then
      apps=("$work"/parlor-xcode/Build/Products/Debug-iphonesimulator/*.app)
      [[ ${#apps[@]} == 1 && -d "${apps[0]}" ]]
      app="${apps[0]}"
    fi
    [[ "$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")" == "$bundle" ]]
    python3 "$verification" manifest "$app" > "$package/app-manifest.json"
    ditto -c -k --sequesterRsrc --keepParent "$app" "$package/parlor-simulator.zip"
    (cd "$package" && shasum -a 256 parlor-simulator.zip > SHA256SUMS)
  fi
  git diff --quiet HEAD
}

boot_android_group() {
  local image='system-images;android-35;google_apis;x86_64'
  local sdk="$ANDROID_HOME/cmdline-tools/latest/bin"
  local emulator="$ANDROID_HOME/emulator/emulator"
  local index port serial major minor
  export ANDROID_AVD_HOME="$work/parlor-avds"
  mkdir -p "$ANDROID_AVD_HOME"
  test -c /dev/kvm
  sudo chmod a+rw /dev/kvm
  "$sdk/sdkmanager" emulator platform-tools "$image"
  "$emulator" -accel-check | tee "$proof/accelerator.txt"
  "$emulator" -version > "$proof/emulator-version.txt"
  IFS=. read -r major minor _ < <(sed -n 's/^Pkg.Revision *= *//p' "$ANDROID_HOME/emulator/source.properties")
  (( major > 36 || (major == 36 && minor >= 5) ))
  awk -v count="$DEVICE_COUNT" '/^MemAvailable:/ {exit !($2 >= (count * 2 + 3) * 1024 * 1024)}' /proc/meminfo
  adb start-server
  for ((index=0; index<DEVICE_COUNT; index++)); do
    port=$((5554 + 2 * index))
    serial="emulator-$port"
    if adb -s "$serial" get-state >/dev/null 2>&1; then return 1; fi
    echo no | "$sdk/avdmanager" create avd --name "parlor-$index" --package "$image" --device pixel_4
    "$emulator" -avd "parlor-$index" -port "$port" -no-window -no-audio -no-snapshot \
      -no-boot-anim -memory 2048 -cores 2 -gpu swiftshader_indirect \
      > "$work/parlor-emulator-$port.log" 2>&1 &
    printf '%s\n' "$!" >> "$work/parlor-emulator-pids"
    printf '%s\n' "$serial" >> "$devices"
  done
  while IFS= read -r serial; do
    timeout 360 bash -c 'until [[ "$(adb -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" == 1 ]]; do sleep 2; done' bash "$serial"
  done < "$devices"
}

boot_ios_group() {
  local runtime index device
  [[ "$(uname -m)" == arm64 ]]
  xcodebuild -version | tee "$proof/xcode.txt"
  grep -Fxq 'Build version 17C529' "$proof/xcode.txt"
  xcrun simctl list runtimes -j > "$proof/runtime-inventory.json"
  runtime=$(python3 - "$proof/runtime-inventory.json" <<'RUNTIME'
import json
import sys

with open(sys.argv[1]) as inventory:
    available = [runtime for runtime in json.load(inventory)["runtimes"]
                 if runtime["isAvailable"] and ".iOS-" in runtime["identifier"]]
if not available:
    raise SystemExit("No supported iOS simulator runtime")
pin = next((runtime for runtime in available if runtime["version"] == "26.3"), None)
selected = pin or max(available, key=lambda value: tuple(map(int, value["version"].split("."))))
print(selected["identifier"])
RUNTIME
  )
  printf '%s\n' "$runtime" > "$proof/selected-runtime.txt"
  for ((index=0; index<DEVICE_COUNT; index++)); do
    device=$(xcrun simctl create "Parlor Smart Lobby $index" com.apple.CoreSimulator.SimDeviceType.iPhone-16 "$runtime")
    printf '%s\n' "$device" >> "$devices"
    xcrun simctl boot "$device"
    xcrun simctl bootstatus "$device" -b
  done
  local asset=AXe-macOS-v2.1.1-universal.tar.gz
  mkdir -p "$work/parlor-axe"
  curl -fsSL --retry 3 "https://github.com/cameroncooke/AXe/releases/download/v2.1.1/$asset" -o "$work/$asset"
  printf '%s  %s\n' 8788838b1b32059d143cf06541b72fcaead85c16274457cdbff086510a5b17fa "$work/$asset" | shasum -a 256 -c -
  tar -xzf "$work/$asset" -C "$work/parlor-axe"
  find "$work/parlor-axe" -name axe -type f -print > "$work/parlor-axe-path"
  [[ -x "$(cat "$work/parlor-axe-path")" ]]
}

install_android() {
  local serial base
  (cd "$package" && sha256sum -c SHA256SUMS)
  while IFS= read -r serial; do
    local record="$proof/$serial"
    mkdir -p "$record"
    adb -s "$serial" shell svc data disable
    adb -s "$serial" shell input keyevent KEYCODE_WAKEUP
    adb -s "$serial" shell wm dismiss-keyguard
    adb -s "$serial" shell svc power stayon true
    adb -s "$serial" install "$package/parlor.apk"
    base=$(adb -s "$serial" shell pm path "$apk_id" | tr -d '\r' | sed -n 's/^package:\(.*\/base\.apk\)$/\1/p')
    [[ -n "$base" ]]
    adb -s "$serial" pull "$base" "$work/$serial.apk" >/dev/null
    cmp "$package/parlor.apk" "$work/$serial.apk"
    sha256sum < "$work/$serial.apk" > "$record/installed-apk-sha256.txt"
    adb -s "$serial" shell getprop ro.build.fingerprint > "$record/android-build.txt"
    adb -s "$serial" shell ip -4 addr show wlan0 > "$record/wlan0.txt"
    adb -s "$serial" shell am start -W -n "$apk_id/$activity" > "$record/launch.txt"
    tr -d "\r" < "$record/launch.txt" | grep -Fx 'Status: ok' > /dev/null
    adb -s "$serial" shell pidof "$apk_id" > "$record/app-pid.txt"
  done < "$devices"
}

install_ios() {
  local device installed app
  (cd "$package" && shasum -a 256 -c SHA256SUMS)
  ditto -x -k "$package/parlor-simulator.zip" "$work/parlor-extracted"
  local apps=("$work"/parlor-extracted/*.app)
  [[ ${#apps[@]} == 1 && -d "${apps[0]}" ]]
  app="${apps[0]}"
  while IFS= read -r device; do
    local record="$proof/$device"
    mkdir -p "$record"
    xcrun simctl install "$device" "$app"
    installed=$(xcrun simctl get_app_container "$device" "$bundle" app)
    python3 "$verification" manifest "$installed" > "$record/installed-app-manifest.json"
    cmp "$package/app-manifest.json" "$record/installed-app-manifest.json"
    xcrun simctl spawn "$device" launchctl list > "$record/runtime-processes.txt"
    if [[ "$PAIRING_NAME" == mixed ]]; then
      open -a Simulator --args -CurrentDeviceUDID "$device"
    fi
    xcrun simctl launch "$device" "$bundle" > "$record/app-launch.txt"
  done < "$devices"
}

install() {
  PAIRING_NAME="$(python3 "$verification" pairing)"
  mkdir -p "$proof"
  [[ "$(head -n 1 "$package/source.txt")" == "$CANDIDATE" ]]
  cp "$package/source.txt" "$package/SHA256SUMS" "$proof/"
  git rev-parse HEAD > "$proof/harness-sha.txt"
  git -C "$LAN_UI_DIR" rev-parse HEAD > "$proof/controller-sha.txt"
  printf '%s\t%s\t%s\n' "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT" "$GITHUB_JOB" > "$proof/workflow-run.tsv"
  if [[ "$PAIRING_NAME" == mixed ]]; then
    (cd "$LAN_UI_DIR" && bash "verification/mobile-lan/preflight-$TARGET.sh")
    if [[ "$TARGET" == android ]]; then
      printf 'emulator-5554\n' > "$devices"
    else
      cp "$work/mobile-lan-preflight/device" "$devices"
    fi
  elif [[ "$TARGET" == android ]]; then
    boot_android_group
  else
    boot_ios_group
  fi

  if [[ "$TARGET" == android ]]; then
    install_android
    if [[ "$PAIRING_NAME" == android ]]; then python3 "$verification" wifi; fi
  else
    install_ios
  fi
}

cleanup() {
  python3 "$verification" stop
  if [[ "$LAB_PAIRING" == mixed ]]; then
    (cd "$LAN_UI_DIR" && bash verification/mobile-lan/trial.sh cleanup "$TARGET")
  elif [[ -f "$devices" ]]; then
    while IFS= read -r device; do
      if [[ "$TARGET" == android ]]; then
        adb -s "$device" emu kill || true
      else
        xcrun simctl delete "$device" || true
      fi
    done < "$devices"
  fi
}

case "${1:?}" in
  build) build ;;
  install) install ;;
  cleanup) cleanup ;;
  *) exit 2 ;;
esac
