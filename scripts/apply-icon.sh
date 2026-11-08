#!/usr/bin/env bash
# Applies the custom launcher icon (tg-icon.png in the repository root) to the build workspace.
#
# Run it AFTER upstream synchronisation + patch application and BEFORE the Gradle build:
#   scripts/apply-patch.sh && scripts/apply-icon.sh && ./gradlew ...
#
#   scripts/apply-icon.sh                 validate tg-icon.png, generate + install all icon resources
#   scripts/apply-icon.sh --check-only    only validate tg-icon.png (fast fail)
#   scripts/apply-icon.sh --dry-run       detect the resource structure and print the plan
#   scripts/apply-icon.sh --alternates    also replace the optional alternative icons (icon_2..icon_6)
#   scripts/apply-icon.sh --adaptive-mode safe|bleed
#   scripts/apply-icon.sh --res DIR       use this res directory instead of auto-detection
#
# Environment: ICON_FILE (default <repo>/tg-icon.png)
#
# tg-icon.png is the source of truth and is never modified (its sha256 is verified afterwards).
# If it is missing or invalid the script exits non-zero and the build must stop.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ICON="${ICON_FILE:-$ROOT/tg-icon.png}"

die() { echo "apply-icon: ERROR: $*" >&2; exit 1; }

case "${1:-}" in -h|--help) sed -n '2,19p' "${BASH_SOURCE[0]}"; exit 0 ;; esac

[ -f "$ICON" ] || die "tg-icon.png not found at: $ICON (put your icon in the repository root)"
[ -s "$ICON" ] || die "tg-icon.png is empty: $ICON"
SIG="$(head -c 8 "$ICON" | od -An -tx1 | tr -d ' \n')"
[ "$SIG" = "89504e470d0a1a0a" ] || die "tg-icon.png is not a PNG file (bad signature)"

command -v python3 >/dev/null 2>&1 || die "python3 is required"
python3 -c "import PIL" 2>/dev/null || die "Pillow is required: pip install pillow"

if command -v sha256sum >/dev/null 2>&1; then SHA="sha256sum"; else SHA="shasum -a 256"; fi
BEFORE="$($SHA "$ICON" | cut -d' ' -f1)"

python3 "$ROOT/scripts/icon_gen.py" --icon "$ICON" --root "$ROOT" "$@"

AFTER="$($SHA "$ICON" | cut -d' ' -f1)"
[ "$BEFORE" = "$AFTER" ] || die "tg-icon.png was modified during the run - this must never happen"
