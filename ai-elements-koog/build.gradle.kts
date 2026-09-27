plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

// Optional: JetBrains Koog as the agent runtime. Kept out of :ai-elements-core because Koog
// brings its own prompt executors, serialization layer and Ktor.
android {
    namespace = "dev.ai.elements.koog"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":ai-elements-core"))
    api(libs.koog.agents.core)
    implementation(libs.koog.agents.event.handler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koog.agents.test)
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
