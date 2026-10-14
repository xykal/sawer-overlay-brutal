plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.saweroverlay.live"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.saweroverlay.live"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "14.0-clean-full-edit-bubble-v1v2"

        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("sawer-release.keystore")
            storePassword = "SawerBrutal2024!"
            keyAlias = "sawerstudio"
            keyPassword = "SawerBrutal2024!"
            // v1+v2+v3 signing enabled — verifikasi tanda tangan lengkap
            isV1SigningEnabled = true
            isV2SigningEnabled = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.webkit:webkit:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
}
