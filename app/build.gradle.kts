plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bimob.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bimob.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-alpha"
    }
}
