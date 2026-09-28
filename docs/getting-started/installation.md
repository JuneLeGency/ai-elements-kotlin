# Installation

The libraries are Android libraries (AAR) for Jetpack Compose. Add the BOM so that every artifact
resolves to the same version, then add the artifacts you use.

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

- `compileSdk` 37 and Java 17 bytecode. `ai-elements-a2a` needs
  [core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring).
- Compose with Material 3 1.5 (Expressive). The libraries are built with AGP 9.4, Kotlin 2.4 and
  Compose 1.13.
- `harness-sandbox-proot` runs PRoot from the native library directory, so package native libraries
  extracted: `android { packaging { jniLibs.useLegacyPackaging = true } }`.
