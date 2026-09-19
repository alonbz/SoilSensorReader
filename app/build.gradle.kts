import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildTimestamp: String = SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date())

android {
    namespace = "com.agsense.soilsensor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.agsense.soilsensor"
        minSdk = 26
        targetSdk = 36
        versionCode = 24
        versionName = "1.9.0"

        buildConfigField("String", "BUILD_DATE", "\"$buildTimestamp\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Release signing for Google Play. Values come from environment variables (GitHub Secrets);
    // when they are missing (e.g. a local build) the release build is left unsigned.
    val releaseKeystore = System.getenv("KEYSTORE_FILE")
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

    // Handles CH340 / CP210x / FTDI / PL2303 / CDC-ACM USB-serial chips automatically
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")

    // Chart for the sensor history screen
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}
