> Native-navigation revision: use the real-screen walkthrough in [NATIVE_UI.md](NATIVE_UI.md).
> Earlier videos and screenshot packages predate this change and need replacement.

# Private Star Reactions — submission handoff

A Telegram Android concept for supporting channels with less public and local
exposure. Personal privacy is on by default, including for Russian-language users.
Enabling privacy also hides existing leaderboard positions and selected Stars in the
prototype, including unloaded posts. The key rule is simple: **opting out affects future contributions only; past private
activity stays hidden in this app.**

The [Designers × AI announcement](https://t.me/designers/266), checked on
17 October 2026, asks for working code and a video of the implemented feature,
submitted by messaging `@designers`. That post specifies no deadline or prize fund.
This repository prepares the handoff; nothing is sent to the organizers automatically.

The privacy work is informed by [OVD-Info’s proposal](https://ovd.info/content/otkrytoe-pismo-v-telegram-o-yuridicheskikh-riskakh-ispolzovaniya-telegram-stars-dlya)
and [Memorial’s letter](https://t.me/s/pzk_memorial/9320). The purchase notice explains
profile visibility; it is not a comprehensive legal-risk advisory. This prototype
anonymises receipt details rather than deleting receipts entirely.

## What to review

- **Personal privacy:** Settings → My Stars → **Make my Stars private**, directly
  above transaction history, with Telegram’s gold Star and closing-lock animation.
  The same setting is available under Privacy and Security → Stars Privacy. Anonymous
  post reactions, no named rankings or selected Star highlights, and receipts with
  only the signed Stars amount, ID and date/time. A brief native notice explains the effect of either toggle.
- **Buying Stars:** A native inline visibility notice above purchase packages, with a
  one-tap link to enable privacy. The offline preview uses sample prices and cannot purchase.
- **Reaction feedback:** Telegram's existing slider, flying 3D Star, Lottie effects,
  animated counts and a real **20-second pre-send Undo**.
- **Channel controls:** Channel settings → Reactions → Keep Stars anonymous.
  The inline switch saves with Update Reactions and participates in native Discard.
  It removes named rankings and selected Star states immediately, keeps minimal
  receipts, and shows large donor-only percentiles for five minutes after sending.
  This also applies with personal privacy off.
- **History:** Long-press a transaction to anonymise it, or anonymise existing history
  from Stars Privacy. Native confirmations; no restore action in the mock.
- **Live Stars:** A usable native live-reaction flow, with a local anonymous send and
  the same Undo interval.

The code is a working **client prototype**. The video uses an offline Android emulator
and fixture data, without a Telegram account, phone number or real payments.

## Existing API and required server work

Ordinary anonymous post reactions use Telegram's existing API in the credentialed
client. Anonymous leaderboard identity alone is not full payment anonymity.

Channel enforcement, anonymous live reactions, permanent history redaction,
percentiles, retroactive hiding of existing leaderboard positions, and separate future
public contributions after account-wide protection
are **local API mocks**. They spend no Stars and are marked Preview. Telegram's
servers, recipient ledgers, payment providers, other clients and backups are unchanged.
Local hidden presentation is not secure deletion. Clearing app data or using another
client can reveal real server records again.

[STARS_PRIVACY.md](STARS_PRIVACY.md) specifies the behavior, privacy precedence,
implementation boundary, limitations and proposed server capabilities. These details
must accompany the source and video. Do not describe this prototype as guaranteeing
protection from device seizure, arrest or server disclosure.

## Reproduce and inspect the code

Repository: [yes-i-like-pina-coladas/Telegram](https://github.com/yes-i-like-pina-coladas/Telegram/tree/feature/stars-privacy).
Branch: `feature/stars-privacy`. Telegram baseline: **13.0.2 (7175)** at
`f32ffeec763f0bdbf4bec25b52d654f77bff733d`.

Setup ends at `d1a1265` (`setup/offline`); subsequent commits implement and polish
this feature. Review only the feature with:

```sh
git diff d1a1265..HEAD -- TMessagesProj TMessagesProj_App
```

Follow [DEVELOPMENT.md](DEVELOPMENT.md) for a recursive shallow clone, Java 17,
SDK 36, NDK 27.2.12479018, CMake 3.22.1 and Gradle 8.13. Follow
[OFFLINE.md](OFFLINE.md) for the ARM64 emulator. Then:

```sh
./dev demo
```

Open **Settings → Telegram Stars**. Tap or hold the Star in **Chats → Design Notes**,
open a receipt, and use **channel header → Edit → Reactions → Keep Stars anonymous**.
The channel avatar opens the real live-story viewer with sample metadata.
See [NATIVE_UI.md](NATIVE_UI.md) for all production navigation paths.
The offline package has no Internet permission or active account.

## Verification

```sh
python3 -m unittest discover -s tests
./dev privacy-test
./dev smoke-test
./dev native-test
./dev compile-check
```

Test environment: Apple Silicon M3 / 16 GB RAM; Android 16 (API 36) AOSP emulator,
ARM64, 720 × 1440 at 320 dpi. The native privacy suite covers seventeen scenarios,
including the direct history control, retained Stars amounts, private-history persistence, separate future public contributions, actual
20-second waits, late Undo, live sends, channel precedence, irreversible history,
retroactive protection of unloaded posts and live reactions, open-sheet privacy updates, both purchase
notices, pending-public-send cancellation, live stream switching, open-receipt redaction,
real five-minute expiry, rapid toggle reversals, theme updates, Russian label fit,
compact RTL/large-text layouts and accessibility actions. [QA.md](QA.md) records the audit.
Screenshots and raw logs stay in ignored `artifacts/`.

Live payments, real account login, cross-device behavior, server enforcement,
physical-device performance, minimum-API devices and manual TalkBack navigation remain
unverified. They are outside the account-free demonstration. Firebase notifications
remain deferred. These are known limitations, not acceptance checks silently marked
as passed.

## Record and assemble the handoff

Install FFmpeg (`brew install ffmpeg`) for recording. Commit the reviewed source and
documentation, then run on the existing emulator:

```sh
./dev privacy-test
python3 Tools/record_stars_demo.py
python3 Tools/prepare_stars_submission.py
```

The recorder rebuilds/installs the offline app and instrumentation APK, then runs
the four `NativeStarsFlowTest` scenarios in separate processes. It joins actual
captures of Settings/history, the channel/admin flow, live Stars and dark mode,
leaving all 20-second waits intact. It records no microphone audio and does not
synthesize motion. The walkthrough changes only offline sample preferences and
creates sample receipts. It never touches a real account. Review the MP4 before sharing.

The packaging command requires a clean committed checkout and a passing recording
from that exact revision. It also requires a passing privacy suite from that clean
revision and verifies every selected screenshot against the suite’s SHA-256 manifest.
Native navigation screenshots require their own passing revision-bound manifest;
the recorder generates it after all four scenarios pass, or use `./dev native-test`.
The privacy suite includes a real five-minute wait. It creates `artifacts/submission-<revision>/` and a ZIP with:

- `MESSAGE.txt`: a concise draft message with immutable source and feature-diff links.
- `stars-privacy-demo.mp4`: the reviewed native flow, with sample-data disclosure.
- `recording.json`: revision, video SHA-256, duration, Android version and ABI.
- `qa.json`: tested revision, scenario names and screenshot hashes.
- `native-ui-qa.json`: production-navigation scenarios and native screenshot hashes.
- This checklist, QA report, feature/API specification, build and offline guides, and GPL license.
- `feature.patch`: feature changes against the published offline setup baseline.
- `SCREENSHOTS.html`: a portable gallery of the key screens with brief descriptions.
- Production navigation screens first, followed by supplementary light/dark,
  Russian, reaction, receipt and channel component checks.
- `REVIEW.txt` and `SHA256SUMS` for inspecting the package.

Only explicitly named assets and committed documents enter the package. It excludes
APKs, debug/release keys, `local.properties`, account/session files, build outputs,
caches and raw logs. The patch is for review on the setup baseline; the pinned public
repository with its submodules is the complete reproducible source.

## Before sending

- [ ] Open the newly generated MP4 and inspect it from start to finish.
- [ ] Confirm that its sample-data/Preview captions and API limitations are clear.
- [ ] Inspect `MESSAGE.txt`, selected screenshots and the package contents.
- [ ] Verify the pinned revision is present in the public fork and the checksums match.
- [ ] Send the draft message, source link and MP4 to `@designers` when ready.

Features Lab is an unofficial GPL-licensed development fork with a distinct name,
package and icon. This handoff contains source and a demonstration, not a public APK
release. No submission, organizer message or release is sent by these tools.
