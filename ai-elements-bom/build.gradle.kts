plugins {
    `java-platform`
    alias(libs.plugins.vanniktech.publish)
}

// One version for every artifact: `implementation(platform("io.github.junelegency:ai-elements-bom:<v>"))`.
dependencies {
    constraints {
        listOf(
            ":ai-elements-core", ":ai-elements-ui", ":ai-elements-a2a", ":ai-elements-mermaid-native",
            ":harness:harness-core", ":harness:harness-filesystem", ":harness:harness-memory",
            ":harness:harness-planning", ":harness:harness-shell", ":harness:harness-sandbox-proot", ":harness:harness-browser",
        ).forEach { api(project(it)) }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
