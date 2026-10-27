plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.alter.autopilot"; compileSdk = 34
    defaultConfig { applicationId = "com.alter.autopilot"; minSdk = 26; targetSdk = 34; versionCode = 3; versionName = "0.3" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
