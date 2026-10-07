plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    // Candidate SNAPSHOTs can be republished locally; immutable third-party releases stay cached.
    configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
}
