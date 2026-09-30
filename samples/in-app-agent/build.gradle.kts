plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Minimal sample: see README.md next to this file.
android {
    namespace = "dev.ai.elements.samples.inappagent"

    defaultConfig {
        applicationId = "dev.ai.elements.samples.inappagent"
        minSdk = 26
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
    // In your app: the BOM plus ai-elements-ui, harness-core and the harness-* capabilities you want.
    implementation(project(":ai-elements-ui"))
    implementation(project(":harness:harness-core"))
    implementation(project(":harness:harness-filesystem"))
    implementation(project(":harness:harness-planning"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
}
