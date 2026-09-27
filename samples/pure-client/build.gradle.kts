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
    // In your app: the BOM plus ai-elements-ui (the elements) and ai-elements-core (the AG-UI / AI SDK backends).
    implementation(project(":ai-elements-ui"))
    implementation(project(":ai-elements-core"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
}
