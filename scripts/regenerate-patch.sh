#!/usr/bin/env bash
# Regenerates custom/patches/custom-features.patch from the CURRENT working-tree
# changes under TMessagesProj/src/main/java (new files included), relative to HEAD.
#
# Use after you resolved a conflict (see build-reports/patch-conflict-report.md):
#   git checkout -b fix-patch
#   git apply --3way custom/patches/custom-features.patch   # fix the conflict markers
#   scripts/regenerate-patch.sh
#
# Only source files under TMessagesProj/src/main/java are included, so branding
# (icons), scripts and workflows never leak into the patch.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
PATCH="custom/patches/custom-features.patch"
SRC_PATH="TMessagesProj/src/main/java"

die() { echo "regenerate-patch.sh: $*" >&2; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "not inside a git work tree"

if git ls-files -u -- "$SRC_PATH" | grep -q .; then
  die "unresolved merge conflicts under $SRC_PATH - resolve them first"
fi
if grep -rlE '^(<<<<<<<|>>>>>>>) ' "$SRC_PATH" >/dev/null 2>&1; then
  die "conflict markers still present under $SRC_PATH:
$(grep -rlE '^(<<<<<<<|>>>>>>>) ' "$SRC_PATH")"
fi

git add -N -- "$SRC_PATH"                       # make new files visible to git diff (intent-to-add)
TMP="$(mktemp)"
git diff --binary HEAD -- "$SRC_PATH" > "$TMP"
[ -s "$TMP" ] || { rm -f "$TMP"; die "no changes under $SRC_PATH relative to HEAD - nothing to put in a patch"; }

# The patch must describe exactly the working tree: reverse-applying it must work.
git apply --reverse --check "$TMP" || { rm -f "$TMP"; die "generated patch does not reverse-apply cleanly"; }

mkdir -p "$(dirname "$PATCH")"
cp "$TMP" "$PATCH"; rm -f "$TMP"
echo "Wrote $PATCH:"
git apply --stat "$PATCH" | tail -n 20
echo
echo "Next: scripts/version.sh patch-bump && scripts/check-upstream.sh --record"
