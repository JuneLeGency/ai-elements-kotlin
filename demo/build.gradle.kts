plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.ai.elements.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ai.elements.demo"
        // 26: the optional A2A module (official A2A Java SDK) needs it.
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    // Lists the shipped translations for Android 13+ per-app language settings.
    androidResources {
        generateLocaleConfig = true
    }

    // The A2A Java SDK's jars each carry these; their notices are reproduced in the app's licences.
    packaging {
        // The Linux sandbox executes PRoot from the native library directory, so libraries are extracted.
        jniLibs.useLegacyPackaging = true
        resources.excludes += listOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/beans.xml")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Demo only: sign release with the debug key so it installs for profiling.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

/** Bundles the repository's Agent Skills (`/skills`, shared with `server/`) as `assets/skills`. */
abstract class BundleSkills : DefaultTask() {
    @get:InputDirectory abstract val source: DirectoryProperty
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = output.get().asFile
        out.deleteRecursively()
        source.get().asFile.copyRecursively(File(out, "skills"))
    }
}

val bundleSkills = tasks.register<BundleSkills>("bundleSkills") {
    source.set(rootProject.layout.projectDirectory.dir("skills"))
    output.set(layout.buildDirectory.dir("generated/skillAssets"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(bundleSkills, BundleSkills::output) }
}

dependencies {
    implementation(libs.androidx.browser)
    implementation(project(":ai-elements-ui"))
    implementation(project(":ai-elements-genui"))
    implementation(project(":ai-elements-mermaid-native"))
    implementation(project(":ai-elements-a2a"))
    implementation(project(":harness:harness-core"))
    implementation(project(":harness:harness-filesystem"))
    implementation(project(":harness:harness-memory"))
    implementation(project(":harness:harness-planning"))
    implementation(project(":harness:harness-sandbox-proot"))
    implementation(project(":harness:harness-browser"))
    implementation(project(":harness:harness-device"))
    implementation(project(":harness:harness-speech"))
    implementation(project(":harness:harness-scheduler"))
    implementation(project(":ai-elements-koog"))
    implementation(libs.koog.client.openai)
    implementation(libs.koog.client.anthropic)
    implementation(libs.koog.client.ollama)
    implementation(libs.koog.http.ktor)
    implementation(libs.ktor.client.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.activity.compose)
    // Installs the baseline profiles Compose & AndroidX ship, for faster startup and smoother scrolling.
    implementation(libs.profileinstaller)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)

    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
