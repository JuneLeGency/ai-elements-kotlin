# Roadmap and workstreams

The goals, decisions and status of each workstream, kept current as work lands.
The goal and the order of work: [GOAL.md](https://github.com/JuneLeGency/ai-elements-kotlin/blob/main/GOAL.md). Rules for all of them: [AGENTS.md](https://github.com/JuneLeGency/ai-elements-kotlin/blob/main/AGENTS.md). Open protocols only, official SDKs over hand-written code, and every
protocol change is verified against a real implementation.

Status: ✅ done and verified · 🟡 in progress · ⬜ planned

## Positioning

AI Elements for Kotlin is the **UI layer and protocol clients** for AI apps on Android, the Compose
counterpart of Vercel AI Elements. It supports two deployment modes that share one UI:

| Mode | Where the agent runs | What the app uses |
|---|---|---|
| **Frontend-only** | Server: Pydantic AI + Pydantic AI Harness (or any AI SDK / AG-UI / A2A agent) | `ai-elements-ui` + core protocol clients |
| **In-app harness** | On the device | the `io.github.junelegency.harness` artifacts (mirroring Pydantic AI Harness), the built-in loop or Koog |

Ecosystem map: server runtime = Pydantic AI + Harness; Kotlin agent runtime = JetBrains Koog;
UI = this library.

## Module layout (target)

| Module | Role | Depends on |
|---|---|---|
| `ai-elements-chat` | Chat models, controller, events and backend contracts; no networking or Compose | kotlinx |
| `ai-elements-core` | Protocol clients (AI SDK, AG-UI, MCP), model APIs, agent loop, capabilities (`SubAgents`, `Skills`), OAuth | chat, kotlinx, OkHttp |
| `ai-elements-ui` | Compose elements, theme | chat |
| `ai-elements-a2a` | A2A through the official `a2a-java-sdk` (optional: protobuf, Gson, desugaring) | core |
| `ai-elements-acp` | Agent Client Protocol through the official ACP Kotlin SDK (optional: Ktor, kotlinx-io) | core |
| `harness/*` (group `io.github.junelegency.harness`) | The in-app agent harness, one artifact per capability (see W2b) | core |
| `ai-elements-koog` | Koog agents streaming into the UI; capabilities as Koog tools | core |
| `ai-elements-mermaid-native` | Mermaid via Compose Canvas (optional) | ui |

## Workstreams

### W1 · Protocol clients (open specs)

| Item | Library / spec | Status |
|---|---|---|
| AI SDK 6 UI Message Stream: full `UIMessage` history, tool approval (`tool-approval-request` → `approval-responded`), client-side tools, preliminary output, `UIMessage` sub-agent output, metadata | AI SDK 6 | ✅ live (LiveHarnessServerTest) + recorded fixtures (RecordedProtocolTest) |
| AG-UI 1.0: frontend tools, interrupts + `resume`, `SUBAGENT_*`, state / activity (JSON Patch), steps, `RUN_FINISHED.usage` | AG-UI 1.0, official `kotlin-core` types, `kotlin-json-patch` | ✅ live 5/5 + recorded fixtures incl. official-encoder SUBAGENT/STATE_DELTA/ACTIVITY/usage |
| ✅ decoding on the official `com.ag-ui.community:kotlin-core` types. ⬜ upstream PR for `SUBAGENT_*` / `ACTIVITY_*` and the kotlinx-datetime 0.7 fix (#2772) | ag-ui Kotlin SDK | ⬜ |
| MCP 2026-07-28 stateless + legacy session fallback, `Mcp-Method` / `Mcp-Name` / `x-mcp-header`, progress, OAuth discovery (RFC 9728 / 8414 / 7591 / 8707) | hand-written (official Kotlin SDK stops at 2025-11-25); switch when it ships 2026-07-28 | ✅ live vs official `mcp` 2.2 (stateless) and `mcp` 1.x (legacy fallback) + unit tests; OAuth ✅ live against the official `mcp` SDK OAuth server (`server/mcp_auth_server.py`: 401 → RFC 9728 → RFC 8414 → RFC 7591 → code + PKCE S256 + RFC 8707 → token → call → refresh) |
| A2A 1.0 (0.3 compatible) as provider and as sub-agent | official `a2a-java-sdk` + its Android HTTP client | ✅ live 3/3 against the official Python `a2a-sdk` |
| Agent Client Protocol v1 as provider: sessions per conversation (`session/load`), updates, permissions, `session/cancel`, plans, client file system | official ACP Kotlin SDK 0.30 (stdio; SDK WebSocket transport until the spec's Streamable HTTP lands) | ✅ fixtures recorded with the official ACP Python SDK client (`AcpRecordedTest` 5/5), live stdio + WebSocket vs the Pydantic AI Harness ACP adapter (`LiveAcpTest` 2/2), demo E2E (`acp_planThenPermission_inOneSession`) |

### W1b · Human in the loop

One neutral model (`ToolApprover.decide` / `input`, `ToolDecision`, `InputRequest` / `InputResponse`,
`ChatState.inputRequests`) behind every protocol's approval and "ask the user" mechanism; the UI
(`Confirmation`, `InputRequestCard` / `SchemaForm`) never knows which protocol asked.

| Item | Spec | Status |
|---|---|---|
| Approve / deny tool calls | AI SDK 6 tool approval, AG-UI interrupts, on-device `requiresApproval`, MCP apps | ✅ (since W1) |
| Approvals offer only what the protocol carries back (`ApprovalAnswers`) | AI SDK: reason; ACP: yes / no; AG-UI and on-device: reason + edits | ✅ |
| Deny with a reason, edit arguments before approving | AG-UI approve-with-edits resume payload `{approved, editedArgs, reason}` (as Pydantic AI advertises it); AI SDK 6 `approval.reason`; on-device loop | ✅ fixtures (`RecordedProtocolTest`), unit (`HumanInTheLoopTest`), UI (`ElementsTest`), E2E on the reference server (`agUi_interrupt_editArgumentsBeforeApproving`, `aiSdk_approval_denyWithAReason`) |
| Agent asks the user (form from a JSON Schema, URL, confirmation) | AG-UI interrupts with `responseSchema` (resume `resolved` + payload / `cancelled`, `expiresAt`) | ✅ fixtures from the official `ag_ui` encoder |
| MCP elicitation (form and URL modes) | 2026-07-28 `InputRequiredResult` (SEP-2322: `inputResponses` + `requestState`, up to 10 rounds); 2025-xx `elicitation/create` on the session stream | ✅ fixtures + live on both eras (official `mcp` 2.x and 1.x SDKs); E2E: on-device agent → MCP `book_table`, edited approval, then the elicitation form |

### W2 · Agent capabilities (Harness parity)

| Capability | Contract (same as Pydantic AI Harness) | Status |
|---|---|---|
| Sub-agents | `delegate_task(agent_name, task)`, static roster instruction | ✅ unit tests (nested run, shared approver); on-device real-model run pending |
| Skills | Agent Skills `SKILL.md`; `load_capability(id)` → `# Skill: <name>` | ✅ unit tests (parse rules, zip install, zip slip) |
| MCP tools | `<server>__<tool>`, approval unless `readOnlyHint` | ✅ live (McpToolset across servers, failure isolation) |
| Planning | `write_plan`, `read_plan`, `add_task`, `update_task_status(es)`, `remove_task` | ✅ `harness-planning` |
| FileSystem | `read_file`, `write_file`, `edit_file`, `list_directory`, `search_files`, `find_files`, `create_directory`, `file_info` | ✅ `harness-filesystem` (+ SAF mounts) |
| Shell | `run_command`, `start_command`, `check_command`, `stop_command` | ✅ `harness-shell` + `harness-sandbox-proot` |
| Memory | `write_memory`, `read_memory`, `delete_memory`, `search_memory` | ✅ `harness-memory` |
| Koog adapter | Koog agent → `ChatBackend`; capabilities → Koog tools | ✅ `ai-elements-koog` (`KoogBackend`, `KoogTool`, JSON Schema → Koog descriptors) on Koog 1.3.0; tests on Koog's mock executor (tool events, approval); demo "Run on JetBrains Koog" switch (Koog OpenAI / Anthropic / Ollama clients on Ktor OkHttp) ✅; real-model E2E written (opt-in `-e ollama`), not yet green: local Ollama is CPU-only (~0.3 tok/s) |
| (Planning, FileSystem, Shell, Memory move to the harness group, W2b) | | |

### W2b · In-app harness (group `io.github.junelegency.harness`)

A separate Maven group under `harness/`, one artifact per capability. Each implements the
`Capability` / `AgentTool` contract of `ai-elements-core` with the tool names and semantics of
Pydantic AI Harness, so on-device and server agents behave and render alike. The capability set
covers what OpenMinis offers on Android, **re-implemented** (OpenMinis is GPL-3.0: its code is
reference only, never copied).

| Artifact | Capability | OpenMinis counterpart | Status |
|---|---|---|---|
| `harness-core` | `AgentHarness`: model + capabilities → `ChatBackend`; local / remote sub-agents, `maxDepth`; `ModelBinding` for any provider | agent loop | ✅ unit tests; the demo runs on it |
| `harness-filesystem` | Workspace + SAF-mounted folders: `read_file`, `write_file`, `edit_file`, `list_directory`, `search_files`, `find_files`, `create_directory`, `file_info` | file tools, mounted folders | ✅ workspace (5 tests: format, hashes, sandbox, protected, approvals); SAF mounts at `/mnt/<name>` (`SharedFolders`, `Mount`/`FolderNode`) ✅ unit test + E2E through the system picker on emulator |
| `harness-shell` | `run_command`, `start_command`, `check_command`, `stop_command` over a pluggable `ShellRuntime` | `shell_execute` | ✅ 4 tests (Harness output format, timeout, background, approval by isolation) |
| `harness-sandbox-proot` | Alpine Linux via upstream proot (separate process, GPL-2 binary + source offer), rootfs downloaded on first use | proot sandbox | ✅ reproducible NDK build (`native/build-proot.sh <abi>`, arm64-v8a + x86_64; the Alpine image follows the device ABI); in-app test on emulator (targetSdk 36): install + commands + shared /workspace; `apk add python3` verified |
| `harness-memory` | `write_memory`, `read_memory`, `delete_memory`, `search_memory` (file store) | memory tools | ✅ 4 tests; `<memory>` injection via `Capability.context()` |
| `harness-planning` | `write_plan`, `read_plan`, `add_task`, `update_task_status(es)`, `remove_task` | — | ✅ 3 tests; live `data-plan` via `ToolCallContext.data` |
| `harness-browser` | Harness browser tools (`navigate`, `snapshot`, `click`, `type_text`, `press_key`, `select_option`, `hover`, `wait_for`, `get_text`, `scroll`, `go_back`, `go_forward`) on an off-screen WebView | `browser_use` | ✅ in-app E2E on emulator (form fill + submit + history) |
| `harness-device` | Device info, clipboard, calendar, contacts, location, alarms / timers, notifications (runtime permissions via `PermissionGate`, approvals for changes) | device integrations | ✅ on-device test against the real providers |
| `harness-speech` | `speak` / `stop_speaking` on the platform TextToSpeech (speech input stays in the UI: `SpeechInput`) | speech | ✅ on-device test (emulator TTS) |
| `harness-scheduler` | `schedule_task` / `list_scheduled_tasks` / `cancel_scheduled_task` on WorkManager; `ScheduledAgentHost` on the Application; headless runs (`runHeadless`) deny approval tools; result notification | scheduled agents | ✅ E2E on emulator (real WorkManager, offline provider) |

### W3 · Reference server (`server/`)

| Item | Status |
|---|---|
| Pydantic AI 2.51 + Harness 0.36: `Planning`, `SubAgents(researcher, writer)`, `Skills(../skills)`, MCP toolset with approval | ✅ |
| AI SDK 6 (`/api/chat`) and AG-UI 1.0 (`/api/agui`): approvals verified (tool-approval-request / interrupt) | ✅ |
| Plan mirrored as `data-plan` (AI SDK) / `STATE_SNAPSHOT` (AG-UI) | ✅ |
| MCP server (official `mcp` 2.2, both eras) at `/mcp` | ✅ verified with curl |
| A2A agent (official `a2a-sdk` 1.1, 1.0 + 0.3 compat) at `/a2a` | ✅ |
| Keyword-scripted offline model exercising every capability (for fixtures and E2E) | ✅ |
| Real-model runs | ⬜ not yet run end to end with a hosted model; the server takes any OpenAI-compatible endpoint (`AGENT_BASE_URL`, `AGENT_API_KEY`, `AGENT_MODEL`) or a Codex sign-in (`CODEX_AUTH_FILE`) |

### W4 · UI elements

| Item | Status |
|---|---|
| `Subagent` element (nested run, live activity, nested approvals) for `delegate_task` / AG-UI subagents / AI SDK `UIMessage` outputs | ✅ component tests 10/10 on emulator; Gallery sample |
| Tool titles (`title`), preliminary output, skill and MCP badges in `ToolCall` | ✅ |
| AG-UI state / activity rendering (`state.plan` → `Plan`) | ✅ `DataPartView` renders the `state` part's `plan` / `task` / `chain-of-thought` keys (rest as JSON), activity types case-insensitively; component test + AG-UI E2E (server STATE_SNAPSHOT → Plan) |
| A2A agent card view (extend `Agent`) | ✅ `description` + `toolsTitle` (skills) |
| User-journey review (phone + tablet): first launch, history, provider, reply, approval, settings, components | ✅ `UxWalkthroughScreenshots` (opt-in `-e ux true -e size phone\|tablet`); fixed: top-left new-chat FAB → top bar + bottom of the drawer, approval answers (5 buttons → Deny / Allow + menu), reply actions (6 icons → 4 + menu), duplicate token chip, starter prompts, provider subtitles, English-only offline replies, HITL / agent terms in zh-CN / zh-TW / ja |
| UI review matrix (GOAL W4 DoD): light/dark × en/zh-CN/zh-TW/ja × phone/tablet | ✅ `ScreenshotMatrixTest` (opt-in `-e screenshots true`, `-e size tablet` after `wm size 2560x1600`); 16 shots reviewed: translations, dark Mermaid/code, list-detail tablet layout OK. An early collapsed-table/diagram artefact was the Compose test clock (streaming fade-ins frozen during `Thread.sleep`); the test now advances `mainClock` first — all 16 shots correct |

### W4b · Generative UI (open specs)

Agents that return interface, not just text. Layering: `ai-elements-ui` knows no UI format;
`ai-elements-genui` holds the A2UI component model + catalog (Compose) that JSX compiles onto; transports map onto neutral data parts in `core` / `a2a`; MCP Apps (host side, needs the MCP
client) is its own artifact.

| Item | Spec / reference | Status |
|---|---|---|
| `WorkflowCanvas` slots: custom node content, toolbar / panel | AI Elements canvas, node, controls, panel, toolbar | ✅ `nodeContent`, `nodeToolbar` (on selection), `panel`, per-node `size` / `data`, temporary edges; component test |
| `ai-elements-genui` base: component catalog (Material, app-extensible), data scopes, actions | A2UI v1.0 catalog model (the shared base; JSX compiles onto it) | ✅ `A2uiCatalog` (`Basic` + `extend`), `ComponentScope`, `DataContext` |
| `JsxPreview`: streaming-tolerant JSX subset compiled onto A2UI components, bindings only (no code execution) | AI Elements `jsx-preview` (`react-jsx-parser`) | ✅ `JsxCompiler` (catalog + HTML tags, inline Markdown runs, handlers → actions, every-prefix streaming test), `JsxPreview` (state kept across stream updates), `jsxCodeBlocks()` for ```` ```jsx ```` fences via the new `AiElementsRenderers.codeBlocks` extension point; unit 5/5, emulator 2/2 |
| A2UI surface: messages → surface model, data binding, user actions; transports (AG-UI, A2A) → `DataPart` | A2UI v1.0 (release candidate), the official cross-language conformance suite, the Basic Catalog's 43 examples, `@ag-ui/a2ui-middleware`, the A2UI A2A extension | ✅ renderer (conformance: expressions 39/39, data model 41/41, message processor 22/22; 2 strict-schema cases are the validator's; fixed 8 spec gaps incl. component cycles → atomic rollback); transports ✅ AG-UI `a2ui-surface` activities ⇄ `forwardedProps.a2uiAction`, A2A `application/a2ui+json` parts + `a2uiRendererDataModel`, AI SDK `data-a2ui`, all onto neutral `DataPart.A2UI`; `a2uiRenderer()` + `ChatController.send(action)`; live server (`server/a2ui_demo.py`) + demo E2E ✅ (`CapabilitiesFlowTest` AG-UI and AI SDK form round trips, also passing with gpt-5.5 as the server model); A2A live ✅ (`/concierge` agent with the A2UI extension on its card, `LiveA2aTest.a2uiSurface_andActionRoundTrip`); recorded fixtures `agui|aisdk/hotel*.sse` validated by the official `a2ui-core` |
| MCP Apps host (`ai-elements-mcp-apps`): `ui://` resources, sandboxed WebView, `ui/*` JSON-RPC bridge | MCP Apps 2026-01-26 + official `ext-apps` 2.0.3 SDK | ✅ `McpAppBridge` (replays an official `App` ↔ `AppBridge` session, `server/mcp_apps/record_fixtures.mjs`; visibility, approval, link, CSP-injection and resource checks; unit 5/5), `McpAppSandbox` (origin per server, CSP header, message port), `McpAppView` / `McpAppsHost` (sessions survive scrolling, root-level dialogs, full screen); core: negotiation, `_meta.ui`, app-only tools hidden, `DataPart.MCP_APP` / `MODEL_CONTEXT`; live `LiveMcpTest.modernServer_mcpApps` (official Python SDK); emulator `McpAppsTest` (official-SDK view: render, approved write, app-only refresh, CSP block, `ui/message` + model context); real model: `CodexLiveTest.chatgptSubscription_mcpAppOnDevice` (gpt-5.5 on a ChatGPT subscription picks the MCP tool, the view renders) ✅ |

### W5 · Demo app

The demo is a showcase of both modes, switchable per conversation:
- **In-app harness**: a Kotlin agent on the device with the `harness/*` capabilities (files, Linux
  sandbox, memory, planning, browser, device) over any model API (OpenAI, Anthropic, Gemini, Ollama,
  ChatGPT / OpenRouter sign-in).
- **Pure client** (like the Codex and Claude mobile apps): the agent runs remotely — an AI SDK or
  AG-UI server (e.g. `server/`), or an A2A agent — and the app only renders sessions, approvals,
  plans, sub-agents and artifacts.

| Item | Status |
|---|---|
| Backend picker: in-app harness · AI SDK server · AG-UI server · A2A agent · direct model API | ✅ provider kinds incl. A2A; remote servers get device-only tools, the in-app harness gets skills + sub-agents |
| Settings: MCP servers (add, test, tools, approval policy, OAuth sign-in), Skills (bundled + zip import), Agents (sub-agents, remote A2A agents) | ✅ verified on emulator (MCP 2026-07-28 connect, 5 tools; live A2A card); MCP OAuth flow verified live against the official SDK's authorization server (library level; the in-app browser step is the same `OAuthClient` flow) |
| Composer sheet to toggle MCP servers / skills / agents per chat | ✅ `CapabilitiesSheet` (composer button, badge count) |
| Bundled skills from `/skills` (shared with the server) | ✅ `bundleSkills` Gradle task |
| E2E on emulator and Xiaomi Pad: every capability over each protocol | 🟡 emulator: CapabilitiesFlowTest 4/4 (AG-UI delegation + interrupt approval, AI SDK skill + plan, A2A), AgentFlowTest, SandboxTest, BrowserTest, DeviceToolsTest, SpeechSchedulerTest 3/3, SharedFoldersTest (system picker); Xiaomi Pad and in-app harness with a real model pending (local Ollama too slow; Codex needs the authorized account) |

### W6 · Library structure

Public launch (2026-10-09): 🟡 keep the existing repository and GitHub Pages project site; add Chinese onboarding and all component examples alongside English. Prepare Central publication of the 20 libraries and BOM. Previous library/API/device checks remain the baseline; validate changed documentation and release automation. Local bilingual site validation passes for 92 guide pages and 20 API modules; the 65 translated component examples match the compiled English catalog. ReleaseScreenshotsTest passes on the emulator and supplies four English/Chinese light/dark screenshots. All 103 fetched Git commits pass the secret scan (one reviewed vendored-JavaScript false positive is narrowly excluded). Repository is public and main contains the bilingual launch commit. GitHub Pages uses Actions with PAGES_ENABLED=true. Central namespace io.github.junelegency is Verified; all four publishing/signing secrets are configured. The RSA-4096 signing public key is published on keyserver.ubuntu.com (fingerprint 5D26B0524F9F077915D31EB6789B9FAC6C80D696). Remote CI, hosted-site verification and the first Central/GitHub release remain in progress.

| Item | Status |
|---|---|
| Review module and package layering: core packages by concern (`chat`, `protocol.aisdk`, `protocol.agui`, `provider.*`, `http`, `agent`, `mcp`, `skills`, `auth`, `config`) | ✅ 98 unit tests + 11 live tests green after the move |
| README, CHANGELOG, NOTICE (a2a-java-sdk, kotlin-json-patch, proot) | ✅ |
| Documentation site: Zensical (`zensical.toml`, `docs/`), code from compiled `DocsSamples.kt` sections, Dokka API under `/api`, screenshots from `ScreenshotMatrixTest`; built in CI | ✅ builds locally and in CI; ⬜ publishing (GitHub Pages) once the repository is public |
| API reference and CI | ✅ Dokka 2.2 over every published module (`./gradlew :dokkaGenerate`); CI: unit tests, lint, publish, Dokka, emulator E2E (x86_64, now with a bundled x86_64 PRoot) |
| Open-source readiness | ✅ CI green end to end (unit, lint, release build with R8, Maven Local, docs; emulator E2E with the reference server: demo 42, ui 30, genui 4, notifications 3 tests, and `tools/check-android-test-results.py` fails a run whose test process died with 0 tests — it had hidden a crashed demo run); release workflow on `vX.Y.Z` tags (Maven Central + GitHub release with the demo APK); Pages deploy behind `PAGES_ENABLED`; CODE_OF_CONDUCT, issue / PR templates, Dependabot, README badges, [releasing guide](../develop/releasing.md); demo-only provider presets out of the library. ⬜ maintainer: Central namespace + signing secrets, making the repository public, enabling Pages |
| First release readiness (2026-10-07) | ✅ local validation: 20 BCV baselines + negative drift check; unit tests 237 (36 opt-in skips, no failures), lint and demo R8 release; isolated Maven consumers (UI-only, pure client, in-app agent, all integrations) debug + R8 release + lint; all 65 component examples compile against published AARs; 45 guide pages / 20 non-empty Dokka modules and links checked; emulator API 37 ElementsTest 33/33 and reference-server CapabilitiesFlowTest 10/10. Release validator 5/5 and actionlint pass. One transient Compose hierarchy failure passed both isolated and full reruns; recorded, not hidden by a skip. Remote CI, Central namespace/signing and Pages deployment still require maintainer release execution. |
| Public API tracking (API review, GOAL W6) | ✅ Official BCV 0.18.2 tasks wired to AGP public release AAR artifacts (no custom ABI parser); all 20 library baselines generated. Negative check rejects a removed declaration. Compatibility policy freezes existing model signatures and sealed/enum branches. SSE events, JSX compiler and expression parser are internal; native Mermaid is explicit opt-in. |
| One-line integration and samples | ✅ `Chat(controller)` / `rememberChat(backend)` (component test); `samples/pure-client` (verified on emulator against the AG-UI server) and `samples/in-app-agent` |
| Protocol-independent UI layer and extension points | ✅ `ToolKind` / `source` in the model, conventions mapped in `core`; UI imports only `core.model` / `core.chat` (enforced by `LayeringTest`); `LocalAiElementsRenderers` + `LocalFileLoader` (component test); core 1 new test class, ElementsTest 13/13, demo E2E 20/20 on emulator |
| Agent's computer (Manus-style) and replay | ✅ tool categories (ACP `ToolKind`) set upstream, fixtures recorded from the Harness ACP adapter and AI SDK server (`read_file`); `AgentComputerCard` / `AgentComputerPanel` / `AgentComputerScaffold` (side pane ≥ 720dp, bottom sheet below); AG-UI event log per the serialization spec with replay through the same parser (`RecordedProtocolTest`); ElementsTest 28/28 on the tablet |
| Terminal output, log compaction, ACP replay, long content | ✅ `TerminalText` (ECMA-48 / xterm subset) with fixtures recorded from `ls`, `git`, curl and Rich; `AgUiEventLog.compact` ported from the reference `compactEvents` and checked against its test cases and recorded runs; ACP `session/load` replay (fixture from the Harness adapter, live over WebSocket); phone portrait (sheet), phone landscape and tablet (side pane) reviewed from screenshots; 300 steps / 20 000-line output test; ElementsTest 29/29 on the tablet |
| Component catalog, categories and JSX | ✅ Components screen in ten categories (FilterChip per group), 65 samples incl. agent's computer, forms, JSX ×5, A2UI, media, voice mode; docs **Components** section generated from device screenshots of the same samples (`ComponentCatalogScreenshots`, `tools/build-component-docs.py`); JSX `select`/`Tabs`/JSON literals with unit tests; `GalleryTest` and a JSX end-to-end test on the tablet |
| Artifact split: `ai-elements-chat` (models + controller) under both `ai-elements-core` and `ai-elements-ui` | ✅ UI runtime classpath has no OkHttp / AG-UI / JSON Patch; all modules build, lint clean, ElementsTest 14/14, demo E2E 11/11 |

## Decisions log

| Date | Decision | Why |
|---|---|---|
| 2026-10-07 | First public release preserves unmarked APIs even in 0.x; use official BCV tasks on release AARs to bridge AGP 9 discovery, retain current data-class and sealed model contracts | User requested library compatibility from first publication; no custom ABI parser |
| 2026-09-27 | No private wire formats; recorded in AGENTS.md | User requirement |
| 2026-09-27 | Server on Pydantic AI Harness capabilities, latest versions | Best practice, no hand-rolled agent features |
| 2026-09-27 | JSON Patch: `io.github.reidsync:kotlin-json-patch` | Same library as the AG-UI Kotlin SDK |
| 2026-09-27 | MCP: keep the hand-written dual-era client | Official Kotlin SDK lacks 2026-07-28 |
| 2026-09-27 | A2A: official `a2a-java-sdk` in an optional module | Official A2A 1.0 SDK; heavy dependencies stay optional |
| 2026-09-27 | AG-UI: official `kotlin-core` types + upstream contribution | Official SDK, fill the gaps upstream |
| 2026-09-27 | Koog via an adapter module | Koog is the Kotlin agent runtime; we are the UI layer |
| 2026-09-27 | Koog runs as a harness model binding (`KoogBackend`) | Koog drives the same capabilities; tool calls reuse core's `runTool`, so approvals and progress look identical. Next candidate: Koog's LiteRT client for on-device models |
| 2026-09-27 | OpenMinis (GPL-3.0): ideas only, no code; sandbox uses upstream proot as a separate process | Licence compatibility with Apache-2.0 |
| 2026-09-27 | In-app harness is its own Maven group `io.github.junelegency.harness`, one artifact per capability, covering OpenMinis' capability set | User requirement; apps pick only what they need |
| 2026-09-27 | AG-UI: official `kotlin-core` 0.4.1 typed events; 1.0 gaps (SUBAGENT_*, subagentRunId, RUN_FINISHED.usage / pendingToolCallIds, array tool results) read from raw JSON until upstream | Official SDK first |
| 2026-09-28 | ACP through the official Kotlin SDK in `ai-elements-acp`; server via Pydantic AI Harness's ACP adapter | Official SDKs; ACP defines only stdio, so remote agents use the SDK's WebSocket transport and are documented as such |
| 2026-09-28 | Docs site on Zensical (successor of Material for MkDocs, which is in maintenance until 2027-05) with snippets from compiled sources | Current best practice; docs cannot drift from the API |
