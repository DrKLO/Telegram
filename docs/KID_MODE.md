# Telegram Kid Mode

## What was changed
- Added centralized kid mode config in `/home/runner/work/Telegram/Telegram/TMessagesProj/src/main/java/org/telegram/messenger/KidModeConfig.java` with `VIDEO_PLAYBACK_BLOCKED = true`.
- Blocked video playback initialization in core playback paths:
  - `MediaController.playMessage(...)`
  - `PhotoViewer.preparePlayer(...)` and `PhotoViewer.playVideoOrWeb()`
  - `SecretMediaViewer.preparePlayer(...)`
  - `WebPlayerView.preparePlayer()`
- Added UI entry-point guards in:
  - `ChatActivity` (`needPlayMessage`, `didPressImage`)
  - `ChannelAdminLogActivity` (`needPlayMessage`, `didPressImage`)
- Disabled autoplay and streaming for videos through `SharedConfig` and `MessageObject.canStreamVideo()`.
- Renamed app label to `Telegram Kid Mode` / `Telegram Kid Mode Beta`.
- Switched package id base to `org.telegram.kidmode` in `/home/runner/work/Telegram/Telegram/gradle.properties`.
- Added CI workflow: `/home/runner/work/Telegram/Telegram/.github/workflows/build-kid-telegram.yml`.

## How video playback is blocked
- Central switch: `KidModeConfig.isVideoPlaybackBlocked()`.
- Message-level video block: `KidModeConfig.shouldBlockVideoPlayback(MessageObject)`.
- Inline-result video block: `KidModeConfig.shouldBlockVideoPlayback(TLRPC.BotInlineResult)`.
- Video player creation is stopped before ExoPlayer initialization in key boundaries listed above.
- Video autoplay is forced off (`SharedConfig.isAutoplayVideo()` returns false in kid mode).
- Video streaming is forced off (`SharedConfig.streamMedia/streamAllVideo/streamMkv` forced false; `MessageObject.canStreamVideo()` returns false).

## What still works
- Text messaging
- Photos/image viewing
- Voice messages and audio/music playback
- Document/file opening and downloads
- Non-video media downloads

## Android compatibility
- Current repository targets:
  - AGP `8.6.1`
  - Gradle `8.7`
  - compileSdk `35`
  - targetSdk `35`
  - minSdk `21`
  - NDK `27.2.12479018`
- Android 8.1 (API 27) remains supported because minSdk is 21, so no downgrade to an older Telegram source/release was needed.

## Build APK
### GitHub Actions
- Workflow file: `.github/workflows/build-kid-telegram.yml`
- Builds debug APK with:
  - JDK 17
  - Android platform 35 + build-tools 35.0.0
  - NDK 27.2.12479018
  - CMake 3.10.2.4988404
- Uploaded artifact name: `telegram-kid-mode-debug-apk`

### Local build
```bash
cd /home/runner/work/Telegram/Telegram
./gradlew :TMessagesProj_App:assembleAfatDebug
```

## Install on Samsung Galaxy Tab A6 (Android 8.1 / API 27)
1. Build the debug APK (local or from workflow artifact).
2. Copy APK to the tablet.
3. Enable installation from unknown sources for your file manager/browser.
4. Install APK.
5. Verify app name appears as **Telegram Kid Mode**.

## API keys / secrets and configuration notes
- This repository includes placeholder/demo configuration for reproducible builds.
- For production/public distribution, replace:
  - `google-services.json` with your own Firebase config.
  - Telegram API values in `BuildVars.java` with your own app credentials.
  - Signing keystore and signing values in `gradle.properties`.
- If you want CI to build signed release artifacts, configure secrets for keystore/signing and inject them at workflow runtime.

## Known limitations
- Video files can still appear in chats/channels but are intentionally non-playable.
- This change blocks Telegram video playback paths; external apps are not controlled by this app.

## How to disable Kid Mode later
1. Set `VIDEO_PLAYBACK_BLOCKED` to `false` in `KidModeConfig`.
2. Remove or relax playback guards in the files listed above if full behavior restoration is required.
3. Rebuild the app.
