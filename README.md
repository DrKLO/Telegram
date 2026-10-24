# Kite для Android

Kite сделан на основе [официального исходного кода Telegram для Android](https://github.com/DrKLO/Telegram)
(лицензия GPL v2). Изменения этого форка от upstream:

- своё имя приложения **Kite** и свой `applicationId` `io.github.garricho.kite`
  (отладочная сборка: `io.github.garricho.kite.beta`), поэтому Kite ставится рядом с официальным Telegram;
- `api_id`/`api_hash` берутся из `local.properties`, в git их нет;
- отключены проверка обновлений официального приложения, Google Play Billing и passkeys
  (они работают только для официальных ID);
- свой тип системного аккаунта для синхронизации контактов.

## Сборка на Ubuntu

0. Установите JDK 17 (нужен именно JDK, а не только JRE):

   ```bash
   sudo apt install -y openjdk-17-jdk
   echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc && source ~/.bashrc
   ```

1. Установите [Android Studio](https://developer.android.com/studio). В *SDK Manager* выберите:
   Android SDK Platform 36, Build-Tools 36.0.0, **NDK 27.2.12479018** и **CMake 3.22.1**
   (во вкладке *SDK Tools* включите *Show Package Details*, чтобы выбрать точную версию).
2. Склонируйте репозиторий **с подмодулями**:

   ```bash
   git clone --recursive --shallow-submodules https://github.com/Garricho/kite-android.git
   cd kite-android
   ```

   Если забыли `--recursive`, выполните: `git submodule update --init --recursive --depth=1`.
3. Создайте файл `local.properties` в корне проекта (он в `.gitignore`):

   ```properties
   sdk.dir=/home/ВАШ_ПОЛЬЗОВАТЕЛЬ/Android/Sdk
   TG_API_ID=12345678
   TG_API_HASH=0123456789abcdef0123456789abcdef
   # Необязательно: собирать нативный код только под ваш телефон (в разы быстрее)
   KITE_ABIS=arm64-v8a
   ```

4. Подключите телефон с включённой отладкой по USB и соберите отладочную версию:

   ```bash
   ./gradlew :TMessagesProj_App:installAfatDebug
   ```

   Первая сборка долгая (компилируется нативный код), дальше быстрее.
   Можно и из Android Studio: *Open* → папка проекта, конфигурация `TMessagesProj_App`, вариант `afatDebug`.

## Перед публикацией в Play Market

- своя иконка (официальный логотип использовать нельзя);
- свой ключ подписи `TMessagesProj/config/release.keystore` и пароли в `gradle.properties`;
- свой проект Firebase (`google-services.json`) для пуш-уведомлений;
- открытый исходный код вашей версии (требование GPL).

---

## Telegram messenger for Android

[Telegram](https://telegram.org) is a messaging app with a focus on speed and security. It’s superfast, simple and free.
This repo contains the official source code for [Telegram App for Android](https://play.google.com/store/apps/details?id=org.telegram.messenger).

## Creating your Telegram Application

We welcome all developers to use our API and source code to create applications on our platform.
There are several things we require from **all developers** for the moment.

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

You will require Android Studio 2025.1.4, Android NDK 27.2.12479018 and Android SDK 36.

1. Clone the Telegram source code with its submodules:
   ```bash
   git clone --recursive --shallow-submodules https://github.com/DrKLO/Telegram.git Telegram
   ```
   In case you forgot the `--recursive` flag, change to the `Telegram` directory and run:
   ```bash
   git submodule init && git submodule update --init --recursive --depth=1
   ```
2. Copy your release.keystore into TMessagesProj/config
3. Fill out RELEASE_KEY_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_STORE_PASSWORD in gradle.properties to access your  release.keystore
4.  Go to https://console.firebase.google.com/, create two android apps with application IDs org.telegram.messenger and org.telegram.messenger.beta, turn on firebase messaging and download google-services.json, which should be copied to the same folder as TMessagesProj.
5. Open the project in the Studio (note that it should be opened, NOT imported).
6. Fill out values in TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java – there’s a link for each of the variables showing where and which data to obtain.
7. You are ready to compile Telegram.

### Localization

We moved all translations to https://translations.telegram.org/en/android/. Please use it.
