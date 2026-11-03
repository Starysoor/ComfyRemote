#!/usr/bin/env bash
set -euo pipefail

PKG="com.comfyremote.app"
ACTIVITY="${PKG}/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
EVIDENCE="tools/qa-screens"
mkdir -p "$EVIDENCE"

adb wait-for-device
adb install -r "$APK"
adb logcat -c

check_alive() {
  local description="$1"
  local pid
  pid=$(adb shell pidof "$PKG" | tr -d '\r' || true)
  if [[ -z "$pid" ]]; then
    echo "FAIL: $description: Iris process not running" >&2
    exit 1
  fi
  adb exec-out screencap -p > "$EVIDENCE/$description.png"
  test -s "$EVIDENCE/$description.png"
  echo "PASS: $description; PID=$pid"
}

echo "QA step 1: fresh launcher cold start"
adb shell am start -W -n "$ACTIVITY"
sleep 4
check_alive "cold-start"

echo "QA step 2: force-stop process and relaunch from launcher"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY"
sleep 4
check_alive "after-force-stop"

echo "QA step 3: disabled system animations"
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY"
sleep 3
check_alive "animations-disabled"

adb logcat -d -s AndroidRuntime:E > "$EVIDENCE/android-runtime-errors.log"
if grep -q "Process: $PKG" "$EVIDENCE/android-runtime-errors.log"; then
  echo "FAIL: app fatal crash found in AndroidRuntime" >&2
  cat "$EVIDENCE/android-runtime-errors.log" >&2
  exit 1
fi

echo "PASS: emulator process lifecycle smoke checks"
