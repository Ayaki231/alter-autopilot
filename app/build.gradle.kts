plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    buildFeatures {
        buildConfig = true
    }

    val keystorePath = System.getenv("ALTER_KEYSTORE_PATH")
    val hasValidKeystore = keystorePath != null && file(keystorePath).exists()

    if (hasValidKeystore) {
        signingConfigs {
            create("alterRelease") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("ALTER_STORE_PASSWORD")
                keyAlias = System.getenv("ALTER_KEY_ALIAS")
                keyPassword = System.getenv("ALTER_KEY_PASSWORD")
            }
        }
    }

    namespace = "com.alter.autopilot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alter.autopilot"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "0.21"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        getByName("release") {
            if (hasValidKeystore) {
                signingConfig = signingConfigs.getByName("alterRelease")
            }
            isMinifyEnabled = false
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
    implementation("com.google.mediapipe:tasks-vision:0.10.32")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
}
