plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// Macrobenchmarks and the Baseline Profile generator for the demo app (androidx.benchmark):
// frame timing of the chat's hot paths on a release build, on a real device.
//   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest     # frame timing / startup
//   ./gradlew :demo:generateReleaseBaselineProfile                # regenerates the shipped profile
android {
    namespace = "dev.ai.elements.benchmark"

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":demo"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.junit.ext)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
