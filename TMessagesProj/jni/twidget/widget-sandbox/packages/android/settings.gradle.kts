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
}

// The framework build: the widget-sandbox library alone. The demo and the native
// renderer modules live in apps/android and consume it through includeBuild.
rootProject.name = "widgetsandbox"
include(":widget-sandbox")
