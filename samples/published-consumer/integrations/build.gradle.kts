plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "dev.ai.elements.samples.integrations"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { applicationId = "dev.ai.elements.samples.integrations"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "consumer" }
    buildFeatures { compose = true }
    sourceSets.getByName("main").kotlin.srcDir(rootProject.layout.buildDirectory.dir("component-samples").get().asFile)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += listOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/beans.xml")
    }
}
dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:${providers.gradleProperty("libraryVersion").get()}"))
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency:ai-elements-a2a")
    implementation("io.github.junelegency:ai-elements-acp")
    implementation("io.github.junelegency:ai-elements-koog")
    implementation("io.github.junelegency:ai-elements-genui")
    implementation("io.github.junelegency:ai-elements-mcp-apps")
    implementation("io.github.junelegency:ai-elements-notifications")
    implementation("io.github.junelegency:ai-elements-mermaid-native")
    implementation("io.github.junelegency.harness:harness-core")
    implementation("io.github.junelegency.harness:harness-filesystem")
    implementation("io.github.junelegency.harness:harness-memory")
    implementation("io.github.junelegency.harness:harness-planning")
    implementation("io.github.junelegency.harness:harness-shell")
    implementation("io.github.junelegency.harness:harness-sandbox-proot")
    implementation("io.github.junelegency.harness:harness-browser")
    implementation("io.github.junelegency.harness:harness-device")
    implementation("io.github.junelegency.harness:harness-speech")
    implementation("io.github.junelegency.harness:harness-scheduler")
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
