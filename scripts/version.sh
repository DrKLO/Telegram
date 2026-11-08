#!/usr/bin/env bash
# Custom version management. Source of truth: custom/version.properties
#
#   scripts/version.sh get KEY                  print one value
#   scripts/version.sh check                    validate the file (semver, integer code, ...)
#   scripts/version.sh gradle-args              print -P overrides for Gradle
#   scripts/version.sh apk-version-code         print the final APK versionCode (afat)
#   scripts/version.sh bump major|minor|patch   bump name + code (code += 1)
#   scripts/version.sh patch-bump               CUSTOM_PATCH_VERSION += 1
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FILE="${VERSION_FILE:-$ROOT/custom/version.properties}"
ABI_VERSION_CODE_AFAT=9   # abiVersionCode of the 'afat' flavor in TMessagesProj_App/build.gradle

die() { echo "version.sh: $*" >&2; exit 1; }
[ -f "$FILE" ] || die "missing $FILE"

get() { grep -E "^$1=" "$FILE" | tail -n1 | cut -d= -f2- | tr -d '\r' || true; }

set_key() {  # set_key KEY VALUE  (key must already exist)
  grep -qE "^$1=" "$FILE" || die "key $1 not found in $FILE"
  local tmp; tmp="$(mktemp)"
  sed -E "s|^$1=.*|$1=$2|" "$FILE" > "$tmp" && cat "$tmp" > "$FILE" && rm -f "$tmp"
}

check() {
  local name code patch
  name="$(get CUSTOM_VERSION_NAME)"; code="$(get CUSTOM_VERSION_CODE)"; patch="$(get CUSTOM_PATCH_VERSION)"
  [[ "$name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "CUSTOM_VERSION_NAME '$name' is not X.Y.Z"
  [[ "$code" =~ ^[0-9]+$ ]] && [ "$code" -ge 1 ] || die "CUSTOM_VERSION_CODE '$code' must be an integer >= 1"
  [[ "$patch" =~ ^[0-9]+$ ]] && [ "$patch" -ge 1 ] || die "CUSTOM_PATCH_VERSION '$patch' must be an integer >= 1"
  [ $(( code * 10 + ABI_VERSION_CODE_AFAT )) -lt 2100000000 ] || die "CUSTOM_VERSION_CODE too large for an Android versionCode"
  [[ "$(get UPSTREAM_VERSION_NAME)" =~ ^[0-9]+(\.[0-9]+)*$ ]] || die "UPSTREAM_VERSION_NAME is invalid"
  [[ "$(get UPSTREAM_VERSION_CODE)" =~ ^[0-9]+$ ]] || die "UPSTREAM_VERSION_CODE is invalid"
  echo "version OK: $name (code $code, apk versionCode $(( code * 10 + ABI_VERSION_CODE_AFAT )), patch v$patch)"
}

cmd="${1:-}"; shift || true
case "$cmd" in
  get)
    [ $# -eq 1 ] || die "usage: get KEY"
    get "$1" ;;
  check)
    check ;;
  gradle-args)
    check >/dev/null
    echo "-PAPP_VERSION_NAME=$(get CUSTOM_VERSION_NAME) -PAPP_VERSION_CODE=$(get CUSTOM_VERSION_CODE)" ;;
  apk-version-code)
    check >/dev/null
    echo $(( $(get CUSTOM_VERSION_CODE) * 10 + ABI_VERSION_CODE_AFAT )) ;;
  bump)
    [ $# -eq 1 ] || die "usage: bump major|minor|patch"
    check >/dev/null
    IFS=. read -r MA MI PA <<< "$(get CUSTOM_VERSION_NAME)"
    case "$1" in
      major) MA=$((MA+1)); MI=0; PA=0 ;;
      minor) MI=$((MI+1)); PA=0 ;;
      patch) PA=$((PA+1)) ;;
      *) die "usage: bump major|minor|patch" ;;
    esac
    set_key CUSTOM_VERSION_NAME "$MA.$MI.$PA"
    set_key CUSTOM_VERSION_CODE "$(( $(get CUSTOM_VERSION_CODE) + 1 ))"
    check ;;
  patch-bump)
    set_key CUSTOM_PATCH_VERSION "$(( $(get CUSTOM_PATCH_VERSION) + 1 ))"
    check ;;
  *)
    die "usage: $0 get KEY | check | gradle-args | apk-version-code | bump major|minor|patch | patch-bump" ;;
esac
