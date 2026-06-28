
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.tielink.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // BaselineProfileRule / Macrobenchmark require API 28+ on the test device
        minSdk = 28
        targetSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // This test module exercises the :app variants
    targetProjectPath = ":app"
}

// Run the generator on a locally connected device/emulator (API 28+).
// Swap to managedDevices if you prefer a Gradle-managed emulator.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}