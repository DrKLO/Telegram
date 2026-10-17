> The app now opens the real Telegram navigation. Follow [NATIVE_UI.md](NATIVE_UI.md)
> for Settings, channel and live-story paths. The component preview described below
> is retained for isolated regression tests, not the submission walkthrough.

# Private Star Reactions

A native Telegram Android prototype for Designers × AI, based on Telegram 13.0.2.
It can be built and demonstrated without a Telegram account, phone number or physical
phone. It reuses Telegram's settings cells, message bubbles, reaction sheet, 3D/Lottie
Star animations, transaction rows, tables, confirmation dialogs and Undo bulletin.
English and Russian strings are included, using the existing theme colours.

## Try it

```sh
./dev demo
```

Choose **Open Stars Privacy preview**. Tap the Star on the sample post, or hold it
for the native reaction sheet. The personal and channel settings are accessible
above the post. Transactions open the native receipt table; long-press a row to
anonymise that receipt. The live-reaction row opens the native live Stars sheet.

The corresponding entry points in the full client are:

- Settings → Privacy and Security → Stars Privacy.
- Channel settings → Reactions → Keep Stars anonymous (when paid reactions are available).
- My Stars → long-press a transaction, or open a private receipt.
- Stars Privacy → Anonymise transaction history for all existing pages.

The offline package has neither Internet nor billing permission and never activates an account.
All preview sends are simulated and spend no Stars. Use [OFFLINE.md](OFFLINE.md)
and [DEVELOPMENT.md](DEVELOPMENT.md) for the pinned toolchain and fresh checkout.

## Behaviour and precedence

**Personal setting.** Private Star Reactions is on by default for every locale,
including Russian. This implements opt-in visibility without guessing a user's
location from their language, SIM or IP. It is stored per account on this client.
Enabling it also hides all existing leaderboard positions and selected Stars in the
prototype, including posts not loaded on this device. Turning it off permits visibility
for new contributions only. Old hidden receipts and
private reaction state stay protected after toggling, re-fetching and restarting.
A five-second native lock bulletin explains the change: **Only new reactions can be
public. Past private activity stays private.** Enabling privacy confirms that existing and new reactions are hidden.

When enabled, every new ordinary post reaction explicitly uses Telegram's
`paidReactionPrivacyAnonymous`. Top Senders, the identity selector, selected Star
highlights and identifying transaction details are hidden. History rows and receipts show
only the signed Stars amount, transaction ID and date/time. Amounts use Telegram’s
native Star icon, number formatting and incoming/outgoing colours. Private receipts
show the same amount in the native table. Incoming/outgoing tabs remain hidden in the
privacy view; the amount’s sign still indicates whether Stars were received or spent. TON transactions are outside
this Stars feature. Aggregate
reaction counts and Telegram's existing animations remain.

On enabling privacy, and again before switching it off, the local mock seals existing
history through the current server-adjusted time, including unloaded pages. Rows also retain irreversible receipt
ID tombstones. New mock public receipts carry explicit provenance, so a new reaction
in the same timestamp second is not confused with a private one. An account-wide mock
protection flag covers previously public and unloaded reactions; new public contributions
have separate provenance. Past personal and
channel reactions retain protection; expired/private highlights cannot return.
Once a receipt is explicitly anonymised, its public provenance cannot restore it.

After account-wide protection, a new public contribution is simulated separately on
any post, including one not previously loaded. Its
receipt and ranking contain only the new amount. Opening or dismissing its sheet never
calls `messages.togglePaidReactionPrivacy` for the old contribution. Telegram's
[current privacy API](https://core.telegram.org/method/messages.togglePaidReactionPrivacy)
addresses a whole post, so we do not assume it can safely split a sender's
past private and future public contributions. These sends are marked Preview and spend
no Stars; ordinary anonymous sends while privacy is on still use the supported API.

**Enable confirmation.** Every user-initiated enable attempt opens a native Telegram
AlertDialog before changing privacy. It explains that existing activity cannot be
made public again, that switching off affects future reactions only, and that the
intended protection across all devices is simulated pending Telegram server support.
History, personal settings, purchase links and purchase accessibility actions share
this confirmation. Cancel does not change privacy or seal a history boundary. Rapid
repeated requests reuse one dialog. Channel-admin enable actions use the same native
pattern with channel-specific text; the production editor still applies only on Save.

The selected Star indicator disappears immediately under privacy. The aggregate
post count and native sending animation stay visible. This removes the in-app marker
that identifies the user’s contribution; it does not establish forensic erasure or
change copies on other devices in this prototype.

**Channel policy.** **Keep Stars anonymous** sits directly below **Enable Paid
Reactions** in the production channel **Edit → Reactions** panel. It is off until
an admin enables it. The switch is a draft until **Update Reactions** succeeds;
the existing unsaved-changes dialog can apply or discard it. Disabling paid
reactions retains the saved privacy preference for when Stars are enabled again.
The mock policy overrides a donor's personal switch, removes named leaderboards
and selected Star states immediately, and returns minimal receipts. Aggregate
Stars and native sending effects remain visible. Previously protected reactions
and receipts remain private after either switch is turned off.

**Percentile.** Enabling channel privacy replaces named rankings with the donor's
own large **Top 10/25/50/100%** figure. The number uses Telegram's AnimatedTextView,
blue theme token and easing; the caption says only the donor can see it. It is
also visible with personal privacy enabled, because it contains no donor identity.
A completed channel reaction starts a five-minute interval; expiry removes the
figure from an open sheet. Nothing is restored from receipt history after restart.
The mock uses a fixed aggregate fixture of ten donors; it does not infer a whole
population from Telegram's top-three list. Ties share rank and cohorts below ten
have no figure. The component test screen additionally exposes a percentile toggle
for testing policy updates; the production admin switch enables it by default.

Dragging the native Stars slider shows a read-only estimate labelled **You would
be in the top**. As the amount rises, the coarse rank decreases with Telegram’s
rolling-number animation (100%, 50%, 25%, 10% in the demo). It uses the same mock
aggregate fixture, creates no receipt, and leaves the completed rank and its
five-minute deadline untouched. A proposed server endpoint would supply estimates
without revealing donors or charging Stars. Closing the sheet discards the estimate.

**History anonymisation.** A native confirmation applies to one transaction or all
transactions through the confirmation time, including unloaded pages. There is no
restore action. Mock tombstones survive process restarts and re-fetches, and cannot
be reversed by disabling either privacy switch. A redacted receipt contains only
signed Stars amount, ID and timestamp; its local TL representation has no peer, title
or message. Amounts (including fractional Stars) survive redaction, re-fetches and
privacy toggles. Mock post, channel and live receipts retain the actual Stars sent
as a negative amount.
Anonymisation is used instead of deleting the remaining receipt, so a user can still
refer to its ID. Future transactions are outside an all-history cutoff.

**Undo.** A reaction has a real 20-second pre-send interval, measured with a monotonic
clock. Another tap restarts the interval. The countdown ring uses the same duration.
Opening a sheet cannot submit early. Undo, leaving the reaction screen, or hiding
the pending bulletin cancels an unsent reaction and rolls back its count and balance
reservation. Enabling personal or channel privacy cancels a pending public reaction, including its
reservation and selected count; switching privacy off cannot revive it. Switching live
streams also cancels a pending send. An already submitted request cannot be undone by this UI. The same
clock model is used for ordinary reactions, live reactions and the offline preview.

**Buying Stars.** Both Buy Stars and the insufficient-balance purchase sheet show a
short native notice above the packages. When privacy is off: “Star reactions may
show your profile in Top Senders when privacy is off. Make my Stars private.” The
last phrase is an inline link that enables the same setting immediately and protects
past activity in the mock. The notice updates to confirm privacy is on. Its inline action is also exposed to accessibility services. It does not
start a purchase. The offline preview uses the production Buy Stars sheet with sample
packages and a no-purchase footer; package taps cannot enter billing.

Past own live reactions are also projected anonymously, including late-loaded history.
A generation boundary keeps live highlights and rankings from reusing old totals; new
public live contributions are simulated separately without a debit. Named paid comments
with text are outside the reaction-only feature.

Open reaction sheets remove sensitive content and dismiss using Telegram’s native
transition if their privacy policy changes. Reopening rebuilds identity controls and
rankings. An open public receipt also removes its identifying content immediately and
closes when privacy or history anonymisation protects it. Donor percentiles also refresh when their expiry/policy event arrives.

## Native design and motion

**Settings → My Stars → immediately above transaction history** now includes the direct
**Make my Stars private** control, even when history is empty. It shares the personal
preference with Privacy and Security → Stars Privacy. The account-free preview uses
this exact production view beside its native transaction rows.

The illustration combines Telegram's existing gold Star Lottie with its passcode-lock
Lottie. Enabling privacy plays the Star once and settles a lock badge into place over
320 ms using Telegram's `EASE_OUT_QUINT`; disabling removes only the badge over
200 ms. It does not unlock or reveal old receipts. There is no idle animation loop.
The control uses a native Switch, selector, theme tokens and wrapping English/Russian
labels. The history control and purchase notice respect Android text scaling. It exposes one checkable accessibility target, follows Android's disabled
animator setting, cancels interrupted motion and settles when detached. Privacy
changes synchronously before motion starts. A brief native notice explains that
opting out affects new reactions only.

Personal settings use Telegram's ActionBar, HeaderCell, TextCheckCell,
TextSettingsCell, TextInfoPrivacyCell and theme tokens. The channel toggle lives in
the production reaction editor, sharing its native switches, rounded section
background, Update Reactions action and unsaved-changes dialog. Its transparent
rounded ripple preserves the shared card corners. Disabled controls reject
accessibility/programmatic clicks as well as taps, retaining the privacy preference.
The native switch animation, sheet transitions, 3D flying Star, Lottie effects,
animated counters and 20-second Undo stay intact. A native lock Lottie accompanies
the brief personal-setting notice; history confirmation uses a success bulletin.
Personal settings retain short English/Russian labels and theme-change callbacks.
Theme changes dismiss a notice before its old colours can become stale. Rapid
toggles replace old notices; navigating away dismisses the notice. The separate
channel component-test page remains available only through the test playground.
New mock receipt rows enter with a short alpha animation. The large donor-only
percentile uses Telegram's animated number renderer, a subtle 6 dp entrance and
its native easing, respecting reduced-motion settings. Anonymous live chips use
Telegram's existing monochrome lock instead of the purple incognito avatar. A hidden percentile disappears immediately. Preview reaction
sheets show a no-payment caption in the native footer instead of payment terms.
Sensitive receipt details are removed immediately rather than faded out on screen.

## Existing API versus proposed API

| Capability | Implementation |
| --- | --- |
| Anonymous sender for an ordinary paid post reaction | Existing `messages.sendPaidReaction` privacy field; explicit anonymous value |
| Personal default, hidden rankings/highlights and minimal history presentation | Client changes in native components; durable local privacy provenance |
| Account-wide hiding of existing leaderboard positions and selected Stars | Persistent local mock, including unloaded posts; no server rewrite |
| New public contribution after account-wide protection | Separate mock contribution/receipt; never republishes an old private total |
| 20-second Undo | Client defers the send; native bulletin and shared deadline model |
| Anonymous live Star reaction and new public live contributions after protection | `StarsPrivacyMockApi`, local receipt and native live interaction; no network send or debit |
| Enforced channel policy and donor percentile | `StarsPrivacyMockApi`, local admin state and aggregate fixtures; channel-policy reactions are simulated |
| Irreversible history anonymisation and private-channel receipt redaction | Persisted local mock of a server redaction contract; no Telegram ledger mutation |

The mocked flows are marked **Preview** at their controls/send surface. They remain
usable, including live Stars. A normal anonymous post reaction in a credentialed
live build still uses the existing payment API; the prototype does not silently
replace those supported anonymous payments with mocks. Public contributions after account-wide protection
use the explicitly labelled mock described above.

This is not proof of full payment anonymity. The existing API's anonymous leaderboard
identity does not make a transfer unlinkable to Telegram, a payment provider or the
recipient's accounting records. The personal switch now simulates retroactive hiding, but does not rewrite past public
reactions on Telegram's servers. Minimal local presentation is not secure deletion of cached messages, account
files, backups or a server ledger. Transaction amounts, IDs and times can still be correlated.
The mock's protection keys and public contribution counters are themselves local app
data, not an encrypted or erased ledger. Clearing that data or using another client
can reveal real server records again. These limitations must accompany the submission.

## Required Telegram server work

These are proposed capabilities, not existing TL methods:

1. A versioned channel policy, enforced on every send path and returned to all
   clients, with admin authorization and updates when the policy changes.
2. Anonymous live reactions separated from named paid comments, with explicit
   capability negotiation, idempotent sends and anonymous receipt responses.
3. Minimal receipts that retain signed Stars amounts, IDs and dates while omitting
   sender, recipient and message fields,
   plus a server-side irrevocable redaction operation by ID or history cutoff.
   Every history page, search, export, cache refresh and other signed-in client
   must honour it. Define retention, backups and recipient-ledger behaviour.
4. A donor-only percentile derived from the complete aggregate distribution, with
   cohort thresholds, coarse buckets and expiry. Never publish a ranked donor list
   for a private channel or expose the donor's identity through another endpoint.
5. Immutable privacy per contribution, separate public/private aggregates and receipt
   provenance, so changing a default or sending again never republishes past activity.
   Timestamp boundaries must be defined by the server, with exact receipt ordering;
   local second-resolution history cutoffs conservatively hide ambiguous real receipts.
6. Account-wide retroactive hiding of existing paid-reaction identities, including
   posts not loaded by the requesting client. Other viewers must receive anonymous
   aggregates; later public contributions must not restore any prior identity or amount.
   The server must persist the account preference and irreversible boundaries, notify
   every active session, and apply them again on login, reconnect and history reload.
   Clients must invalidate identifying cached presentations and remove selected Star
   markers. An offline second device cannot be promised immediate synchronization;
   older clients need capability handling before this can be presented as protection.
7. A precise anonymity threat model and independent review before describing the
   feature as protecting donors from account/device seizure or server disclosure.

The current boundary is `StarsPrivacyMockApi`. It deliberately has no network or
payment calls. Its local state models the missing server responses so the design
can be evaluated now. Replace it with negotiated server support rather than merely
removing the Preview labels. Existing Telegram servers do not enforce these policies.

Relevant existing API documentation:
[paid reactions](https://core.telegram.org/api/reactions#paid-reactions),
[Stars](https://core.telegram.org/api/stars),
[transaction history](https://core.telegram.org/method/payments.getStarsTransactions).

## Verification and demo

```sh
python3 -m unittest discover -s tests
./dev privacy-test
./dev smoke-test
./dev compile-check
```

The privacy suite exercises real native views and the production reaction controller:
account isolation; deadline/cancellation/rollback; a balance refresh during Undo;
light/dark settings and sheets; native toggle notices; protected history after disable;
new public receipts on the same post; explicit anonymous choices after opt-out;
row reuse; signed, zero, large and fractional Stars amounts; live mock sends; channel overrides;
five-minute boundary logic; percentile thresholds; irreversible single/all history
redaction, unloaded pages and process restart. No live API account is used.

`privacy-test` resets only the offline package's mock privacy state between scenarios,
checks a fresh process with the switch disabled, then leaves its personal switch enabled.
It does not reset a live account or uninstall the app. Screenshots and test logs are saved under ignored `artifacts/stars-privacy/`
and `artifacts/stars-privacy-test.txt`. These tests do not validate real payments,
server anonymity or cross-device enforcement. The suite includes a real five-minute
wait to verify that the percentile disappears from an open sheet while the Star stays unselected throughout.

Recorded flow: public sample history, receipt and selected Star → native enable confirmation →
privacy removes identifying information and the selected marker → irreversible
opt-out notice and minimal receipt → purchase warning →
history anonymisation → native admin policy → animated slider percentile → Undo →
live Stars → dark history. The short edit removes launches and inactive intervals,
keeps UI motion at normal speed, and adds concise captions above the app. The full
capture retains the actual Undo waits. Both sample data and proposed API behaviour
are disclosed. Review [SUBMISSION.md](SUBMISSION.md) before sharing.

## Verified on 17 October 2026

On the Apple Silicon Mac / Android 16 ARM64 AOSP emulator:

- All seventeen privacy instrumentation scenarios passed, including a real 20-second wait,
  late Undo, a production-controller mock commit, a fresh-process history check,
  theme updates, rapid toggle reversals, Russian label fit and native switch accessibility.
  Amount checks cover signed, zero, large and fractional values, native row/receipt
  presentation, mock sends, immutable redaction and process restart. The late-Undo
  test measures from the send’s monotonic timestamp so screenshot time cannot extend its wait.
  The history control also passed with Android system animations disabled. The QA
  additions cover pending-public-send cancellation, stream switching, open receipts,
  purchase accessibility actions, compact RTL/large-text layouts and actual five-minute expiry.
- Both original offline playground smoke tests passed.
- All thirteen Python helper/package tests passed.
- Offline native build/reinstall and the full client compile check passed.
- Native light/dark screens, animated toggle notices, anonymous reaction sheets, minimal
  receipt tables and channel/admin confirmations were visually inspected.
- The refinement checks cover turning privacy off, unloaded/reused receipt rows, a restart
  with the switch off, and separate new public contributions on a previously private post.
- The staged native walkthrough passed and was recorded to the local
  `artifacts/stars-privacy-demo.mp4` (H.264/30 fps, ignored by Git).
  `artifacts/stars-privacy-demo.json` records its exact duration, source revision and hash.
  The export decoded successfully and sampled frames were visually checked.

Real payments, cross-device behaviour and Telegram server enforcement have not been
verified. No physical device, Telegram login, credentials or real Stars were used.

See [QA.md](QA.md) for the audit findings, evidence and remaining validation boundaries.

## Review map

| Area | Source |
| --- | --- |
| Animated control beside history | `Stars/StarsPrivacyView.java` |
| Personal defaults and precedence | `Stars/StarsPrivacy.java` |
| Immutable local provenance and proposed server responses | `Stars/StarsPrivacyMockApi.java` |
| Native personal settings and feedback lifecycle | `Stars/StarsPrivacySettingsActivity.java`, `StarsPrivacyActivity.java` |
| Inline channel admin switch and Apply/Discard | `Components/Reactions/ChatCustomReactionsEditActivity.java` |
| Large percentile and native lock glyph | `Stars/StarsDonorPercentileView.java`, `StarsPrivacyIcon.java` |
| Reaction deadline and send integration | `Stars/PaidReactionUndo.java`, `StarsController.java` |
| Native post sheet and receipt presentation | `Stars/StarsReactionsSheet.java`, `StarsIntroActivity.java` |
| Shared irreversible confirmation | `Stars/StarsHistoryPrivacy.java` |
| Live integration | `Stories/LiveCommentsView.java` |
| Account-free production-screen fixtures | `TMessagesProj_App/src/featuresLab/java/org/telegram/features/OfflineTelegramData.java` |
| Supplementary component harness | `TMessagesProj_App/src/featuresLab/java/org/telegram/features/StarsPrivacyPreview.java` |
| Privacy regression tests | `TMessagesProj_App/src/featuresLabOfflineTest/java/org/telegram/features/StarsPrivacyTest.java` |
| Production navigation and recorded scenarios | `TMessagesProj_App/src/featuresLabOfflineTest/java/org/telegram/features/NativeStarsFlowTest.java` |

Paths beginning `Stars/`, `Stories/` or `Components/` are relative to
`TMessagesProj/src/main/java/org/telegram/ui/`. The recording and handoff helpers are
`Tools/record_stars_demo.py` and `Tools/prepare_stars_submission.py`. They use only the
offline package and a fixed allowlist of review files; they never submit or publish.
