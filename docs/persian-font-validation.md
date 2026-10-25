# Optional Vazirmatn validation record

Prepared on 2026-10-25. Results below distinguish the current fork from a
future port to the newer official upstream revision.

## Source and build identity

- Feature base: `0aae959806c57be8ad457980f2a5c6277ff18635` (13.0.3).
- Initial review HEAD: `50351d48df255a239da27ba4bc198d996db4e724`, plus the
  uncommitted follow-ups. HEAD alone does not identify these inputs.
- `Tools/check-persian-font.ps1` records input paths and SHA-256 hashes in
  `build/font-check/review/result.json`. The immutable test input snapshot is
  `build/font-check/review/tested-source.json`; the final snapshot after the
  test-fixture corrections is `build/font-check/review/tested-source-final.json`.
  Application inputs are unchanged between those snapshots.
- The tested application state is now recorded in `86aad3f9d` (following
  coverage commit `2397c3457`); its `TMessagesProj` Git tree is
  `67e7644b22e23b32a500d944e6ba62f646c7d665`. The validation follow-up adds
  the benchmark source, tooling and this record without changing that tree.
- Source parsing: 206 Java files passed. All 33 standalone range regressions,
  two resource XML files, bundled font/license and diff checks passed.
- Both universal Debug and Release APKs were packaged and signature-verified
  before the story/pull-up/call follow-ups. Final Debug Java compilation
  passed after those application changes. Packaging is not device validation.

## Android execution

The dedicated device is `emulator-5554`, model `sdk_gphone64_x86_64`, Android
16 / API 36, initial font scale 1.0. The test host is
`org.telegram.messenger.web`; the runner package is
`org.telegram.messenger.web.test`. This is separate from the `.beta` Debug
application and the release package. No app data is cleared.

The contributor's active build was allowed to finish: Gradle reported BUSY
before test preparation and IDLE before the test-host build was started.
No Gradle stop, process termination, checkout, rebase, uninstall or ADB server
restart was used to obtain that state.

| Check | Result |
| --- | --- |
| Dedicated host and instrumentation APK build | Passed, `afatDebug`; host build 15m 55s, test-only rebuild 36s |
| Correctness instrumentation, font scale 1.0 | Passed: `OK (30 tests)`, zero failures, 2.278s |
| Correctness instrumentation, font scale 1.5 | Passed: `OK (30 tests)`, zero failures, 3.044s; restored scale to 1.0 |
| Comparative text microbenchmark | Passed: `OK (1 test)`; measured values below |
| Device versions below API 36 | Not executed in this session |
| Real-device scroll/typing frame timings | Not measured |

The test module requires API 26, although the application supports API 23.
API 23-25 therefore need separate application/device verification.

The initial correctness run had two fixture failures. A detached editable
TextView lacked the layout parameters supplied by a real parent, and avatar
drawing ran before `Theme.createCommonResources`. The fixture now supplies
layout parameters before measurement and initializes the standard theme
resources before drawing. No application code changed to make those tests pass.
Both complete reruns passed. Larger-font instrumentation is not a substitute for
visual accessibility, TalkBack or full-screen overflow checks.

Build command (from the repository root):

```powershell
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_AppTests:assembleAfatDebug :TMessagesProj_AppTests:assembleAfatDebugAndroidTest -I Tools/persian-font-tests.init.gradle -x :TMessagesProj_AppTests:generateScheme --console=plain --max-workers=2
```

After installing both APKs with `adb -s emulator-5554 install -r`, run:

```powershell
adb -s emulator-5554 shell am instrument -w -r -e class org.telegram.ui.Components.PersianFontApplierTest org.telegram.messenger.web.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e class org.telegram.ui.Components.PersianFontPerformanceTest org.telegram.messenger.web.test/androidx.test.runner.AndroidJUnitRunner
```

Raw output is retained locally under `build/font-check/review` as
`android-correctness-initial.txt`, `android-correctness.txt`,
`android-correctness-font-scale-1.5.txt`, `android-performance.txt` and
`performance.json`. APK identities and log hashes are in `session-evidence.json`.
All generated evidence and binaries are ignored by Git.

SHA-256 identities of the installed APKs:

```text
afatDebug host: DD557CC7097705FAB6DD1AA7F24739076FA6A6170DE99A3FF58FF23F29EFF445
afatDebug test: 68F81D77D47E8FBBE2BC23AA6B8F6F3F24B217E01525CB4CAFF09B98E36EC628
```

## Performance method

The microbenchmark compares off/on costs over nine rounds, alternating their
order. Each case has warmups and reports median elapsed nanoseconds per
operation plus the slowest round. Cases cover mixed-script font application,
Latin text, wrapped layouts, warm Canvas label measurements and repeated
application to already-styled input.

An optional ART counter reports process-wide allocated bytes, including other
app threads; it is not a precise per-thread allocation count. Emulator results
are diagnostic measurements, not release or real-device performance claims.
No universal pass/fail latency threshold is inferred from them.

Measured medians on the dedicated API 36 emulator, font scale 1.0:

| Operation | Font off, ms/op | Font on, ms/op |
| --- | ---: | ---: |
| Apply to mixed text (40 repeated phrases) | 0.0010 | 0.4303 |
| Apply to short Latin text | 0.0007 | 0.0038 |
| Construct wrapped mixed-text layout, width 720 px | 0.7134 | 5.9846 |
| Measure short Canvas label with warm cache | 0.0054 | 0.0049 |
| Apply to already-styled mixed text | 1.2290 | 2.1862 |

The wrapped-layout median increased about 8.4 times for this synthetic
mixed-script workload. The enabled initial styling and wrapped layout reported
approximately 23 KB and 82 KB process-wide allocation per operation,
respectively. Reapplying to styled text also creates temporary work even when
the original result can be reused. These are remaining performance concerns;
the passing benchmark only confirms execution and measurement, not acceptable
UI latency. Profile representative chats on physical hardware before final
review. The warm-cache result does not establish a general speed improvement.

For final device evidence, compare the same release build with the setting
off/on, identical content and device conditions. Record scrolling a populated
chat list, folder switching, a long formatted mixed-script chat and typing
with composition. Retain raw frame/allocation recordings with the input hashes.

## Official upstream compatibility

`git ls-remote` on 2026-10-25 advertised official `DrKLO/Telegram:master` at
`3de37c8cfb039452be7398909461da6288babf15`; the fork's `origin/master` remains
at the older feature base. Between those official revisions, 250 paths changed
and 14 overlap this feature.

Three-way text checks used normalized copies under
`build/font-check/review/upstream-merge-check`, without modifying the index,
working source, HEAD or branch. Eleven overlapping files merge textually.
That is not a compilation or behavioral compatibility result.

- `ShareAlert.java`: one conflict combines the font-aware copy-link label with
  a new upstream pill selector. A candidate preserves the upstream selector
  and uses `PersianTextView`; its Java syntax check passed.
- `strings.xml`: append-position conflict. A candidate retains upstream's new
  strings and both font keys; XML parsing and unique resource names passed.
- `QrActivity.java`: deleted upstream and replaced by the `QrShare` flow.
  The replacement includes native views and Canvas labels. These require a
  deliberate port and new QR checks; retaining the deleted class is not a fix.

Candidate files and `result.json` are local review artifacts. They have not
been applied to the working feature branch. The newer upstream port remains
a prerequisite for requesting final review against that revision.

## Manual evidence to attach

| Scenario | Required evidence |
| --- | --- |
| Persian and English UI; font off/on | Same-content screenshots and exact APK/source identity |
| Bold/italic links, code, emoji, Persian/Arabic digits and punctuation | Expected fonts, wrapping and ellipsis |
| Editing, IME composition and selection | Selection/composition remain stable while styling |
| Font toggle, restart and returning to cached screens | Setting persistence and refreshed layouts |
| Two-step verification and bot/emoji controls | RTL/LTR, narrow/landscape widths and touch bounds |
| Call invitation, call actions and story tutorial | Labels fit; both font settings checked |
| Accessibility | Large font scale and TalkBack on a device |
| Older supported Android | Application verification below the test host's API floor |

The contributor reports broad successful manual coverage. Those reports do
not replace recorded results for every row above or for the newer upstream.
