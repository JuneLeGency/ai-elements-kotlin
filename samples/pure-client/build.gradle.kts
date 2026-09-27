plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Minimal sample: see README.md next to this file.
android {
    namespace = "dev.ai.elements.samples.pureclient"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ai.elements.samples.pureclient"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
    // In your app: implementation(platform("io.github.junelegency:ai-elements-bom:<version>")) + implementation("io.github.junelegency:ai-elements-ui")
    implementation(project(":ai-elements-ui"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
}
