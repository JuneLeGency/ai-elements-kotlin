plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kotlin.compose)
}

// Optional: Mermaid drawn with Compose Canvas (cmp-mermaid) instead of a WebView.
// Kept out of :ai-elements-ui because it is experimental and bundles ~4 MB of fonts.
android {
    namespace = "dev.ai.elements.mermaid"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
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
    implementation(libs.cmp.mermaid.compose)
}

// Maven Central: `./gradlew publishToMavenCentral` with mavenCentralUsername / mavenCentralPassword
// and signingInMemoryKey(+Password) set; `publishToMavenLocal` works without them.
mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
