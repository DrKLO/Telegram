# Architecture audit — 0.1.0-alpha

Audit date: 2026-10-15. Starting checkout: `f32ffeec763f0bdbf4bec25b52d654f77bff733d`, commit `update to 13.0.2 (7175)`. Working tree was clean on branch `work`. The origin is `https://github.com/ntych/AmeGram-Android.git`; implementation uses `feature/amegram-foundation`. The official `DrKLO/Telegram` master ref resolved to the same SHA during this audit. Full upstream history is present, beginning with `4622ba9186` (`first commit`). No re-import is needed.

## Modules and responsibilities

| Area | Existing architecture |
| --- | --- |
| `TMessagesProj` | Android library: Java messenger/controllers/storage, `tgnet` protocol objects and JNI, custom View UI, common resources |
| `TMessagesProj_App` | Regular application shell, application loader, manifests/flavors and packaging |
| `TMessagesProj_AppStandalone` | Separate standalone loader/application shell |
| `TMessagesProj_AppHuawei` | Huawei-specific integration; requires separate service review |
| `TMessagesProj_AppHockeyApp` | Beta/distribution integration; includes upstream Crashlytics code and is not a supported AmeGram target |
| `TMessagesProj_AppTests` | Existing generated TL/instrumentation tests; Kotlin is enabled here |
| `TMessagesProj_Modules/media` | Pinned Media3 submodule; ExoPlayer, sessions and FFmpeg integration |
| `TMessagesProj/lib/jlatexmath` | Pinned math-rendering submodule |
| `buildSrc` | Kotlin Gradle plugins, localization/resource generators, TL schema generators/validators |

The main library uses Java; Kotlin primarily serves build tooling and existing Android tests. Do not introduce Compose, a new networking stack, a mock server or dependency injection framework for this milestone.

## Toolchain

- Gradle wrapper 8.13; Android Gradle Plugin 8.13.2.
- Kotlin Gradle plugin 2.1.0 for existing Kotlin Android tests; buildSrc uses Gradle's Kotlin DSL compiler with language/API 1.9 settings.
- Java source/target 8, with core-library desugaring 2.1.5. This environment runs JDK 21; it emits Java 8 target deprecation warnings. AGP requires at least JDK 17.
- Android min SDK 23; compile/target SDK 36; Build Tools 36.0.0.
- NDK 27.2.12479018; CMake 3.22.1; native target `tmessages.49`.
- Regular application flavors: `afat` and `bundleAfat`; `afatDebug` is the initial AmeGram target. ABIs: armeabi-v7a, arm64-v8a, x86, x86_64.
- Existing lint configuration suppresses MissingTranslation, ExtraTranslation and BlockedPrivateApi. No additional suppression or security-check removal was introduced.

## Native dependencies

`.gitmodules` pins 19 top-level submodules, including TDLib, tgcalls, tlottie, BoringSSL, FFmpeg, dav1d, libvpx, libyuv, OpenH264, Opus/Ogg/opusfile, Abseil, Boost Regex, WAMR, libprisma and wallet-engine. All top-level submodules were initialized at their recorded revisions in the cloud checkout.

`jni/CMakeLists.txt` builds tgnet, SQLite, calls and messenger JNI and links static archives from `jni/prebuild/lib/<ABI>`. This repository includes prebuilt archives and per-component source build scripts. The upstream build manifest identifies the NDK and build tools; a clean rebuild of all native archives was not performed in this task. Do not replace crypto/protocol libraries to bypass build failures. Keep each component's license and source obligations.

## UI, navigation and localization

`LaunchActivity` owns the custom navigation layout. Screens inherit `BaseFragment`; `ActionBarLayout` / `INavigationLayout` manage stacks, including tablet panes. `SettingsActivity` builds settings through `UniversalRecyclerView`, `UItem` and its `SettingCell` factory. The AmeGram entry reuses `presentSettingFragment` and propagates the active account.

`AmeGramSettingsActivity` is a `BaseFragment` with a small native scrollable vertical layout. It uses Telegram's existing stack and creates fresh destination fragments on each click. Its own text wraps, links/switch have at least 56dp height, links expose button semantics and headers use the AndroidX accessibility-heading backport. It adds no in-screen animation or blur. Visual behavior, large fonts, TalkBack, keyboard navigation, tablets and frame performance still need device checks.

`Theme` supplies the existing app-wide color keys, theme editor, selected themes and day/night behavior. `AmeGramPalette` is deliberately scoped to AmeGram Settings, inherits the upstream dark/light selection and updates on theme notifications/resume. The primary purple is #8B5CF6; normal-sized text uses #7C3AED on light surfaces and #A78BFA on dark surfaces for contrast. OLED is black only when the upstream dark theme is active. Existing chat themes are not overwritten.

New strings use the `AmeGram` prefix in the existing default and Russian `strings.xml`. This is intentional: buildSrc's Telegram localization generators enumerate those exact filenames. A separate string XML file would not automatically participate in the custom language assets. Nonlocalized technical resources use `values/amegram.xml`.

Upstream 13.0.2 already contains Liquid Glass and blur infrastructure. AmeGram masks `FLAG_LIQUID_GLASS` in runtime/settings queries and omits its user control. This disables the gated liquid effect; it does not establish a full app-wide removal of existing frosted blur, transparency or floating navigation. That compatibility-sensitive redesign is a later milestone.

## Settings and feature boundaries

Upstream uses `SharedConfig`, `MessagesController` SharedPreferences, `Theme` preferences and newer `org.telegram.utils.settings` wrappers. The newer `BooleanSetting.of` factory is package-private and tied to its existing preference store. A small `AmeGramSettings` wrapper follows the same SharedPreferences/apply convention but owns `amegram_settings`, keeping fork data isolated instead of modifying that upstream abstraction.

The only current AmeGram preference is `oled_dark_background`, default false, device-wide across accounts. `apply()` updates memory immediately and queues the disk write; Android performs lifecycle flushing. No messages, chat identifiers, auth data or encryption keys are stored here. Process-death/reboot persistence still needs a device smoke test. No custom notes or backup format exists yet.

`AmeGramFeatureFlags` contains compile-time availability gates. AmeLab is unavailable. Future experiments require both an availability gate and an individually named, default-false local opt-in preference; do not add a global opt-in that silently enables experiments.

## Identity, services and compatibility-sensitive changes

The application ID is `dev.umi.amegram`, version `0.1.0-alpha`, base versionCode 1 (upstream ABI encoding makes regular afat code 19). Java/JNI/resource namespaces remain upstream `org.telegram.*` to avoid unnecessary breakage. Android components may retain these class names while belonging to the independent application package.

Android authenticator and contacts sync XML now reference `amegram_account_type`. The existing app build plugin overrides this resource with each variant's final application ID, and ContactsController uses the same resource. This prevents AmeGram's account maintenance from enumerating/removing official Telegram Android accounts. FileProvider authorities already use `${applicationId}`. Telegram protocol/deep links and contact MIME identifiers are retained; interaction/coexistence still requires device verification.

All shipped app labels and manifest launcher aliases use AmeGram branding and a temporary purple A monogram. The inherited alternate-icon selector is hidden until real independent variants exist. Required source/license headers are preserved; font license assets are no longer excluded by the root packaging pattern.

BuildVars reads API values generated from AMEGRAM_API_ID/AMEGRAM_API_HASH; missing values produce nonfunctional source-check constants only. Packaging requires configured API values. Upstream update downloads, official-only passkeys and upstream SMS app hashes are disabled/cleared rather than falsely presented as registered AmeGram services. Logs default off, including debug builds. A focused source audit also removed raw future login tokens, phone-code hashes, push decryption keys/payloads, FCM registration tokens, Play Integrity/reCAPTCHA tokens and billing purchase tokens from identified log statements. Token storage and challenge/key verification remain upstream; this is not a complete security audit. LoginActivity advertises SMS app-hash retrieval only when an app hash is configured. No telemetry/advertising SDK was added. Retained upstream service dependencies are not equivalent to audited or configured AmeGram services; only the regular debug target is supported initially. Signing, Firebase, Google auth, maps, Huawei, payments and distribution must be reviewed before a release.

## Verification boundaries

See [VERIFICATION.md](VERIFICATION.md) for actual commands/results. Compiling Java/Kotlin and resources does not verify login, messaging, JNI, Android account coexistence, push or rendering. No user authentication was attempted. No credentials, protocol implementation, transport security or cryptographic algorithm was weakened for these changes.
