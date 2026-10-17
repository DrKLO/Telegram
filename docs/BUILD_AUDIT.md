# Build audit and CI — 2026-10-17

Baseline: `404c11a24dd48278dd67504936bd33dc6c8b28e1` on the unmerged `feature/amegram-foundation` branch. This change is stacked on that foundation; it does not merge into master or change application IDs, release signing, version numbers, the palette or implemented settings behavior.

## Toolchain and component compatibility

| Component | Configuration / finding |
| --- | --- |
| Gradle / AGP | Wrapper 8.13 / AGP 8.13.2; wrapper distribution SHA-256 now verified against the official distribution checksum |
| Java | JDK 21 for local checks and CI; Android source/target remains Java 8 with desugaring 2.1.5. Java 8 target deprecation warnings remain |
| Kotlin | Existing Android-test plugin 2.1.0; buildSrc uses Gradle's embedded Kotlin compiler and explicit language/API 1.9. No versions upgraded |
| Android | compile/target 36, Build Tools 36.0.0; main application min SDK 23. Existing AppTests min SDK 26 is separate |
| Native | NDK 27.2.12479018, CMake 3.22.1, `tmessages.49`; native dependencies and gitlinks retained. Main library linker already requests 16 KiB max/common page sizes; final APK-wide alignment/runtime compatibility is not established by source checks |
| Media / math | Source checks need only pinned `TMessagesProj_Modules/media` and `TMessagesProj/lib/jlatexmath` submodules. Full native builds still require all relevant recorded submodules |
| Regular release | AGP model verifies `afatRelease` and `bundleAfatRelease`: `dev.umi.amegram` |
| Regular debug | AGP model verifies `afatDebug`: `dev.umi.amegram.beta` |
| Other application shells | Upstream `.web`, Huawei and HockeyApp variants remain unchanged and are not advertised as supported AmeGram distribution targets |
| Emulator | Android Emulator 37.2.12, platform-tools 37.0.1, Android 35 default x86_64 image; software TCG because /dev/kvm and VMX/SVM acceleration are unavailable |

## Findings and changes

1. **No PR build automation existed.** Added `.github/workflows/android-source-checks.yml`: pull_request plus workflow_dispatch, read-only repository permissions, pinned official Node 24 action commits, JDK 21, pinned SDK/NDK/CMake, one Gradle worker and a 4 GiB heap. It never receives repository secrets and uploads only unit-test reports.
2. **The previous "credential-free" library check still depended on committed upstream Firebase fixtures.** Removed Google-services processing from the shared library; it belongs to the consuming application. Added explicit `-PamegramSourceChecks=true` for PR source/resource/unit checks. In this mode the regular app does not process service configuration, API constants are 0/empty, optional store integrations are not configured, and APK/bundle packaging is unconditionally prohibited. This is compilation validation, not an installable client or Firebase integration test.
3. **Service configuration was tracked and only the regular debug path was ignored.** Removed six upstream Google/Huawei JSON fixtures from the new Git tree using index-only removal; existing local files and the owner's ignored debug file were preserved. Added ignore rules for service configurations across source sets, environment files and common signing/private-key formats. Git history was not rewritten. The tracked public upstream `release.keystore` fixture predates AmeGram and is retained byte-for-byte under the production-signing constraint; a committed-file check permits only its recorded blob and rejects replacement with owner material. It must never be used as a production key.
4. **Optional store plugins increased the source-check configuration surface.** Source-check mode excludes the Huawei, HockeyApp, standalone and instrumentation application shells and their Google/Huawei/Crashlytics build plugins. Normal mode and runtime dependency declarations are retained. No runtime SDK was removed speculatively: Firebase App Indexing, legacy Play Services/SafetyNet/Vision, Stripe, billing and bundled verification AARs warrant separate usage/privacy/provenance review before changing them. Removing them now could break existing upstream paths.
5. **The original 8 GiB Gradle heap is unsuitable for this 8 GiB container alongside analysis/emulation.** CI/local documented checks override it to 4 GiB with one worker and no parallel projects. The previous full-lint OOM is recorded in VERIFICATION.md; global production settings are not silently changed.
6. **There was no check of final variant IDs.** Added `checkAmeGramApplicationIds`, which reads AGP's actual variant model. Neither package ID changed. Android authenticator/contact synchronization still use the per-variant AmeGram resource.
7. **API ID validation arbitrarily limited IDs to nine digits.** It now accepts the nonnegative signed 32-bit range while still requiring a positive ID and nonempty owner hash before normal APK packaging; error messages never disclose values.
8. **A native queued-request failure callback returned bool on one path and nothing on another.** Changed its early exit to `return;`, matching the void scheduled callback and preserving cancellation operations. The file passes the NDK compiler with `-Werror=return-type`. No handshake, crypto or integrity verification was altered.
9. **Our About screen had two SetTextI18n warnings.** Moved the same displayed metadata into an English/Russian positional format resource. This preserves the text and styling. Resource/Java checks verify the new resource; a new full lint count is not claimed.
10. **The prior Firebase blocker is resolved locally.** The owner-provided file contains the correct debug package, remains mode 0600/ignored/untracked, and regular application resource processing passed. Contents were not printed, committed, uploaded as an artifact or placed in CI.
11. **A complete client launch is still gated by real owner Telegram API configuration.** AMEGRAM_API_ID and AMEGRAM_API_HASH are not configured. Normal APK packaging remains blocked rather than using upstream or fabricated values. No real-user login or messaging test has been performed.
12. **Full lint is not a green build check yet.** The last complete report contains 348 errors and 6163 warnings, with no error locations on the earlier changed lines. Some diagnostics are library-only permission/custom-view findings and need evaluation against merged app manifests; others require permission/API/threading review. No blanket baseline, new suppression or security-check bypass was introduced. Source/unit/resource CI is deliberately separate from unresolved full lint/native/device gates.

The configuration-boundary Python check verifies tracked filenames, ignore behavior, generated API assignments, the legacy fixture key's fixed identity and independent Android account resources. It is not a general secret/entropy scanner or a complete security audit.

## Rechecking without owner configuration

The exact PR command is:

```bash
python3 -m unittest discover -s tools/tests -v
python3 tools/check_build_contract.py
git submodule update --init --recursive --depth=1 -- TMessagesProj_Modules/media TMessagesProj/lib/jlatexmath
./gradlew -PamegramSourceChecks=true --no-daemon --no-parallel --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g' --console=plain \
  checkAmeGramApplicationIds :TMessagesProj:compileDebugJavaWithJavac \
  :TMessagesProj:compileDebugLibraryResources :TMessagesProj:testDebugUnitTest \
  :TMessagesProj_App:processAfatDebugResources
```

Source-check mode must reject `:TMessagesProj_App:assembleAfatDebug` even if the local machine has owner service/API configuration. Normal debug builds must continue rejecting missing Telegram API configuration. The workflow does not synthesize google-services.json and cannot produce or publish an APK. Do not run a production build under the source-check flag.

## Verification results

These are new audit-session results. Historical initial milestone checks remain in [VERIFICATION.md](VERIFICATION.md).

| Check | Result / scope |
| --- | --- |
| Clean-checkout Gradle source command above | **PASS**, 3m 28s, all 223 tasks executed. Independent checkout of the audit code with no build outputs, no service JSON and only the two source-check submodules. Existing downloaded Gradle/dependency caches were available; this was not an empty dependency-cache test |
| Palette JVM tests | **PASS**, 2 tests, 0 failures/errors; actual XML report checked |
| Configuration-boundary Python tests and repository check | **PASS**, 3 tests plus the tracked-path/ignore/resource checks |
| Workflow syntax/static analysis | **PASS**, actionlint 1.7.7; official action commit metadata inspected. A GitHub-hosted execution is a separate result, not implied by local validation |
| Actual regular AGP variant IDs | **PASS**, debug `dev.umi.amegram.beta`, release and bundle release `dev.umi.amegram` |
| Source-mode APK prohibition | **PASS**, `assembleAfatDebug` fails with the exact source-check packaging error in the clean checkout |
| Normal-mode missing-API prohibition | **PASS**, normal `assembleAfatDebug` fails with the expected owner API configuration error in a checkout with no service JSON |
| Owner-configured regular debug application resources | **PASS** after removing library Google-services processing: 152 tasks, 18s. The private debug JSON stays outside Git |
| Native callback | **PASS**, the recorded arm64-v8a NDK compilation command for ConnectionsManager.cpp was rerun with `-fsyntax-only -Werror=return-type`. This does not claim a full native rebuild or x86_64 runtime validation |
| Release-signing inputs / production properties | **UNCHANGED** against the foundation baseline; no owner signing material supplied |
| Full lint | **NOT GREEN**; previous 348-error/6163-warning report retained. Not rerun after the About resource fix; no new complete lint count claimed |
| Configured APK / AmeGram launch / instrumentation | **BLOCKED**, owner Telegram API ID/hash are unavailable. Firebase alone is insufficient; no fabricated API values or mock client used |

### Emulator result

Official emulator and Android 35 x86_64 packages were installed and AVD `AmeGram_API35` created. ADB reports SDK 35 / x86_64. The first software boot reached `sys.boot_completed=1`, but its crash buffer also contained a `system_server` SIGABRT earlier during boot. This establishes eventual boot completion, not a stable clean device or successful AmeGram launch. Gradle was stopped and the emulator restarted alone with a smaller 480x800 display and 2 GiB guest memory to recheck readiness. The isolated run also reached `sys.boot_completed=1`; `am start -W -a android.settings.SETTINGS` reported `Status: ok`. Its fresh crash buffer contains a Bluetooth process SIGABRT, so stable instrumentation readiness is **not** claimed. These are Android system probes, not AmeGram tests.

The host has neither `/dev/kvm` nor CPU VMX/SVM support. Software TCG is substantially slower and cannot replace an accelerated device for reliable interactive/instrumentation validation. No screenshot or app-install result is presented as evidence of a working client.
