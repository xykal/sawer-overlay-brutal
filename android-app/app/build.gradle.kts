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
        versionCode = 12
        versionName = "12.0-teks-ai-elements-melayang"

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
            // Jika di CI, bisa pakai env variable
            // storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "SawerBrutal2024!"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // R8 aktif — bikin APK lebih kecil & optimasi
            isShrinkResources = true // Hapus resource gak kepake
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
