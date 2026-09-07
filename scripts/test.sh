#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 scripts/test-release.py
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
adb="$ANDROID_HOME/platform-tools/adb"
[[ -x "$adb" ]] || { echo "Set ANDROID_HOME to an installed Android SDK." >&2; exit 1; }
# Use the repository's pinned Java when mise is available.
gradle=(./gradlew)
if command -v mise >/dev/null; then gradle=(mise exec -- ./gradlew); fi
"${gradle[@]}" --no-daemon --no-build-cache --rerun-tasks --dependency-verification strict \
  assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest lintDebug
bash scripts/test-devices.sh
echo 'All local tests passed (JVM, lint, and API 29/36 instrumentation).'
