plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

// Optional: Agent Client Protocol through the official ACP Kotlin SDK. Kept out of
// :ai-elements-core because the SDK brings Ktor, kotlinx-io and kotlin-logging.
android {
    namespace = "dev.ai.elements.acp"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        // Sent as the client's `clientInfo.version` in `initialize`.
        buildConfigField("String", "VERSION", "\"${providers.gradleProperty("VERSION_NAME").get()}\"")
    }

    buildFeatures.buildConfig = true

    testOptions.unitTests.all { test ->
        (findProperty("liveAcp") as String?)?.let { test.systemProperty("live.acp", it) }
        (findProperty("liveAcpCommand") as String?)?.let { test.systemProperty("live.acp.command", it) }
        test.testLogging { showStandardStreams = true }
    }
}

dependencies {
    api(project(":ai-elements-core"))
    api(libs.acp)
    implementation(libs.acp.ktor.client)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
