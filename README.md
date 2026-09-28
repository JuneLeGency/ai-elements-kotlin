# AI Elements for Kotlin

**Material 3 Expressive** Jetpack Compose components and agent tooling for AI apps on Android: the
Compose counterpart of [Vercel AI Elements](https://elements.ai-sdk.dev). It connects only through
open protocols: AI SDK, AG-UI, MCP, MCP Apps, A2A, Agent Client Protocol, A2UI and Agent Skills.

<p>
  <img src="docs/assets/screenshots/phone-light-en.webp" width="260" alt="A conversation with a plan, a tool call, Markdown and a Mermaid diagram">
  <img src="docs/assets/screenshots/phone-dark-en.webp" width="260" alt="The same conversation in the dark theme">
</p>

```kotlin
AiElementsTheme {
    Chat(rememberChat { approver -> AgUiBackend("https://agents.example.com/api/agui", approver = approver) })
}
```

That is a complete chat: streaming Markdown, reasoning, tool calls with approvals, sub-agents, plans
and sources, and a live view of the agent's computer (screenshots, terminal, diffs) with a timeline
and replay. Swap the backend to talk to another protocol; the UI stays the same.

**[Documentation](docs/index.md)**: [install](docs/getting-started/installation.md) ·
[pure client](docs/getting-started/pure-client.md) · [in-app agent](docs/getting-started/in-app-agent.md) ·
[protocols](docs/protocols/index.md) · [elements](docs/guides/elements.md) ·
[human in the loop](docs/guides/human-in-the-loop.md) · [steps and replay](docs/guides/steps-and-replay.md) · [architecture](docs/concepts/architecture.md).
Build the site with `tools/build-docs.sh`, or preview it with `uvx zensical serve`.

## Two ways to use it

| Mode | Where the agent runs | You add |
|---|---|---|
| **Pure client** | On your server (Pydantic AI, LangGraph, Mastra, any AI SDK / AG-UI server), a remote A2A agent, or a coding agent over ACP | `ai-elements-ui` + a protocol backend |
| **In-app agent** | On the device, over any model API | `ai-elements-ui` + `harness-*` capabilities: files, a Linux sandbox, memory, planning, a browser, device tools, speech, scheduled tasks, sub-agents, skills, MCP |

## Protocols

| Backend | Spec | Artifact |
|---|---|---|
| `UiMessageStreamBackend` | Vercel AI SDK 6 UI Message Stream (v5, v4 Data Stream) | `ai-elements-core` |
| `AgUiBackend` | AG-UI 1.x, official `kotlin-core` types | `ai-elements-core` |
| `A2aBackend` | A2A 1.0 (0.3 compatible), official `a2a-java-sdk` | `ai-elements-a2a` |
| `AcpBackend` | Agent Client Protocol, official ACP Kotlin SDK | `ai-elements-acp` |
| OpenAI, Anthropic, Gemini, Ollama | model APIs, with the agent loop on the device | `ai-elements-core` |
| `McpClient` / `McpToolset` | MCP 2026-07-28 (2025-xx sessions), MCP Apps 2026-01-26 | `ai-elements-core`, `ai-elements-mcp-apps` |
| A2UI renderer, `JsxPreview` | A2UI v1.0 | `ai-elements-genui` |

The rules for protocol work (spec extension points only, no private wire formats, official SDKs,
recorded fixtures) are in [AGENTS.md](AGENTS.md).

## Install

```kotlin
dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:0.3.0-SNAPSHOT"))
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency:ai-elements-core")
}
```

`0.3.0` is not released yet: `./gradlew publishToMavenLocal` and add `mavenLocal()`. Every artifact
and its minSdk is listed in [Installation](docs/getting-started/installation.md).

## Try it

```bash
./gradlew :demo:installDebug                                        # the demo app (works offline)
cd server && uv sync && uv run uvicorn main:app --host 0.0.0.0 --port 8788   # the reference server
```

The [reference server](docs/develop/reference-server.md) is a Pydantic AI + Pydantic AI Harness
agent served over AI SDK, AG-UI, A2A, ACP and MCP, with a scripted model that needs no key. The
[demo app](docs/develop/demo-app.md) connects to it and to real providers.

## Develop

```bash
./gradlew testDebugUnitTest lintDebug          # unit and recorded-fixture tests
./gradlew :demo:connectedDebugAndroidTest      # UI end to end
```

Live tests, fixtures and device notes are in [Testing](docs/develop/testing.md). Read
[AGENTS.md](AGENTS.md) and [Contributing](docs/develop/contributing.md) before changing code; the
workstreams are in the [roadmap](docs/project/roadmap.md) and changes in the [CHANGELOG](CHANGELOG.md).

Toolchain: AGP 9.4 (built-in Kotlin) · Gradle 9.7 · Kotlin 2.4 · Compose 1.13 · Material 3 1.5
(Expressive).

## License

Apache License 2.0, see [LICENSE](LICENSE). Bundled third-party code, fonts and executables are
listed in [NOTICE](NOTICE); `harness-sandbox-proot` bundles PRoot (GPL-2.0) as a separate executable,
see its [NOTICE](harness/harness-sandbox-proot/NOTICE).
