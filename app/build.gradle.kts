plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bimob.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nagaplas.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.00727"\n        buildConfigField("String", "VERSION_LABEL", "\\\"v0.00727\\\"")
        externalNativeBuild {
            cmake { arguments += "-DBIMOB_NATIVE_BUILD_TESTS=OFF" }
        }
    }

    signingConfigs {
        create("ciDebug") {
            storeFile = rootProject.file("ci/bimob-debug.keystore")
            storePassword = "android"
            keyAlias = "bimob-debug"
            keyPassword = "android"
            storeType = "JKS"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("ciDebug")
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    externalNativeBuild {
        cmake {
            path = file("../native-core/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
