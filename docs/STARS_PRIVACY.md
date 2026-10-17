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
- Channel settings → Reactions → Star reaction privacy (when paid reactions are available).
- My Stars → long-press a transaction, or open a private receipt.
- Stars Privacy → Anonymise transaction history for all existing pages.

The offline package cannot access the Internet and never activates an account.
All preview sends are simulated and spend no Stars. Use [OFFLINE.md](OFFLINE.md)
and [DEVELOPMENT.md](DEVELOPMENT.md) for the pinned toolchain and fresh checkout.

## Behaviour and precedence

**Personal setting.** Private Star Reactions is on by default for every locale,
including Russian. This implements opt-in visibility without guessing a user's
location from their language, SIM or IP. It is stored per account on this client.
Turning it off permits the ordinary visibility controls on ordinary channels;
it does not force anyone's identity to be public or undo anonymisation.

When enabled, every new ordinary post reaction explicitly uses Telegram's
`paidReactionPrivacyAnonymous`. Top Senders, the identity selector, selected Star
highlights and transaction details are hidden. History rows and receipts show
only transaction ID and date/time. Incoming/outgoing tabs are hidden. They also remain hidden after history anonymisation
to avoid revealing a redacted record through its direction. TON transactions are outside
this Stars feature. Aggregate
reaction counts and Telegram's existing animations remain.

**Channel policy.** Require anonymous reactions is off until the admin enables it.
It overrides a donor's personal switch, removes named leaderboards, and returns
redacted receipts. With the personal switch off, a new reaction's selected state
lasts five minutes. With the personal switch on, there is no selected state at all.
An older reaction with no transient timestamp, including after a restart, stays
unselected. Aggregate Stars remain visible.

**Percentile.** Instead of public rankings, the admin can allow a rounded, donor-only
Top 10/25/50/100% label for five minutes. It is hidden by the personal privacy switch.
The mock uses a fixed aggregate fixture of ten donors; it does not infer a whole
population from Telegram's top-three list. Ties share rank and cohorts below ten
have no label. This is an explicit design choice for the previously unspecified
percentile audience; no donor names or individual amounts appear in it.

**History anonymisation.** A native confirmation applies to one transaction or all
transactions through the confirmation time, including unloaded pages. There is no
restore action. Mock tombstones survive process restarts and re-fetches, and cannot
be reversed by disabling either privacy switch. A redacted receipt contains only
ID and timestamp; its local TL representation has no peer, title, message or amount.
Anonymisation is used instead of deleting the remaining receipt, so a user can still
refer to its ID. Future transactions are outside an all-history cutoff.

**Undo.** A reaction has a real 20-second pre-send interval, measured with a monotonic
clock. Another tap restarts the interval. The countdown ring uses the same duration.
Opening a sheet cannot submit early. Undo, leaving the reaction screen, or hiding
the pending bulletin cancels an unsent reaction and rolls back its count and balance
reservation. An already submitted request cannot be undone by this UI. The same
clock model is used for ordinary reactions, live reactions and the offline preview.

## Existing API versus proposed API

| Capability | Implementation |
| --- | --- |
| Anonymous sender for an ordinary paid post reaction | Existing `messages.sendPaidReaction` privacy field; explicit anonymous value |
| Personal default, hidden rankings/highlights and minimal history presentation | Client changes in native components |
| 20-second Undo | Client defers the send; native bulletin and shared deadline model |
| Anonymous live Star reaction | `StarsPrivacyMockApi`, local receipt and native live interaction; no network send or debit |
| Enforced channel policy and donor percentile | `StarsPrivacyMockApi`, local admin state and aggregate fixtures; channel-policy reactions are simulated |
| Irreversible history anonymisation and private-channel receipt redaction | Persisted local mock of a server redaction contract; no Telegram ledger mutation |

The mocked flows are marked **Preview** at their controls/send surface. They remain
usable, including live Stars. A normal anonymous post reaction in a credentialed
live build still uses the existing payment API; the prototype does not silently
replace those supported payments with mocks.

This is not proof of full payment anonymity. The existing API's anonymous leaderboard
identity does not make a transfer unlinkable to Telegram, a payment provider or the
recipient's accounting records. Past public reactions are not changed by the personal
switch. Minimal local presentation is not secure deletion of cached messages, account
files, backups or a server ledger. Transaction IDs and times can still be correlated.
The mock's persistence is local app data; clearing that data or using another client
can reveal real server records again. These limitations must accompany the submission.

## Required Telegram server work

These are proposed capabilities, not existing TL methods:

1. A versioned channel policy, enforced on every send path and returned to all
   clients, with admin authorization and updates when the policy changes.
2. Anonymous live reactions separated from named paid comments, with explicit
   capability negotiation, idempotent sends and anonymous receipt responses.
3. Minimal receipts whose schema omits sender/recipient/message/amount fields,
   plus a server-side irrevocable redaction operation by ID or history cutoff.
   Every history page, search, export, cache refresh and other signed-in client
   must honour it. Define retention, backups and recipient-ledger behaviour.
4. A donor-only percentile derived from the complete aggregate distribution, with
   cohort thresholds, coarse buckets and expiry. Never publish a ranked donor list
   for a private channel or expose the donor's identity through another endpoint.
5. A precise anonymity threat model and independent review before describing the
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
light/dark settings and sheets; row reuse; live mock sends; channel overrides;
five-minute boundary logic; percentile thresholds; irreversible single/all history
redaction, unloaded pages and process restart. No live API account is used.

`privacy-test` resets only the offline package's mock privacy state between scenarios,
then leaves its personal switch enabled. It does not reset a live account or uninstall
the app. Screenshots and test logs are saved under ignored `artifacts/stars-privacy/`
and `artifacts/stars-privacy-test.txt`. These tests do not validate real payments,
server anonymity, cross-device enforcement or five minutes of wall-clock UI playback.

Suggested video: personal setting → anonymous reaction and 20-second Undo → minimal
receipt → admin policy → donor-only percentile with personal setting off → irreversible
history confirmation → native live reaction. Identify the offline fixtures and proposed
API behaviour in the video caption. Review [SUBMISSION.md](SUBMISSION.md) before sharing.

## Verified on 17 October 2026

On the Apple Silicon Mac / Android 16 ARM64 AOSP emulator:

- All six privacy instrumentation scenarios passed, including a real 20-second wait,
  late Undo, a production-controller mock commit and a fresh-process history check.
- Both original offline playground smoke tests passed.
- All eight Python helper tests passed.
- Offline native build/reinstall and the full client compile check passed.
- Native light/dark screens, anonymous reaction sheets, minimal receipt tables and
  channel/admin confirmations were visually inspected.
- The staged native walkthrough passed and was recorded to the local
  `artifacts/stars-privacy-demo.mp4` (62 seconds, H.264/30 fps, ignored by Git).
  The export decoded successfully and sampled frames were visually checked.

Real payments, cross-device behaviour and Telegram server enforcement have not been
verified. No physical device, Telegram login, credentials or real Stars were used.
