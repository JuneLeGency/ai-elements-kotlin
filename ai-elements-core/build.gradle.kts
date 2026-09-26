plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.ai.elements.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.unitTests.all { test ->
        // Opt-in live agent tests (see LiveAgentTest): -PliveOllama=... -PliveAgentServer=...
        listOf(
            "liveOllama" to "live.ollama", "liveModel" to "live.model", "liveAgentServer" to "live.agentServer",
            "liveCodexAuth" to "live.codexAuth", "liveCodexModel" to "live.codexModel",
        )
            .forEach { (property, key) -> (findProperty(property) as String?)?.let { test.systemProperty(key, it) } }
        test.testLogging { showStandardStreams = true }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Maven Central: `./gradlew publishToMavenCentral` with mavenCentralUsername / mavenCentralPassword
// and signingInMemoryKey(+Password) set; `publishToMavenLocal` works without them.
mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
