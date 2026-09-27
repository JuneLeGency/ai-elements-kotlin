// Root build file for ai-elements-kotlin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9.x built-in Kotlin: no `kotlin.android` plugin needed.
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    // API reference for every published module: ./gradlew :dokkaGenerate → build/dokka/html
    alias(libs.plugins.dokka)
}

/** Every published library (the BOM lists the same). */
val publishedModules = listOf(
    ":ai-elements-chat", ":ai-elements-core", ":ai-elements-ui", ":ai-elements-genui", ":ai-elements-a2a", ":ai-elements-acp", ":ai-elements-koog", ":ai-elements-mermaid-native",
    ":harness:harness-core", ":harness:harness-filesystem", ":harness:harness-memory", ":harness:harness-planning",
    ":harness:harness-shell", ":harness:harness-sandbox-proot", ":harness:harness-browser", ":harness:harness-device",
    ":harness:harness-speech", ":harness:harness-scheduler",
)

subprojects {
    if (path in publishedModules) apply(plugin = "org.jetbrains.dokka")
}

dependencies {
    publishedModules.forEach { dokka(project(it)) }
}

dokka {
    moduleName.set("AI Elements for Kotlin")
}
