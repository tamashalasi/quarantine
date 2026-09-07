#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
port="${TEST_EMULATOR_PORT:-5554}"
export ANDROID_SERIAL="emulator-$port"
boot_timeout="${TEST_BOOT_TIMEOUT:-300}"
log="${TEST_EMULATOR_LOG:-emulator.log}"

# Invoked indirectly by the EXIT trap.
# shellcheck disable=SC2317
diagnostics() {
  result=$?
  if (( result != 0 )); then
    echo "::error::Emulator startup failed. See emulator output below."
    tail -200 "$log" 2>/dev/null || true
    timeout 10 adb devices -l || true
    df -h . "$ANDROID_HOME" || true
    if [[ -n "${emulator_pid:-}" ]]; then
      kill "$emulator_pid" 2>/dev/null || true
    fi
  fi
}
trap 'diagnostics' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$ANDROID_HOME/emulator/emulator" -accel-check
timeout 15 adb start-server
nohup "$ANDROID_HOME/emulator/emulator" \
  -avd "${TEST_AVD_NAME:-quarantine-test}" -port "$port" \
  -accel on -cores 2 -memory 3072 -timezone Etc/UTC -skin 480x800 -feature -Vulkan \
  -no-window -no-audio -no-boot-anim -no-snapshot \
  -camera-back none -camera-front none -gpu swiftshader > "$log" 2>&1 < /dev/null &
emulator_pid=$!

# One deadline covers both ADB discovery and Android boot. Never wait for ADB indefinitely.
deadline=$((SECONDS + boot_timeout))
while (( SECONDS < deadline )); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    echo '::error::Emulator process exited before Android finished booting.'
    exit 1
  fi
  remaining=$((deadline - SECONDS))
  booted="$(timeout "$((remaining < 5 ? remaining : 5))" adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$booted" == 1 ]] && (( SECONDS < deadline )); then
    remaining=$((deadline - SECONDS))
    if timeout "$((remaining < 10 ? remaining : 10))" adb shell cmd window dismiss-keyguard; then
      echo "Emulator $ANDROID_SERIAL booted successfully."
      exit 0
    fi
  fi
  remaining=$((deadline - SECONDS))
  if (( remaining <= 0 )); then break; fi
  echo "Waiting for $ANDROID_SERIAL to boot ($remaining seconds remaining)..."
  sleep "$((remaining < 5 ? remaining : 5))"
done
echo "::error::Emulator did not finish booting within $boot_timeout seconds."
exit 1
