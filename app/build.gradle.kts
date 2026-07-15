plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.tielink"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tielink"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + OkHttp + Moshi (reflective adapter, no codegen)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Navigation
    implementation(libs.navigation.compose)

    // ViewModel Compose
    implementation(libs.lifecycle.viewmodel.compose)

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // ML Kit OCR (Chinese)
    implementation(libs.mlkit.text.recognition.chinese)

    // PDF text extraction
    implementation(libs.pdfbox.android)

    // TensorFlow Lite (Embedding models for semantic matching)
    // 使用 2.16.1 版本，排除api模块以避免命名空间冲突
    implementation(libs.tensorflow.lite) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation(libs.tensorflow.lite.support) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
    }

    // DOCX handled via direct ZIP+XML (no extra dependency)

    // Konfetti — celebration confetti animation
    implementation(libs.konfetti.compose)

    // ProfileInstaller — installs the baseline profile bundled in the APK at runtime
    implementation(libs.profileinstaller)

    // Baseline profile produced by the :baselineprofile module
    baselineProfile(project(":baselineprofile"))

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
