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
    local identity="UITEST0001.$bundle" entitlements="$work/parlor-lab.entitlements"
  python3 - "$identity" "$entitlements" <<'PYPLIST'
import plistlib
import sys
with open(sys.argv[2], "wb") as output:
    plistlib.dump({"application-identifier": sys.argv[1], "keychain-access-groups": [sys.argv[1]]}, output)
PYPLIST
    xcodebuild -quiet -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
      -derivedDataPath "$work/parlor-xcode" \
      ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- \
      CODE_SIGN_STYLE=Manual CODE_SIGN_ENTITLEMENTS="$entitlements" build
    app="$work/parlor-xcode/Build/Products/Debug-iphonesimulator/Parlor.app"
    if [[ ! -d "$app" ]]; then
      apps=("$work"/parlor-xcode/Build/Products/Debug-iphonesimulator/*.app)
      [[ ${#apps[@]} == 1 && -d "${apps[0]}" ]]
      app="${apps[0]}"
    fi
    [[ "$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")" == "$bundle" ]]
    codesign --verify --strict "$app"
    codesign -d --entitlements :- "$app" > "$package/simulator-entitlements.plist"
    [[ "$(/usr/libexec/PlistBuddy -c 'Print application-identifier' "$package/simulator-entitlements.plist")" == "$identity" ]]
    [[ "$(/usr/libexec/PlistBuddy -c 'Print keychain-access-groups:0' "$package/simulator-entitlements.plist")" == "$identity" ]]
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
  local index port serial major minor guest_pid
  export ANDROID_AVD_HOME="$work/parlor-avds"
  mkdir -p "$ANDROID_AVD_HOME"
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
  test -c /dev/kvm
  sudo chmod a+rw /dev/kvm
  "$sdk/sdkmanager" emulator platform-tools "$image"
  "$emulator" -accel-check | tee "$proof/accelerator.txt"
  "$emulator" -version > "$proof/emulator-version.txt"
  IFS=. read -r major minor _ < <(sed -n 's/^Pkg.Revision *= *//p' "$ANDROID_HOME/emulator/source.properties")
  (( major > 36 || (major == 36 && minor >= 5) ))
  local memory_mib=2048 cpu_cores=2
  local display=()
  if (( DEVICE_COUNT > 3 )); then
    # Bound guest CPU pressure on the four-core Linux runner. The last
    # five-seat fleet lost its runner during fifth-guest boot at two vCPUs.
    memory_mib=1536
    cpu_cores=1
    display=(-skin 720x1280)
  fi
  awk -v count="$DEVICE_COUNT" -v memory="$memory_mib" '
    /^MemAvailable:/ {
      print "available_kib=" $2
      print "required_kib=" (count * memory + 3072) * 1024
      exit !($2 >= (count * memory + 3072) * 1024)
    }' /proc/meminfo | tee "$proof/host-memory.txt"
  adb version > "$proof/adb-version.txt"
  adb start-server
  for ((index=0; index<DEVICE_COUNT; index++)); do
    port=$((5554 + 2 * index))
    serial="emulator-$port"
    if adb -s "$serial" get-state >/dev/null 2>&1; then return 1; fi
    echo no | "$sdk/avdmanager" create avd --name "parlor-$index" --package "$image" --device pixel_4
    "$emulator" -avd "parlor-$index" -port "$port" -no-window -no-audio -no-snapshot \
      -no-boot-anim -memory "$memory_mib" -cores "$cpu_cores" -gpu swiftshader_indirect "${display[@]}" \
      > "$work/parlor-emulator-$port.log" 2>&1 &
    guest_pid=$!
    printf '%s\n' "$guest_pid" >> "$work/parlor-emulator-pids"
    printf '%s\n' "$serial" >> "$devices"
    printf 'Waiting for %s to boot\n' "$serial"
    timeout 240 bash -c '
      until [[ "$(adb -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" == 1 ]]; do
        kill -0 "$2" || exit 1
        sleep 2
      done
    ' bash "$serial" "$guest_pid"
    adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r' > "$proof/$serial-boot.txt"
    adb -s "$serial" shell settings put global window_animation_scale 0
    adb -s "$serial" shell settings put global transition_animation_scale 0
    adb -s "$serial" shell settings put global animator_duration_scale 0
    printf '%s completed boot\n' "$serial"
  done
}

# Keep a wedged CoreSimulator request from consuming the entire device lease.
# Stdout stays machine-readable for create, inventory and container receipts.
bounded_simctl() {
  local deadline="$1"
  shift
  python3 - "$proof" "$deadline" "$@" <<'SIMCTL'
import json
from pathlib import Path
import subprocess
import sys
import time

proof, seconds, operation, *args = sys.argv[1:]
started = time.monotonic()
try:
    result = subprocess.run(["/usr/bin/xcrun", "simctl", operation, *args],
                            timeout=int(seconds), check=False)
    status = result.returncode if result.returncode >= 0 else 128 - result.returncode
except subprocess.TimeoutExpired:
    status = 124
with (Path(proof) / "ios-provisioning.jsonl").open("a") as stream:
    stream.write(json.dumps({"operation": operation, "status": status,
                             "elapsed_seconds": round(time.monotonic() - started, 3)}) + "\n")
print(f"CoreSimulator {operation} exited with {status}", file=sys.stderr)
raise SystemExit(status)
SIMCTL
}

boot_ios_group() {
  local runtime index device
  [[ "$(uname -m)" == arm64 ]]
  xcodebuild -version | tee "$proof/xcode.txt"
  grep -Fxq 'Build version 17C529' "$proof/xcode.txt"
  bounded_simctl 90 list runtimes -j > "$proof/runtime-inventory.json"
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
    printf 'Allocating simulator %s of %s\n' "$((index + 1))" "$DEVICE_COUNT"
    device=$(bounded_simctl 90 create "Parlor Smart Lobby $index" com.apple.CoreSimulator.SimDeviceType.iPhone-16 "$runtime")
    printf '%s\n' "$device" >> "$devices"
    bounded_simctl 240 bootstatus "$device" -b
  done
}

install_ios_controller() {
  local asset=AXe-macOS-v2.1.1-universal.tar.gz
  mkdir -p "$work/parlor-axe"
  curl -fsSL --retry 3 "https://github.com/cameroncooke/AXe/releases/download/v2.1.1/$asset" -o "$work/$asset"
  printf '%s  %s\n' 8788838b1b32059d143cf06541b72fcaead85c16274457cdbff086510a5b17fa "$work/$asset" | shasum -a 256 -c -
  tar -xzf "$work/$asset" -C "$work/parlor-axe"
  find "$work/parlor-axe" -name axe -type f -print > "$work/parlor-axe-path"
  [[ -x "$(cat "$work/parlor-axe-path")" ]]
}

install_android() {
  local serial base expected installed attempt
  (cd "$package" && sha256sum -c SHA256SUMS)
  read -r expected _ < "$package/SHA256SUMS"
  while IFS= read -r serial <&3; do
    local record="$proof/$serial"
    mkdir -p "$record"
    if (( DEVICE_COUNT >= 5 )); then
      adb -s "$serial" shell wm density 320
      adb -s "$serial" shell wm density > "$record/display-density.txt"
    fi
    if [[ "$PAIRING_NAME" == android ]]; then
      timeout 45 bash -c '
        until adb -s "$1" shell svc wifi enable && adb -s "$1" shell svc data disable; do sleep 2; done
      ' bash "$serial"
    else
      adb -s "$serial" shell svc data disable
    fi
    adb -s "$serial" shell input keyevent KEYCODE_WAKEUP
    adb -s "$serial" shell wm dismiss-keyguard
    adb -s "$serial" shell svc power stayon true
    timeout 90 adb -s "$serial" install "$package/parlor.apk" </dev/null
    base=$(adb -s "$serial" shell pm path "$apk_id" | tr -d '\r' | sed -n 's/^package:\(.*\/base\.apk\)$/\1/p')
    [[ -n "$base" ]]
    # Verify the bytes in Android's installed APK in place. Pulling every
    # copy through ADB failed mid-transfer on a five-emulator runner.
    installed=""
    for ((attempt=1; attempt<=3; attempt++)); do
      if installed=$(timeout 30 adb -s "$serial" shell sha256sum "$base" | tr -d '\r') &&
          [[ "$installed" =~ ^([0-9a-f]{64})[[:space:]] ]]; then
        installed=${BASH_REMATCH[1]}
        break
      fi
      installed=""
      sleep 2
    done
    if [[ "$installed" != "$expected" ]]; then
      printf '%s installed APK checksum missing or different after verification\n' "$serial" >&2
      return 1
    fi
    printf '%s\n' "$installed" > "$record/installed-apk-sha256.txt"
    adb -s "$serial" shell getprop ro.build.fingerprint > "$record/android-build.txt"
    adb -s "$serial" shell ip -4 addr show wlan0 > "$record/wlan0.txt"
    adb -s "$serial" shell am start -W -n "$apk_id/$activity" > "$record/launch.txt"
    tr -d "\r" < "$record/launch.txt" | grep -Fx 'Status: ok' > /dev/null
    adb -s "$serial" shell pidof "$apk_id" > "$record/app-pid.txt"
  done 3< "$devices"
}

install_ios() {
  local device installed app
  (cd "$package" && shasum -a 256 -c SHA256SUMS)
  ditto -x -k "$package/parlor-simulator.zip" "$work/parlor-extracted"
  local apps=("$work"/parlor-extracted/*.app)
  [[ ${#apps[@]} == 1 && -d "${apps[0]}" ]]
  app="${apps[0]}"
  codesign --verify --strict "$app"
  local executable
  executable=$(/usr/libexec/PlistBuddy -c 'Print CFBundleExecutable' "$app/Info.plist")
  python3 "$verification" check-ios-entitlement "$app/$executable" "UITEST0001.$bundle" > "$proof/simulator-entitlements.json"
  while IFS= read -r device <&3; do
    local record="$proof/$device"
    mkdir -p "$record"
    bounded_simctl 90 install "$device" "$app"
    if installed=$(bounded_simctl 45 get_app_container "$device" "$bundle" app); then
      printf 'simctl\n' > "$record/installed-app-location-source.txt"
    else
      # After a successful install, a stalled container query can be replaced
      # by a unique bundle lookup under this disposable device's data directory.
      # The same byte-for-byte manifest check still follows.
      installed=$(python3 - "$device" "$bundle" <<'BUNDLE'
from pathlib import Path
import plistlib
import re
import sys

device, expected_bundle = sys.argv[1:]
uuid = re.compile(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
if not uuid.fullmatch(device):
    raise SystemExit("Invalid owned Simulator ID")
root = Path.home() / "Library/Developer/CoreSimulator/Devices" / device / "data/Containers/Bundle/Application"
matches = []
for app in root.glob("*/*.app"):
    if not uuid.fullmatch(app.parent.name) or app.is_symlink() or app.parent.is_symlink():
        continue
    with (app / "Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    if info.get("CFBundleIdentifier") == expected_bundle:
        matches.append(app)
if len(matches) != 1:
    raise SystemExit("No unique installed app in the owned Simulator")
print(matches[0])
BUNDLE
      )
      printf 'verified-owned-simulator-bundle\n' > "$record/installed-app-location-source.txt"
    fi
    python3 "$verification" manifest "$installed" > "$record/installed-app-manifest.json"
    cmp "$package/app-manifest.json" "$record/installed-app-manifest.json"
    bounded_simctl 45 spawn "$device" launchctl list > "$record/runtime-processes.txt"
    if [[ "$PAIRING_NAME" == mixed ]]; then
      open -a Simulator --args -CurrentDeviceUDID "$device"
    fi
    bounded_simctl 60 launch "$device" "$bundle" > "$record/app-launch.txt"
  done 3< "$devices"
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
    if [[ "$TARGET" == android ]]; then
      python3 - "$LAN_UI_DIR/verification/mobile-lan/preflight-android.sh" <<'SDK_RETRY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
command = '"$sdk/cmdline-tools/latest/bin/sdkmanager" \'platform-tools\' "$image"'
retry = """download_sdk() {
  local sdk_state
  sdk_state=$(mktemp -d "$out/sdk-state.XXXXXX")
  ANDROID_USER_HOME="$sdk_state" "$sdk/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' "$image"
}
if ! download_sdk 2>&1 | tee "$out/sdk-install-1.log"; then
  grep -Fq 'Error on ZipFile' "$out/sdk-install-1.log"
  printf 'Retrying the corrupted SDK download once using fresh job-owned cache.\n'
  download_sdk
fi"""
if text.count(command) != 1:
    raise SystemExit("The pinned upstream SDK setup changed")
path.write_text(text.replace(command, retry))
SDK_RETRY
    fi
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
    install_ios_controller
    open -a Simulator --args -CurrentDeviceUDID "$(head -n 1 "$devices")"
  fi
}

# The mixed relay's first ping establishes reachability. Keep a bounded,
# independent series during gameplay so a later interruption can be separated
# from an application session transition. This output carries no packet data.
mixed_lan_probe() {
  local sent completed delivered
  while [[ -f "$work/mobile-lan/ready" ]]; do
    sent="$(date -u +%s%3N)"
    delivered=0
    if timeout 4 adb -s emulator-5554 shell ping -I wlan0 -c 1 -W 2 192.168.137.2 >/dev/null 2>&1; then
      delivered=1
    fi
    completed="$(date -u +%s%3N)"
    printf '{"send_epoch_ms":%s,"complete_epoch_ms":%s,"delivered":%s}\n' "$sent" "$completed" "$delivered"
    sleep 3
  done
}

cleanup() {
  python3 "$verification" stop
  if [[ "$TARGET" == android ]]; then
    for emulator_log in "$work"/parlor-emulator-*.log; do
      [[ -f "$emulator_log" ]] || continue
      grep -Ei '(^|: )(ERROR|PANIC|FATAL)( |:)' "$emulator_log" | tail -50         > "$proof/${emulator_log##*/}.errors.txt" || true
    done
  fi
  if [[ "$LAB_PAIRING" == mixed ]]; then
    (cd "$LAN_UI_DIR" && bash verification/mobile-lan/trial.sh cleanup "$TARGET")
  elif [[ -f "$devices" ]]; then
    while IFS= read -r device <&3; do
      if [[ "$TARGET" == android ]]; then
        adb -s "$device" emu kill || true
      else
        bounded_simctl 45 delete "$device" || true
      fi
    done 3< "$devices"
  fi
}

case "${1:?}" in
  build) build ;;
  install) install ;;
  cleanup) cleanup ;;
  mixed-lan-probe) mixed_lan_probe ;;
  *) exit 2 ;;
esac
