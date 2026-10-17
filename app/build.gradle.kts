plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.ksp)
}

chaquopy {
    defaultConfig {
        // Python runtime bundled in the app; yt-dlp runs fully on-device.
        // Pinned to the version verified against YouTube on the build machine
        // (extraction + full-speed downloads with no JS runtime present).
        version = "3.10"
        pip {
            install("yt-dlp==2026.08.19")
        }
    }
}

android {
    namespace = "com.fetchly.app"
    compileSdk = 35
    // Pin the installed Build-Tools (AGP 8.5.2 would otherwise want 34.0.0).
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.fetchly.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 20
        versionName = "2.0"

        // 64-bit only: ffmpeg-kit publishes no 32-bit builds, and without the
        // merge engine a 32-bit device could not download any video at all.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.splash)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.workmanager)
    implementation(libs.datastore)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)

    // On-device merge engine (LGPL-3.0): yt-dlp downloads streams, this muxes them.
    implementation(libs.ffmpeg.kit.full)

    testImplementation(libs.junit)
    testImplementation(libs.json.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
}
