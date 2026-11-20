# RiseGram local API configuration

Do not commit your Telegram API credentials to Git.

In the project root, create or edit `local.properties` (this file is ignored by Git) and add:

```properties
RISEGRAM_API_ID=YOUR_API_ID
RISEGRAM_API_HASH=YOUR_API_HASH
```

Then sync Gradle and build the Android app.

The values are injected into the app at build time. They will not appear in tracked source files, but the API hash can still be extracted from a distributed APK, so this does not make it secret after release.
