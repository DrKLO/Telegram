## [1.0.3]

### Anti-Unsend
- No functional change.

### Edit History
- No functional change.

### Other changes
- Version bump to 1.0.3.

### Known issues
- See project documentation for known limitations.

## [1.0.2]

### Anti-Unsend
- Existing anti-unsend functionality.

### Edit History
- Existing local edit history functionality.

### Other changes
- Custom patch maintenance and compatibility updates.

### Known issues
- See project documentation for known limitations.

# Changelog

Release notes are generated from the section of the version being released
(`## [X.Y.Z]`, matching `CUSTOM_VERSION_NAME` in `custom/version.properties`).
Every section must contain the three headings **Anti-Unsend**, **Edit History** and **Known issues**.
A release cannot be published without its section (`scripts/gen-release-notes.sh` fails).

## [1.0.0]

### Anti-Unsend
- When another user deletes ("unsends for everyone") a message you had already received, it stays in its original chat position together with its media and gets an "Unsent Message" label.
- Reply, Forward and Share are disabled for retained messages.
- Works both ways: a message you received that the sender deletes is kept, AND a message YOU sent that the other person deletes is also kept (with the same "Unsent Message" label). Only server-originated deletions are affected; deleting messages yourself works exactly as before. Secret chats and Saved Messages are not retained.
- Self-destruct / view-once photos, videos, voice and round messages and messages with an auto-delete timer that you **received** are kept too: they are retained when the sender deletes them, and Telegram's own timer on your device no longer removes them. Your own outgoing messages and your own deletions are unaffected.

### Edit History
- When another user edits a message, previous versions are kept locally and shown as quote blocks above the latest text; repeated edits keep every version.
- "Copy" still copies only the latest text.

### Other changes
- Patch v6: corrected CustomHistory storage queue type to DispatchQueue2.
- Custom launcher icon generated from `tg-icon.png` at build time.
- Independent versioning (`custom/version.properties`) instead of Telegram's version numbers.
- Retained data is stored only in two additive tables inside the existing local Telegram database; nothing is uploaded.

### Known issues
- View-once media that Telegram's server has already destroyed after it was opened may show as expired when the chat is reloaded from the server; the downloaded file itself is not deleted.
- Messages removed by an auto-delete timer on the server side are shown with the same "Unsent Message" label.
- Secret chats: Telegram's self-destruct timers are skipped for received messages, but a deletion requested by the partner inside a secret chat is not retained.
- Not distinguishable from "I deleted it for myself on another device": both are retained.
- Clearing the app cache can remove the media of a retained message; the message itself stays.
- Edit history is shown for plain text messages only (caption edits are stored but not displayed).
- There is no settings screen to manage retained history yet.
- The label is English only.
