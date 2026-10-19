# Building AmeGram

## Toolchain and checkout

Use a reviewed commit and its exact submodule revisions (see UPSTREAM.md). Install JDK 17+ (this session uses 21), Android SDK/target 36, Build Tools 36.0.0, NDK 27.2.12479018 and CMake 3.22.1. The source/target language level remains Java 8; existing Android tests use Kotlin. Use the checked-in Gradle 8.13 wrapper and Android Studio 2025.1.4 or a compatible IDE. Allow ample memory/disk for the upstream native/media build.

```bash
git submodule update --init --recursive --depth=1
./gradlew --version
```

Configure the SDK through ANDROID_HOME or an ignored local.properties. In the prepared Codex environment, `source /workspace/cloud-setup/env.sh` selects its installed JDK, SDK, Gradle cache, proxy and CA trust. Keep TLS verification enabled and retain the configured proxy. No Docker image was built for this milestone.

## Pull-request checks without credentials

Use the same explicit source-check profile as GitHub Actions:

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

This profile compiles the real sources and resources and runs JVM tests without Google-services processing, optional store plugins or owner API values. It always generates API ID 0 / empty hash and **rejects every APK/bundle packaging attempt**. It is not an installable client, a mock backend, or a way to bypass application registration. Native compilation and emulator/instrumentation tests are separate checks requiring the appropriate configuration. CI uses read-only permissions and does not request secrets or upload configuration/build outputs.

The shared library no longer processes google-services.json: generated Firebase values belong to the application. Source checks work in a clean checkout with no service JSON files. Normal debug builds continue using the owner's real application configuration.

The full lint command remains available separately:

```bash
./gradlew :TMessagesProj:lintDebug -Pandroid.injected.build.abi=arm64-v8a --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g'
```

The existing lint backlog is documented in [BUILD_AUDIT.md](BUILD_AUDIT.md); it has not been blanket-suppressed to make CI green. That lint command can trigger native prerequisites. Palette unit tests cover contrast/OLED behavior; preference instrumentation tests require a configured APK and device.

## Owner configuration required before a debug APK

1. Register AmeGram at https://my.telegram.org using Telegram's [API registration instructions](https://core.telegram.org/api/obtaining_api_id). Supply **your application's own** API ID and hash as `AMEGRAM_API_ID` and `AMEGRAM_API_HASH` through the environment's secret bindings or an equivalent local secret manager. No personal login codes, passwords or session tokens are needed for compilation. Do not put values in Git, shell history, CI output, screenshots or Gradle command arguments. Client API configuration is embedded in an APK by design; never use this mechanism for user authentication/session secrets.
2. The regular debug package is `dev.umi.amegram.beta`, not Telegram's package. Register it in your Firebase project and supply its downloaded configuration as an untracked variant file at `TMessagesProj_App/src/debug/google-services.json`. No Google/Huawei service JSON belongs in Git. Upstream fixtures have been removed from the current tree and are not a configuration template; never edit a package field to fabricate registration. The shared library does not process service configuration. Assess final merged application resources against your own registrations before device use.
3. Review Google/maps/auth/push integrations and signing fingerprints for this independent application. Upstream SafetyNet/Google auth/Huawei values and SMS app hashes are cleared; official-only passkeys are disabled. Manual Telegram login remains the intended supported path, but has not been device-verified. Do not claim services work merely because their classes compile.
4. Use the standard locally generated Android debug keystore. Do not distribute a debug build as a signed release. The upstream `TMessagesProj/config/release.keystore` and public fixture passwords remain provenance/test inputs for unconfigured upstream distribution scripts; AmeGram debug signing no longer uses them.

Once configuration is available:

```bash
./gradlew :TMessagesProj_App:processAfatDebugResources
./gradlew :TMessagesProj_App:assembleAfatDebug -Pandroid.injected.build.abi=x86_64 --max-workers=1
```

The x86_64 selection above matches the documented emulator. Select arm64-v8a for a corresponding physical device, or omit the injected ABI to build all configured native targets. The regular module is the supported initial application target. The base app ID is `dev.umi.amegram`, versionName `0.1.0-alpha`, base versionCode 1; the upstream ABI encoding makes afat code 19. Keep the upstream source/resource namespace (`org.telegram.*`); it is separate from the install identity.

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

## Troubleshooting and emulator setup

- `APK packaging blocked`: configure owner Telegram API values in the environment. A valid Firebase file does not supply Telegram API credentials.
- `No matching client found`: verify the Firebase Android package is `dev.umi.amegram.beta` for regular debug; use the variant file path above. Do not print the JSON.
- `Source-check mode cannot package`: remove `-PamegramSourceChecks=true` only when building a genuinely configured client; do not remove the packaging guard.
- Missing Media3/math projects: initialize the two pinned source-check submodules. Full native builds need the additional native submodules too.
- OOM / vanished Gradle daemon: use the documented 4 GiB heap, one worker and no parallel projects; avoid simultaneous full lint/native builds and software emulation on an 8 GiB host.
- Daemon wildcard/socket failure: the host sandbox must permit Gradle's local sockets; offline mode still needs them. Keep proxy and TLS verification intact.
- Java 8 target / Gradle deprecation warnings: retained upstream configuration; not a reason to upgrade major dependencies during this audit.
- Full lint failure: triage the recorded permission/API/threading/custom-view diagnostics; source-check CI is not a claim that full lint passes.

For a headless test device, install the official SDK packages and create a local AVD:

```bash
sdkmanager 'emulator' 'system-images;android-35;default;x86_64'
avdmanager create avd --name AmeGram_API35 --package 'system-images;android-35;default;x86_64' --device pixel_2
emulator -accel-check
emulator -avd AmeGram_API35 -no-window -no-audio -no-snapshot -gpu swiftshader
adb wait-for-device
adb shell getprop sys.boot_completed
```

Use a writable Android user/AVD directory appropriate to your environment. KVM on Linux requires host CPU virtualization and `/dev/kvm`; it cannot be manufactured inside this container. If unavailable, `-accel off` uses slow software TCG and may take much longer to boot. ADB connectivity alone is not boot completion: require `sys.boot_completed=1` and inspect the active UI before calling the device ready. App installation still requires a valid configured AmeGram APK; emulator setup does not remove the API/Firebase requirements. Software emulation does not validate other ABIs or 16 KiB page-size devices.

See [BUILD_AUDIT.md](BUILD_AUDIT.md) for this session's actual checks and limitations.
