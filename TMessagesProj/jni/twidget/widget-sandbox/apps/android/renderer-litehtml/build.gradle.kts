// litehtml renderer prototype: the C++ core in renderer-litehtml/ (repo root, shared with the iOS/macOS demo) built
// by the NDK's CMake through externalNativeBuild, plus the Kotlin Renderer (fonts, text metrics and the display-list
// painter on android.graphics) that WidgetHost plugs in. Not part of the product library; see docs/litehtml-renderer.md.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.telegram.widgetsandbox.litehtml"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.telegram.widgetsandbox:widget-sandbox")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
}
