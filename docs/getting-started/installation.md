# Installation

The libraries are Android libraries (AAR) for Jetpack Compose. Add the BOM so that every artifact
resolves to the same version, then add the artifacts you use.

Declare dependency repositories in your settings file:

```kotlin title="settings.gradle.kts"
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Before the first public release only, after publishToMavenLocal:
        mavenLocal()
    }
}
```

```kotlin title="build.gradle.kts"
dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:0.3.0-SNAPSHOT"))
    implementation("io.github.junelegency:ai-elements-ui")          // elements (protocol-independent)
    implementation("io.github.junelegency:ai-elements-core")        // AI SDK / AG-UI / model-API backends, agent loop, MCP

    // Optional, as needed:
    implementation("io.github.junelegency:ai-elements-genui")       // generative UI: A2UI surfaces, JsxPreview
    implementation("io.github.junelegency:ai-elements-mcp-apps")    // MCP Apps: interactive views of MCP tools
    implementation("io.github.junelegency:ai-elements-a2a")         // A2A agents (official a2a-java-sdk)
    implementation("io.github.junelegency:ai-elements-acp")         // Agent Client Protocol (official ACP Kotlin SDK)
    implementation("io.github.junelegency:ai-elements-koog")        // JetBrains Koog as the agent runtime
    implementation("io.github.junelegency.harness:harness-core")    // in-app agent
    implementation("io.github.junelegency.harness:harness-filesystem")
    implementation("io.github.junelegency.harness:harness-sandbox-proot") // + harness-shell
}
```

!!! note "Snapshot builds"
    `0.3.0` is not released yet. Until it is, build the libraries yourself with
    `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

## Artifacts

| Artifact | What it is | minSdk |
|---|---|---|
| `ai-elements-chat` | The protocol-independent layer: the chat model (≈ AI SDK `UIMessage`), `ChatEvent`, `ChatBackend`, `ChatController` (≈ `useChat`). No networking, no Compose. | 24 |
| `ai-elements-core` | Protocol clients (AI SDK, AG-UI), model APIs, the agent loop, `SubAgents`, `Skills`, MCP, OAuth. Each is a `ChatBackend` or a capability on top of `ai-elements-chat`. No Compose. | 24 |
| `ai-elements-ui` | The Compose elements and `AiElementsTheme`. It depends on `ai-elements-chat` only, so it renders any backend, including your own. | 24 |
| `ai-elements-genui` | Generative UI: A2UI v1.0 surfaces rendered natively (Basic Catalog, extensible), `JsxPreview`. | 26 |
| `ai-elements-mcp-apps` | An MCP Apps host: `ui://` views of MCP tools in a sandboxed WebView, bridged to their server. | 24 |
| `ai-elements-notifications` | Agent run notifications: an Android 16 Live Update while the agent works, "reply ready" when it ends. Optional, as it brings the notification permissions. | 24 |
| `ai-elements-a2a` | A2A 1.0 on the official Java SDK: remote agents as providers or sub-agents. Needs core library desugaring. | 26 |
| `ai-elements-acp` | Agent Client Protocol on the official Kotlin SDK: coding agents as providers. | 24 |
| `ai-elements-koog` | A JetBrains Koog agent as a `ChatBackend` or harness model binding. | 26 |
| `ai-elements-mermaid-native` | Mermaid drawn with Compose Canvas instead of a WebView (experimental). | 24 |
| `ai-elements-bom` | Aligns the versions of everything here. | — |

In-app agent capabilities (group `io.github.junelegency.harness`):

| Artifact | Tools | minSdk |
|---|---|---|
| `harness-core` | `AgentHarness`: a model plus capabilities make a `ChatBackend`; local and remote sub-agents. | 24 |
| `harness-filesystem` | `read_file`, `write_file`, `edit_file`, `list_directory`, `search_files`, `find_files`, `create_directory`, `file_info`, in the workspace and in folders the user shares. | 26 |
| `harness-memory` | `write_memory`, `read_memory`, `delete_memory`, `search_memory`; `MEMORY.md` injected each turn. | 26 |
| `harness-planning` | `write_plan`, `read_plan`, `add_task`, `update_task_status(es)`, `remove_task`; drives the `Plan` element. | 26 |
| `harness-shell` | `run_command`, `start_command`, `check_command`, `stop_command` over a pluggable runtime. | 26 |
| `harness-sandbox-proot` | Alpine Linux via PRoot (bundled executable, GPL-2.0, see its NOTICE). | 26 |
| `harness-browser` | `navigate`, `snapshot`, `click`, `type_text`, `get_text`, `screenshot`, … on an off-screen WebView. | 26 |
| `harness-device` | Device info, clipboard, calendar, contacts, location, alarms, notifications (asks for permissions). | 26 |
| `harness-speech` | `speak`, `stop_speaking` on the platform text-to-speech engine. | 26 |
| `harness-scheduler` | `schedule_task`, `list_scheduled_tasks`, `cancel_scheduled_task`: background runs on WorkManager. | 26 |

R8 rules ship with the libraries.

## Requirements

- The tested consumer baseline is JDK 21, Gradle 9.8.0, AGP 9.4.1 (built-in Kotlin 2.4.20),
  `compileSdk` 37.2 and Java 17 bytecode. Older consumer toolchains have not been certified. `ai-elements-a2a` needs
  [core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring).
- Compose with Material 3 1.5 (Expressive). The libraries are built with AGP 9.4, Kotlin 2.4 and
  Compose 1.13.
- `harness-sandbox-proot` runs PRoot from the native library directory, so package native libraries
  extracted: `android { packaging { jniLibs.useLegacyPackaging = true } }`.


Compose 1.13.0-alpha03 and Material 3 1.5.0-alpha29 are prerelease dependencies. Their transitive
requirements apply to consumers too; the AI Elements BOM aligns our artifacts, not arbitrary
versions of AndroidX chosen by an app. See [API stability](../develop/api-compatibility.md).

For a new Compose app, enable the Compose compiler plugin and `buildFeatures.compose = true`,
set Java source/target compatibility to 17, and add `androidx.activity:activity-compose:1.13.0`.
The ViewModel example also uses `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0` and
`androidx.lifecycle:lifecycle-runtime-compose:2.11.0`.

### A2A consumer configuration

```kotlin
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging.resources.excludes += listOf(
        "META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/INDEX.LIST",
        "META-INF/DEPENDENCIES", "META-INF/beans.xml",
    )
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
```

Keep the dependencies' license notices in your distribution when resolving duplicate metadata.
See the repository NOTICE. These packaging exclusions alone do not replace license attribution.

### Verify the packages

The [published consumer build](https://github.com/JuneLeGency/ai-elements-kotlin/tree/main/samples/published-consumer)
compiles UI-only, pure-client, in-app-agent and optional-integration apps from Maven coordinates,
including release builds with R8. It is separate from the library build.
