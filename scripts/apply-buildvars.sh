#!/usr/bin/env bash
# Injects YOUR OWN Telegram api_id / api_hash into BuildVars.java in the build workspace.
#
# Telegram's README requires every fork to use its own api_id
# (https://core.telegram.org/api/obtaining_api_id). The values are secrets, so they are
# NOT stored in the patch or in git; they are injected at build time from the
# environment variables TG_API_ID and TG_API_HASH.
#
#   scripts/apply-buildvars.sh             inject if both variables are set, otherwise warn and continue
#   scripts/apply-buildvars.sh --require   fail if they are not set (used for public releases)
#
# The script verifies that the expected declarations exist and that the replacement
# really happened; it never guesses.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQUIRE=0
[ "${1:-}" = "--require" ] && REQUIRE=1

die() { echo "apply-buildvars: ERROR: $*" >&2; exit 1; }

API_ID="${TG_API_ID:-}"
API_HASH="${TG_API_HASH:-}"

if [ -z "$API_ID" ] && [ -z "$API_HASH" ]; then
  if [ "$REQUIRE" -eq 1 ]; then
    die "TG_API_ID and TG_API_HASH are required (create them at https://my.telegram.org/apps and store them as secrets)"
  fi
  echo "apply-buildvars: WARNING: TG_API_ID / TG_API_HASH not set - building with the API credentials that ship in the upstream repo."
  echo "apply-buildvars: WARNING: acceptable for local testing only; do not distribute such a build."
  exit 0
fi

[ -n "$API_ID" ] && [ -n "$API_HASH" ] || die "set both TG_API_ID and TG_API_HASH (or neither)"
[[ "$API_ID" =~ ^[0-9]+$ ]]        || die "TG_API_ID must be numeric"
[[ "$API_HASH" =~ ^[0-9a-f]{32}$ ]] || die "TG_API_HASH must be 32 lowercase hex characters"

FILE="$(find "$ROOT/TMessagesProj/src" -name BuildVars.java -path '*org/telegram/messenger*' | head -n 1)"
[ -n "$FILE" ] || die "BuildVars.java not found"

grep -qE '^\s*public static int APP_ID = [0-9]+;\s*$' "$FILE"            || die "expected declaration 'public static int APP_ID = <number>;' not found in $FILE"
grep -qE '^\s*public static String APP_HASH = "[0-9a-f]+";\s*$' "$FILE"   || die "expected declaration 'public static String APP_HASH = \"...\";' not found in $FILE"

sed -i -E "s|^(\s*public static int APP_ID = )[0-9]+;|\1${API_ID};|; s|^(\s*public static String APP_HASH = \")[0-9a-f]+(\";)|\1${API_HASH}\2|" "$FILE"

grep -qE "^\s*public static int APP_ID = ${API_ID};\s*$" "$FILE"           || die "APP_ID replacement failed"
grep -qE "^\s*public static String APP_HASH = \"${API_HASH}\";\s*$" "$FILE" || die "APP_HASH replacement failed"
echo "apply-buildvars: custom api_id injected into $(basename "$FILE") (values not printed)."
