# Designers × AI submission checklist

The [official announcement](https://t.me/designers/266) asks for **working,
ready-to-implement code plus a video of the feature in action**, submitted by messaging
`@designers`. The linked post does not announce a deadline or prize fund. Recheck
that post and any newer instructions when the feature is ready.

## Establish the base environment

- [ ] Android Studio sync and Java/native compilation succeed.
- [ ] Features Lab installs alongside official Telegram.
- [ ] A separate test account logs in with locally configured API credentials.
- [ ] Saved Messages and light/dark themes work; the session survives restart.
- [ ] An incremental update installs without clearing the account.
- [ ] A phone recording exports to a playable MP4.

## Develop the feature

- [ ] Explain the user problem, entry point, and expected behavior.
- [ ] Implement it in this Telegram client and keep the diff focused.
- [ ] Check light/dark themes, relevant loading/empty/error states, navigation,
      keyboard behavior, and real phone performance.
- [ ] Record the tested phone/Android version and known limitations.
- [ ] If the feature needs push notifications, maps, or another service, configure
      and test that service before claiming the feature works end to end.

## Prepare the review package

- [ ] Push the feature branch to the public fork; record its exact commit.
- [ ] Provide reproducible setup/build instructions and a concise explanation of
      the change, with the feature diff separated from development setup.
- [ ] Provide a short MP4 showing the actual implemented behavior using staged data.
- [ ] Inspect recordings, screenshots, logs, patches, and archives for personal data.
- [ ] Exclude `local.properties`, debug/release signing keys, account/session files,
      caches, build directories, and compile-check APKs from source archives.
- [ ] Preserve licensing and clearly identify the build as unofficial. Check the
      upstream README's distribution requirements before distributing any APK.

Publishing source in the fork does not send anything to Telegram. Send the final
source link and reviewed video to `@designers` only when you explicitly choose to
submit; this setup does not create a submission or contact the organizers.
