## Unofficial Telegram messenger for Android

[Telegram](https://telegram.org) is a messaging app with a focus on speed and security. It’s superfast, simple and free.
This repo contains the source code for [Cloudveil Messenger App for Android](https://play.google.com/store/apps/details?id=org.cloudveil.messenger).

##Creating your Telegram Application

### API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTproto protocol manuals: https://core.telegram.org/mtproto

### Usage

First of all, take a look at **src/main/java/org/telegram/messenger/BuildVars.java** and fill it with correct values.
Import the root folder into your IDE (tested on Android Studio), then run project.

### Localization

We moved all translations to https://www.transifex.com/projects/p/telegram/. Please use it.

# Step-by-step guide

1. Install git
    ```
    apt-get install git
    ```
1. Clone project 
    ```
    git clone https://github.com/cloudveiltech/CloudVeilMessenger.git
    ```
1. Clone submodules
    ```
    git submodule update --init --recursive -- "TMessagesProj/jni/libtgvoip"
    ```
1. Or just 
    ```
    git clone --recursive https://github.com/cloudveiltech/CloudVeilMessenger.git
   ```
1. Create and download your `google-services.json` using firebase
    https://support.google.com/firebase/answer/7015592

    **NOTICE:** There's two packages in the project: one for release and one for debug (with .beta suffix). 
    So you have to create 2 apps in firebase: `your.package.name` and `your.package.name.beta`.
1. Place `google-services.json` into `TMessagesProj/`
1. Download android studio
https://developer.android.com/studio/index.html 
1. Create `keystore.properties` file and place it into `./TMessagesProj/../../` i.e. two levels higher.

    Contents of `keystore.properties` should look like:
    
    storeFileCloudVeil=path/to/jks/file.jks  
    storePasswordCloudVeil=store.password  
    keyAliasCloudVeil=key.alias  
    keyPasswordCloudVeil=key.password  
    ```
1. Open Android studio
1. Open project in Android studio.
1. Install SDK and NDK and all missing dependencies 
1. Probably don't agree with gradle update.
1. Set your own variables in ` TMessagesProj\src\main\java\org\telegram\messenger\BuildVars.java`
1. Press build.
1. Wait..
1. Should be all.

# Troubleshooting
## Windows users
If you build on Windows you probably want to set both of `LOCAL_SHORT_COMMANDS` and `APP_SHORT_COMMANDS` as the following.

   ```
    externalNativeBuild {     
        ndkBuild {
            arguments "NDK_APPLICATION_MK:=jni/Application.mk", "APP_PLATFORM:=android-14", "LOCAL_SHORT_COMMANDS=true", "APP_SHORT_COMMANDS=true"
            abiFilters "armeabi-v7a", "x86"
        }
    }
   ```
    	
It would help to overcome command length limit on windows.
https://stackoverflow.com/questions/12598933/ndk-build-createprocess-make-e-87-the-parameter-is-incorrect

1. Double check folder `TMessagesProj/jni/libtgvoip`. 
    It should have some files within. Triple check that file `TMessagesProj/jni/libtgvoip/audio/Resampler.cpp` exist. 
    If it's not just download submodule into `TMessagesProj/jni/libtgvoip`.
   
     ```
     git clone https://github.com/grishka/libtgvoip
     ```
     

1. If you caught some aapt errors, double check `res/values-ko/strings.xml` and replace `100%` to `100%%`.
1. Double check NDK revision. Project won't be built on r15. 
1. if you get  OOM from build system:

```
{"kind":"error","text":"java.lang.OutOfMemoryError: GC overhead limit exceeded","sources":[{}]}
```

add to Gradle build script:
```
dexOptions {
  javaMaxHeapSize "2g"
}
```

