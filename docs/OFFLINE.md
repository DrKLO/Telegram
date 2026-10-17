# Develop without a phone or account

Run this from the project directory:

```sh
./dev demo
```

This boots **Features Lab API 36**, builds and installs the offline APK, and opens
Telegram's real tab navigation with sample data. No physical phone, SIM, Google account, Telegram account, `api_id`,
or `api_hash` is required. The emulator stays open after the command completes.

## What you can do

- Use the real **Settings → Telegram Stars**, personal privacy, channel and live-story paths. See the [native UI walkthrough](NATIVE_UI.md).
- Open **Design Notes** in Chats for the actual channel/post UI, or its live-story bubble for the actual live interface.
- Inspect Telegram's actual `DialogCell` and `ChatMessageCell` components with
  built-in sample content, rather than a web recreation of the app.
- Switch between light and dark themes; the selected theme is saved on the device.
- Edit source, rerun `./dev demo`, and retain the emulator's local app data.
- Record a demo: `./dev record 15`. MP4s are saved in ignored `artifacts/`.
- Inspect app logs with `./dev logs`, or run the app from Android Studio.

Run `./dev smoke-test` on the running emulator to check network/credential isolation,
the preview pages in light and dark themes, and theme persistence after a process
restart. It changes the offline app's selected theme to dark, saves screenshots and
results in `artifacts/`, and retains app data. It does not test the online package.

The host is `TMessagesProj_App/src/featuresLab/java/org/telegram/features/FeaturesLabActivity.java`.
The `Playground` fragment is retained for isolated component tests. The legacy theme preview
is `TMessagesProj/src/main/java/org/telegram/ui/ThemePreviewActivity.java`; shared
components live under `TMessagesProj/src/main/java/org/telegram/ui/Cells` and
`Components`. Feature code should reuse the same components used by the live app.

## Boundaries

This is an **offline build of the real UI with sample data**, not a full account-free Telegram service.
The native composer/search controls do not send messages or search a server.
There is no login session, Saved Messages account, synchronization, calling, push,
or live chat history. The Stars privacy feature uses local fixtures and mocked server capabilities.
Real payments and server enforcement still need separate integration tests.

The offline package is `org.telegram.features.offline.beta`, separate from both
official Telegram and the optional live lab build. Its merged manifest removes
`android.permission.INTERNET` and disables the normal login activity. API credentials
are always compiled as ID 0 / empty hash, even if local credentials exist.
The preview uses existing Telegram UI code; it does not fake authorization or
contact a test/production Telegram server.

Telegram also provides [test accounts](https://core.telegram.org/api/auth#test-accounts),
but those still use server-side accounts and API application credentials. They are
not needed for this offline workflow.

## Recreate the virtual device on another Apple Silicon Mac

After installing the toolchain in [DEVELOPMENT.md](DEVELOPMENT.md), run:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "emulator" "system-images;android-36;default;arm64-v8a"
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name Features_Lab_API_36 \
  --package "system-images;android-36;default;arm64-v8a" --device pixel_5
```

Answer **no** to a custom hardware profile and review any SDK license prompt. In
Device Manager, use a 720 × 1440 display at 320 dpi, 2 CPU cores, and about 2.5 GB
RAM. Android 16's emulator may raise a lower RAM setting to 2560 MB. The prepared
device uses a 3 GB data partition and no SD card. The AOSP image needs no Google
Play login and takes less storage than a Play Store image.

The helper disables snapshots to limit disk growth and preserves app data across
normal restarts. It never wipes the device or uninstalls the app automatically.
Keep at least 5 GB free for startup and more for substantial build changes.
Close the emulator window when finished to release its RAM. Its startup log is
`artifacts/emulator.log`.

## Optional live integration later

Use `./dev configure` and `./dev install` to build the distinct online package with
your own credentials. This explicitly overrides the offline Gradle default.
Online integration can also run on an emulator, so a physical phone remains
optional, although it is useful for final performance/camera/gesture testing.

For submission, show working feature code and clearly state which data is simulated.
Do not present the stock theme preview as a completed competition entry.

## Verified on 17 October 2026

On the M3 Mac with the Android 16 ARM64 AOSP emulator:

- Offline native build and incremental reinstall succeeded without credentials.
- Both Android smoke tests passed: visible preview pages in both themes and theme
  persistence after a full process restart. No account was activated.
- Eight helper tests passed, including credential-free offline builds and installing
  into the separate package without clearing data.
- The merged/installed APK has no INTERNET permission or Firebase startup provider;
  its normal login activity is disabled and all launcher icons target the offline host.
- A screen recording exported and decoded successfully. A 15-second H.264/30 fps
  preview clip is available locally at `artifacts/offline-preview-demo.mp4`.

Screenshots, emulator state, test output, and videos remain local and ignored by Git.
The original live-account acceptance checks remain pending and are not required
to use the offline UI playground.
