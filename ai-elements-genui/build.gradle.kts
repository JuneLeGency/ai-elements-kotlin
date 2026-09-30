plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.publish)
}

// Generative UI on the elements: A2UI surfaces rendered natively (the component model and catalog
// JsxPreview compiles onto). Protocol-transport free: transports hand it A2UI messages.
android {
    namespace = "dev.ai.elements.genui"

    defaultConfig {
        // 26: A2UI date and time functions use java.time.
        minSdk = 26
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
    api(project(":ai-elements-ui"))
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.snakeyaml) // the A2UI conformance suites are YAML

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
