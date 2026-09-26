plugins {
    alias(libs.plugins.android.library)
    // AGP 9.x built-in Kotlin — no kotlin.android plugin.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.ai.elements.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compose compiler is bundled with Kotlin 2.x.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9.x built-in Kotlin: jvmTarget defaults to compileOptions.targetCompatibility.
}

dependencies {
    api(project(":ai-elements-core"))

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.compose.ui.tooling)
}
