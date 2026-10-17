# Submission QA — Private Star Reactions

Audit date: 17 October 2026. Target: the native, account-free Android prototype on
`feature/stars-privacy`, based on Telegram 13.0.2. This is a client implementation
review and emulator validation, not certification of payment anonymity.

## Findings addressed

| Finding | Correction and regression coverage |
| --- | --- |
| A pending public reaction could survive an on/off privacy cycle and become public after its deadline. | Enabling personal or channel privacy cancels pending public sends and rolls back reserved balance/counts. Anonymous pending sends retain their anonymous intent. The production post controller, live view and preview are covered. |
| Switching live streams could carry a pending reaction or donor state into the next stream. | A stream change cancels the pending send and clears its counts, donor lists and public-contribution provenance. A delayed assertion checks that no receipt appears. |
| An already-open public receipt did not react to a privacy change. | Its identifying contents become invisible synchronously before native dismissal; reopening shows only amount, ID and date. The observer follows the view lifecycle. |
| Privacy notifications could wait behind navigation animations. | Privacy updates bypass the animation notification queue. Tests change privacy during an active transition and assert immediate redaction/cancellation. |
| Anonymous live reactions could produce an empty-name Top Sender chip after opt-out. | Anonymous entries are omitted from the live leaderboard and its visibility calculation; stale entrance motion is cancelled on immediate updates. |
| The purchase notice's inline link lacked an explicit accessibility action on its parent cell. | Accessibility services receive a labelled “Make my Stars private” action. The test invokes it and verifies the setting and notice update. |
| New history/purchase text did not follow Android text scaling. | Both controls use scalable text with the same default sizes. Layout checks cover 320 dp width, 150% text and mirrored RTL placement. |
| All-history anonymisation used the device clock while other privacy cutoffs used Telegram time. | The confirmation uses the same server-adjusted clock as the privacy boundary. |
| The submission packager could include screenshots unrelated to its recording revision. | It now requires a passing suite on the same clean commit and checks screenshot hashes before creating the package. Python tests reject dirty/stale/failed QA and changed screenshots. |
| The offline host used only the legacy Back override. | It now registers Android 13+ system Back and retains the older-device fallback. Native navigation tests send real Back key events instead of calling the Activity method directly. |
| Android Lint interpreted the literal “10%” in the donor explanation as a formatting directive. | Both language resources mark this explanation as unformatted text. The actual percentile label retains its numeric format argument. |
| Long live/channel pending titles clipped the “Preview” label beside Undo. | Shorter English and Russian titles keep the mock status visible in the native banner. The Star count remains in the subtitle. |
| Verification documentation still described ten tests and an untested five-minute expiry. | The report and submission guide now describe the expanded suite and separate remaining limitations. |

## Verification

The native suite has 17 scenarios. Each runs in a fresh process; the final two
intentionally share persisted privacy state to test restart behavior. The suite
includes real 20-second Undo intervals and a real five-minute open-sheet expiry.
The privacy suite also checks that Internet and Play billing permissions are absent.
The two original offline smoke tests check rendering, lack of Internet permission,
absence of an active Telegram account and theme persistence. Twelve Python tests
cover local configuration, device selection, build guards and package integrity.

Commands:

```sh
python3 -m unittest discover -s tests
./dev privacy-test
./dev smoke-test
./dev compile-check
```

Environment: Android 16 / API 36 AOSP emulator, ARM64, 720 × 1440 at 320 dpi,
Apple Silicon M3 / 16 GB RAM; pinned Java 17 / Gradle 8.13 / NDK 27.2.12479018.
The current machine has about 10 GB of free space after its existing installation.
That is sufficient for the measured incremental checks, not the 40 GB target for a
fresh clone and full native build.

Evidence is stored locally in ignored `artifacts/`. The submission's `qa.json`
identifies the exact clean source revision, scenario names and selected image
hashes. `recording.json` independently identifies the recording revision and video
hash. `SHA256SUMS` covers the delivered review files. The package excludes APKs,
local credentials, signing material, raw logs and account data.

## Static-analysis boundary

The final Android Lint pass reports 348 library errors and 6,169 warnings, plus
five app errors (one classified as fatal) and five warnings. None of the error
locations fall on lines added by the privacy feature relative to the offline
setup baseline (`d1a1265`). The app findings concern existing manifest entries:
removed/disabled Firebase components, a disabled launch activity and telephony
hardware declarations. The overall Lint command still fails; this is not a clean
repository-wide static-analysis result.

The audit corrected literal-percent resource errors, the offline host's system
Back handling and an inherited commented-out live-reaction title branch. The
pending title now says “Sending Stars.” Ten warnings remain on feature-diff lines:
seven synchronous-preference-write warnings and three view-constructor warnings.
No blanket Lint baseline or suppression was added.

The mock deliberately commits privacy cutoffs/tombstones synchronously before
publishing a state change. Lint warns about this disk work. The programmatically
constructed privacy views also produce XML-constructor warnings; they require an
explicit account/fragment and are not XML-inflated. Large-history performance is
not benchmarked. Production server integration should replace mock preference
storage with an appropriate persistent data layer.

The feature diff is checked for whitespace errors, private-key markers, common
provider-token patterns and personal absolute paths. These checks supplement the
package allowlist; they are not a comprehensive security audit of Telegram.

## Coverage against the proposals

The [OVD-Info letter](https://ovd.info/content/otkrytoe-pismo-v-telegram-o-yuridicheskikh-riskakh-ispolzovaniya-telegram-stars-dlya)
requests default anonymity, a global setting, risk information, history deletion
and a longer cancellation period. The [Memorial letter](https://t.me/s/pzk_memorial/9320)
also requests admin controls and retroactive hiding of public identities.

| Proposal | Current implementation / remaining work |
| --- | --- |
| Anonymous by default, at least for Russian accounts | Enabled for every locale/account by default; no location guessing. |
| Global personal privacy setting | Native settings plus animated control beside transaction history. |
| Admin anonymous-only policy | Native local mock. Enforcing it for all senders requires server support. |
| Hide past public identity / rankings | Local retroactive projection, including unloaded posts and live history; separate new public mock contributions. Other viewers are unchanged. |
| More time to cancel | Actual 20-second pre-send Undo. This is not a refund of a completed payment. |
| Partial/all history deletion | Individual/all irreversible **local anonymisation** is implemented. It retains amount, ID and time. Complete deletion is not implemented. |
| Risk education | Purchase notice explains profile visibility and links to the personal control. A reviewed Russia-specific legal-risk advisory is still missing. |

The last two rows are intentional gaps relative to the full letters; do not describe
the proposal as completely implemented. Avoid claims that a private label prevents
prosecution, disclosure, device inspection or correlation of amounts and timestamps.

## Remaining validation and production work

- Real-account login, actual payments, recipient views and a second independent
  Telegram client have not been tested. The emulator has no account and no Internet
  or billing permission. No real Stars are spent during the demonstration.
- Channel enforcement, live anonymity, irrevocable history redaction, retroactive
  hiding and split public/private totals remain API mocks. Local data, server
  records, backups and payment-provider records are not securely erased.
- Receipt provenance and channel policies are local account-slot state. Production
  integration must bind these to authenticated server identities, handle logout and
  account replacement, and enforce them across all signed-in devices.
- A physical device, API 23, tablets, landscape, sustained performance and manual
  TalkBack navigation remain untested. Automated accessibility-node and layout checks
  are useful coverage, not a substitute for an assistive-technology usability test.
- Full My Stars navigation with a signed-in account is compile-checked; the offline
  preview exercises the shared production controls, rows, sheets and send controller.
- Firebase push remains deferred. No APK release or message to organizers is part of
  this package. The user reviews the video and sends the final submission manually.
