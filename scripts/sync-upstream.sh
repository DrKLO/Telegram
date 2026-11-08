#!/usr/bin/env bash
# Merges official Telegram (upstream) into the current fork branch, in a separate branch,
# and checks that the custom patch and the icon scripts still work on the result.
#
#   scripts/sync-upstream.sh [--branch upstream-sync]
#
# Steps: fetch upstream -> merge into <branch> -> apply-patch --check -> apply-icon --dry-run
#        -> check-upstream --record (commit) .
# It never builds, never tags and never publishes: upstream synchronisation and public
# releases are separate operations.
#
# If the patch no longer applies the script stops, writes build-reports/patch-conflict-report.md,
# returns to the original branch and exits 1 - nothing of the custom work is discarded.
# The new branch is only created locally; pushing / opening a PR is done by the caller.
#
# Environment: UPSTREAM_URL, UPSTREAM_BRANCH (default master).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/DrKLO/Telegram.git}"
UPSTREAM_BRANCH="${UPSTREAM_BRANCH:-master}"
SYNC_BRANCH="upstream-sync"
while [ $# -gt 0 ]; do
  case "$1" in
    --branch) SYNC_BRANCH="$2"; shift ;;
    -h|--help) sed -n '2,16p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "sync-upstream: unknown argument '$1'" >&2; exit 2 ;;
  esac
  shift
done

die() { echo "sync-upstream: $*" >&2; exit 1; }
out() { [ -n "${GITHUB_OUTPUT:-}" ] && echo "$1" >> "$GITHUB_OUTPUT" || true; }

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "not inside a git work tree"
[ -z "$(git status --porcelain --untracked-files=no)" ] || die "working tree has uncommitted changes - commit or stash them first"

ORIG_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$ORIG_BRANCH" != "HEAD" ] || die "detached HEAD - check out a branch first"

git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "$UPSTREAM_URL"
if [ "$(git rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
  git fetch --no-tags --unshallow upstream "$UPSTREAM_BRANCH" >/dev/null 2>&1 || true
fi
git fetch --no-tags upstream "$UPSTREAM_BRANCH" || die "could not fetch upstream/$UPSTREAM_BRANCH"

BEHIND="$(git rev-list --count "HEAD..upstream/$UPSTREAM_BRANCH")"
if [ "$BEHIND" -eq 0 ]; then
  echo "sync-upstream: already up to date with upstream/$UPSTREAM_BRANCH."
  out "up_to_date=true"
  exit 0
fi
echo "sync-upstream: $BEHIND new upstream commit(s) to merge."
out "up_to_date=false"

restore() { git merge --abort >/dev/null 2>&1 || true; git checkout -q "$ORIG_BRANCH"; git branch -D "$SYNC_BRANCH" >/dev/null 2>&1 || true; }

git checkout -q -B "$SYNC_BRANCH"
if ! git merge --no-edit "upstream/$UPSTREAM_BRANCH" >/tmp/sync-merge.log 2>&1; then
  cat /tmp/sync-merge.log >&2
  mkdir -p build-reports
  { echo "# Upstream merge conflict"; echo; echo "Merging upstream/$UPSTREAM_BRANCH into $ORIG_BRANCH produced conflicts in files that are NOT part of the custom patch:"; echo; echo '```'; git diff --name-only --diff-filter=U; echo '```'; echo; echo "Resolve them manually (git merge upstream/$UPSTREAM_BRANCH) - nothing was changed on $ORIG_BRANCH."; } > build-reports/upstream-merge-conflict.md
  restore
  die "merge conflict - see build-reports/upstream-merge-conflict.md"
fi

echo "sync-upstream: checking the custom patch against the merged tree ..."
if ! ./scripts/apply-patch.sh --check; then
  cp build-reports/patch-conflict-report.md /tmp/patch-conflict-report.md 2>/dev/null || true
  restore
  mkdir -p build-reports && cp /tmp/patch-conflict-report.md build-reports/ 2>/dev/null || true
  die "the custom patch no longer applies to the new upstream - see build-reports/patch-conflict-report.md. No APK may be produced from this state."
fi

if [ -f tg-icon.png ]; then
  echo "sync-upstream: checking that the icon script still understands the resource layout ..."
  ./scripts/apply-icon.sh --dry-run >/dev/null || { restore; die "icon script failed on the new upstream layout - update scripts/icon_gen.py"; }
fi

./scripts/check-upstream.sh --record
git add custom/version.properties
git commit -q -m "Record upstream base after sync ($(git rev-parse --short upstream/"$UPSTREAM_BRANCH"))" || true

echo "sync-upstream: OK. Branch '$SYNC_BRANCH' = $ORIG_BRANCH + upstream/$UPSTREAM_BRANCH (patch verified)."
echo "sync-upstream: push it and open a pull request; publishing a release is a separate step."
git checkout -q "$ORIG_BRANCH"
