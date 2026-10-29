#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -f target.txt ]]; then
  echo "target.txt is missing. Export a fresh kit from ELSEWHERE." >&2
  exit 1
fi
TARGET="$(tr -d '\r\n' < target.txt)"
if [[ -z "$TARGET" ]]; then
  echo "target.txt is empty." >&2
  exit 1
fi

command -v adb >/dev/null 2>&1 || {
  echo "adb was not found in PATH. Install Android platform-tools and enable USB debugging." >&2
  exit 1
}
PYTHON="${PYTHON:-python3}"
command -v "$PYTHON" >/dev/null 2>&1 || {
  echo "Python 3 was not found in PATH (set PYTHON to override)." >&2
  exit 1
}
[[ -f frida-jdwp-loader.py ]] || {
  echo "frida-jdwp-loader.py is missing." >&2
  exit 1
}
[[ -f elsewhere-sensor.js ]] || {
  echo "elsewhere-sensor.js is missing." >&2
  exit 1
}

echo "ELSEWHERE JDWP kit"
echo "Target : $TARGET"
echo "Starting frida-jdwp-loader. The target will be restarted."
echo "Logs: adb logcat -s ELSEWHERE-SENSOR:*"
echo ""

ARGS=(frida -n "$TARGET" -i script -l ./elsewhere-sensor.js)
LOCAL_GADGET="$(find "$ROOT" -maxdepth 1 -type f -name 'frida-gadget-*.so' | head -n 1 || true)"
if [[ -n "$LOCAL_GADGET" ]]; then
    echo "Using local Gadget: $LOCAL_GADGET"
    ARGS+=(-g "$LOCAL_GADGET")
fi

exec "$PYTHON" frida-jdwp-loader.py "${ARGS[@]}"
