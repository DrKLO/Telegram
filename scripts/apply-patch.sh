#!/usr/bin/env bash
# Applies custom/patches/mygram.patch to the working tree.
#
#   scripts/apply-patch.sh            apply (idempotent: "already applied" is detected)
#   scripts/apply-patch.sh --check    only check, change nothing
#   scripts/apply-patch.sh --patch FILE --report DIR
#
# Exit codes: 0 applied / already applied / check OK, 1 conflict or error, 2 usage.
# A failed patch is NEVER ignored: a readable report is written to
# build-reports/patch-conflict-report.md and nothing is half-applied
# (git apply is all-or-nothing).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATCH="$ROOT/custom/patches/mygram.patch"
REPORT_DIR="$ROOT/build-reports"
CHECK_ONLY=0

while [ $# -gt 0 ]; do
  case "$1" in
    --check)  CHECK_ONLY=1 ;;
    --patch)  PATCH="$2"; shift ;;
    --report) REPORT_DIR="$2"; shift ;;
    -h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "apply-patch.sh: unknown argument '$1'" >&2; exit 2 ;;
  esac
  shift
done

cd "$ROOT" || exit 1
die() { echo "apply-patch.sh: $*" >&2; exit 1; }

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "not inside a git work tree"
[ -s "$PATCH" ] || die "patch file missing or empty: $PATCH"
git apply --stat "$PATCH" >/dev/null 2>&1 || die "patch file is not a valid git patch: $PATCH"

prop() { [ -f custom/version.properties ] && grep -E "^$1=" custom/version.properties | tail -n1 | cut -d= -f2- | tr -d '\r'; }

# Which custom change lives in which file (used only for the report).
describe_file() {
  case "$1" in
    */messenger/CustomHistory.java)    echo "NEW FILE: all Anti-Unsend / Edit History logic and the two custom tables" ;;
    */messenger/MessagesController.java) echo "3 hooks: server delete updates (processUpdateArray), push delete (deleteMessagesByPush), edit update capture" ;;
    */messenger/MessagesStorage.java)  echo "1 line in openDatabase: CustomHistory.init(...)" ;;
    */messenger/NotificationCenter.java) echo "new event customHistoryChanged" ;;
    */messenger/MessageObject.java)    echo "canForwardMessage guard + edit-history text in generateLayout" ;;
    */Cells/ChatMessageCell.java)      echo "'Unsent Message' label in the bubble footer" ;;
    */ui/ChatActivity.java)            echo "observer + handler, reply guards, long-press menu filter, Copy fix" ;;
    *) echo "(no description recorded)" ;;
  esac
}

# 1. Already applied?  (every hunk present => reverse check passes)
if git apply --reverse --check "$PATCH" >/dev/null 2>&1; then
  echo "apply-patch: custom patch is already applied - nothing to do."
  exit 0
fi

# 2. Forward check
if CHECK_OUT="$(git apply --check --verbose "$PATCH" 2>&1)"; then
  if [ "$CHECK_ONLY" -eq 1 ]; then
    echo "apply-patch: check OK - patch applies cleanly."
    exit 0
  fi
  git apply --verbose "$PATCH" || die "git apply failed unexpectedly after a successful check"
  git apply --reverse --check "$PATCH" >/dev/null 2>&1 || die "post-apply verification failed (patch not fully present)"
  echo "apply-patch: patch applied and verified."
  exit 0
fi

# 2b. Stale patch: try a 3-way merge against the current upstream tree.
# This is common when the upstream Telegram source has moved since the patch was generated.
if CHECK_OUT_3WAY="$(git apply --3way --check --verbose "$PATCH" 2>&1)"; then
  if [ "$CHECK_ONLY" -eq 1 ]; then
    echo "apply-patch: check OK via 3-way merge - patch applies cleanly."
    exit 0
  fi
  git apply --3way --verbose "$PATCH" || die "git apply --3way failed unexpectedly after a successful 3-way check"
  git apply --reverse --check "$PATCH" >/dev/null 2>&1 || die "post-apply verification failed after 3-way merge (patch not fully present)"
  echo "apply-patch: patch applied and verified via 3-way merge."
  exit 0
fi

# 3. Conflict -> stop, report.
mkdir -p "$REPORT_DIR"
REPORT="$REPORT_DIR/patch-conflict-report.md"
BASE="$(prop PATCH_BASE_COMMIT)"
UPSTREAM="$(prop UPSTREAM_COMMIT)"

# Files git complains about (one per line, de-duplicated).
FAILED_FILES="$(printf '%s\n' "$CHECK_OUT" \
  | sed -nE 's/^error: patch failed: (.*):[0-9]+$/\1/p; s/^error: (.*): patch does not apply$/\1/p; s/^error: (.*): already exists in working directory$/\1/p; s/^error: (.*): does not exist in .*$/\1/p' \
  | sort -u)"

{
  echo "# Custom patch conflict report"
  echo
  echo "The custom patch does **not** apply cleanly. The build was stopped and no APK must be published from this tree."
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| Date (UTC) | $(date -u +%Y-%m-%dT%H:%M:%SZ) |"
  echo "| Patch | \`${PATCH#"$ROOT"/}\` |"
  echo "| Tree HEAD | \`$(git rev-parse --short HEAD 2>/dev/null || echo unknown)\` |"
  echo "| Upstream commit recorded | \`${UPSTREAM:-not recorded}\` |"
  echo "| Patch generated against | \`${BASE:-not recorded}\` |"
  echo "| Upstream version recorded | $(prop UPSTREAM_VERSION_NAME) ($(prop UPSTREAM_VERSION_CODE)) |"
  echo
  echo "## Files that conflict"
  echo
  if [ -z "$FAILED_FILES" ]; then
    echo "_git did not name a specific file - see the raw output below._"
  else
    while IFS= read -r f; do
      [ -n "$f" ] || continue
      echo "### \`$f\`"
      echo
      echo "- Custom change here: $(describe_file "$f")"
      if [ -n "$BASE" ] && git cat-file -e "$BASE^{commit}" 2>/dev/null && git rev-parse HEAD >/dev/null 2>&1; then
        CHANGED="$(git log --oneline "$BASE..HEAD" -- "$f" 2>/dev/null | head -15)"
        if [ -n "$CHANGED" ]; then
          echo "- Upstream commits touching this file since the patch base (most recent first):"
          echo
          echo '```'
          echo "$CHANGED"
          echo '```'
        else
          echo "- No upstream commit touches this file since the patch base: the file may have been modified locally."
        fi
      else
        echo "- Patch base commit not available locally, cannot list upstream changes (run \`scripts/check-upstream.sh --record\` once)."
      fi
      echo
    done <<< "$FAILED_FILES"
  fi
  echo "## Raw git output"
  echo
  echo '```'
  echo "$CHECK_OUT"
  echo '```'
  echo
  echo "## How to fix"
  echo
  echo "1. In a throw-away branch run \`git apply --3way custom/patches/mygram.patch\` and resolve the conflict markers in the files above."
  echo "2. Rebuild the patch with \`scripts/regenerate-patch.sh\` and bump \`CUSTOM_PATCH_VERSION\` (\`scripts/version.sh patch-bump\`)."
  echo "3. Re-run \`scripts/apply-patch.sh --check\` on a clean checkout, then \`scripts/check-upstream.sh --record\`."
  echo
  echo "Custom modifications are never discarded automatically."
} > "$REPORT"

echo "apply-patch: PATCH CONFLICT - build must stop." >&2
echo "apply-patch: report written to ${REPORT#"$ROOT"/}" >&2
printf '%s\n' "$CHECK_OUT" >&2
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$REPORT" >> "$GITHUB_STEP_SUMMARY"
exit 1
