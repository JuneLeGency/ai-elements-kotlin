plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.ai.elements.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    // Protocol-independent: the models and ChatController only, not the protocols or transports.
    api(project(":ai-elements-chat"))

    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material3)
    implementation(libs.compose.ui.graphics)
    implementation(libs.activity.compose)
    implementation(libs.androidx.browser) // Custom Tabs for links in content
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.highlights)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(project(":ai-elements-core")) // offline MockAgentBackend in component tests
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Maven Central: `./gradlew publishToMavenCentral` with mavenCentralUsername / mavenCentralPassword
// and signingInMemoryKey(+Password) set; `publishToMavenLocal` works without them.
mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
