import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: one fixed key, so each new APK installs over the old one.
// In CI it comes from the repo's secrets (see .github/workflows/release.yml);
// on your computer from ~/.config/ladrok/remote-signing.properties (or LADROK_SIGNING=<file>).
val signing = Properties().apply {
    val file = System.getenv("LADROK_SIGNING")?.let(::file)
        ?: file("${System.getProperty("user.home")}/.config/ladrok/remote-signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    System.getenv("LADROK_KEYSTORE")?.let { setProperty("storeFile", it) }
    System.getenv("LADROK_KEYSTORE_PASSWORD")?.let { setProperty("storePassword", it); setProperty("keyPassword", it) }
}
// CI sets these from the tag (v1.2.0) and the run number, so every release is an update.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val version = System.getenv("LADROK_VERSION") ?: "0.0.0-dev"

android {
    namespace = "tv.ladrok.remote"
    compileSdk = 37

    defaultConfig {
        applicationId = "tv.ladrok.remote"
        minSdk = 31 // Android 12: the Bluetooth runtime permissions, and the HID device profile (28+)
        targetSdk = 37
        versionCode = buildNumber
        versionName = version
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias") ?: "ladrok-remote"
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
