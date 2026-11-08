#!/usr/bin/env bash
# Verifies a built APK. Exits non-zero if ANY check fails.
#
#   scripts/verify-apk.sh --apk app.apk \
#       [--package org.telegram.messenger] [--version-name 1.0.0] [--version-code 19] \
#       [--res-dir TMessagesProj/src/main/res] [--out-dir build-reports]
#
# Checks: valid zip, apksigner signature, package / versionName / versionCode,
# native libraries for all four ABIs, custom patch present (marker strings in dex),
# launcher icon is the generated custom icon (pixel comparison), icon is not upstream's.
#
# Tools come from the Android SDK build-tools (ANDROID_HOME / ANDROID_SDK_ROOT) or
# from AAPT2=/path and APKSIGNER=/path.
set -uo pipefail

APK=""; PKG=""; VNAME=""; VCODE=""; RES_DIR=""; OUT_DIR="build-reports"
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) APK="$2"; shift ;;
    --package) PKG="$2"; shift ;;
    --version-name) VNAME="$2"; shift ;;
    --version-code) VCODE="$2"; shift ;;
    --res-dir) RES_DIR="$2"; shift ;;
    --out-dir) OUT_DIR="$2"; shift ;;
    -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "verify-apk: unknown argument '$1'" >&2; exit 2 ;;
  esac
  shift
done
[ -n "$APK" ] || { echo "verify-apk: --apk is required" >&2; exit 2; }

FAILS=0
pass() { echo "  [ OK ] $*"; }
fail() { echo "  [FAIL] $*"; FAILS=$((FAILS+1)); }
warn() { echo "  [WARN] $*"; }

find_tool() {  # find_tool NAME ENVVAR
  local name="$1" override="${!2:-}"
  if [ -n "$override" ]; then echo "$override"; return; fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -n "$sdk" ] && [ -d "$sdk/build-tools" ]; then
    local candidate
    while IFS= read -r candidate; do
      [ -x "$candidate/$name" ] && { echo "$candidate/$name"; return; }
    done < <(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V -r)
  fi
  command -v "$name" 2>/dev/null || true
}

AAPT2="$(find_tool aapt2 AAPT2)"
APKSIGNER="$(find_tool apksigner APKSIGNER)"

echo "verify-apk: $APK"
[ -f "$APK" ] || { echo "  [FAIL] APK not found"; exit 1; }
[ -n "$AAPT2" ] || { echo "  [FAIL] aapt2 not found (set ANDROID_HOME)"; exit 1; }
[ -n "$APKSIGNER" ] || { echo "  [FAIL] apksigner not found (set ANDROID_HOME)"; exit 1; }
mkdir -p "$OUT_DIR"

# 1. zip integrity
if unzip -tq "$APK" >/dev/null 2>&1; then pass "archive is a valid zip"; else fail "archive is corrupt"; fi

# 2. signature
if SIG_OUT="$("$APKSIGNER" verify --print-certs "$APK" 2>&1)"; then
  CERT="$(printf '%s\n' "$SIG_OUT" | sed -nE 's/^Signer #1 certificate SHA-256 digest: (.*)$/\1/p' | head -n1)"
  pass "signature verifies (signer SHA-256: ${CERT:-unknown})"
else
  fail "apksigner verify failed: $(printf '%s' "$SIG_OUT" | head -n 3 | tr '\n' ' ')"
fi

# 3. badging: package / version
BADGING="$("$AAPT2" dump badging "$APK" 2>&1)" || { fail "aapt2 dump badging failed"; BADGING=""; }
printf '%s\n' "$BADGING" > "$OUT_DIR/apk-badging.txt"
PKG_LINE="$(printf '%s\n' "$BADGING" | grep -m1 '^package:' || true)"
# Anchored on purpose: the line also contains compileSdkVersionCodename='..', which a loose name='..' match would hit.
A_PKG="$(printf '%s' "$PKG_LINE"  | sed -nE "s/^package:[[:space:]]+name='([^']*)'.*/\1/p")"
A_CODE="$(printf '%s' "$PKG_LINE" | sed -nE "s/.*[[:space:]]versionCode='([^']*)'.*/\1/p")"
A_NAME="$(printf '%s' "$PKG_LINE" | sed -nE "s/.*[[:space:]]versionName='([^']*)'.*/\1/p")"
expect() {  # expect LABEL ACTUAL EXPECTED  (skipped when EXPECTED is empty)
  [ -n "$3" ] || return 0
  if [ "$2" = "$3" ]; then pass "$1 matches expected ($3)"; else fail "$1 is '$2', expected '$3'"; fi
}
if [ -n "$A_PKG" ]; then pass "package=$A_PKG versionName=$A_NAME versionCode=$A_CODE"; else fail "could not read package info from badging"; fi
expect "package" "$A_PKG" "$PKG"
expect "versionName" "$A_NAME" "$VNAME"
expect "versionCode" "$A_CODE" "$VCODE"

# 4. native libraries for all ABIs
LIBS="$(unzip -Z1 "$APK" 2>/dev/null | grep -E '^lib/[^/]+/lib.*\.so$' || true)"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  if printf '%s\n' "$LIBS" | grep -qE "^lib/$abi/libtmessages[^/]*\.so$"; then pass "native library for $abi present"
  else fail "native library libtmessages*.so missing for $abi"; fi
done

# 5. custom patch really compiled in (string literals survive R8 obfuscation)
DEX_TMP="$(mktemp)"; unzip -p "$APK" 'classes*.dex' > "$DEX_TMP" 2>/dev/null || true
for marker in custom_unsent custom_edit_history; do
  if grep -aq "$marker" "$DEX_TMP"; then pass "custom patch marker '$marker' found in dex"
  else fail "custom patch marker '$marker' NOT found in dex - patch missing from this build"; fi
done
rm -f "$DEX_TMP"

# 6. launcher icon
ICON_PATHS="$(printf '%s\n' "$BADGING" | sed -nE "s/^application-icon-([0-9]+):'([^']+)'.*/\1 \2/p")"
if [ -z "$ICON_PATHS" ]; then
  fail "badging lists no application icon"
else
  ADAPTIVE="$(printf '%s\n' "$ICON_PATHS" | awk '$1==65535{print $2}' | head -n1)"
  if [ -n "$ADAPTIVE" ]; then pass "adaptive launcher icon declared ($ADAPTIVE)"; else warn "no adaptive icon (anydpi) entry in badging"; fi
  PNG_ENTRY="$(printf '%s\n' "$ICON_PATHS" | awk '$1!=65535 && $2 ~ /\.png$/ {print $1" "$2}' | sort -n -r | head -n1)"
  if [ -z "$PNG_ENTRY" ]; then
    warn "no legacy PNG icon in badging - skipping pixel comparison"
  elif [ -z "$RES_DIR" ]; then
    warn "--res-dir not given - skipping pixel comparison of the icon"
  else
    ICON_ENTRY="${PNG_ENTRY#* }"
    EXTRACTED="$(mktemp --suffix=.png)"
    if unzip -p "$APK" "$ICON_ENTRY" > "$EXTRACTED" 2>/dev/null && [ -s "$EXTRACTED" ]; then
      EXPECTED="$RES_DIR/mipmap-xxxhdpi/ic_launcher.png"
      [ -f "$EXPECTED" ] || EXPECTED="$(find "$RES_DIR" -path '*/mipmap-*/ic_launcher.png' 2>/dev/null | sort | head -n1)"
      RESULT="$(python3 - "$EXTRACTED" "$EXPECTED" <<'PY'
import sys
from PIL import Image, ImageChops, ImageStat
a = Image.open(sys.argv[1]).convert("RGBA").resize((48, 48), Image.LANCZOS)
b = Image.open(sys.argv[2]).convert("RGBA").resize((48, 48), Image.LANCZOS)
diff = ImageChops.difference(a, b)
mean = sum(ImageStat.Stat(diff).mean) / 4.0
print("%.2f" % mean)
PY
      )" || RESULT=""
      if [ -z "$RESULT" ]; then fail "icon comparison could not run (python3 + Pillow needed)"
      elif awk "BEGIN{exit !($RESULT < 6.0)}"; then pass "launcher icon in APK matches the generated custom icon (mean diff $RESULT/255)"
      else fail "launcher icon in APK differs from the generated custom icon (mean diff $RESULT/255) - icon was not applied"; fi
    else
      fail "could not extract icon entry '$ICON_ENTRY' from the APK"
    fi
    rm -f "$EXTRACTED"
  fi
  if RES_DUMP="$("$AAPT2" dump resources "$APK" 2>/dev/null)"; then
    if printf '%s\n' "$RES_DUMP" | grep -q "custom_icon_foreground"; then pass "custom adaptive foreground resource present"
    else warn "resource name 'custom_icon_foreground' not visible (names may be optimised away)"; fi
  fi
fi

SHA="$( (sha256sum "$APK" 2>/dev/null || shasum -a 256 "$APK") | cut -d' ' -f1)"
echo "verify-apk: size $(du -h "$APK" | cut -f1), sha256 $SHA"
if [ "$FAILS" -eq 0 ]; then echo "verify-apk: ALL CHECKS PASSED"; exit 0; fi
echo "verify-apk: $FAILS CHECK(S) FAILED" >&2
exit 1
