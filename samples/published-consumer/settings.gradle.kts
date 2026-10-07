pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Never fall back to Central or ~/.m2 for our artifacts: test exactly this publication.
        exclusiveContent {
            forRepository { maven { url = uri(providers.gradleProperty("artifactRepository").get()) } }
            filter { includeGroup("io.github.junelegency"); includeGroup("io.github.junelegency.harness") }
        }
        google()
        mavenCentral()
    }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "published-consumer"
include(":ui-only", ":pure-client", ":in-app-agent", ":integrations")
