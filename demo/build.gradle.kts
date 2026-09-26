plugins {
    alias(libs.plugins.android.application)
    // AGP 9.x built-in Kotlin — no kotlin.android plugin.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.ai.elements.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ai.elements.demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Real-service UI test configuration (override via gradle -P flags).
        // Use findProperty (returns the raw string) — NOT providers.gradleProperty,
        // whose Provider.toString() leaks "or(provider(?), fixed(...))" into the field.
        val gwBaseUrl = (findProperty("realGatewayBaseUrl") as? String) ?: "http://10.0.2.2:9090/v1"
        val gwApiKey = (findProperty("realGatewayApiKey") as? String) ?: ""
        val gwModel = (findProperty("realGatewayModel") as? String) ?: "claude-sonnet-5"
        buildConfigField("String", "REAL_GATEWAY_BASE_URL", "\"$gwBaseUrl\"")
        buildConfigField("String", "REAL_GATEWAY_API_KEY", "\"$gwApiKey\"")
        buildConfigField("String", "REAL_GATEWAY_MODEL", "\"$gwModel\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Compose compiler is bundled with Kotlin 2.x.
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9.x built-in Kotlin: jvmTarget defaults to compileOptions.targetCompatibility.
}

dependencies {
    implementation(project(":ai-elements-core"))
    implementation(project(":ai-elements-ui"))

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
