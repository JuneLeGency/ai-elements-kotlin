# Changelog

## 0.3.0 (unreleased)

### Added — agents
- **Protocols**: AI SDK 6 (full `UIMessage` history, tool approval, client-side tools, preliminary
  output, `UIMessage` sub-agent output); AG-UI 1.0 on the official `kotlin-core` types (frontend
  tools, interrupts / resume, `SUBAGENT_*`, state and activity JSON Patch, steps, usage);
  MCP 2026-07-28 client with legacy-session fallback, OAuth discovery and `McpToolset`;
  A2A 1.0 (`ai-elements-a2a`, official `a2a-java-sdk`) as provider and as sub-agent.
- **Capabilities** mirroring Pydantic AI Harness: `Capability`, `SubAgents` (`delegate_task`),
  `Skills` (Agent Skills, `load_capability`), `ToolCallContext` (progress, nested runs, data parts).
- **In-app harness** (group `io.github.junelegency.harness`): `harness-core` (`AgentHarness`),
  `harness-filesystem` (workspace plus user-shared SAF folders at `/mnt/<name>`), `harness-memory`, `harness-planning`, `harness-shell`,
  `harness-sandbox-proot` (Alpine Linux on PRoot), `harness-browser` (off-screen WebView),
  `harness-device` (calendar, contacts, location, clipboard, alarms, notifications),
  `harness-speech` (text to speech) and `harness-scheduler` (scheduled background runs on
  WorkManager, `runHeadless`).
- **Elements**: `Subagent`; `ToolCall` titles, MCP server and skill badges, live progress;
  `Agent` descriptions and skill lists (A2A cards).
- `ai-elements-koog`: a JetBrains Koog agent as a `ChatBackend` (and as an `AgentHarness` model
  binding), with AI Elements tools as Koog tools; approvals and progress work as with the built-in loop.
- `ai-elements-bom`; `server/` rebuilt on Pydantic AI 2.51 + Harness 0.36 with MCP and A2A endpoints.

### Changed — agents
- `ai-elements-core` packages by concern: `chat`, `model`, `protocol.aisdk`, `protocol.agui`,
  `provider.*`, `http`, `agent`, `mcp`, `skills`, `auth`, `config` (was one `backend` package).

### Added
- 17 elements to match AI Elements: `Agent`, `ModelSelector`, `Question`, `Persona`, `SpeechInput`,
  `AudioPlayer` (+ `rememberAudioPlayerState`), `Transcription`, `MicSelector`, `VoiceSelector`,
  `Terminal`, `StackTrace`, `TestResults`, `FileTree`, `Commit`, `SchemaDisplay`, `PackageInfo`,
  `EnvironmentVariables`, `Sandbox`, `Snippet`.
- Theming: `AiPalette` (7 generated Material 3 schemes), `AiContrast`, `fontFamily` and
  `codeFontFamily` on `AiElementsTheme`, `LocalCodeFontFamily`.
- OAuth sign-in in core: PKCE loopback and device flows, single-flight refresh, OpenRouter, and
  experimental subscription sign-ins (ChatGPT, xAI, Kimi).
- Localization: English, Simplified Chinese, Traditional Chinese, Japanese.
- `MermaidRenderer` interface; the Compose Canvas renderer moved to the optional
  `ai-elements-mermaid-native` artifact.
- Consumer R8 rules in both libraries; Maven Central publishing.

### Changed
- Packages follow the AI Elements categories: `Persona` → `ui.voice`, `WorkflowCanvas` /
  `CanvasNode` / `agentRunGraph` → `ui.workflow`, `Artifact` / `WebPreview` → `ui.code`.
- `AssistantRow`, `assistantRows`, `AssistantRowItem` are internal (implementation of `Conversation`).
- Overflowing code, terminals and source chips fade at the edge that can still scroll.
- `calculate` returns 10 significant digits (`778516.8889`, not `778516.8888888889`).
- `LocalMermaidRenderer` now holds a `MermaidRenderer` (was an enum).
- Denser chat reading type scale (`AiType`).

### Fixed
- xAI sign-in used `/oauth/*`; the endpoints are `/oauth2/authorize` and `/oauth2/token` (per its
  OpenID discovery document) — sign-in could not work before.
- `StackTrace`: Python source lines were read as the message; `/usr/lib` frames counted as app code.
- Conversation list preview dropped escaped characters (`1234 \* 5678` → `1234  5678`).
- OAuth on Android 7 (API 24–25): `java.util.Base64` replaced with Kotlin's codec.
- Inline Mermaid/KaTeX snapshots could capture a mid-resize frame (enlarged corner).
- Soft keyboard popping up by itself on tablets (single-pane list-detail).

## 0.2.0
- Initial Material 3 Expressive rebuild: conversation, messages, tools, Markdown/Mermaid/KaTeX,
  8 protocol backends, adaptive layouts.
