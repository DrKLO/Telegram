# Telegram Custom – Design & Patch Notes (Phase 2 + patch)

## Base this patch was generated against

| Item | Value |
|---|---|
| Upstream | https://github.com/DrKLO/Telegram |
| Version in `gradle.properties` | `APP_VERSION_NAME=13.1.1`, `APP_VERSION_CODE=7237` |
| Source archive | `Telegram-master.zip`, sha256 `4b65e10b8fc63b4cbcb7377026a5da470782547fc0c0575bf0b71da676028c10` |
| Upstream commit hash | **unknown** – the zip has no `.git`. Fill in after cloning (`git rev-parse HEAD`). |
| Patch | `custom/patches/custom-features.patch` (7 files, +649 / −5, patch version 6) |

Apply: `git apply --check custom/patches/custom-features.patch && git apply custom/patches/custom-features.patch`
(verified against pristine copies of the 6 modified upstream files from the zip).

## Files touched

| File | Change | Why |
|---|---|---|
| `messenger/CustomHistory.java` (**new**) | All logic: tables, retention, edit capture, display helpers | Keeps hooks in upstream files tiny |
| `MessagesController.java` | 4 hooks (server delete updates, push delete, edit update, `checkDeletingTask`) | Server-originated paths + Telegram's own expiry timer |
| `MessagesStorage.java` | `openDatabase` → `CustomHistory.init`; `emptyMessagesMedia` skips kept messages | Create/load custom tables; keep view-once media |
| `NotificationCenter.java` | 1 new event `customHistoryChanged` | Live refresh of the label |
| `MessageObject.java` | 2 lines: `canForwardMessage`, `generateLayout` | Block forward; prepend previous versions |
| `ChatMessageCell.java` | 1 block | "Unsent Message" label next to the time |
| `ChatActivity.java` | observer + handler, reply guards, menu filter, Copy fix | Block reply/forward/share; refresh cells |

## Anti-Unsend

* **Hook points (verified in source):** `TL_updateDeleteMessages` and `TL_updateDeleteChannelMessages` are collected in `MessagesController.processUpdateArray` into `deletedMessages` (key `0` = non-channel chats, `-channelId` = channels). Right before that list is handed to the UI and to `MessagesStorage.markMessagesAsDeleted(..., deleteFiles=true)`, `CustomHistory.retainUnsent` removes the retained ids from it. Push-originated deletes (`deleteMessagesByPush`) are handled the same way on a copy of the id list.
* **What is retained:** any message that exists in `messages_v2` and is not in a secret chat or Saved Messages (this path only sees cloud-chat delete updates) - **both directions**: a message you received that the sender deletes, and a message YOU sent that the other party deletes, are both kept with the "Unsent Message" label. (An earlier version only retained received messages; changed on request.) Self-destruct / view-once media and messages with an auto-delete timer are retained the same way.
* **Not touched:** user-initiated deletes (`MessagesController.deleteMessages`, `deleteMessagesRange`), clear-history, scheduled/quick-reply deletes, secret-chat deletes. A retained message can still be deleted normally by the user.
* **UI:** label "Unsent Message, <time>" in the bubble footer; Reply, Forward, Share removed from the long-press menu; swipe/quote reply and multi-select forward are blocked.
* **Fail-open:** if the storage queue does not answer within 10 s, normal Telegram deletion proceeds.

## Self-destruct, view-once and auto-delete messages are kept

Requested behaviour: none of these is deleted. Two mechanisms delete them in stock Telegram, both are covered:

* **Sender deletes / server expires them** → same retention path as "Anti-Unsend" above (no exclusion any more).
* **Telegram's own timer on your device.** `enc_tasks_v4` tasks are executed by `MessagesController.checkDeletingTask`: messages are removed with `deleteMessages(...)`, media is emptied with `MessagesStorage.emptyMessagesMedia` (also used when a view-once viewer is closed, `doDeleteShowOnceTask`). `CustomHistory.excludeKept` removes **received** (`out = 0`) messages from both operations (sent self-destruct messages still expire locally on the sending device, same as stock Telegram - only "Anti-Unsend" above, for a server-pushed deletion, applies to both directions). The finished task rows are still cleared (the unfiltered `taskAll` is passed to `getNewDeleteTask`), otherwise the same task would be re-executed forever.
* Your own explicit deletions behave as in stock Telegram. `SecretMediaViewer` only displays timers and deletes nothing itself.

## Edit History

* **Capture:** in the edit-update handler, *before* `new MessageObject(...)` and before `putMessages` overwrites the row, `CustomHistory.captureEdit` reads the old row, and if the text changed stores a new version in `custom_edit_history(uid, mid, ver, edit_date, saved_date, text, data)` (`data` = full serialized previous message, so formatting/media references are preserved). Repeated edits create versions 1, 2, 3 …; duplicate updates are ignored. Only incoming messages.
* **Display:** in `MessageObject.generateLayout` (after entities are applied) previous versions are prepended as italic quote blocks above the latest text, oldest first. The native "edited" marker stays. A `HistoryMarker` span makes this idempotent; Copy strips the prefix.
* **Alternative UI** (blurred previous version) was not needed.

## Storage

Two additive tables created with `CREATE TABLE IF NOT EXISTS` inside the existing per-account database; no existing table or column is altered (so no change to `LAST_DB_VERSION` and no upstream migration conflict). Both are loaded into memory at DB open. `CustomHistory.getCounts(account)` and `CustomHistory.clearAll(account)` exist for management; **no settings screen was added**. Nothing leaves the device.

## Known limitations / risks (please read)

1. **Not compiled or run.** The sandbox has no Android SDK/NDK/submodules. APIs were checked against the source by hand and the files were syntax-parsed, but a real Gradle build and on-device test are required.
2. **Cannot distinguish "other user unsent it" from "I deleted it for myself on another device"** – both arrive as the same update, so both are retained; applies in both directions now (sent and received messages).
3. **Retention now also covers your own sent messages**, so if the other party deletes a message you sent, it stays on your side too with the same label - this is a deliberate change from the original spec, made on request.
4. **Self-destruct / view-once / auto-delete messages are kept on purpose** (see below). This defeats the sender's expectation that such content disappears, and the sender is not told. Untested on a device. For view-once media that the server already destroyed after opening, a reload from the server can replace the local row with an "expired" placeholder (the downloaded file is not deleted).
5. **Media cache cleanup** (Settings → Storage → Clear cache, or "keep media" auto-clean) can still remove files of retained messages; the row stays, the media shows as unavailable.
6. **Not verified on device:** whether a history reload from the server ever removes the local retained row. Telegram has no obvious such path (all `markMessagesAsDeleted` callers were reviewed) but this must be tested.
7. **Edit history displays text only** for plain text messages; caption edits of media are stored but not rendered. `TYPE_EMOJIS`-only messages are skipped.
8. Label is hard-coded English ("Unsent Message"); no `strings.xml` change to avoid upstream conflicts.
9. Previous-version text is held in memory (small); for very heavy use consider lazy loading.
10. Upstream risk: line positions will move; hooks are anchored on distinctive statements and should re-apply with `git apply --3way`. Highest churn: `MessagesController.processUpdateArray`, `ChatActivity`.

## Test checklist (from the project spec)

Anti-Unsend: text, image, video, document/PDF, voice, audio, GIF, sticker, several at once, group, channel, restart, media persistence, missing media.
Edit history: one edit, consecutive edits, long text, formatted text, restart.
Regression: normal delete (own, for everyone), normal edit, send/receive, reply/forward of normal messages, downloads, notifications, secret chats, multiple accounts.

## Phase 3 – repository infrastructure (see `custom/README.md`)

Done: `scripts/` (patch apply + conflict report, patch regeneration, icon generation, build-vars injection, version tooling, upstream check/sync, APK verification, release notes), `custom/version.properties`, `custom/CHANGELOG.md`, `.github/workflows/build-release.yml` (validate → build → verify → artifacts → optional release) and `sync-upstream.yml` (never publishes).

Still open:
* `tg-icon.png` is not in the zip – you must supply it.
* The Gradle build, `assembleAfatRelease` output and CI runtime have **not** been run (no Android toolchain in the sandbox).
* Feature gaps listed above (settings screen, caption-edit display, cache-cleanup protection, translated label).
* Decisions: package name / signature coexistence with official Telegram, `CHECK_UPDATES`, own Firebase config.
