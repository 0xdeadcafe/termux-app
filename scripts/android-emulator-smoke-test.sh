#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" && -f "$ROOT_DIR/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$ROOT_DIR/local.properties" | head -1)"
fi
[[ -n "$SDK_DIR" ]] || { echo "Set ANDROID_HOME or sdk.dir in local.properties" >&2; exit 1; }

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"
AVD="${TERMUX_SMOKE_AVD:-termux_api30_x86_64}"
LOG="${TMPDIR:-/tmp}/termux-smoke-$AVD.log"
STARTED_EMULATOR=0

[[ -x "$ADB" && -x "$EMULATOR" ]] || { echo "Missing adb/emulator under $SDK_DIR" >&2; exit 1; }

cleanup() {
  if [[ "$STARTED_EMULATOR" == 1 ]]; then
    "$ADB" emu kill >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

cd "$ROOT_DIR"
ANDROID_HOME="$SDK_DIR" ANDROID_SDK_ROOT="$SDK_DIR" ./gradlew :app:assembleDebug >/dev/null
APK=(app/build/outputs/apk/debug/termux-app_*debug_x86_64.apk)
[[ -f "${APK[0]}" ]] || { echo "x86_64 debug APK not found" >&2; exit 1; }

if ! "$ADB" get-state >/dev/null 2>&1; then
  [[ -r /dev/kvm && -w /dev/kvm ]] || { echo "No adb device and /dev/kvm is not accessible. Re-login or run: sg kvm -c '$0'" >&2; exit 1; }
  nohup "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot >"$LOG" 2>&1 &
  STARTED_EMULATOR=1
fi

"$ADB" wait-for-device
timeout 180 bash -c 'until "$0" shell getprop sys.boot_completed 2>/dev/null | tr -d "\r" | grep -q 1; do sleep 2; done' "$ADB"

"$ADB" uninstall com.termux >/dev/null 2>&1 || true
"$ADB" install -r -d -t "${APK[0]}" >/dev/null
"$ADB" logcat -c
"$ADB" shell monkey -p com.termux -c android.intent.category.LAUNCHER 1 >/dev/null

timeout 90 bash -c 'until "$0" shell ps -A | grep -q "com.termux" && "$0" shell ps -A | grep -Eq "(^|[[:space:]])-?(bash|sh|login)$"; do sleep 2; done' "$ADB"
"$ADB" logcat -d -v time >"$LOG"
if grep -Eiq 'avc: denied \{ execute_no_trans \}|execvp.*(EACCES|Permission denied)|Permission denied.*(/data/data/com.termux/files/usr/bin/(bash|login|sh)|exec)' "$LOG"; then
  echo "Termux smoke test failed; see $LOG" >&2
  exit 1
fi

echo "Termux smoke test passed using ${APK[0]} on $AVD"
echo "Log: $LOG"
