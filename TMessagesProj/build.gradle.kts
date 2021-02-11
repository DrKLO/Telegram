plugins {
    id("com.android.application")
}

repositories {
    mavenCentral()
    google()
    jcenter()
}

configurations {
    compile {
        exclude( module = "support-v4")
    }
}

configurations.all {
    exclude(group = "com.google.firebase", module = "firebase-core")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
}

dependencies {
    implementation("androidx.core:core:1.3.2")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.3.2")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.sharetarget:sharetarget:1.1.0")

    compileOnly("org.checkerframework:checker-qual:2.5.2")
    compileOnly("org.checkerframework:checker-compat-qual:2.5.0")
    implementation("com.google.firebase:firebase-messaging:21.0.1")
    implementation("com.google.firebase:firebase-config:20.0.2")
    implementation("com.google.firebase:firebase-datatransport:17.0.10")
    implementation("com.google.firebase:firebase-appindexing:19.1.0")
    implementation("com.google.android.gms:play-services-maps:17.0.0")
    implementation("com.google.android.gms:play-services-auth:19.0.0")
    implementation("com.google.android.gms:play-services-vision:16.2.0")
    implementation("com.google.android.gms:play-services-wearable:17.0.0")
    implementation("com.google.android.gms:play-services-location:17.1.0")
    implementation("com.google.android.gms:play-services-wallet:18.1.2")
    implementation("com.microsoft.appcenter:appcenter-distribute:3.3.1")
    implementation("com.microsoft.appcenter:appcenter-crashes:3.3.1")
    implementation("com.googlecode.mp4parser:isoparser:1.0.6")
    implementation("com.stripe:stripe-android:2.0.2")
    implementation(files("libs/libgsaverification-client.aar"))

    coreLibraryDesugaring ("com.android.tools:desugar_jdk_libs:1.1.1")
}

android {
    compileSdkVersion(30)
    buildToolsVersion = "30.0.3"
    ndkVersion = "21.1.6352462"

    defaultConfig.applicationId = "org.telegram.messenger"

    externalNativeBuild {
        cmake {
            path("jni/CMakeLists.txt")
        }
    }

    lintOptions {
        disable("MissingTranslation")
        disable("ExtraTranslation")
        disable("BlockedPrivateApi")
    }

    dexOptions {
        jumboMode = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    val RELEASE_KEY_ALIAS: String by project
    val RELEASE_KEY_PASSWORD: String by project
    val RELEASE_STORE_PASSWORD:String by project

    signingConfigs {
        create("debug1") {
            storeFile = file("config/release.keystore")
            storePassword = RELEASE_STORE_PASSWORD
            keyAlias = RELEASE_KEY_ALIAS
            keyPassword = RELEASE_KEY_PASSWORD
        }

        create("release") {
            storeFile = file("config/release.keystore")
            storePassword = RELEASE_STORE_PASSWORD
            keyAlias = RELEASE_KEY_ALIAS
            keyPassword = RELEASE_KEY_PASSWORD
        }
    }

    buildTypes {
        getByName("debug") {
            debuggable(true)
            jniDebuggable(true)
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".beta"
            minifyEnabled(false)
            isShrinkResources = false
            multiDexEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            ndk.debugSymbolLevel = "FULL"
        }

        /*debugAsan {
            debuggable true
            jniDebuggable true
            signingConfig signingConfigs.debug
            applicationIdSuffix ".beta"
            minifyEnabled true
            multiDexEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            packagingOptions {
                doNotStrip "**.so"
            }
            sourceSets {
                main {
                    jniLibs {
                        srcDir {
                            'jniLibs'
                        }
                    }
                    resources {
                        srcDir {
                            'jniRes'
                        }
                    }
                }
            }
        }*/

        create("HA"){
            debuggable(false)
            jniDebuggable(false)
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".beta"
            isMinifyEnabled = true
            multiDexEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            ndk.debugSymbolLevel = "FULL"
        }

        getByName("release") {
            debuggable(false)
            jniDebuggable(false)
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = false
            multiDexEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            ndk.debugSymbolLevel = "FULL"
        }
    }

    sourceSets.getByName("debug") {
        manifest.srcFile("config/debug/AndroidManifest.xml")
    }

    sourceSets.getByName("HA") {
        manifest.srcFile("config/debug/AndroidManifest.xml")
    }

    sourceSets.getByName("release") {
        manifest.srcFile("config/release/AndroidManifest.xml")
    }

    flavorDimensions("minApi")

    productFlavors {
        create("armv7") {
            ndk {
                abiFilters.add("armeabi-v7a")
            }
            ext {
                versionCode = 1
            }
        }
        create("x86") {
            ndk {
                abiFilters.add("x86")
            }
            ext {
                versionCode = 2
            }
        }
        create("armv7_SDK23") {
            ndk {
                abiFilters.add("armeabi-v7a")
            }
            sourceSets.getByName("debug") {
                manifest.srcFile("config/debug/AndroidManifest_SDK23.xml")
            }
            sourceSets.getByName("release") {
                manifest.srcFile("config/release/AndroidManifest_SDK23.xml")
            }
            minSdkVersion(23)
            ext {
                versionCode = 3
            }
        }
        create("x86_SDK23") {
            ndk {
                abiFilters.add("x86")
            }
            sourceSets.getByName("debug") {
                manifest.srcFile("config/debug/AndroidManifest_SDK23.xml")
            }
            sourceSets.getByName("release") {
                manifest.srcFile("config/release/AndroidManifest_SDK23.xml")
            }
            minSdkVersion(23)
            ext {
                versionCode = 4
            }
        }
        create("arm64") {
            ndk {
                abiFilters.add("arm64-v8a")
            }
            ext {
                versionCode = 5
            }
        }
        create("x64") {
            ndk {
                abiFilters.add("x86_64")
            }
            ext {
                versionCode = 6
            }
        }
        create("arm64_SDK23") {
            ndk {
                abiFilters.add("arm64-v8a")
            }
            sourceSets.getByName("debug") {
                manifest.srcFile("config/debug/AndroidManifest_SDK23.xml")
            }
            sourceSets.getByName("release") {
                manifest.srcFile("config/release/AndroidManifest_SDK23.xml")
            }
            minSdkVersion(23)
            ext {
                versionCode = 7
            }
        }
        create("x64_SDK23") {
            ndk {
                abiFilters.add("x86_64")
            }
            sourceSets.getByName("debug") {
                manifest.srcFile("config/debug/AndroidManifest_SDK23.xml")
            }
            sourceSets.getByName("release") {
                manifest.srcFile("config/release/AndroidManifest_SDK23.xml")
            }
            minSdkVersion(23)
            ext {
                versionCode = 8
            }
        }
        create("afat") {
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
            }
            sourceSets.getByName("debug") {
                manifest.srcFile("config/debug/AndroidManifest_SDK23.xml")
            }
            sourceSets.getByName("release") {
                manifest.srcFile("config/release/AndroidManifest_SDK23.xml")
            }
            ext {
                versionCode = 9
            }
        }
    }

    defaultConfig.versionCode = 2227

    applicationVariants.all {
        outputs.forEach { output ->
            check(output is com.android.build.gradle.internal.api.ApkVariantOutputImpl)

            output.versionCodeOverride = defaultConfig.versionCode?.times(10)!!.plus(productFlavors[0]?.versionCode!!)
            output.outputFileName = "app.apk"
        }
    }

    variantFilter = Action<com.android.build.api.variant.VariantFilter> {
        if (name != "release" && !name.contains("afat"))
           ignore = true
    }

    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(30)
        versionName = "7.4.2"


        vectorDrawables {
            generatedDensities("mdpi", "hdpi","xhdpi","xxhdpi")
        }

        externalNativeBuild {
            cmake {
                version = "3.10.2"
                arguments("-DANDROID_STL=c++_static", "-DANDROID_PLATFORM=android-16", "-j=16")
            }
        }
    }
}

plugins {
    id("com.google.gms.google-services")
}
