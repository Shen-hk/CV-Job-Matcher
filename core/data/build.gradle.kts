plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.tielink.core.data"
    compileSdk = 35

    defaultConfig { minSdk = 24 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    api(libs.coroutines.core)
    api(libs.room.runtime)
    api(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.hilt.android)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.pdfbox.android)
}
