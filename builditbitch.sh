#!/bin/bash

#preparing
mkdir -p /home/source/TMessagesProj/build/outputs/apk && mkdir -p /home/source/TMessagesProj/build/outputs/native-debug-symbols && cp -R /home/source/. /home/gradle

#comment this block to use default keys
stat /home/gradle/TMessagesProj/config/release.keystore
mv /home/gradle/TMessagesProj/config/release.keystore /home/gradle/TMessagesProj/config/release.keystore.old
echo $GRAHER_KEYSTORE_BASE64 | base64 --decode >/home/gradle/TMessagesProj/config/release.keystore
echo "RELEASE_KEY_ALIAS $RELEASE_KEY_ALIAS"
stat /home/gradle/TMessagesProj/config/release.keystore
cp /home/gradle/gradle.properties /home/gradle/gradle.properties.old
sed -i 's/RELEASE_STORE_PASSWORD=android/RELEASE_STORE_PASSWORD=$GRAHER_RELEASE_STORE_PASSWORD/g' /home/gradle/gradle.properties
sed -i 's/RELEASE_KEY_ALIAS=android/RELEASE_KEY_ALIAS=$GRAHER_RELEASE_KEY_ALIAS/g' /home/gradle/gradle.properties
sed -i 's/RELEASE_KEY_PASSWORD=android/RELEASE_KEY_PASSWORD=$GRAHER_RELEASE_KEY_PASSWORD/g' /home/gradle/gradle.properties
#comment this block to use default keys

#building a bundle
cd /home/gradle && gradle assembleBundleRelease

#comment this block to use default keys
rm /home/gradle/TMessagesProj/config/release.keystore
mv /home/gradle/TMessagesProj/config/release.keystore.old /home/gradle/TMessagesProj/config/release.keystore
rm /home/gradle/gradle.properties
mv /home/gradle/gradle.properties.old /home/gradle/gradle.properties
#comment this block to use default keys

#moving back
cp -R /home/gradle/TMessagesProj/build/outputs/apk/. /home/source/TMessagesProj/build/outputs/apk && cp -R /home/gradle/TMessagesProj/build/outputs/native-debug-symbols/. /home/source/TMessagesProj/build/outputs/native-debug-symbols

exit 0
