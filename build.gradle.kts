plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.toibot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.toibot"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "4.5-toibot-auto-apk"
    }

    buildTypes {
        release {
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
