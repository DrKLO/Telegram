import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Maven coordinates: apps/android substitutes them with this project through
// includeBuild; a published AAR would carry the same ones.
group = "org.telegram.widgetsandbox"
version = "0.6.0"

android {
    namespace = "org.telegram.widgetsandbox"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                // Pure C: no STL. QuickJS is the fork submodule under packages/ios/Sources/CQuickJS.
                arguments += listOf("-DANDROID_STL=none")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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

    // Fixture tests written once for both runs: the JVM unit tests (src/test) and the instrumented ones (src/androidTest).
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The JVM fixture tests read src/androidTest/assets from disk: declared as an input, a fixture change alone reruns
// them instead of restoring the last result from the build cache.
tasks.withType<Test>().configureEach {
    inputs.dir("src/androidTest/assets").withPropertyName("fixtures").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.junit)
}
