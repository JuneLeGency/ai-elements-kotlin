plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

// Optional: A2A through the official A2A Java SDK. Kept out of :ai-elements-core because the
// SDK brings protobuf, Gson and Java 17 library APIs (consumers enable core library desugaring).
android {
    namespace = "dev.ai.elements.a2a"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    // The A2A Java SDK's jars each carry these; only matters for this module's own test APK.
    packaging {
        resources.excludes += listOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/beans.xml")
    }

    testOptions.unitTests.all { test ->
        (findProperty("liveA2a") as String?)?.let { test.systemProperty("live.a2a", it) }
        test.testLogging { showStandardStreams = true }
    }
}

dependencies {
    api(project(":ai-elements-core"))
    api(libs.a2a.java.sdk.client)
    implementation(libs.a2a.java.sdk.http.client.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
