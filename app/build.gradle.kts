import java.util.Properties
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}
val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
val appVersionName = versionProps.getProperty("VERSION_NAME", "0.1.0")
val buildTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd.HHmm"))

android {
    namespace = "com.recomo.remotecontrol"
    compileSdk = 34
    val openvinsRoot = rootProject.projectDir.resolve("../../external/open_vins").absolutePath
    val openvinsEigenRoot = rootProject.projectDir.resolve("../../external/eigen").absolutePath
    val openvinsOpenCvDir = rootProject.projectDir.resolve("../../external/opencv-mobile/prebuilt/sdk/native/jni").absolutePath
    val openvinsBoostRoot = rootProject.projectDir.resolve("../../external/Boost-for-Android/build/out/arm64-v8a").absolutePath
    val openvinsEnable = (findProperty("openvinsEnable") as String?)?.uppercase() ?: "OFF"
    val openvinsDepsOnly = (findProperty("openvinsDepsOnly") as String?)?.uppercase() ?: "OFF"
    val openvinsLinkOpenCv =
        (findProperty("openvinsLinkOpenCv") as String?)?.uppercase()
            ?: if (openvinsEnable == "ON" || openvinsDepsOnly == "ON") "ON" else "OFF"

    defaultConfig {
        applicationId = "com.recomo.remotecontrol"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = "$appVersionName.$buildTimestamp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf(
                    "-DOPENVINS_ENABLE=$openvinsEnable",
                    "-DOPENVINS_DEPS_ONLY=$openvinsDepsOnly",
                    "-DOPENVINS_LINK_OPENCV=$openvinsLinkOpenCv",
                    "-DOPENVINS_ROOT=$openvinsRoot",
                    "-DOPENVINS_EIGEN_ROOT=$openvinsEigenRoot",
                    "-DOPENVINS_OPENCV_DIR=$openvinsOpenCvDir",
                    "-DOPENVINS_BOOST_ROOT=$openvinsBoostRoot",
                    "-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH",
                    "-DCMAKE_PREFIX_PATH=$openvinsOpenCvDir/abi-arm64-v8a",
                    "-DBoost_COMPILER=-clang-darwin"
                )
            }
        }
        buildConfigField("String", "OPENVINS_ENABLE", "\"$openvinsEnable\"")
        buildConfigField("String", "OPENVINS_DEPS_ONLY", "\"$openvinsDepsOnly\"")
        buildConfigField("String", "OPENVINS_LINK_OPENCV", "\"$openvinsLinkOpenCv\"")
        buildConfigField("String", "OPENVINS_ROOT", "\"$openvinsRoot\"")
        buildConfigField("String", "OPENVINS_EIGEN_ROOT", "\"$openvinsEigenRoot\"")
        buildConfigField("String", "OPENVINS_OPENCV_DIR", "\"$openvinsOpenCvDir\"")
        buildConfigField("String", "OPENVINS_BOOST_ROOT", "\"$openvinsBoostRoot\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE*",
                "META-INF/gradle/incremental.annotation.processors",
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties"
            )
        }
        // sherpa-onnx AAR (via :common) bundles its own libonnxruntime.so;
        // :app also has onnxruntime-android:1.17.0. Both are ABI-compatible;
        // pickFirst resolves the duplicate for all ABIs.
        jniLibs {
            pickFirsts += setOf(
                "lib/arm64-v8a/libonnxruntime.so",
                "lib/armeabi-v7a/libonnxruntime.so",
                "lib/x86/libonnxruntime.so",
                "lib/x86_64/libonnxruntime.so"
            )
        }
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            kotlin.srcDirs("src/main/java")
            jniLibs.srcDirs(
                "src/main/jniLibs",
                "../../../external/opencv-mobile/prebuilt/sdk/native/libs",
                "../../../external/Boost-for-Android/build/out/arm64-v8a/lib"
            )
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    // Shared module (network, models, video, preview)
    implementation(project(":common"))

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    implementation("io.github.webrtc-sdk:android:114.5735.10")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.1")
    implementation("org.slf4j:slf4j-android:1.7.36")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // ARCore for real-time pose tracking (V3DR)
    implementation("com.google.ar:core:1.40.0")

    // CameraX (phone-moco capture)
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // Room (phone-moco metadata storage)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Retrofit + OkHttp (phone-moco upload)
    val retrofitVersion = "2.9.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Logging for v3dr components
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // UVCCamera for USB Video Class device support (HDMI capture cards)
    implementation("org.uvccamera:lib:0.0.13")

    // Filament for trajectory preview (3D)
    implementation(files("libs/filament-v1.67.0-android.aar"))
    implementation(files("libs/filament-utils-v1.67.0-android.aar"))
    implementation(files("libs/gltfio-v1.67.0-android.aar"))

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // MockK (Kotlin-native mocking)
    testImplementation("io.mockk:mockk:1.13.10")

    // Turbine (Flow/StateFlow testing)
    testImplementation("app.cash.turbine:turbine:1.1.0")

    // Coroutines test
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // kotlinx-serialization-json (for protocol contract tests — already in main, need for test compilation)
    // (inherited from implementation scope)

    // JUnit 4 (kept for legacy/androidTest compatibility)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}
