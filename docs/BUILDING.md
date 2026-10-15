# Building AmeGram

## Toolchain and checkout

Use a reviewed commit and its exact submodule revisions (see UPSTREAM.md). Install JDK 17+ (this session uses 21), Android SDK/target 36, Build Tools 36.0.0, NDK 27.2.12479018 and CMake 3.22.1. The source/target language level remains Java 8; existing Android tests use Kotlin. Use the checked-in Gradle 8.13 wrapper and Android Studio 2025.1.4 or a compatible IDE. Allow ample memory/disk for the upstream native/media build.

```bash
git submodule update --init --recursive --depth=1
./gradlew --version
```

Configure the SDK through ANDROID_HOME or an ignored local.properties. In the prepared Codex environment, `source /workspace/cloud-setup/env.sh` selects its installed JDK, SDK, Gradle cache, proxy and CA trust. Keep TLS verification enabled and retain the configured proxy. No Docker image was built for this milestone.

## Checks without application credentials

```bash
./gradlew :TMessagesProj:compileDebugJavaWithJavac
./gradlew :TMessagesProj:compileDebugLibraryResources
./gradlew :TMessagesProj:testDebugUnitTest
./gradlew :TMessagesProj:lintDebug -Pandroid.injected.build.abi=arm64-v8a --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g'
```

The lint invocation selects arm64-v8a for its native prerequisites; use separate ABI passes for broader coverage. buildSrc Kotlin compiles as a dependency. Unit tests verify text contrast in every supported light/dark/OLED palette and OLED scope. Instrumentation tests in TMessagesProj_AppTests cover preference reload, disable, unrelated-key preservation and isolation from upstream preferences; they need an installable, configured test target and a device. They do not log in to Telegram.

Without API variables, library BuildConfig contains ID 0 and an empty hash solely to permit source/resource checks. These constants are not a usable Telegram application configuration. Packaging is guarded; do not bypass the guard or use fabricated values to claim a build.

## Owner configuration required before a debug APK

1. Register AmeGram at https://my.telegram.org using Telegram's [API registration instructions](https://core.telegram.org/api/obtaining_api_id). Supply **your application's own** API ID and hash as `AMEGRAM_API_ID` and `AMEGRAM_API_HASH` through the environment's secret bindings or an equivalent local secret manager. No personal login codes, passwords or session tokens are needed for compilation. Do not put values in Git, shell history, CI output, screenshots or Gradle command arguments. Client API configuration is embedded in an APK by design; never use this mechanism for user authentication/session secrets.
2. The regular debug package is `dev.umi.amegram.beta`, not Telegram's package. Register it in your Firebase project and supply its downloaded configuration as an untracked variant file at `TMessagesProj_App/src/debug/google-services.json`. Keep the upstream checked-in dummy file only as provenance; it has no matching AmeGram client. Do not edit its client package to fabricate configuration. The library also retains an upstream dummy Google-services configuration for source checks; assess the final merged resources against your own service registrations before device use.
3. Review Google/maps/auth/push integrations and signing fingerprints for this independent application. Upstream SafetyNet/Google auth/Huawei values and SMS app hashes are cleared; official-only passkeys are disabled. Manual Telegram login remains the intended supported path, but has not been device-verified. Do not claim services work merely because their classes compile.
4. Use the standard locally generated Android debug keystore. Do not distribute a debug build as a signed release. The upstream `TMessagesProj/config/release.keystore` and public fixture passwords remain provenance/test inputs for unconfigured upstream distribution scripts; AmeGram debug signing no longer uses them.

Once configuration is available:

```bash
./gradlew :TMessagesProj_App:processAfatDebugResources
./gradlew :TMessagesProj_App:assembleAfatDebug
```

The regular module is the supported initial application target. The base app ID is `dev.umi.amegram`, versionName `0.1.0-alpha`, base versionCode 1; the upstream ABI encoding makes afat code 19. Keep the upstream source/resource namespace (`org.telegram.*`); it is separate from the install identity.

The current task intentionally stops APK packaging when API configuration is missing. Other upstream application modules need their own service/privacy audit before packaging. Non-debug distribution is blocked even if API values are configured: independent release signing, service registrations, notice/source delivery and device validation must be implemented/reviewed in a subsequent change. No release or deployment is authorized by a successful local build.

## Native and dependency requirements

CMake links archives under `TMessagesProj/jni/prebuild/lib/<ABI>` and the sources selected by the pinned checkout. See `.gitmodules`, `jni/CMakeLists.txt` and `jni/prebuild/build_*.sh`. Existing checked-in archives are present, but all native dependencies have not been rebuilt in this task. Follow the component scripts and their prerequisites when rebuilding; do not change security checks or cryptographic implementations to silence errors.

## Device smoke test before calling the client working

- Confirm AmeGram label/icon/package, unofficial notice and coexistence with official Telegram.
- Verify Android account types and contacts sync never enumerate/remove the official app's accounts.
- With explicit authorization for a test account, verify supported login, messages, media and notifications; no auth secrets in logs.
- Open Settings → AmeGram Settings; toggle OLED, reopen, force-stop/relaunch and reboot. It must remain scoped to that screen and apply only in dark mode.
- Verify theme switching while visible, TalkBack switch/button semantics, large fonts, keyboard focus, landscape/tablets, system animation scale and back navigation.
- Verify Liquid Glass cannot be enabled; document any remaining upstream blur/transparency as pending work.

See VERIFICATION.md for what actually ran here.
