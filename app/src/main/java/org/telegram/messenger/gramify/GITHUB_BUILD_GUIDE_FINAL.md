# GitHub Se Build Kaise Kare - Devgram Remastered by Dev

Haan, GitHub se hi build hoga, aapke repo `Devgram-Remastered-` me already workflow hai.

## Step 1: Aapke Repo Me Files Daalo

Aapke repo ka link: https://github.com/sex-toy-king/Devgram-Remastered-

1. GitHub pe jao, `Add file` -> `Upload files`
2. Ye 6 files upload karo `app/src/main/java/org/telegram/messenger/gramify/` folder me:
   - Devgram_Bot_10_Slots_Safe.java
   - DevgramBotPanelFinalSafe.java
   - DevgramBubbleService.java (aapka wala clean)
   - Devgram_Final_RemasteredByDev_PinLock.java
   - GramifyBubble_FIXED.java
   - Onboarding_Channel_Group.java

Agar `gramify` folder nahi hai to banao.

## Step 2: AndroidManifest.xml Update Karo

`app/src/main/AndroidManifest.xml` me ye add karo `<application>` ke andar:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.INTERNET" />

<service
    android:name=".gramify.DevgramBubbleService"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

## Step 3: App Name Change - Remastered by Dev

`app/src/main/res/values/strings.xml` me:

```xml
<string name="AppName">Devgram Remastered by Dev</string>
```

## Step 4: GitHub Actions Workflow - Auto APK Build

Aapke repo me already `.github/workflows/build-apk.yml` hai, usko isse replace karo:

```yaml
name: Build Devgram Remastered by Dev APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Setup NDK
        run: |
          sdkmanager --install "ndk;25.1.8937393"
          echo "ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.1.8937393" >> $GITHUB_ENV

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build APK
        run: ./gradlew assembleAfatRelease --no-daemon

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: Devgram-Remastered-by-Dev-APK
          path: app/build/outputs/apk/**/app-*.apk
```

## Step 5: Push Karo Aur Build Dekho

1. GitHub pe `Commit changes` karo
2. Upar `Actions` tab pe jao
3. `Build Devgram Remastered by Dev APK` workflow chal raha hoga
4. 10-15 min me complete hoga
5. Neeche `Artifacts` me `Devgram-Remastered-by-Dev-APK` milega, download karo

## Local Build (Android Studio)

Agar Android Studio se karna hai:

1. Android Studio me project open karo
2. `Build` -> `Generate Signed Bundle/APK` -> `APK` -> `Release`
3. APK `app/build/outputs/apk/` me milega

## Important Notes

- Package name change karna hai to `com.devgram.remasteredbydev` rakho
- Telegram ka api_id/api_hash `BuildVars.java` me dalna padega (my.telegram.org se)
- Pin Lock 56530 hai, har jagah Remastered by Dev branding hai

Done! GitHub se auto build ho jayega. 🫍
