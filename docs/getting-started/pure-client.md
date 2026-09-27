# Quick start: pure client

In this mode the agent runs elsewhere, on your server, a remote A2A agent, or a coding agent over
ACP, and the app renders its conversation. You need `ai-elements-ui` and the backend for your
protocol (`ai-elements-core` for AI SDK and AG-UI).

## One composable

`Chat` wires the conversation, tools, approvals, sub-agents, plans and the prompt input to a
`ChatController`:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:minimal"
```

`rememberChat` gives the backend an `approver`: the app's human in the loop, which shows approvals
and forms in the conversation.

## In a ViewModel

In an app, keep the controller in a `ViewModel` so that the conversation survives configuration
changes. Compose the pieces yourself when you need more control:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:viewmodel"
```

Sub-agents, plans, shared state and activities from the server render on their own. For example, a
`delegate_task` call or an AG-UI `SUBAGENT_*` run becomes a `Subagent` card with the nested run.

## Choose a backend

Every backend is a `ChatBackend`. Pick the one your agent speaks:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:backends"
```

[Protocols](../protocols/index.md) compares them and explains each one, including how the server
side should be built.

## Try it against the reference server

The repository includes a [reference server](../develop/reference-server.md), a Pydantic AI agent
served over every protocol, with a scripted model that needs no API key:

```bash
cd server && uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

Point a backend at `http://10.0.2.2:8788/api/agui` (the Android emulator's alias for your computer),
or use the LAN address of your computer on a device.

The `samples/pure-client` app in the repository is a complete, minimal app for this mode.
