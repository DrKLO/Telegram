// Blitz renderer prototype: the Rust crate in renderer-blitz/ (repo root, shared with the iOS/macOS demo) built with cargo-ndk into
// src/main/jniLibs, plus the Kotlin Renderer that WidgetHost plugs in.
// Not part of the product library; see docs/blitz-experiment.md.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.telegram.widgetsandbox.blitz"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
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

// cargo-ndk needs rustup's cargo (Homebrew's has no Android targets) and the NDK the project pins.
val cargoNdk = tasks.register<Exec>("cargoNdk") {
    val home = System.getProperty("user.home")
    val sdk = System.getenv("ANDROID_HOME") ?: "$home/Library/Android/sdk"
    workingDir = file("../../renderer-blitz")
    environment("PATH", "/opt/homebrew/opt/rustup/bin:$home/.cargo/bin:" + System.getenv("PATH"))
    environment("ANDROID_NDK_HOME", "$sdk/ndk/28.2.13676358")
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-o", file("src/main/jniLibs").path, "build", "--release")
    inputs.dir(file("../../renderer-blitz/src"))
    inputs.file(file("../../renderer-blitz/Cargo.toml"))
    outputs.dir(file("src/main/jniLibs"))
}
tasks.named("preBuild") { dependsOn(cargoNdk) }

dependencies {
    implementation("org.telegram.widgetsandbox:widget-sandbox")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
}
