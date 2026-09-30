plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

// Agent run notifications: an Android 16 Live Update while the agent works, "reply ready" after.
// Optional (it brings the notification permissions): apps opt in by adding it.
android {
    namespace = "dev.ai.elements.notifications"

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":ai-elements-ui"))
    // NotificationCompat.ProgressStyle / promoted ongoing (Android 16 Live Updates).
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.runner)
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
