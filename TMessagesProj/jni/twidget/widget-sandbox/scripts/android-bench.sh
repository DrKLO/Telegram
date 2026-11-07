#!/bin/bash
# Renderer bench on Android (docs/android-renderer-bench.md): runs the demo's
# `bench` autorun for one renderer, captures `dumpsys meminfo` of every process
# of the package while the widgets are held alive (the WebView renderer process
# is an isolated uid the app cannot measure itself), pulls the report.
#
#   scripts/android-bench.sh webview 20            # cold process, no prewarm
#   scripts/android-bench.sh webview 20 prewarm    # a spare WebView loaded at launch first
#   scripts/android-bench.sh blitz 20
#   scripts/android-bench.sh none 20             # engine + validator only (NullRenderer in Bench.kt): the renderer cost is the difference
#   CORPUS=common scripts/android-bench.sh litehtml 20   # the common-profile corpus: every renderer draws the same picture
#
# Output: reports/android-bench/<renderer>[-prewarm]-<count>.json and .meminfo.txt.
# Needs: a booted emulator/device, the demo installed (cd apps/android && ./gradlew :demo:installDebug).
# Several devices attached: ANDROID_SERIAL=<serial> TAG=phone scripts/android-bench.sh … (adb honours ANDROID_SERIAL).
set -euo pipefail
RENDERER=${1:-webview}; COUNT=${2:-10}; PREWARM=${3:-}; HOLD=${4:-8}; POOLS=${POOLS:-}   # POOLS=1: validator pool (WidgetHost.Options.pools) with POOL_PREWARM validators built at launch
CORPUS=${CORPUS:-feed}   # CORPUS=common: the renderer-comparison corpus (corpus/common/README.md) instead of compat + generated
PKG=org.telegram.widgetsandbox.demo
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
OUT=reports/android-bench; mkdir -p "$OUT"
NAME="$RENDERER${PREWARM:+-prewarm}${POOLS:+-pools}$([ "$CORPUS" = feed ] || echo "-$CORPUS")-$COUNT${TAG:+-$TAG}"   # TAG=phone keeps a device's reports apart from the emulator's
EXTRA="--es renderer $RENDERER --ei count $COUNT --ei hold $HOLD${PREWARM:+ --ez prewarm true}${POOLS:+ --ez pools true --ei prewarmValidators ${POOL_PREWARM:-4}} --es corpus $CORPUS"

$ADB shell am force-stop $PKG; $ADB shell input keyevent KEYCODE_WAKEUP; sleep 2
$ADB logcat -c
$ADB shell am start -W -n $PKG/.MainActivity --es autorun bench $EXTRA > /dev/null
wait_phase() { for i in $(seq 1 600); do if $ADB logcat -d -s TwidgetBench 2>/dev/null | grep -q "phase=$1"; then return 0; fi; sleep 0.5; done; echo "timeout waiting for phase=$1" >&2; return 1; }
wait_phase live
# Every process whose name starts with the package: the app and (WebView) :sandboxed_process0.
{ echo "== ps"; $ADB shell "ps -A | grep -i twidget"; echo "== dumpsys meminfo"; $ADB shell dumpsys meminfo; } > "$OUT/$NAME.meminfo.txt"
# The WebView renderer runs as com.google.android.webview:sandboxed_process0 under an isolated uid; it is part of the widget's cost.
echo "== PSS by process ($NAME)"; grep -E "^ {4,5}[0-9,]+K: ($PKG|com.google.android.webview:sandboxed)" "$OUT/$NAME.meminfo.txt" || true
wait_phase done
$ADB shell run-as $PKG cat files/bench-report.json > "$OUT/$NAME.json"
echo "report: $OUT/$NAME.json"
