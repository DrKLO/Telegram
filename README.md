## Dahl - unofficial client of Telegram for Android

[Dahl](https://dahl.tilda.ws/) is a third-party Telegram client with not many but useful modifications.

## Creating your Telegram Application

1. [**Obtain your own api_id**](https://core.telegram.org/api/obtaining_api_id) for your application.
2. Please **do not** use the name Telegram for your app — or make sure your users understand that it is unofficial.
3. Kindly **do not** use our standard logo (white paper plane in a blue circle) as your app's logo.
3. Please study our [**security guidelines**](https://core.telegram.org/mtproto/security_guidelines) and take good care of your users' data and privacy.
4. Please remember to publish **your** code too in order to comply with the licences.

### API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTproto protocol manuals: https://core.telegram.org/mtproto

### Compilation Guide

**Note**: In order to support [reproducible builds](https://core.telegram.org/reproducible-builds), this repo contains dummy release.keystore,  google-services.json and filled variables inside BuildVars.java. Before publishing your own APKs please make sure to replace all these files with your own.

You will require Android Studio 3.4, Android NDK rev. 20 and Android SDK 8.1

1. Download the Dahl source code from https://github.com/Telegru/Teleport-Android.git ( git clone https://github.com/Telegru/Teleport-Android.git )
2. Copy your release.keystore into TMessagesProj/config
3. Fill out keyPassword, keyAlias, storePassword in keystore.properties file to access your release.keystore
4.  Go to https://console.firebase.google.com/, create two android apps with application IDs ru.tusco.messenger and ru.tusco.messenger.beta, turn on firebase messaging and download google-services.json, which should be copied to the same folder as TMessagesProj.
5. Open the project in the Studio (note that it should be opened, NOT imported).
6. Fill out values in TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java – there’s a link for each of the variables showing where and which data to obtain.
7. You are ready to compile Dahl.

### Localization

We moved all translations to https://translations.telegram.org/en/android/. Please use it.
