#!/usr/bin/env bash
# Regenerates the single MyGram patch from the working-tree customizations.
# Includes custom Java logic, app identity/branding, manifest, XML resources,
# and Gradle package configuration. Run only after resolving conflicts.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
PATCH="custom/patches/mygram.patch"

PATHS=(
  TMessagesProj/src/main/AndroidManifest.xml
  TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
  TMessagesProj/src/main/java/org/telegram/messenger/ContactsController.java
  TMessagesProj/src/main/java/org/telegram/messenger/MediaDataController.java
  TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java
  TMessagesProj/src/main/java/org/telegram/messenger/CustomHistory.java
  TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java
  TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
  TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java
  TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java
  TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java
  TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
  TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java
  TMessagesProj/src/main/res/xml/auth.xml
  TMessagesProj/src/main/res/xml/auth_menu.xml
  TMessagesProj/src/main/res/xml/contacts.xml
  TMessagesProj/src/main/res/xml/shortcuts.xml
  TMessagesProj/src/main/res/xml/sync_contacts.xml
  TMessagesProj/src/main/res/values/strings.xml
  gradle.properties
)

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "not inside a git work tree" >&2; exit 1; }
if git ls-files -u -- "${PATHS[@]}" | grep -q .; then
  echo "unresolved merge conflicts in custom patch paths" >&2
  exit 1
fi
if grep -rlE '^(<<<<<<<|=======|>>>>>>>)' "${PATHS[@]}" >/dev/null 2>&1; then
  echo "conflict markers remain in custom patch paths" >&2
  exit 1
fi

git add -N -- "${PATHS[@]}" 2>/dev/null || true
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
git diff --binary HEAD -- "${PATHS[@]}" > "$TMP"
[ -s "$TMP" ] || { echo "no custom changes found in the configured paths" >&2; exit 1; }
git apply --reverse --check "$TMP" || { echo "generated patch does not reverse-apply cleanly" >&2; exit 1; }
mkdir -p "$(dirname "$PATCH")"
cp "$TMP" "$PATCH"
echo "Wrote $PATCH"
git apply --stat "$PATCH"
