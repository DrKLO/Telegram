import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The frozen native renderer prototypes (gradle.properties, tw.nativeRenderers): their modules and the demo's table of
// them (src/nativeRenderers) only when asked for; otherwise src/noNativeRenderers says there are none.
val nativeRenderers = providers.gradleProperty("tw.nativeRenderers").orNull == "true"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.telegram.widgetsandbox.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.telegram.widgetsandbox.demo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main").java.srcDir(if (nativeRenderers) "src/nativeRenderers/java" else "src/noNativeRenderers/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.telegram.widgetsandbox:widget-sandbox")
    if (nativeRenderers) {
        implementation(project(":renderer-blitz"))
        implementation(project(":renderer-litehtml"))
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
