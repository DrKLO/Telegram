## Solid is Telegram messenger for Android focused on simplicity, beacuse simplicity is beauty

[Telegram](https://telegram.org) is a messaging app with a focus on speed and security. It’s superfast, simple and free.
This repo contains the official forked and reworked source code for [Telegram App for Android](https://play.google.com/store/apps/details?id=org.telegram.messenger).

### API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTproto protocol manuals: https://core.telegram.org/mtproto

TG: https://t.me/+uXCIuarCi_RmMDVi

### Compilation Guide

In order to support [reproducible builds](https://core.telegram.org/reproducible-builds), this repo contains dummy release.keystore,  google-services.json and filled variables inside BuildVars.java. Before publishing your own APKs please make sure to replace all these files with your own.

You will require Android Studio 3.4, Android NDK rev. 20 and Android SDK 8.1
