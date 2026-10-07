# Quick start: in-app agent

First complete [Installation](installation.md) and the [network manifest setup](pure-client.md#app-setup).
Add the harness artifacts used by your capabilities; their minSdk may exceed the UI module's.

In this mode the agent loop runs on the device and calls a model API directly. `AgentHarness`
combines a model with capabilities (tools and their instructions) into a `ChatBackend`, so the same
elements render it.

## Minimal app

Start with files and planning, without requiring a sandbox, installed skills or MCP servers:

```kotlin title="build.gradle.kts"
dependencies {
    // Keep the BOM and activity/lifecycle dependencies from Installation.
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency.harness:harness-core")
    implementation("io.github.junelegency.harness:harness-filesystem")
    implementation("io.github.junelegency.harness:harness-planning")
}
```

Use minSdk 26. This example uses a local Ollama endpoint on your computer with `qwen3:4b`
already installed. Ensure that endpoint is reachable from the emulator; substitute your endpoint
and model as appropriate. No hosted-model credential is needed for this local setup.

```kotlin
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.harness.AgentHarness
import dev.ai.elements.harness.model
import dev.ai.elements.harness.filesystem.FileSystem
import dev.ai.elements.harness.planning.Planning
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.theme.AiElementsTheme
import java.io.File
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:minimal-in-app-agent"
```

Call `AppAgentScreen()` inside your activity's `setContent`. Send a prompt asking it to make a
plan or read a workspace file. The complete app is in `samples/in-app-agent`; its sources also
compile in the isolated Maven consumer build. Add further capabilities using the
[full harness example](../guides/harness.md).

## What the capabilities do

- `FileSystem` reads and edits files in the workspace and in folders the user shares.
- `Shell` runs commands in an Alpine Linux sandbox (PRoot) with the workspace mounted at
  `/workspace`. The agent can `apk add` packages.
- `Memory` keeps notes across conversations and injects `MEMORY.md` each turn.
- `Planning` keeps a plan that renders as the `Plan` element.
- `Skills` offers [Agent Skills](https://agentskills.io) (`SKILL.md` folders) that the model loads
  on demand with `load_capability`.
- `mcpServers.toolset()` adds the tools of the MCP servers the user configured.
- `localSubAgents` are other agents the model can delegate to with `delegate_task`.

The tool names and behaviour mirror [Pydantic AI Harness](https://github.com/pydantic/pydantic-ai-harness),
so an on-device agent and a server agent built with it render identically. See
[In-app agent](../guides/harness.md) for every capability.

## Safety defaults

Tools that change data ask the user first: file writes, MCP tools that are not read-only, and the
shell outside a sandbox. The approval appears in the conversation; see
[Human in the loop](../guides/human-in-the-loop.md).

## The Linux sandbox

The sandbox executes PRoot from the native library directory, so the app must package native
libraries extracted:

```kotlin title="build.gradle.kts"
android { packaging { jniLibs.useLegacyPackaging = true } }
```

The Alpine root filesystem (about 4 MB, pinned and checksum-verified) downloads on first use;
`AlpineSandbox.state` reports progress.

The `samples/in-app-agent` app in the repository is a complete, minimal app for this mode.


## Credentials and lifecycle

Obtain provider credentials from your app's authenticated configuration; provide `apiKey` when switching from local Ollama to a hosted model. Do not embed a shared production provider secret in an APK. Use your backend
or user-owned credentials according to your product's deployment model.

Own the harness and controller in a ViewModel, as in `samples/in-app-agent`. Capability resources
such as browser, sandbox and speech engines must follow their documented lifecycle; configuring
an element does not grant Android permissions. [Harness](../guides/harness.md) covers each capability.
