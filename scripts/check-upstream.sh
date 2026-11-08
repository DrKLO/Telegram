#!/usr/bin/env bash
# Verifies (or records) the upstream Telegram commit this fork is based on.
#
#   scripts/check-upstream.sh            verify custom/version.properties against git history
#   scripts/check-upstream.sh --record   write UPSTREAM_COMMIT / PATCH_BASE_COMMIT / UPSTREAM_VERSION_*
#                                        (only after the custom patch was checked against this tree)
#
# Environment: UPSTREAM_URL (default https://github.com/DrKLO/Telegram.git),
#              UPSTREAM_BRANCH (default master), NO_FETCH=1 to skip fetching.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/DrKLO/Telegram.git}"
UPSTREAM_BRANCH="${UPSTREAM_BRANCH:-master}"
PROPS="custom/version.properties"
MODE="verify"
[ "${1:-}" = "--record" ] && MODE="record"

die()  { echo "check-upstream: $*" >&2; exit 1; }
warn() { echo "check-upstream: WARNING: $*" >&2; }
prop() { grep -E "^$1=" "$PROPS" | tail -n1 | cut -d= -f2- | tr -d '\r' || true; }
gprop() { grep -E "^$1=" gradle.properties | tail -n1 | cut -d= -f2- | tr -d '\r' || true; }
set_prop() {
  local tmp; tmp="$(mktemp)"
  sed -E "s|^$1=.*|$1=$2|" "$PROPS" > "$tmp" && cat "$tmp" > "$PROPS" && rm -f "$tmp"
}

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "not inside a git work tree"
[ -f "$PROPS" ] || die "missing $PROPS"
[ -f gradle.properties ] || die "missing gradle.properties (is this the Telegram source tree?)"

# Make sure an 'upstream' remote exists and is fetched.
if ! git remote get-url upstream >/dev/null 2>&1; then
  echo "check-upstream: adding remote 'upstream' -> $UPSTREAM_URL"
  git remote add upstream "$UPSTREAM_URL"
fi
if [ "${NO_FETCH:-0}" != "1" ]; then
  if [ "$(git rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
    git fetch --no-tags --unshallow upstream "$UPSTREAM_BRANCH" 2>&1 | tail -n 2 || true
  fi
  git fetch --no-tags upstream "$UPSTREAM_BRANCH" 2>&1 | tail -n 2 || die "could not fetch upstream/$UPSTREAM_BRANCH"
fi
git rev-parse --verify -q "upstream/$UPSTREAM_BRANCH" >/dev/null || die "upstream/$UPSTREAM_BRANCH not available"

if [ "$MODE" = "record" ]; then
  "$ROOT/scripts/apply-patch.sh" --check || die "refusing to record: the custom patch does not apply to this tree"
  BASE="$(git merge-base HEAD "upstream/$UPSTREAM_BRANCH")" || die "no common ancestor with upstream/$UPSTREAM_BRANCH"
  set_prop UPSTREAM_COMMIT "$BASE"
  set_prop PATCH_BASE_COMMIT "$BASE"
  set_prop UPSTREAM_VERSION_NAME "$(gprop APP_VERSION_NAME)"
  set_prop UPSTREAM_VERSION_CODE "$(gprop APP_VERSION_CODE)"
  echo "check-upstream: recorded upstream $BASE (Telegram $(gprop APP_VERSION_NAME), code $(gprop APP_VERSION_CODE))"
  echo "check-upstream: commit custom/version.properties."
  exit 0
fi

# ---- verify ----
COMMIT="$(prop UPSTREAM_COMMIT)"
[ -n "$COMMIT" ] || die "UPSTREAM_COMMIT is empty in $PROPS. Run once: scripts/check-upstream.sh --record"
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || die "UPSTREAM_COMMIT '$COMMIT' is not a full 40-character hash"

git cat-file -e "$COMMIT^{commit}" 2>/dev/null || die "UPSTREAM_COMMIT $COMMIT does not exist in this repository"
git merge-base --is-ancestor "$COMMIT" "upstream/$UPSTREAM_BRANCH" \
  || die "UPSTREAM_COMMIT $COMMIT is not part of upstream/$UPSTREAM_BRANCH (not an official Telegram commit)"
git merge-base --is-ancestor "$COMMIT" HEAD \
  || die "UPSTREAM_COMMIT $COMMIT is not an ancestor of HEAD - this fork is not based on it"

PB="$(prop PATCH_BASE_COMMIT)"
[ -n "$PB" ] || die "PATCH_BASE_COMMIT is empty in $PROPS. Run: scripts/check-upstream.sh --record"
git cat-file -e "$PB^{commit}" 2>/dev/null || die "PATCH_BASE_COMMIT $PB does not exist in this repository"

# Informational: how far behind upstream are we, and does gradle.properties match the record?
BEHIND="$(git rev-list --count "HEAD..upstream/$UPSTREAM_BRANCH" 2>/dev/null || echo '?')"
REC_NAME="$(prop UPSTREAM_VERSION_NAME)"; REC_CODE="$(prop UPSTREAM_VERSION_CODE)"
CUR_NAME="$(gprop APP_VERSION_NAME)";     CUR_CODE="$(gprop APP_VERSION_CODE)"
if [ "$REC_NAME" != "$CUR_NAME" ] || [ "$REC_CODE" != "$CUR_CODE" ]; then
  warn "gradle.properties says Telegram $CUR_NAME ($CUR_CODE) but $PROPS records $REC_NAME ($REC_CODE) - update the record after verifying the patch (--record)."
fi
echo "check-upstream: OK - based on official commit ${COMMIT:0:12}, Telegram $CUR_NAME ($CUR_CODE), $BEHIND commit(s) behind upstream/$UPSTREAM_BRANCH."
