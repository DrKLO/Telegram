buildscript {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.1.2")
        classpath("com.google.gms:google-services:4.3.5")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
    }
}

