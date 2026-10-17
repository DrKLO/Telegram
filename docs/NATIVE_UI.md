# Stars privacy in Telegram's real screens

The offline APK now launches Telegram's production `MainTabsActivity`, with its
Chats, Contacts, Settings and Profile tabs. It no longer launches the assembled
Stars Privacy demonstration page. The screens below are the same classes used
by the live client; sample data is provided behind the UI.

## Walkthrough

1. Open **Settings**, scroll to **Telegram Stars**, and open it. The production
   page retains its animated Star header, balance, purchase/gift actions and
   transaction list. **Make my Stars private** sits immediately above history.
   The recorded scenario starts with a new public sample: today’s receipts for
   Pavel Durov and Telegram, with bundled channel avatars and post links. Enabling
   privacy removes names, avatars and post links while retaining signed amounts,
   IDs and dates. These are fictitious transactions, not actual payments.
2. Tap **Buy More Stars** to inspect the real package sheet and privacy notice.
   Prices are samples; tapping a package does not start billing.
3. Enable the history privacy control. A native confirmation explains irreversible
   protection and the cross-device API mock. Cancel leaves the setting unchanged.
   The same confirmation appears on every enable attempt, including purchase links
   and channel privacy. The short opt-out notice explains that disabling privacy
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
   stay hidden even with personal privacy off. Dragging the native Stars slider
   previews **Top 100% → 50% → 25% → 10%** with Telegram’s rolling digits. The
   label distinguishes this estimate from a completed donation; moving the slider
   neither sends Stars nor changes history. These policies model proposed APIs.
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

## Edited demonstration

`Tools/record_stars_demo.py` records the four real navigation tests and uses device
clock markers to produce a short, captioned `artifacts/stars-privacy-demo.mp4`.
The caption band sits above the app; native gestures and animations play at normal
speed. Launches, navigation detours and inactive waits are cut. The 20-second Undo
continues to run in the app; its omitted wait is disclosed in the caption. The full
uncut capture is retained as `artifacts/stars-privacy-walkthrough.mp4` for timing QA.
The edit manifest records each source hash and cut boundary.

The recording also compares the same post before and after enabling privacy, then
shows a completed private send: the selected Star marker disappears while the
aggregate count and native effects remain. No other device is connected; cross-device
protection is a proposed server policy, disclosed as a simulation in the dialogs.

The public opening fixture is created only by the offline test. Normal first-launch
privacy remains enabled, and there is no product action to restore protected history.
Avatar source attribution is in `TMessagesProj_App/src/featuresLab/assets/demo/README.txt`.
