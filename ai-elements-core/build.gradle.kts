plugins {
    alias(libs.plugins.android.library)
    // AGP 9.x built-in Kotlin — no kotlin.android plugin.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.ai.elements.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compose compiler is bundled with Kotlin 2.x via the Kotlin plugin;
        // no separate kotlinCompilerExtensionVersion is required.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9.x built-in Kotlin: jvmTarget defaults to compileOptions.targetCompatibility.
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    // Gateway backends stream over HTTP(SSE). OkHttp is the transport.
    api(libs.okhttp)

    // API keys / OAuth tokens are stored encrypted (Keystore-backed).
    api(libs.security.crypto)

    // The core module also carries the shared Material3 Expressive theme + tokens,
    // so the UI module and apps share one design system.
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.animation)
    api(libs.compose.animation.core)
    api(libs.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
