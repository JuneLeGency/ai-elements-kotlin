# Quick start: in-app agent

In this mode the agent loop runs on the device and calls a model API directly. `AgentHarness`
combines a model with capabilities (tools and their instructions) into a `ChatBackend`, so the same
elements render it.

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:in-app-agent"
```

Render it like any other backend, for example with `Chat(chat)`.

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
