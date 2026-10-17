# Designers × AI submission checklist

The [official announcement](https://t.me/designers/266) asks for **working,
ready-to-implement code plus a video of the feature in action**, submitted by messaging
`@designers`. The linked post does not announce a deadline or prize fund. Recheck
that post and any newer instructions when the feature is ready.

## Feature under review: Private Star Reactions

[Feature behaviour, API proposal and validation](STARS_PRIVACY.md).
Source branch: `feature/stars-privacy`; setup remains in the earlier `setup/offline`
and `setup/android` commits. The prototype uses Telegram's native Android controls.
Personal anonymous paid post reactions use the existing API in a credentialed build.
Channel enforcement, permanent history redaction, percentiles and anonymous live
reactions use a local mock. No account or real payment is involved in the demo.

Do not call this full payment anonymity or secure deletion: existing Telegram servers,
other clients, payment providers and recipient records have not changed. Include the
server work described in STARS_PRIVACY.md with any submission.

## Establish the offline environment

The default development path is now the [offline emulator](OFFLINE.md). Its sample
data demonstrates the implemented feature without proving live messaging or server
enforcement. A submission video must show the implemented feature;
identify any simulated data and untested server behavior clearly.

- [x] Offline build installs and launches in the ARM64 emulator without credentials.
- [x] Telegram's preview components render in light/dark themes.
- [x] Theme selection survives a process restart and incremental reinstall works.
- [x] A screen recording exports to a decodable MP4.

## Validate live integration when the feature needs it

- [ ] Android Studio sync and Java/native compilation succeed.
- [ ] Features Lab installs alongside official Telegram.
- [ ] A separate test account logs in with locally configured API credentials.
- [ ] Saved Messages and light/dark themes work; the session survives restart.
- [ ] An incremental update installs without clearing the account.
- [ ] A phone recording exports to a playable MP4.

## Develop the feature

- [x] Explain the user problem, entry point, and expected behavior.
- [x] Implement native personal/admin/history/live flows and isolate missing API behaviour in a mock.
- [ ] Check light/dark themes, relevant loading/empty/error states, navigation,
      keyboard behavior, and real phone performance.
- [ ] Record the tested phone/Android version and known limitations.
- [ ] If the feature needs push notifications, maps, or another service, configure
      and test that service before claiming the feature works end to end.

## Prepare the review package

- [x] Publish `feature/stars-privacy` to the public fork. Use `git rev-parse HEAD` to pin the submitted revision.
- [x] Provide reproducible setup/build instructions and a concise explanation of
      the change, with the feature diff separated from development setup.
- [x] Provide a short MP4 showing the actual implemented behavior using staged data: local `artifacts/stars-privacy-demo.mp4`.
- [ ] Inspect recordings, screenshots, logs, patches, and archives for personal data.
- [ ] Exclude `local.properties`, debug/release signing keys, account/session files,
      caches, build directories, and compile-check APKs from source archives.
- [ ] Preserve licensing and clearly identify the build as unofficial. Check the
      upstream README's distribution requirements before distributing any APK.

Publishing source in the fork does not send anything to Telegram. Send the final
source link and reviewed video to `@designers` only when you explicitly choose to
submit; this setup does not create a submission or contact the organizers.
