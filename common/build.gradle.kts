plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.recomo.common"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        // sherpa-onnx AAR ships arm64-v8a, armeabi-v7a, x86, x86_64 — keep
        // arm64-v8a only so we don't bloat the APK with 50+ MB of unused ABIs.
        // Re-add x86_64 if we need emulator testing for voice.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // Compose (for preview composables)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Hilt — library-module usage for @Module declarations consumed by :app and :app-user
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    // Networking — Ktor WebSocket + HTTP
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // HTTP upload stack (Retrofit + OkHttp) — phone-moco cloud upload
    val retrofitVersion = "2.9.0"
    api("com.squareup.retrofit2:retrofit:$retrofitVersion")
    api("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // WebRTC
    implementation("io.github.webrtc-sdk:android:114.5735.10")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Filament for 3D trajectory preview
    implementation(files("../app/libs/filament-v1.67.0-android.aar"))
    implementation(files("../app/libs/filament-utils-v1.67.0-android.aar"))
    implementation(files("../app/libs/gltfio-v1.67.0-android.aar"))

    // sherpa-onnx — on-device Whisper STT (offline). Apache-2.0.
    // Used behind the VoiceRecognizer interface as an alternative to the
    // platform SpeechRecognizer (see chat/voice/). Ships arm64-v8a only.
    api(files("libs/sherpa-onnx-1.12.38.aar"))

    // ARCore (phone-moco optional pose prior; graceful fallback if unavailable at runtime)
    api("com.google.ar:core:1.40.0")

    // CameraX (phone-moco built-in device camera capture)
    val cameraxVersion = "1.3.1"
    api("androidx.camera:camera-core:$cameraxVersion")
    api("androidx.camera:camera-camera2:$cameraxVersion")
    api("androidx.camera:camera-lifecycle:$cameraxVersion")
    api("androidx.camera:camera-video:$cameraxVersion")
    api("androidx.camera:camera-view:$cameraxVersion")

    // Media3 ExoPlayer (phone-moco review playback)
    api("androidx.media3:media3-exoplayer:1.2.1")
    api("androidx.media3:media3-ui:1.2.1")
    api("androidx.media3:media3-common:1.2.1")

    // WorkManager (phone-moco background upload worker)
    api("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore (phone-moco capture settings persistence)
    api("androidx.datastore:datastore-preferences:1.0.0")

    // EncryptedSharedPreferences (phone-moco upload auth token storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Timber (phone-moco logging — matches :app v3dr code style)
    api("com.jakewharton.timber:timber:5.0.1")

    // UVCCamera for USB Video Class device support (HDMI capture cards)
    implementation("org.uvccamera:lib:0.0.13")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.1")
    implementation("org.slf4j:slf4j-android:1.7.36")
}

kapt {
    correctErrorTypes = true
}
