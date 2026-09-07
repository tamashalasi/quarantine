#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${QUARANTINE_KEYSTORE:?Set QUARANTINE_KEYSTORE}"
: "${QUARANTINE_KEY_ALIAS:?Set QUARANTINE_KEY_ALIAS}"
: "${QUARANTINE_STORE_PASSWORD:?Set QUARANTINE_STORE_PASSWORD}"
: "${QUARANTINE_KEY_PASSWORD:?Set QUARANTINE_KEY_PASSWORD}"
if [[ $# -ne 2 ]]; then
  echo 'Usage: sign-release.sh UNSIGNED.apk SIGNED.apk' >&2
  exit 2
fi
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 "$1"
"$ANDROID_HOME/build-tools/34.0.0/apksigner" sign \
  --ks "$QUARANTINE_KEYSTORE" --ks-key-alias "$QUARANTINE_KEY_ALIAS" \
  --ks-pass env:QUARANTINE_STORE_PASSWORD --key-pass env:QUARANTINE_KEY_PASSWORD \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --v4-signing-enabled false --out "$2" "$1"
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose --print-certs "$2"
