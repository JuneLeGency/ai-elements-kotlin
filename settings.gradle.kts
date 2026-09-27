pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ai-elements-kotlin"

include(":ai-elements-core")
include(":ai-elements-ui")
include(":ai-elements-mermaid-native")
include(":ai-elements-a2a")
include(":harness:harness-core")
include(":harness:harness-filesystem")
include(":harness:harness-memory")
include(":demo")
