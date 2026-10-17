# Develop with Features Lab

Features Lab is a separately installed, unofficial Telegram Android development
app. This checkout starts from Telegram **13.0.2 (7175)**, commit
`f32ffeec763f0bdbf4bec25b52d654f77bff733d`.

**Current default: account-free emulator development.** Start with
[OFFLINE.md](OFFLINE.md) and `./dev demo`. The phone/account sections below describe
the optional live Telegram workflow. The offline client uses production navigation
with sample data, without creating a Telegram account or simulating a login.
See [NATIVE_UI.md](NATIVE_UI.md) for the Settings, purchase, channel and live-story paths.

## Toolchain

| Component | Version |
| --- | --- |
| Android Studio | Narwhal 4 Feature Drop 2025.1.4, Apple Silicon installer 2025.1.4.8 |
| Gradle JVM | Java 17 |
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Android SDK / Build Tools | API 36 / 36.0.0 |
| NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| Minimum phone Android version | Android 6.0 / API 23 |

The setup reuses the existing SDK at `~/Library/Android/sdk` and Homebrew Java 17.
The `dev` command sets Java and SDK paths for this project; it does not change your
shell profile. Android Studio reads the ignored `.gradle/config.properties` file
through `GRADLE_LOCAL_JAVA_HOME`. Gradle uses a 4 GB heap and two workers; native
compilation has a two-job pool and a one-job link pool.

Start with 40 GB of free storage as a working-space target. Actual source, downloads,
indexing, and build sizes vary. The offline workflow uses one emulator; the optional
live workflow can use either an emulator or a physical phone.

On a fresh Apple Silicon Mac, install Android Studio 2025.1.4 from the
[official archive](https://developer.android.com/studio/archive). Install Java 17
and Git LFS with `brew install openjdk@17 git-lfs`. In Studio's SDK Manager, install
Android SDK Platform 36, Android SDK Build-Tools 36.0.0, Android SDK Platform-Tools,
Android SDK Command-line Tools, NDK (Side by side) 27.2.12479018, and CMake 3.22.1.
Enable **Show Package Details** to choose the exact versions, and review the SDK
licenses when prompted. Then run `./dev doctor` from the checkout.

## Reproduce this checkout

```sh
GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 --branch feature/stars-privacy \
  --recurse-submodules --shallow-submodules \
  https://github.com/yes-i-like-pina-coladas/Telegram.git
cd Telegram
git remote add upstream https://github.com/DrKLO/Telegram.git
./dev doctor
```

Install Git LFS before initializing submodules. `GIT_LFS_SKIP_SMUDGE=1` leaves
wallet-engine's Swift/web visual-test PNG snapshots as LFS pointers; those assets
are not used by the Android app. All submodules must remain at the commits recorded
by the parent repository. Do not run `git submodule update --remote`.

For an interrupted checkout, first run:

```sh
GIT_LFS_SKIP_SMUDGE=1 git submodule update --init --recursive --depth 1 --jobs 2
```

Do not reset a submodule containing your own changes. Inspect its status first.

## Connect your phone

1. Open Settings → About phone. Tap **Build number** seven times. On Samsung,
   Build number is normally under Software information.
2. Enable **USB debugging** under Developer options.
3. Connect a USB cable that supports data. Unlock the phone and accept the RSA
   authorization prompt for this Mac. Select File transfer if the device requires it.
4. Run `./dev devices`; the phone should be listed with state `device`.
5. Run `./dev doctor` to verify the Android version and supported ARM architecture.

If it says `unauthorized`, unlock the phone and approve the prompt. If it is absent,
try another cable or USB port. No Android USB driver is normally required on macOS.
When multiple devices are connected, set `ANDROID_SERIAL` to the intended serial.

The helper detects `arm64-v8a` or `armeabi-v7a` from the connected phone, saves that
choice locally, and builds only that architecture. Without a connected phone it
uses the last local setting, initially `arm64-v8a`. Android Studio uses the saved
setting; run the helper once after changing phones.

## Create a test account and enter your API credentials

1. Register a separate Telegram account in the **official Telegram app**, using a
   second phone number that you control. This can be a second account in the same
   official app; it does not require a second physical phone.
2. Open [my.telegram.org/apps](https://my.telegram.org/apps), sign in, and complete
   the API development form for your application. Keep login codes on your devices.
3. In Terminal, in this project, run `./dev configure`. Enter your `api_id` and
   `api_hash` at the prompts; the hash is hidden.
4. Run `./dev install`, then sign into Features Lab with the test account. Keep the
   official app available to receive Telegram's login code.

The values are stored in ignored `local.properties` with owner-only permissions.
Never paste them into chat, put them in a commit, or include the file in an archive.
A compiled APK contains its API credentials, so this initial workflow publishes
source only. `local.properties.example` documents the configuration without values.

Normal builds require valid credentials. `./dev compile-check` explicitly builds
with API ID 0 and an empty hash, solely to verify compilation before registration
is ready. That APK is **not a login-ready or distributable build**. `./dev install`
always performs a normal credential-checked rebuild before installing.

## Daily commands

| Command | Purpose |
| --- | --- |
| `./dev demo` | Boot the dedicated emulator, build and launch the offline playground |
| `./dev emulator` | Start the dedicated virtual device without rebuilding |
| `./dev offline-build` | Build the offline APK without credentials |
| `./dev offline-install` | Build/install the offline APK on a selected device |
| `./dev privacy-test` | Verify personal/admin/history privacy and 20-second Undo in the offline app |
| `./dev smoke-test` | Check offline rendering, isolation, and theme persistence |
| `./dev doctor` | Check toolchain, pinned submodules, credential presence, and phone |
| `./dev configure` | Enter API credentials locally |
| `./dev devices` | Inspect USB device authorization |
| `./dev build` | Incremental debug build for the selected phone architecture |
| `./dev compile-check` | Compilation only, with no account credentials |
| `./dev install` | Build, install an update, and launch Features Lab |
| `./dev logs` | Follow logs for the running app process; stop with Ctrl-C |
| `./dev record 30` | Record 30 seconds from the phone and retrieve an MP4 |
| `./dev studio` | Open the project in the configured Android Studio |

The APK is `TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`. Recordings go
into the ignored `artifacts/` folder. The recorder accepts durations from 1 to 180
seconds and captures the phone screen without microphone audio. Turn off unrelated
notifications and demonstrate staged test content before recording. Inspect the
video before sharing. Logs can contain account information; do not publish them raw.

For the equivalent Gradle task, use Java 17 and the repository wrapper:

```sh
./gradlew :TMessagesProj_App:assembleAfatDebug -PfeaturesLab=true -PfeaturesLabOffline=false
```

Do not use a separately installed system Gradle. Do not upgrade AGP, the wrapper,
or NDK automatically when Android Studio suggests it.

## Android Studio

Run `./dev studio`, open this project (not a new Android application), and allow the
Gradle sync. Select **afatDebug** for `TMessagesProj_App` in Build Variants, the
**Features Lab** run configuration, and the Features Lab emulator (or a connected phone). The shared run
configuration lives in `.run/Features_Lab.run.xml`.

On the prepared Mac, the pinned version is installed as
`/Applications/Android Studio Features Lab.app`; `./dev studio` opens that copy.
The earlier Android Studio installation is still available separately.
Gradle defaults to the offline playground. `./dev build` and `./dev install`
explicitly select the credential-checked live build instead.

The local SDK and Java paths are generated by `./dev doctor` / `./dev studio`.
If Studio imports different defaults, select **GRADLE_LOCAL_JAVA_HOME** under
Settings → Build, Execution, Deployment → Build Tools → Gradle.

The baseline currently emits an upstream Jetifier warning about the Cast library
mixing AndroidX and old support-library references. Studio sync and the ARM64 debug
build succeed with this warning. Keep the pinned Gradle version despite its
deprecation notices.

## Earlier live-build validation — 17 October 2026

For the completed account-free emulator checks, see [OFFLINE.md](OFFLINE.md#verified-on-17-october-2026).

- Android Studio 2025.1.4 sync succeeded using Java 17.
- The complete ARM64 `afatDebug` compile-check build succeeded in 10m 58s.
- An unchanged incremental build succeeded in 6s (296 of 300 tasks up to date).
- APK signature verification passed. Package, app label, ARM64-only contents, and
  the merged manifest's Firebase startup exclusions were checked.
- The credential guard rejected a normal build without local API credentials.
- Six helper-workflow tests passed: `python3 -m unittest discover -s tests`.

No phone was connected and no personal API credentials were configured during
these checks. Installation, runtime startup, login, Saved Messages, theme switching,
session persistence, incremental installation, and MP4 playback remain pending.
The built APK is a credential-free compile check. This is not yet a fully verified
phone environment.

## What the development configuration changes

- Installs as `org.telegram.features.beta`, with a flask icon and the name Features Lab.
- Uses your local Android debug keystore, not Telegram's checked-in dummy release key.
- Uses your local API credentials and disables official-client passkeys, SafetyNet
  configuration, and automatic upstream APK updates for this build.
- Skips Firebase/Google Services config processing, removes Firebase's startup
  provider, and disables push-token requests. Foreground Telegram messaging still
  uses Telegram's regular network connection; background push delivery is not a
  supported acceptance criterion yet.
- Leaves maps/service-specific integrations unconfigured. Set them up when the
  chosen feature needs them. No Firebase project is created in this phase.
- Loads only the regular app and its dependencies to reduce Studio/build overhead.

The checked-in upstream service fixtures are retained for comparison; the lab build
does not use them as its Firebase configuration. Use `-PfeaturesLab=false` only to
inspect upstream configurations, not to produce a Features Lab distribution.

## Feature and submission workflow

Keep `setup/offline` as the current setup baseline (`setup/android` preserves the
earlier phone/account setup). Create one `feature/<name>`
branch per concept. Keep UI feature commits separate from setup changes. Avoid
editing submodules unless the feature requires it; preserve existing GPL notices.

Before calling the setup fully ready, verify a successful build, installation,
login, Saved Messages, light/dark themes, restart/session persistence, a second
incremental installation, and a playable screen recording. See [SUBMISSION.md](SUBMISSION.md)
for the later source-and-video handoff.
