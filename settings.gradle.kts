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

include(":ai-elements-chat")
include(":ai-elements-core")
include(":ai-elements-ui")
include(":ai-elements-genui")
include(":ai-elements-mcp-apps")
include(":ai-elements-notifications")
include(":ai-elements-mermaid-native")
include(":ai-elements-a2a")
include(":ai-elements-acp")
include(":ai-elements-koog")
include(":ai-elements-bom")
include(":harness:harness-core")
include(":harness:harness-filesystem")
include(":harness:harness-memory")
include(":harness:harness-planning")
include(":harness:harness-shell")
include(":harness:harness-browser")
include(":harness:harness-device")
include(":harness:harness-speech")
include(":harness:harness-scheduler")
include(":harness:harness-sandbox-proot")
include(":demo")
include(":benchmark")

include(":samples:pure-client")
include(":samples:in-app-agent")
