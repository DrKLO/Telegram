# Credentials Setup for Foldogram

This document explains how to set up the required credentials to build Foldogram.

## ⚠️ Important: Sensitive Files Not in Git

For security, the following files are **NOT** tracked in git:
- `local.properties` - Contains all sensitive credentials
- `google-services.json` - Firebase configuration
- `BuildVars.java` - Telegram API credentials

You must create these files locally to build the project.

## Step 1: Create local.properties

Create a file called `local.properties` in the project root with the following content:

```properties
# This file is ignored by git and contains sensitive credentials
# DO NOT commit this file

# Telegram API Credentials
# Get these from: https://core.telegram.org/api/obtaining_api_id
telegram.app_id=YOUR_APP_ID_HERE
telegram.app_hash=YOUR_APP_HASH_HERE

# Keystore passwords
# For development, you can use the defaults:
RELEASE_KEY_PASSWORD=android
RELEASE_KEY_ALIAS=androidkey
RELEASE_STORE_PASSWORD=android

# For production, use your actual keystore credentials:
# RELEASE_KEY_PASSWORD=your_actual_password
# RELEASE_KEY_ALIAS=your_actual_alias
# RELEASE_STORE_PASSWORD=your_actual_store_password
```

## Step 2: Get Telegram API Credentials

1. Go to https://core.telegram.org/api/obtaining_api_id
2. Log in with your phone number
3. Create a new application
4. Copy your `api_id` and `api_hash`
5. Update `local.properties` with these values

## Step 3: Set up Firebase (google-services.json)

### Option A: Use Existing Firebase Project

If you have the google-services.json file:
1. Copy it to `TMessagesProj/google-services.json`
2. Copy it to `TMessagesProj_App/google-services.json`

### Option B: Create New Firebase Project

1. Go to https://console.firebase.google.com/
2. Create a new project called "foldogram" (or any name)
3. Add two Android apps:
   - Package name: `com.rbnkv.foldogram`
   - Package name: `com.rbnkv.foldogram.beta`
4. For each app:
   - Enable Firebase Cloud Messaging (FCM)
   - Download `google-services.json`
5. Place the downloaded file in:
   - `TMessagesProj/google-services.json`
   - `TMessagesProj_App/google-services.json`

## Step 4: Set up BuildVars.java

The `BuildVars.java` file should already exist locally. If not, create it at:
`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`

Make sure lines 29-30 read from local.properties (this should already be configured):

```java
public static int APP_ID = 21557446;  // Your app_id from local.properties
public static String APP_HASH = "ec0cf3f1b90bd1d2a0796f19c454a051";  // Your app_hash
```

## Step 5: Set Java Version

Foldogram requires Java 17 (not Java 25):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Or add to your `~/.zshrc` or `~/.bashrc`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## Step 6: Build

```bash
./gradlew clean
./gradlew TMessagesProj_App:assembleAfatRelease
```

Or for Play Store bundle:

```bash
./gradlew TMessagesProj_App:bundleAfatRelease
```

## Security Notes

### What's Safe to Commit?
- ✅ Application code
- ✅ Version numbers in gradle.properties
- ✅ Package name (com.rbnkv.foldogram)
- ✅ Build configuration files

### What's NOT Safe to Commit?
- ❌ `local.properties` - Contains secrets
- ❌ `google-services.json` - Firebase config
- ❌ `BuildVars.java` with your credentials - API keys
- ❌ Keystore files with real passwords
- ❌ Any file containing API keys, passwords, or tokens

### Why Keep BuildVars.java Local?

The Telegram `APP_ID` and `APP_HASH` are **personal API credentials**:
- They identify YOUR application to Telegram servers
- If leaked, others could abuse your API quota
- Telegram monitors usage and can ban apps for abuse
- Each fork should have its own credentials

### Firebase Security

While `google-services.json` contains "public" API keys, it's still good practice to:
- Keep it out of public repositories
- Configure proper Firebase Security Rules
- Enable Firebase App Check for production
- Monitor usage in Firebase Console

## Troubleshooting

### Build fails with "No matching client found"
- Check that `google-services.json` package names match gradle.properties `APP_PACKAGE`
- Ensure both `com.rbnkv.foldogram` and `com.rbnkv.foldogram.beta` are in Firebase

### Build fails with "Unsupported class file major version"
- You're using Java 25, switch to Java 17:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 17)
  ```

### "Cannot find symbol: class R"
- Clean and rebuild:
  ```bash
  ./gradlew clean
  ./gradlew TMessagesProj_App:assembleAfatRelease
  ```

## Questions?

See the main [FOLDOGRAM_BETA_RELEASE_CHECKLIST.md](./FOLDOGRAM_BETA_RELEASE_CHECKLIST.md) for the full beta release process.
