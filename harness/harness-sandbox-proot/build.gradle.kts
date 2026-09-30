plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
}

// Alpine Linux sandbox: PRoot (GPL-2.0) is bundled as separate executables in jniLibs, built from
// upstream termux/proot by native/build-proot.sh; the Kotlin code here is Apache-2.0 (see NOTICE).
android {
    namespace = "dev.ai.elements.harness.sandbox"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":harness:harness-shell"))
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
