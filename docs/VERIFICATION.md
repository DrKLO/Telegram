# Verification record — 0.1.0-alpha foundation

This is the historical 2026-10-15 record. See [BUILD_AUDIT.md](BUILD_AUDIT.md) for the 2026-10-17 CI, Firebase and emulator recheck; earlier blockers here must not be treated as current without that update.

Date: 2026-10-15. Baseline: `f32ffeec763f0bdbf4bec25b52d654f77bff733d`. Code commits: `5fbd90d266` (foundation) and `c310e8e9a3` (sensitive log removal / SMS capability). This is a source-development milestone, not a released or device-verified client.

## Environment

Prepared Codex Cloud checkout with JDK 21, Gradle 8.13, AGP 8.13.2, SDK 36, Build Tools 36.0.0, NDK 27.2.12479018 and CMake 3.22.1. All 19 top-level submodules are initialized at recorded gitlinks. The container memory limit is 8 GiB. No owner-provided AMEGRAM_API_ID / AMEGRAM_API_HASH bindings are present; values were not requested or printed. No real user login, messages or session material were used.

## Executed checks

| Check | Result |
| --- | --- |
| Official upstream master lookup | Same full SHA as the initial fork checkout |
| `buildSrc:compileKotlin` | Passed after integrating AGP's `makeResValueKey` API; later invocations reused the result |
| `:TMessagesProj:compileDebugJavaWithJavac` | Passed for the final code, including the log-removal changes |
| `:TMessagesProj:compileDebugLibraryResources` | Completed/cached successfully; library resources compiled, not a full application link |
| `:TMessagesProj:testDebugUnitTest` | 2 tests, 0 failures, 0 errors; WCAG AA text/accent contrast across light/dark/OLED and OLED dark-only behavior |
| CMake `tmessages.49`, arm64-v8a | Task completed with the existing pinned native archives/cached build outputs; not a clean rebuild of all third-party native sources |
| `:TMessagesProj:lintDebug` | Completed and failed: **348 errors, 6163 warnings**. No error locations overlap changed source lines, and no errors occur in the new AmeGram packages; see scope below. |
| `:TMessagesProj_App:processAfatDebugResources` | Blocked at `processAfatDebugGoogleServices`: `No matching client found for package name 'dev.umi.amegram.beta'` |
| Debug APK attempt | Attempted `:TMessagesProj_App:assembleAfatDebug`; stopped by the build guard: `APK packaging blocked: set your own AMEGRAM_API_ID and AMEGRAM_API_HASH in the build environment.` No APK produced. |
| New preference instrumentation source | Separately compiled with javac against Android 36, the built library, JUnit 4.13.2, AndroidX test-ext 1.1.5 and monitor 1.6.1; this is source compilation only |
| Device/instrumentation execution | Not run: no SDK emulator is installed; adb aborts trying to create `/home/agent/.android` on a read-only filesystem, including with the available Android directory environment settings |
| Identity/resource checks | 20 AppName/AppNameBeta resources use AmeGram; modified manifests parse as XML; generated regular-debug `amegram_account_type` is `dev.umi.amegram.beta` |
| Legal/history checks | LICENSE and gitlinks unchanged; archived upstream README blob exactly matches the baseline README |
| Patch whitespace | `git diff --check` passed |

The largest lint error categories are AppCompatCustomView (138), WrongConstant (48), MissingPermission (45), NewApi (30), UnsafeOptInUsageError (26) and WrongThread (20). Error locations were compared against the baseline-to-HEAD changed-line ranges and none overlap those ranges. This establishes that the reported error locations are in retained code, not that a full baseline lint run produced an identical report; no full baseline lint comparison was run. The new Settings screen has two SetTextI18n warnings for the About metadata concatenation; a localized format resource is a small follow-up. Existing lint errors remain unresolved and must be triaged, particularly permission/API/threading findings. No blanket baseline or new suppression was added.

The successful source/resource/test tasks are distinguishable from the combined command's overall lint outcome. No successful APK build, install, screenshot, login or messaging test is claimed.

## Commands

After sourcing the environment's existing toolchain/proxy/CA configuration:

```bash
./gradlew :TMessagesProj:compileDebugJavaWithJavac \
  :TMessagesProj:compileDebugLibraryResources \
  :TMessagesProj:testDebugUnitTest :TMessagesProj:lintDebug \
  -Pandroid.injected.build.abi=arm64-v8a --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g' --console=plain

./gradlew :TMessagesProj_App:processAfatDebugResources --console=plain
./gradlew :TMessagesProj_App:assembleAfatDebug \
  -Pandroid.injected.build.abi=arm64-v8a --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g' --console=plain
```

The full-ABI lint attempt was intentionally stopped during armeabi-v7a native compilation to select arm64-v8a for this milestone. A subsequent arm64 lint pass with the upstream 8 GiB heap/parallel defaults lost its daemon; the cgroup recorded an OOM kill. The final pass uses a 4 GiB heap and one worker. No security checks, Google-services validation or lint rules were disabled to obtain a result. Toolchain warnings about Java 8 targets, deprecated Gradle options and existing native code were retained.

The new instrumentation test checks local preference defaults, enable/reload/disable, an unrelated-key invariant and upstream-store isolation. Its synchronous commit waits for queued apply writes, but it does not prove process-death or reboot behavior; that requires a device test. No full upstream instrumentation suite was executed.

## Security scope and remaining work

The focused log review removed identified raw login, push, integrity, CAPTCHA and purchase token values, push keys and push payloads from explicit log statements. A targeted source scan of the changed logging paths found none of those remaining raw-value patterns. This is not a complete security/privacy review, nor runtime verification with user data. The network/cryptographic checks and token/challenge callbacks remain present.

Provide owner-registered API configuration and a real Firebase debug client through secret/local configuration as documented in BUILDING.md. Then complete full application resource linking, debug APK packaging, install/device smoke tests, official-app account coexistence, settings persistence across process death, theme/accessibility/navigation checks and the remaining ABI coverage. Independent release signing, service registrations, source/license delivery and distribution review are still required. No release, deployment or merge into master was performed.
