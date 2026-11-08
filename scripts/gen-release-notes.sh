#!/usr/bin/env bash
# Generates GitHub release notes for the current CUSTOM_VERSION_NAME.
#
#   scripts/gen-release-notes.sh [--out FILE]
#
# Header (version, upstream version/commit, patch version, build date) comes from
# custom/version.properties; the body is the matching "## [X.Y.Z]" section of
# custom/CHANGELOG.md. Fails if the section is missing/empty or lacks one of the
# required headings, so an incomplete release can never be published.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/custom/version.properties"
CHANGELOG="$ROOT/custom/CHANGELOG.md"
OUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --out) OUT="$2"; shift ;;
    -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "gen-release-notes: unknown argument '$1'" >&2; exit 2 ;;
  esac
  shift
done

die() { echo "gen-release-notes: $*" >&2; exit 1; }
prop() { grep -E "^$1=" "$PROPS" | tail -n1 | cut -d= -f2- | tr -d '\r' || true; }

[ -f "$PROPS" ] || die "missing $PROPS"
[ -f "$CHANGELOG" ] || die "missing $CHANGELOG"
"$ROOT/scripts/version.sh" check >/dev/null

VER="$(prop CUSTOM_VERSION_NAME)"
CODE="$(prop CUSTOM_VERSION_CODE)"
APK_CODE="$("$ROOT/scripts/version.sh" apk-version-code)"
UP_NAME="$(prop UPSTREAM_VERSION_NAME)"; UP_CODE="$(prop UPSTREAM_VERSION_CODE)"
UP_COMMIT="$(prop UPSTREAM_COMMIT)"; PATCH_V="$(prop CUSTOM_PATCH_VERSION)"; PATCH_BASE="$(prop PATCH_BASE_COMMIT)"
[ -n "$UP_COMMIT" ] || die "UPSTREAM_COMMIT is empty - run scripts/check-upstream.sh --record"

BODY="$(awk -v ver="$VER" '
  /^## \[/ { if (p) exit; if (index($0, "## [" ver "]") == 1) { p = 1; next } }
  p { print }
' "$CHANGELOG")"
[ -n "$(printf '%s' "$BODY" | tr -d '[:space:]')" ] || die "no (or empty) section '## [$VER]' in custom/CHANGELOG.md"

for heading in "### Anti-Unsend" "### Edit History" "### Known issues"; do
  printf '%s\n' "$BODY" | grep -qxF "$heading" || die "section [$VER] is missing the required heading '$heading'"
done

NOTES="# Telegram Custom $VER

| | |
|---|---|
| Custom version | $VER (versionCode $CODE, APK versionCode $APK_CODE) |
| Telegram upstream | $UP_NAME ($UP_CODE), commit \`${UP_COMMIT:0:12}\` |
| Custom patch | version $PATCH_V, generated against \`${PATCH_BASE:0:12}\` |
| Build date | $(date -u +%Y-%m-%d) (UTC) |
$BODY
"
if [ -n "$OUT" ]; then printf '%s\n' "$NOTES" > "$OUT"; echo "gen-release-notes: wrote $OUT" >&2; else printf '%s\n' "$NOTES"; fi
