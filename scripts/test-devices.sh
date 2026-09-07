#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
adb="$ANDROID_HOME/platform-tools/adb"
mkdir -p dist/local-tests
owned_serial=''
saved_error_dialogs=''
cleanup() {
  if [[ -n "$owned_serial" ]]; then
    timeout 15 "$adb" -s "$owned_serial" logcat -d > "dist/local-tests/logcat-$owned_serial.txt" 2>&1 || true
    if [[ "$saved_error_dialogs" == null ]]; then
      timeout 10 "$adb" -s "$owned_serial" shell settings delete global hide_error_dialogs >/dev/null 2>&1 || true
    elif [[ -n "$saved_error_dialogs" ]]; then
      timeout 10 "$adb" -s "$owned_serial" shell settings put global hide_error_dialogs "$saved_error_dialogs" >/dev/null 2>&1 || true
    fi
    timeout 15 "$adb" -s "$owned_serial" emu kill >/dev/null 2>&1 || true
  fi
}
finish() {
  result=$?
  if (( result != 0 )) && [[ -n "$owned_serial" ]]; then
    timeout 15 "$adb" shell dumpsys window > "dist/local-tests/failure-window-$owned_serial.txt" 2>&1 || true
    timeout 15 "$adb" logcat -d > "dist/local-tests/failure-logcat-$owned_serial.txt" 2>&1 || true
    timeout 15 "$adb" exec-out screencap -p > "dist/local-tests/failure-$owned_serial.png" 2>/dev/null || true
  fi
  cleanup
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
for api in 29 36; do
  saved_error_dialogs=''
  if [[ "$api" == 29 ]]; then
    avd="${TEST_AVD_API29:-quarantine-api29}"
    port="${TEST_PORT_API29:-5560}"
  else
    avd="${TEST_AVD_API36:-quarantine-api36}"
    port="${TEST_PORT_API36:-5562}"
  fi
  [[ "$port" =~ ^[0-9]+$ ]] && (( port >= 5554 && port <= 5682 && port % 2 == 0 )) || {
    echo "Invalid emulator port: $port" >&2; exit 1;
  }
  export ANDROID_SERIAL="emulator-$port"
  "$adb" start-server
  if "$adb" devices | awk '{print $1}' | grep -Fxq "$ANDROID_SERIAL"; then
    echo "$ANDROID_SERIAL is already in use. Choose another TEST_PORT_API$api." >&2; exit 1
  fi
  TEST_AVD_NAME="$avd" TEST_EMULATOR_PORT="$port" \
    TEST_EMULATOR_LOG="dist/local-tests/emulator-api$api.log" bash scripts/start-test-emulator.sh
  owned_serial="$ANDROID_SERIAL"
  actual_api="$("$adb" shell getprop ro.build.version.sdk | tr -d '\r')"
  [[ "$actual_api" == "$api" ]] || { echo "$avd must run API $api, got $actual_api." >&2; exit 1; }
  saved_error_dialogs="$("$adb" shell settings get global hide_error_dialogs | tr -d '\r')"
  # Android CTS uses this setup so system dialogs cannot steal injected test input.
  # App crashes still fail instrumentation; logcat is retained and the setting restored.
  "$adb" shell settings put global hide_error_dialogs 1
  "$adb" shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
  "$adb" shell wm size 480x800
  "$adb" shell wm density 160
  "$adb" shell settings put global window_animation_scale 0
  "$adb" shell settings put global transition_animation_scale 0
  "$adb" shell settings put global animator_duration_scale 0
  "$adb" shell settings put system screen_off_timeout 1800000
  "$adb" shell cmd window dismiss-keyguard
  # Test AVDs must be disposable: clear app state and suppress any previously enabled service.
  "$adb" shell settings delete secure enabled_accessibility_services
  "$adb" install -r app/build/outputs/apk/debug/app-debug.apk
  "$adb" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  "$adb" shell pm clear app.quarantine.android
  # Boot completion alone does not guarantee a focused, usable application window.
  timeout 30 "$adb" shell am start -W -n app.quarantine.android/.MainActivity
  ready=0
  for attempt in {1..15}; do
    focus="$(timeout 10 "$adb" shell dumpsys window | grep 'mCurrentFocus=' || true)"
    if [[ "$focus" == *'app.quarantine.android/app.quarantine.android.MainActivity'* ]]; then
      ready=$((ready + 1))
      if (( ready >= 3 )); then break; fi
    else
      ready=0
    fi
    sleep 2
  done
  (( ready >= 3 )) || { echo 'Android did not give Quarantine window focus. Check startup dialogs and diagnostic files.' >&2; exit 1; }
  report="dist/local-tests/instrumentation-api$api.txt"
  timeout 600 "$adb" shell am instrument -w \
    app.quarantine.android.test/androidx.test.runner.AndroidJUnitRunner | tee "$report"
  # am instrument can exit zero even when JUnit fails or instrumentation crashes.
  grep -Eq '^OK \([1-9][0-9]* tests?\)' "$report"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed' "$report"; then exit 1; fi
  cleanup
  owned_serial=''
done
echo 'API 29/36 instrumentation passed.'
