pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        // One catalog for both builds: the framework's.
        create("libs") { from(files("../../packages/android/gradle/libs.versions.toml")) }
    }
}

// The demo and, with -Ptw.nativeRenderers=true (gradle.properties), the frozen native renderer modules. The framework (org.telegram.widgetsandbox:widget-sandbox)
// comes from packages/android as an included build: Gradle substitutes the coordinates with that
// project, so a change there rebuilds the demo without publishing. A client outside this repo
// takes the same coordinates from Maven instead.
includeBuild("../../packages/android")

rootProject.name = "widgetsandbox-apps"
include(":demo")
if (providers.gradleProperty("tw.nativeRenderers").orNull == "true") include(":renderer-blitz", ":renderer-litehtml")
