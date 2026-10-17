# Stars privacy in Telegram's real screens

The offline APK now launches Telegram's production `MainTabsActivity`, with its
Chats, Contacts, Settings and Profile tabs. It no longer launches the assembled
Stars Privacy demonstration page. The screens below are the same classes used
by the live client; sample data is provided behind the UI.

## Walkthrough

1. Open **Settings**, scroll to **Telegram Stars**, and open it. The production
   page retains its animated Star header, balance, purchase/gift actions and
   transaction list. **Make my Stars private** sits immediately above history.
2. Tap **Buy More Stars** to inspect the real package sheet and privacy notice.
   Prices are samples; tapping a package does not start billing.
3. Toggle the history privacy control. The short notice explains that opting out
   affects new reactions only. Existing protected receipts remain private.
   Open a receipt to inspect its Stars amount, ID and date/time.
4. Return to **Settings → Privacy & Security → Stars Privacy** for the personal
   preference and irreversible local history anonymisation actions.
5. Open **Chats → Design Notes**. Tap the post's Star for a reaction, or hold it
   for Telegram's actual reaction sheet. The actual chat animation and 20-second
   Undo run through `StarsController`; completed sample sends appear in history.
6. Open the channel's header and choose **Edit → Reactions**. **Keep Stars
   anonymous** is directly below **Enable Paid Reactions**. Save with **Update
   Reactions**, or use the existing discard dialog. After sending Stars, hold the
   post's Star again to see your large donor-only percentile. Selected Star states
   stay hidden even with personal privacy off. These policies model proposed APIs.
7. Open **Design Notes** in the live-story strip. The production story viewer,
   Star button, reaction sheet, comments and Undo display use simulated stream
   metadata. Live video/audio and actual broadcasting are not connected.

Run `./dev demo` to open the app and `./dev native-test` to exercise the native
navigation on the emulator. Screenshots and test evidence go to
`artifacts/native-ui/` and `artifacts/native-ui-qa.json`.

## Data boundary

`OfflineTelegramData` supplies the sample profile, channel, post, Stars balance,
transactions, purchase options, application configuration and live-story metadata.
`OfflineDemo` routes requests only when the separately compiled
`FEATURES_LAB_OFFLINE` flag is true. The offline manifest still removes Internet
and Play Billing permissions, and the application credentials remain empty.
Unsupported requests return an explicit offline error rather than waiting for a
server. No authenticated `UserConfig.currentUser` is created or saved.

Private sample receipts persist only minimal receipt fields; public sample
receipts retain their public presentation only while the privacy policy allows it.
The privacy cutoff and tombstones continue to govern reloaded pages. Native
post/live totals are stored separately from receipt rows, so refreshing the sample
server data retains completed reactions without a private receipt-to-post mapping.
Native
navigation, cells, sheets, theme resources and animations remain shared with the
live build. The offline media player displays the native empty-stream state and
metadata without starting a media connection or requesting camera/microphone use.

The previous `StarsPrivacyPreview` remains available only through an explicit
component-test launch extra, so the detailed regression suite can still isolate
edge cases. It should not be used to represent the full Settings experience in
new submission screenshots or recordings.

## Version and verification scope

This remains based on Telegram Android **13.0.2 (7175)**, commit
`f32ffeec763f0bdbf4bec25b52d654f77bff733d`. On 17 October 2026 this matched the
latest published official Android source. Telegram's official APK channel already
listed 13.0.3 (7183), whose source had not yet been published in that repository.
The offline data adapter does not imply that the project contains unpublished
13.0.3 changes.

Native UI tests complement the existing component privacy tests. They do not
validate real account login, billing, server enforcement, cross-device history
redaction or a real live broadcast. The earlier packaged demo predates this
navigation change and must not be presented as evidence for this revision.

The offline provider also remembers the paid-reactions enable switch across app
restarts. The separate channel anonymity preference is saved only with the native
Update Reactions action. Turning Stars off retains that preference.
