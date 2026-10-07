plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "dev.ai.elements.samples.inappagent"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { applicationId = "dev.ai.elements.samples.inappagent"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "consumer" }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    sourceSets.getByName("main") { kotlin.srcDir("../../in-app-agent/src/main/kotlin"); manifest.srcFile("../../in-app-agent/src/main/AndroidManifest.xml") }
}
dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:${providers.gradleProperty("libraryVersion").get()}"))
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency.harness:harness-core")
    implementation("io.github.junelegency.harness:harness-filesystem")
    implementation("io.github.junelegency.harness:harness-planning")
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
}
