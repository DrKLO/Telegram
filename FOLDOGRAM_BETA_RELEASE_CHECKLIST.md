# Foldogram Beta Release Checklist

## ✅ Completed Steps

- [x] Created combined branch with both PRs:
  - PR #1934: Fix fragment destruction when multiple LaunchActivity instances exist
  - PR #1926: Fix tablet layout on fold/unfold
- [x] Updated version to 12.4.2-beta (6511)
- [x] Changed package name to `com.rbnkv.foldogram`
- [x] Changed app name to "Foldogram" / "Foldogram Beta"
- [x] Custom API credentials already configured (APP_ID: 21557446)
- [x] Release keystore in place

## 🎨 Pending: App Icons

### Generate Icons
Use AI image generators (DALL-E, Midjourney, etc.) with the prompts you provided:
- Light mode variant (soft blue gradient background)
- Dark mode variant (deep navy gradient background)

### Icon Specifications Needed

1. **Adaptive Icon (Android 8.0+)**
   - Foreground layer: 1024x1024 PNG (icon safe area: 432x432 center)
   - Background layer: 1024x1024 PNG (solid color or gradient)
   - Create `ic_launcher_foreground.xml` and `ic_launcher_background.xml`

2. **Legacy Icons (all sizes)**
   ```
   TMessagesProj/src/main/res/
   ├── mipmap-mdpi/ic_launcher.png       (48x48)
   ├── mipmap-hdpi/ic_launcher.png       (72x72)
   ├── mipmap-xhdpi/ic_launcher.png      (96x96)
   ├── mipmap-xxhdpi/ic_launcher.png     (144x144)
   └── mipmap-xxxhdpi/ic_launcher.png    (192x192)
   ```

3. **Round Icons (for launchers that support it)**
   - Same sizes as above, but in `ic_launcher_round.png`

### Tools for Icon Generation
- **Android Asset Studio**: https://romannurik.github.io/AndroidAssetStudio/
  - Upload your 1024x1024 icon
  - Automatically generates all sizes
  - Creates adaptive icon resources

## 📱 Firebase Setup

### Create Firebase Project
1. Go to: https://console.firebase.google.com/
2. Create new project or use existing

### Add Android Apps
Create **two** Android apps in Firebase:
- **Production**: `com.rbnkv.foldogram`
- **Beta**: `com.rbnkv.foldogram.beta`

### Enable Firebase Cloud Messaging
1. In each app, enable Firebase Cloud Messaging (FCM)
2. Download `google-services.json` for the production app
3. Replace: `TMessagesProj/google-services.json`

## 🔑 Signing Configuration

### Verify Keystore Passwords
Check that these are set in `gradle.properties`:
```properties
RELEASE_KEY_PASSWORD=<your_password>
RELEASE_KEY_ALIAS=<your_alias>
RELEASE_STORE_PASSWORD=<your_store_password>
```

## 🏗️ Build Release Bundle

Once icons and Firebase are configured:

```bash
# Clean previous builds
./gradlew clean

# Build release bundle
./gradlew TMessagesProj_App:bundleAfatRelease
```

Output will be at:
```
TMessagesProj_App/build/outputs/bundle/afatRelease/TMessagesProj_App-afat-release.aab
```

## 📤 Play Store Beta Publishing

### 1. Create Play Console Account
- Sign up at: https://play.google.com/console/signup
- Pay one-time $25 registration fee

### 2. Create App
1. Click "Create app"
2. App name: **Foldogram**
3. Default language: English (United States)
4. App type: Application
5. Category: Communication

### 3. Complete Store Listing
Required assets:
- **App icon**: 512x512 PNG
- **Feature graphic**: 1024x500 PNG
- **Phone screenshots**: At least 2 (min 320px on shortest side)
- **Tablet screenshots**: At least 2 (7-inch and 10-inch)
- **Foldable screenshots**: Recommended (unfolded and folded states)
- **Short description**: Max 80 characters
- **Full description**: Max 4000 characters

Example descriptions:
```
Short: Telegram client optimized for foldable devices with enhanced tablet layout
Full: Foldogram is a fork of Telegram Messenger specifically designed for foldable
      devices. Enjoy seamless transitions between folded and unfolded states,
      enhanced tablet layouts, and all the features you love from Telegram.
```

### 4. Content Rating
Complete the questionnaire for IARC rating

### 5. Set Up Beta Track
1. Go to **Testing > Internal testing** or **Closed testing**
2. Create new release
3. Upload your `.aab` file
4. Add release notes
5. Review and rollout

### 6. Add Beta Testers
- **Internal testing**: Add by email (up to 100 testers)
- **Closed testing**: Create list of testers or use Google Groups
- **Open testing**: Anyone can join

### 7. Privacy Policy
⚠️ **Required for communication apps**

You need to provide a privacy policy URL. Create a simple page covering:
- What data you collect
- How you use it
- Data retention
- User rights

Host it on GitHub Pages, your website, or use a privacy policy generator.

## 🚀 Beta Testing Flow

1. Upload AAB to beta track
2. Add testers (by email or Google Group)
3. Testers receive email invitation
4. They click opt-in link
5. App appears in Play Store for them
6. They install and test
7. Collect feedback
8. Iterate!

## ⚠️ Important Notes

### Telegram Fork Requirements (from README.md)
- ✅ Own API credentials (already have)
- ✅ Different package name (com.rbnkv.foldogram)
- ✅ Different app name (Foldogram)
- ⚠️ Need different icon (pending)
- ✅ Must publish source code (your GitHub fork)

### GPL License Compliance
Since you're forking GPL-licensed code:
1. Keep your fork public on GitHub
2. Include LICENSE file
3. Document your changes
4. Maintain copyright notices

### Play Store Specific
- First review may take 3-7 days
- Beta updates typically faster (hours to 1 day)
- Monitor the Play Console for any policy issues

## 📋 Pre-Launch Checklist

Before uploading to Play Store:
- [ ] Icons replaced with Foldogram branding
- [ ] Firebase configured
- [ ] google-services.json updated
- [ ] Keystore passwords correct
- [ ] Clean build successful
- [ ] AAB generated
- [ ] Tested locally on at least one device
- [ ] Privacy policy published
- [ ] Store listing assets prepared
- [ ] Beta testers list ready

## 🔗 Useful Links

- Firebase Console: https://console.firebase.google.com/
- Play Console: https://play.google.com/console/
- Android Asset Studio: https://romannurik.github.io/AndroidAssetStudio/
- Material Design Icons: https://fonts.google.com/icons
- Telegram API Docs: https://core.telegram.org/api

---

**Current Status**: Ready to build once icons and Firebase are configured
**Next Step**: Generate app icons and update Firebase configuration
