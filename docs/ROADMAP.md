# Roadmap and workstreams

The goals, decisions and status of each workstream, kept current as work lands.
The goal and the order of work: [GOAL.md](GOAL.md). Rules for all of them: [AGENTS.md](../AGENTS.md). Open protocols only, official SDKs over hand-written code, and every
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
| `ai-elements-core` | Chat model, `ChatController`, protocol clients (AI SDK, AG-UI, MCP), model APIs, agent loop, capabilities (`SubAgents`, `Skills`), OAuth | kotlinx, OkHttp |
| `ai-elements-ui` | Compose elements, theme | core |
| `ai-elements-a2a` | A2A through the official `a2a-java-sdk` (optional: protobuf, Gson, desugaring) | core |
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
| Real-model runs | ⬜ proxy upstreams are dead (435 / revoked); `CODEX_AUTH_FILE` option added — waiting for the user to start it with the authorized account |

### W4 · UI elements

| Item | Status |
|---|---|
| `Subagent` element (nested run, live activity, nested approvals) for `delegate_task` / AG-UI subagents / AI SDK `UIMessage` outputs | ✅ component tests 10/10 on emulator; Gallery sample |
| Tool titles (`title`), preliminary output, skill and MCP badges in `ToolCall` | ✅ |
| AG-UI state / activity rendering (`state.plan` → `Plan`) | ✅ `DataPartView` renders the `state` part's `plan` / `task` / `chain-of-thought` keys (rest as JSON), activity types case-insensitively; component test + AG-UI E2E (server STATE_SNAPSHOT → Plan) |
| A2A agent card view (extend `Agent`) | ✅ `description` + `toolsTitle` (skills) |
| UI review matrix (GOAL W4 DoD): light/dark × en/zh-CN/zh-TW/ja × phone/tablet | ✅ `ScreenshotMatrixTest` (opt-in `-e screenshots true`, `-e size tablet` after `wm size 2560x1600`); 16 shots reviewed: translations, dark Mermaid/code, list-detail tablet layout OK. An early collapsed-table/diagram artefact was the Compose test clock (streaming fade-ins frozen during `Thread.sleep`); the test now advances `mainClock` first — all 16 shots correct |

### W4b · Generative UI (open specs)

Agents that return interface, not just text. Layering: `ai-elements-ui` knows no UI format;
`ai-elements-genui` holds a neutral node tree (`UiNode`) + component registry (Compose) shared by the
formats; transports map onto neutral data parts in `core` / `a2a`; MCP Apps (host side, needs the MCP
client) is its own artifact.

| Item | Spec / reference | Status |
|---|---|---|
| `WorkflowCanvas` slots: custom node content, toolbar / panel | AI Elements canvas, node, controls, panel, toolbar | ✅ `nodeContent`, `nodeToolbar` (on selection), `panel`, per-node `size` / `data`, temporary edges; component test |
| `ai-elements-genui` base: component catalog (Material, app-extensible), data scopes, actions | A2UI v1.0 catalog model (the shared base; JSX compiles onto it) | ✅ `A2uiCatalog` (`Basic` + `extend`), `ComponentScope`, `DataContext` |
| `JsxPreview`: streaming-tolerant JSX subset → `UiNode`, bindings only (no code execution) | AI Elements `jsx-preview` (`react-jsx-parser`) | ⬜ |
| A2UI surface: messages → surface model, data binding, user actions; transports (AG-UI, A2A) → `DataPart` | A2UI v1.0 (release candidate) + the Basic Catalog's 43 official examples | 🟡 renderer ✅: all 18 Basic Catalog components, 14 functions + `@index`, `formatString`, templates / relative scopes, two-way binding, checks, actions (+ `sendDataModel`), `callRendererFunction` gate, `openUrl` activation + scheme rules; unit tests replay all 43 examples; all 43 render on the emulator (screens reviewed), login-form interaction test. Transports ⬜ |
| MCP Apps host (`ai-elements-mcp-apps`): `ui://` resources, sandboxed WebView, `ui/*` JSON-RPC bridge | MCP Apps extension + official `ext-apps` SDK fixtures | ⬜ |

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

| Item | Status |
|---|---|
| Review module and package layering: core packages by concern (`chat`, `protocol.aisdk`, `protocol.agui`, `provider.*`, `http`, `agent`, `mcp`, `skills`, `auth`, `config`) | ✅ 98 unit tests + 11 live tests green after the move |
| README, CHANGELOG, NOTICE (a2a-java-sdk, kotlin-json-patch, proot) | ✅ |
| API reference and CI | ✅ Dokka 2.2 over every published module (`./gradlew :dokkaGenerate`); CI: unit tests, lint, publish, Dokka, emulator E2E (x86_64, now with a bundled x86_64 PRoot) |
| Public API tracking (API review, GOAL W6) | ⬜ blocked by tooling: Kotlin 2.4 `abiValidation()` finds no compiled classes under AGP 9 built-in Kotlin ("provider has no value"), and binary-compatibility-validator 0.18.2 needs the `kotlin-android` plugin AGP 9 no longer uses. Adopt the official one once it supports AGP 9 built-in Kotlin; no hand-rolled checker |
| One-line integration and samples | ✅ `Chat(controller)` / `rememberChat(backend)` (component test); `samples/pure-client` (verified on emulator against the AG-UI server) and `samples/in-app-agent` |
| Protocol-independent UI layer and extension points | ✅ `ToolKind` / `source` in the model, conventions mapped in `core`; UI imports only `core.model` / `core.chat` (enforced by `LayeringTest`); `LocalAiElementsRenderers` + `LocalFileLoader` (component test); core 1 new test class, ElementsTest 13/13, demo E2E 20/20 on emulator |
| Artifact split: `ai-elements-chat` (models + controller) under both `ai-elements-core` and `ai-elements-ui` | ✅ UI runtime classpath has no OkHttp / AG-UI / JSON Patch; all modules build, lint clean, ElementsTest 14/14, demo E2E 11/11 |

## Decisions log

| Date | Decision | Why |
|---|---|---|
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
