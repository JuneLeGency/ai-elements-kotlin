# Changelog

## 0.3.1 (unreleased)

- Chinese documentation image paths work in GitHub Markdown previews as well as the hosted site;
  documentation checks reject source image links that traverse a directory symlink.
- README guide links use the published English/Chinese site and include the Maven Central version badge.

## 0.3.0

### Release readiness
- English and Simplified Chinese documentation and READMEs, including all 65 component examples,
  onboarding, protocol guides and a responsive landing page. Four new light/dark conversation
  screenshots are captured from real Compose elements on the emulator. Site validation checks
  both languages, local screenshots and cross-language links.
- Stable public APIs are preserved starting with the first public release, including 0.x;
  deprecated entry points remain callable. Native Mermaid is explicitly opt-in with
  `ExperimentalNativeMermaidApi`. The SSE event, JSX compiler and A2UI expression parser implementation types are now internal before publication.
- Official Kotlin Binary Compatibility Validator baselines for all 20 libraries, wired to AGP 9
  release AARs; `apiCheck` fails on unreviewed changes. The model compatibility policy freezes data
  class constructors/copy and sealed/enum branches and requires behavioral/source review too.
- **Before first publication:** `ToolRenderer` now receives `(String, ToolDecision) -> Unit`
  instead of a Boolean-only callback. Custom renderers can forward edited arguments, denial reasons
  and remembered decisions. `ToolCall`, `ToolPartView` and standalone message rendering accept rich
  decision callbacks; existing Boolean callbacks on the elements remain available.
- Component documentation: compiled examples, imports, artifact requirements, interaction notes and
  symbol links for all 65 catalog entries, plus a supporting-composable index. The isolated Maven
  consumer build compiles those exported examples against the published AARs.
- Dokka now explicitly discovers AGP 9 Kotlin sources and dependency classpaths; its successful
  build previously produced an empty API reference. Site checks reject empty module documentation,
  missing images, broken guide links and anchors.
- Installation and quick starts include network/debug HTTP setup, the tested consumer toolchain,
  prerelease AndroidX versions, A2A desugaring, lifecycle and credentials guidance.
- Releases validate the exact tag/version/final CHANGELOG section and installation coordinates, and run the full reusable CI
  (API, unit/lint, R8, Maven consumers, docs and emulator E2E) before publication. Pages deploys
  only from `main`.

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
- Human in the loop beyond yes / no: `ToolApprover.decide` (`ToolDecision`: reason, edited
  arguments) and `ToolApprover.input` (`InputRequest` → `InputResponse`), `ChatState.inputRequests`,
  `ChatController.respondToInput`. AG-UI resumes with `{approved, reason, editedArgs}` and answers
  interrupts that carry a `responseSchema`; AI SDK approvals carry `reason`; the on-device loop runs
  edited arguments and tells the model why a call was denied. MCP elicitation on both eras
  (`InputRequiredResult` retries with `inputResponses` / `requestState`; `elicitation/create` on
  legacy streams), form and URL modes; `McpClient.callTool(onInput = …)`, and MCP tools ask through
  the chat. UI: `InputRequestCard` / `SchemaForm` (text, formats, numbers with bounds, booleans,
  single and multiple choice, required) and `Confirmation` with deny-with-reason and
  edit-then-approve.
- `McpClient.serverInfo` on 2026-07-28 servers (from the results' `_meta`).
- `StickToBottom` follows with `requestScrollToItem` (applied by the next measure) instead of
  `scrollToItem`, whose forced remeasure re-entered layout when pinning ran inside a layout pass
  (seen on a tablet).
- `A2aAgent.cardOrNull(timeoutMs)`: listing remote agents no longer blocks a turn on an
  unreachable one (the blocking HTTP connect ignored the coroutine timeout; a physical device
  waited ~16 s per turn on the emulator-only default agent); failures are not retried for a minute.
- Denser, calmer conversation (as the mainstream assistant apps lay it out): replies use the full
  width (no avatar column); reasoning is one quiet line ("Thought for 2s ›") that opens under a
  hairline rule; tool calls, sub-agents, plans and tasks are one-line cards (name, argument or
  activity preview, a quiet status that is coloured only for approval / error / denial);
  smaller source pills and table cells.
- `PromptInput` switches to one row (field + send) when there is too little height for two
  (a phone in landscape with the keyboard up) instead of squashing its toolbar.
- Demo: phones have no bottom navigation bar — Components and Settings open from the chat's
  drawer (Back returns); the top bar is a single-line "Provider ▾" title at 56dp.
- A2UI forms keep what the user entered while they scroll out of sight (sessions live in the
  renderer, not in the lazy list item).
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

- `ai-elements-acp`: **Agent Client Protocol** on the official ACP Kotlin SDK — coding agents
  (Claude Code, Codex, Gemini CLI, Pydantic AI Harness `run_acp_stdio`, …) as chat providers.
  `AcpAgent.webSocket(url)` (the SDK's WebSocket transport) or `AcpAgent.process(command)` (stdio);
  one ACP session per conversation (resumed with `session/load` when supported); text, reasoning,
  tool calls (diffs as unified diffs), `plan` updates as the Plan element, usage;
  `session/request_permission` through the app's approvals; `session/cancel` on stop; optional
  client file system (`AcpFileSystem`). The reference server serves its agent over ACP with the
  Harness ACP adapter (`acp_agent.py` on stdio, `ws://…/acp`); the demo has an "ACP agent" provider.
- Approvals offer only what the protocol can carry back (`ApprovalAnswers` on
  `ChatEvent.ToolApprovalRequest` / `ToolPart`): AG-UI and the on-device loop take a reason and
  edited arguments, AI SDK a reason, ACP yes / no. Previously AI SDK approvals showed "Edit and
  approve" and dropped the edits.

- Documentation site (`docs/`, Zensical): getting started, every protocol, guides, architecture,
  development and the API reference (Dokka) under `/api`; `tools/build-docs.sh` builds it, CI
  uploads it. Its code comes from `DocsSamples.kt`, compiled with the demo (which caught a wrong
  generative-UI example in the old README). The README is now a short entry point.

- `AskUser`: Pydantic AI Harness's `ask_user_question` on the device (same schema, limits,
  instruction and result). On-device agents ask directly; agent servers get it as an AG-UI frontend
  tool or an AI SDK client-side tool (the reference server declares it with an `ExternalToolset`).
  Questions arrive as standard JSON Schema; `SchemaForm` shows choices with descriptions as a list
  and accepts the user's own answer (a plain string `anyOf` alternative), plus `minItems` / `maxItems`.

- Voice: "Read aloud" on every reply (platform text-to-speech, Markdown read as prose), dictation
  with input level, cancel and done, and `VoiceMode`, a hands-free conversation over any
  `ChatController` (listens, streams the reply aloud sentence by sentence, tap to interrupt, mute,
  pauses for approvals and questions). `Chat` and `PromptInput(onVoiceMode)` open it from an empty
  composer. `ai-elements-ui` declares the speech services' package-visibility `<queries>`.
- Approvals: **always allow** a tool for the rest of the conversation (`ToolDecision.remember`);
  ACP answers with the agent's `allow_always` option.

- Demo settings are two levels deep: a grouped home with each entry's current state, one page per
  entry, editors as dialogs (full screen on phones), side by side on tablets.
- Fixed: `tools/generate-icons.py` reads each symbol's viewport (`auto_awesome` ships with 24, not
  960, so the assistant sparkle was invisible) and refuses paths outside it.

- Speech engines are configurable: `SpeechSettings` / `LocalSpeechSettings` pick the recognition
  service (or on-device recognition, Android 12+), the text-to-speech engine, voice, rate and pitch
  for dictation, "Read aloud" and voice mode; `recognitionServices()` and `SpeechOutputState.engines`
  / `voices` list what the device has. The demo has a Voice settings page with a preview.

- Demo fonts: a serif UI font (the platform serif, Noto Serif CJK / 思源宋体 for Chinese, adds
  nothing to the APK) and a separate code font with previews: match the UI, system monospace, Geist
  Mono, JetBrains Mono, Fira Code (SIL OFL 1.1, bundled variable fonts).

- Images show upright: decoding applies the EXIF orientation of camera photos (`ImageDecoder` on
  Android 9+, `ExifInterface` before), and tapping one opens `ImageViewer`: pinch zoom, pan,
  double-tap zoom and quarter-turn rotation.

- Video and audio in messages: `VideoAttachment` shows the first frame at the recording's upright
  aspect with its duration and plays full screen (`VideoPlayerDialog`, platform player and
  controls); audio files get the `AudioPlayer`. `data:` media is cached to a file
  (`playableUri`). No new dependencies.

- Documents in messages (`DocumentAttachment`): PDFs show their first page and page count and
  open in `PdfViewerDialog` (platform `PdfRenderer`, every page, pinch zoom); Word, Excel,
  PowerPoint and other files open in an app that can show them (`openExternally`, through the
  library's own `AiElementsFileProvider`, `${applicationId}.aielements.files`). No new dependencies.

- Steps with screenshots (like Manus's computer view): a tool call's screenshots (AI SDK `file`
  parts after the output, AG-UI 1.x media parts in `TOOL_CALL_RESULT`, MCP image content, on-device
  `ToolCallContext.file`) show as a strip under the call. `WebBrowser` attaches a viewport
  screenshot after each page-changing call; the reference server has a `browse` tool that returns one.
- Tool categories: `ToolPart.category` / `location` in the ACP `ToolKind` vocabulary, set upstream
  — ACP `kind` + `locations`, the Pydantic AI Harness file-system and shell tools (mapped as the
  Harness ACP presenter does), `AgentTool.categoryFor` / `locationFor` on device (harness
  `FileSystem`, `Shell`, `WebBrowser` declare theirs). The reference server reads a workspace through
  the Harness `FileSystem` capability (read-only).
- The agent's computer: `AgentComputerCard` (live preview under a working reply) and
  `AgentComputerPanel` (each step by its category: screenshot with address bar, `Terminal`, diff,
  file, page; timeline, autoplay, "back to live"), in `AgentComputerScaffold`: a side pane from
  720dp, a bottom sheet below. Replaces `AgentRunPlayback`.
- AG-UI event logs (AG-UI serialization): `AgUiBackend(eventLog = …)` keeps each thread's events
  (`AgUiEventLog.Files` / `InMemory`), timestamps them, links runs with `parentRunId` and records
  the client's input in `RUN_STARTED.input`; `AgUiEventLog.replayOf(message)` replays a reply
  through the same parser at the recorded pace — `Chat(replay = …)` offers "Replay run".

- Terminal output as a terminal shows it: `TerminalText` (in `ai-elements-chat`) interprets the
  ECMA-48 / xterm subset programs write — 16 / 256 / 24-bit colours and backgrounds, bold, dim,
  italic, underline, inverse, strikethrough, `\r` / `\b` redraws, cursor moves and erases, OSC 8
  links. `Terminal` and tool outputs render it (VS Code's dark and light palettes, 1 000-line
  scrollback); on the device `Shell` gives the model the plain text the terminal would show
  (`TERM=dumb` for Android's shell too).
- AG-UI log compaction: `AgUiEventLog.compact`, ported from the reference `compactEvents`;
  `AgUiEventLog.delete` (the demo deletes a conversation's logs with it).
- ACP replay: `AcpBackend.replayOf` reopens the session with `session/load` on a connection of its
  own and plays the reply's turn; replies record their turn in the `acp` metadata. The reference
  server's ACP agent keeps sessions (Harness `InMemorySessionStore`).
- The agent's computer on long runs: a continuous timeline beyond 20 steps, lazy screenshot strips,
  step views capped at 100 000 characters.
- Docs: "Choose your setup" — which artifacts, backend and UI level for each kind of agent, what
  the agent's computer gets from each protocol, and what the library vs. the app decides for
  storing, compacting, replaying and reconnecting.

- Browser screenshots for the model (Pydantic AI Harness browser contract): `WebBrowser`'s
  `screenshot(full_page?)` tool and `screenshotOnNavigate`, PNG up to 5 MB. Tools return content to
  the model with `ToolCallContext.content` (Pydantic AI `ToolReturn.content`); on-device model loops
  (OpenAI Chat / Responses, Anthropic, Gemini, Ollama) send it after the tool results
  (`runToolWithContent`, `ToolResult`). The steps view's screenshots outline the element an action
  targeted, and are drawn at the page's scroll position. The demo's offline agent runs a scripted
  browser session ("Browse a web page").
- `WebBrowser(viewport = BrowserViewport.Desktop)` by default: 1280 × 720 CSS pixels (Playwright's
  default) with Chrome's "Desktop site" user agent; screenshots at CSS-pixel resolution, so image
  coordinates are click coordinates. The step view opens screenshots full screen.
- `AgentComputerScaffold(layout = …)`: `Auto`, `SidePane`, `BottomSheet`, or `Hosted` for apps that
  place `AgentComputerPanel` themselves. The demo hosts it in the extra pane of M3's
  `ListDetailPaneScaffold`, so on tablets it opens on the right beside the chat. Browser actions
  wait for the navigation they start, as Playwright's auto-waiting does; actions that may navigate
  run after their script returns and every script call is time-limited (a page that unloads never
  answers `evaluateJavascript`).

- Component catalog: the demo's Components screen groups every element in ten categories with a
  filter per group, and adds samples for the agent's computer, step views, input-request forms, JSX
  (layout, forms with bindings and actions, choices / slider / date / tabs, data-driven cards,
  streaming), A2UI, attachments, video, PDF, voice mode, the conversation and its empty state.
  `ComponentCatalogScreenshots` renders each sample and `tools/build-component-docs.py` builds the
  docs site's **Components** section from them (picture, purpose, main API, AI Elements name).
- JSX: `<select>` / `<option>` → ChoicePicker, `<Tabs>` / `<Tab title>` → Tabs, JSON array and object
  literals as property values; the Generative UI guide documents every tag, bindings, actions and
  streaming. The offline agent answers "Generative UI (JSX form)" with a JSX booking form.
- A2UI Row: containers without a `weight` (Card, Column, List) share the row instead of the first
  taking it all.

- Run notifications (new artifact `ai-elements-notifications`, optional since it brings the
  notification permissions; `AgentProgress` itself is in `ai-elements-ui`): `AgentProgress.of(chatState)` (thinking, working, writing, waiting for approval
  or input, done, failed; plan, steps, reply) and `AgentRunNotifications`: an Android 16 Live Update
  while the agent runs (`ProgressStyle` with the plan as segments, promoted to the status bar chip
  with a short text, elapsed time, Stop; a plain progress notification before Android 16), "waiting
  for you" with Review (approvals stay in the chat), and "reply ready" / "stopped" at the end. The
  demo keeps runs going in the background with a `dataSync` foreground service and asks for
  notifications in context.

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
- Icons are Material Symbols Rounded, generated as `ImageVector`s per module by
  `tools/generate-icons.py` (`AiIcons`, only the icons in use); the frozen
  `material-icons-extended` dependency is gone. Tools show what they are with one icon everywhere
  (chat, canvas, agent cards): ƒx for functions, a puzzle piece for a provider's (MCP) tools, a book
  for skills.
- Approvals sit inside the tool call (no card in a card) with **Deny** and **Allow**; the rarer
  answers (always allow, edit and approve, deny with a reason) are in a "⋯" menu, so a card never
  shows five buttons (tags unchanged: `approve-always`, `edit-and-approve`, `deny-with-reason`,
  opened by `approval-more`).
- A reply's action row shows copy, read aloud, regenerate and the agent's computer; the run graph
  and "open in / share" are in its "⋯" menu (`message-more`).
- `ChatEmptyState(subtitle)` is optional (`null` hides it).
- Opening a conversation shows its end in the first frame: the list is positioned before that
  frame's layout, and settled Markdown blocks parse in composition (the block a stream is writing
  still parses off the frame), so replies are laid out at their final height instead of filling in
  over the next frames and pushing the list down.
- Performance: `:benchmark` (Macrobenchmark: startup, scrolling a long answer, streaming, switching
  conversations; frame timing with and without the Baseline Profile) and the demo's Baseline
  Profile (`BaselineProfileGenerator`). History rows are announced as selected.
- Demo: one tap opens a conversation from the history drawer. The drawer slid with the expressive
  spring, which settles for ~0.8 s after it looks still, and a tap in that tail stopped the drawer
  instead of reaching the row; the drawer now uses the standard motion (its content keeps the
  expressive one), and opening it puts the keyboard away. `HistoryTest` covers the flow.
- Links in content (citations, sources, Markdown, A2UI, MCP Apps) open in a Custom Tab with the
  theme's colours (`CustomTabsUriHandler`, provided by `AiElementsTheme`; `openLinksInCustomTabs =
  false` keeps the default browser). `ai-elements-ui` depends on `androidx.browser`.
- `ProviderProfile.Presets` holds the public model APIs only (OpenAI, Anthropic, Gemini, OpenRouter)
  and the offline agent; the reference-server, Ollama and local-proxy profiles moved to the demo
  (`DemoPresets`). `ProviderStore(context, presets)` takes an app's own list.
- `ai-elements-acp` and `ai-elements-koog` ship the R8 rule for Ktor's JVM-only debugger probe, so
  minified apps build without extra rules.
- The offline agent (`MockAgentBackend`) answers in the prompt's language (English, Simplified and
  Traditional Chinese, Japanese), as a model would, and no longer echoes the prompt.
- Translations reviewed with one glossary per language: "human in the loop" is 确认与提问 /
  確認與提問 / 承認と質問; agent is 智能体 / 代理 / エージェント throughout; `ai-elements-genui` and
  `ai-elements-mcp-apps` strings are resources (were English literals).
- Demo, after a user-journey review on phone and tablet: new chat is on the chat's top bar (disabled
  on an empty chat) and at the bottom of the history drawer, in the thumb's reach, not a
  top-left FAB; the history has a title and search on top; four task-shaped starter prompts instead
  of eight feature names; the provider sheet shows protocol and model without repeating the name
  or "default"; the token count shows once, under the reply. `UxWalkthroughScreenshots` (opt-in
  `-e ux true -e size phone|tablet`) captures the journey for review.
- Things that belong on one line stay on one line: short inline code, "number unit" pairs,
  the composer's model chip, settings switch rows (no selected tint).

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
