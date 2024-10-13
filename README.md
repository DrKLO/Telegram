# Telegram Messenger for Android

Welcome to the official source code repository of [Telegram for Android](https://play.google.com/store/apps/details?id=org.telegram.messenger). Telegram is a cloud-based messaging app with a focus on speed and security. It’s free, simple, and superfast, offering a wide range of features to its users.

This repository allows developers to build and contribute to Telegram for Android by leveraging our source code.

---

## Getting Started with Your Own Telegram Application

We encourage developers to use our API and source code to build apps on the Telegram platform. However, there are several **important guidelines** that **all developers** need to follow when creating their Telegram-based applications:

### Essential Requirements for Developers:

1. **Obtain Your Own API ID**  
   Each app interacting with Telegram’s platform must have a unique API ID. Obtain yours by following the instructions [here](https://core.telegram.org/api/obtaining_api_id).

2. **Avoid Using the Name "Telegram"**  
   Please choose a unique name for your application. Using "Telegram" is prohibited, as it may confuse users. Be sure your users understand that your app is unofficial.

3. **Create a Unique Logo**  
   Do not use Telegram's official logo (white paper plane in a blue circle). Design your own logo to differentiate your app.

4. **Follow Security Guidelines**  
   Protect your users' privacy and data. Make sure to read and implement the [**security guidelines**](https://core.telegram.org/mtproto/security_guidelines) for handling user information securely.

5. **Publish Your Code**  
   In compliance with open-source licensing, if you modify the Telegram source code, you must publish your changes. This helps foster transparency and collaboration within the community.

---

## Documentation for API and Protocol

- Access the **Telegram API** documentation: [Telegram API Documentation](https://core.telegram.org/api)
- Learn more about the **MTProto Protocol**: [MTProto Protocol Documentation](https://core.telegram.org/mtproto)

These resources provide in-depth information about working with Telegram’s backend and communication protocols.

---

## Build and Compilation Guide

If you're planning to compile and build your own version of Telegram for Android, please follow the steps below. It's important to ensure that all required dependencies and configurations are set up correctly.

### Prerequisites

Before you begin, ensure that the following tools and dependencies are installed:

- **Android Studio** (version 3.4 or higher)
- **Android NDK** (revision 20 or later)
- **Android SDK** (version 8.1, API level 27 or later)

### Step-by-Step Compilation Instructions

1. **Clone the Repository**  
   First, download the Telegram source code to your local machine using Git:  
   ```bash
   git clone https://github.com/DrKLO/Telegram.git
   ```

2. **Set Up Your Keystore**  
   Place your `release.keystore` file in the following directory:  
   ```bash
   TMessagesProj/config
   ```

3. **Configure Gradle Properties**  
   Open the `gradle.properties` file and input your keystore credentials:
   - `RELEASE_KEY_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_STORE_PASSWORD`

4. **Configure Firebase**  
   To enable Firebase services, follow these steps:
   - Go to the [Firebase Console](https://console.firebase.google.com/).
   - Create two Android apps with the following IDs:
     - `org.telegram.messenger`
     - `org.telegram.messenger.beta`
   - Enable Firebase Cloud Messaging.
   - Download the `google-services.json` file and place it in the `TMessagesProj` directory.

5. **Open the Project in Android Studio**  
   Launch Android Studio and select **Open** (not Import) to open the project.

6. **Configure Build Variables**  
   In `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`, fill in the necessary values. Each variable has a corresponding link that explains how to obtain the required data.

7. **Compile Your APK**  
   Once all configurations are complete, you can compile and build the APK by clicking the **Run** button in Android Studio.

---

## Supporting Reproducible Builds

Telegram supports [Reproducible Builds](https://core.telegram.org/reproducible-builds) to ensure the integrity of the source code. This repository includes dummy files such as `release.keystore` and `google-services.json`. Before distributing your version of Telegram, ensure that these files are replaced with your own.

---

## Localization

We’ve centralized all language translations for Telegram for Android on our [Translation Platform](https://translations.telegram.org/en/android/). If you want to contribute by translating Telegram into your language, please use this platform to get started.

---

By following these instructions and adhering to the guidelines, you will be able to build, customize, and publish your own version of Telegram for Android. We are excited to see how you contribute to the Telegram ecosystem!
