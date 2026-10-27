plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    signingConfigs {
        create("alterRelease") {
            val keyFile = System.getenv("ALTER_KEYSTORE_PATH")
            if (keyFile != null) {
                storeFile = file(keyFile)
                storePassword = System.getenv("ALTER_STORE_PASSWORD")
                keyAlias = System.getenv("ALTER_KEY_ALIAS")
                keyPassword = System.getenv("ALTER_KEY_PASSWORD")
            }
        }
    }
 namespace = "com.alter.autopilot"; compileSdk = 34
    defaultConfig { applicationId = "com.alter.autopilot"; minSdk = 26; targetSdk = 34; versionCode = 8; versionName = "0.8" }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("alterRelease")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}


dependencies {
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
}
