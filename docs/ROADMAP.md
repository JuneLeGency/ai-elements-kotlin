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
| MCP 2026-07-28 stateless + legacy session fallback, `Mcp-Method` / `Mcp-Name` / `x-mcp-header`, progress, OAuth discovery (RFC 9728 / 8414 / 7591 / 8707) | hand-written (official Kotlin SDK stops at 2025-11-25); switch when it ships 2026-07-28 | ✅ live vs official `mcp` 2.2 (stateless) and `mcp` 1.x (legacy fallback) + unit tests; OAuth discovery untested live |
| A2A 1.0 (0.3 compatible) as provider and as sub-agent | official `a2a-java-sdk` + its Android HTTP client | ✅ live 3/3 against the official Python `a2a-sdk` |

### W2 · Agent capabilities (Harness parity)

| Capability | Contract (same as Pydantic AI Harness) | Status |
|---|---|---|
| Sub-agents | `delegate_task(agent_name, task)`, static roster instruction | ✅ unit tests (nested run, shared approver); on-device real-model run pending |
| Skills | Agent Skills `SKILL.md`; `load_capability(id)` → `# Skill: <name>` | ✅ unit tests (parse rules, zip install, zip slip) |
| MCP tools | `<server>__<tool>`, approval unless `readOnlyHint` | ✅ live (McpToolset across servers, failure isolation) |
| Planning | `write_plan`, `read_plan`, `add_task`, `update_task_status(es)`, `remove_task` | ⬜ harness module |
| FileSystem | `read_file`, `write_file`, `edit_file`, `list_directory`, `search_files`, `find_files`, `create_directory`, `file_info` | ⬜ harness module |
| Shell | `run_command`, `start_command`, `check_command`, `stop_command` | ⬜ harness + sandbox |
| Memory | `write_memory`, `read_memory`, `delete_memory`, `search_memory` | ⬜ harness module |
| Koog adapter | Koog agent → `ChatBackend`; capabilities → Koog tools | ⬜ |
| (Planning, FileSystem, Shell, Memory move to the harness group, W2b) | | |

### W2b · In-app harness (group `io.github.junelegency.harness`)

A separate Maven group under `harness/`, one artifact per capability. Each implements the
`Capability` / `AgentTool` contract of `ai-elements-core` with the tool names and semantics of
Pydantic AI Harness, so on-device and server agents behave and render alike. The capability set
covers what OpenMinis offers on Android, **re-implemented** (OpenMinis is GPL-3.0: its code is
reference only, never copied).

| Artifact | Capability | OpenMinis counterpart | Status |
|---|---|---|---|
| `harness-filesystem` | Workspace + SAF-mounted folders: `read_file`, `write_file`, `edit_file`, `list_directory`, `search_files`, `find_files`, `create_directory`, `file_info` | file tools, mounted folders | ⬜ |
| `harness-shell` | `run_command`, `start_command`, `check_command`, `stop_command` over a pluggable `ShellRuntime` | `shell_execute` | ⬜ |
| `harness-sandbox-proot` | Alpine Linux via upstream proot (separate process, GPL-2 binary + source offer), rootfs downloaded on first use | proot sandbox | ⬜ |
| `harness-memory` | `write_memory`, `read_memory`, `delete_memory`, `search_memory` (file store) | memory tools | ⬜ |
| `harness-planning` | `write_plan`, `read_plan`, `add_task`, `update_task_status(es)`, `remove_task` | — | ⬜ |
| `harness-browser` | WebView browsing / page reading / actions | `browser_use` | ⬜ |
| `harness-device` | Clipboard, calendar, contacts, notifications, location, alarms (runtime permissions) | device integrations | ⬜ |
| `harness-speech` | Speech recognition and TTS for agents | speech | ⬜ |
| `harness-scheduler` | Scheduled / background agent runs (WorkManager, foreground service) | scheduled agents | ⬜ |

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
| AG-UI state / activity rendering (`state.plan` → `Plan`) | ⬜ |
| A2A agent card view (extend `Agent`) | ⬜ |

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
| Backend picker: in-app harness · AI SDK server · AG-UI server · A2A agent · direct model API | ⬜ |
| Settings: MCP servers (add, test, tools, approval policy, OAuth sign-in), Skills (bundled + zip import), Agents (sub-agents, remote A2A agents) | ⬜ |
| Composer sheet to toggle MCP servers / skills / agents per chat | ⬜ |
| Bundled skills from `/skills` (shared with the server) | ⬜ |
| E2E on emulator and Xiaomi Pad: every capability over each protocol | ⬜ |

### W6 · Library structure

| Item | Status |
|---|---|
| Review module and package layering: core packages by concern (`protocol.aisdk`, `protocol.agui`, `mcp`, `provider.*`, `agent`, `skills`, `auth`, `config`) | ⬜ after W1/W2 stabilise |
| README, CHANGELOG, NOTICE (a2a-java-sdk, kotlin-json-patch, proot) | ⬜ |

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
| 2026-09-27 | OpenMinis (GPL-3.0): ideas only, no code; sandbox uses upstream proot as a separate process | Licence compatibility with Apache-2.0 |
| 2026-09-27 | In-app harness is its own Maven group `io.github.junelegency.harness`, one artifact per capability, covering OpenMinis' capability set | User requirement; apps pick only what they need |
| 2026-09-27 | AG-UI: official `kotlin-core` 0.4.1 typed events; 1.0 gaps (SUBAGENT_*, subagentRunId, RUN_FINISHED.usage / pendingToolCallIds, array tool results) read from raw JSON until upstream | Official SDK first |
