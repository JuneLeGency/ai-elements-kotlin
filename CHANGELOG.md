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
- **Elements**: AG-UI `state` parts render their `plan` / `task` as the Plan / Task elements; `Subagent`; `ToolCall` titles, MCP server and skill badges, live progress;
  `Agent` descriptions and skill lists (A2A cards).
- `ai-elements-koog`: a JetBrains Koog agent as a `ChatBackend` (and as an `AgentHarness` model
  binding), with AI Elements tools as Koog tools; approvals and progress work as with the built-in loop.
- New artifact `ai-elements-mcp-apps`: an MCP Apps host (2026-01-26). `ui://` views run in a
  sandboxed WebView (per-server origin, CSP from `_meta.ui.csp` as a response header, message-port
  bridge, no file or permission access). The host answers `ui/initialize`, `tools/call` (app-visible
  tools of the view's own server; writes need approval), `resources/read`, `ui/message`,
  `ui/update-model-context`, `ui/open-link` (http/s only) and `ui/request-display-mode` (inline and
  full screen), and sends tool input, result, host context (Material theme as the standard CSS
  variables) and teardown. `McpAppsHost` shows approvals and full screen at the screen's root, and
  views keep running while they scroll away. Checked against a session recorded between the official
  `App` and `AppBridge` SDKs, and end to end on the emulator with a view built on the official SDK.
- MCP: `McpClient(capabilities = …)` and `McpApps.CLIENT_CAPABILITIES`; `McpTool.meta`,
  `uiResourceUri`, `visibility` (app-only tools are hidden from the model); resource and tool results
  keep `_meta` / the raw result; tools with a view add a `DataPart.MCP_APP` part.
- `DataPart.MODEL_CONTEXT`: context for the model on a user turn (inline text for model APIs,
  `RunAgentInput.context` for AG-UI, a `data-model-context` part for the AI SDK).
- The offline mock agent calls a tool the prompt names.
- New artifact `ai-elements-genui`: A2UI v1.0 surfaces rendered natively (`a2uiRenderer`,
  `A2uiSurfaceView`, extensible `A2uiCatalog`; passes the official conformance suite) and
  `JsxPreview`. A2UI rides each transport's binding: AG-UI `a2ui-surface` activities and
  `forwardedProps.a2uiAction`, A2A `application/a2ui+json` parts, AI SDK `data-a2ui` parts.
  The reference server's `hotel` script sends a booking form (validated with the official
  `a2ui-core`); the demo renders it and the round trip runs on the emulator over AG-UI and AI SDK,
  and live over A2A against the server's A2UI concierge (`/concierge`).
- MCP authorization verified live end to end against the official `mcp` SDK OAuth server
  (`server/mcp_auth_server.py`, `LiveMcpOAuthTest`).
- New artifact `ai-elements-chat` (models, `ChatEvent`, `ChatBackend`, `ChatController`; same packages):
  `ai-elements-ui` now depends on it alone (no OkHttp / protocol libraries); `ai-elements-core` builds
  on it. Apps that used backends through `ai-elements-ui` add `ai-elements-core` explicitly.
- `AiElementsTheme` takes a brand `colorScheme`, `typography` and `shapes`.
- Protocol-independent elements: `ToolPart.kind` (`ToolKind.Function` / `Delegation` / `Skill`) and
  `ToolPart.source` replace tool-name checks and `"title · server"` labels in the UI; protocols map
  onto them in `core` (`ToolConventions`, AG-UI `SUBAGENT_*`), on-device tools declare them
  (`AgentTool.kindFor` / `source`). `DataPart.STATE` names shared agent state. Extension points:
  `LocalAiElementsRenderers` (tool / data renderers) and `LocalFileLoader`. `LayeringTest` guards it.
- `Chat(controller)` and `rememberChat(backend)`: a complete chat in one composable; `samples/pure-client`
  and `samples/in-app-agent` as minimal apps (built by CI).
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
