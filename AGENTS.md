# AGENTS.md

Instructions for AI coding agents (and humans) working in this repository. The goal and order of work live in
[docs/GOAL.md](docs/GOAL.md), workstream status in [docs/ROADMAP.md](docs/ROADMAP.md); update it as work lands. These rules are
binding; `CONTRIBUTING.md` has the day-to-day conventions.

## 1. Open protocols only — no private protocols

This library is a client for **public, versioned specifications**. Everything that
crosses a process boundary must follow one of them, exactly as specified:

| Concern | Specification | Implemented in |
|---|---|---|
| Agent ↔ UI streaming | Vercel **AI SDK** UI Message Stream (v5/v6), v4 Data Stream | `core/protocol/aisdk/` |
| Agent ↔ UI streaming | **AG-UI** 1.x (events, subagents, interrupts/resume, state, activities) | `core/protocol/agui/` (official AG-UI `kotlin-core` types) |
| Tools and context from servers | **MCP** 2026-07-28 Streamable HTTP, with fallback to the 2025-xx session revisions | `core/mcp/` |
| Interactive tool views | **MCP Apps** 2026-01-26 (`io.modelcontextprotocol/ui`: `ui://` views, `ui/*` bridge, sandbox + CSP) | `core/mcp/McpApps.kt` (negotiation, `_meta.ui`), `ai-elements-mcp-apps` (host) |
| Generative UI | **A2UI** v1.0 (Basic Catalog; AG-UI, A2A and AI SDK bindings) | `ai-elements-genui` (renderer), transports in `core` / `a2a` |
| Agent ↔ agent | **A2A** 1.0 JSON-RPC binding, with 0.3 compatibility | `ai-elements-a2a` (official `a2a-java-sdk`) |
| Skills | **Agent Skills** (`SKILL.md` + YAML frontmatter) | `core/skills/` |
| Sign-in | OAuth 2.1 / RFC 6749, 7636, 8252, 8628, 8414, 9728, 7591, 8707 | `core/auth/`, `core/mcp/McpAuth.kt` |
| Model APIs | OpenAI Chat Completions / Responses, Anthropic Messages, Gemini, Ollama | `core/provider/*` |

Rules:

1. **Do not invent wire formats.** No custom event types, no ad-hoc JSON envelopes, no
   bespoke headers, no "our own" SSE framing. If a feature seems to need one, it belongs
   to a spec extension point instead:
   - AI SDK: `data-*` parts (app data), `message-metadata`, preliminary tool output,
     tool approval (`tool-approval-request` / `approval-responded`);
   - AG-UI: `STATE_SNAPSHOT` / `STATE_DELTA` (JSON Patch), `ACTIVITY_*`, `SUBAGENT_*`,
     `RUN_FINISHED.outcome` interrupts + `RunAgentInput.resume`, `CUSTOM` as a last resort;
   - MCP: `_meta`, tool annotations, `notifications/progress`;
   - A2A: `metadata`, `DataPart`, artifacts.
2. **Follow the current revision** of each spec, and keep the documented fallbacks for
   older peers (MCP legacy sessions, A2A 0.3, AI SDK v4). Read the spec before changing
   a parser; cite the section in the KDoc when behaviour is subtle.
3. **Internal types are mappings, not protocols.** `ChatEvent`, `Message`/`Part`,
   `ToolCallContext` are in-process Kotlin models that each backend maps its spec onto.
   They must never be serialized onto the network as-is.
4. **Server-side agent conventions come from Pydantic AI Harness.** The demo server
   (`server/`) is built from Pydantic AI + Pydantic AI Harness capabilities
   (`Planning`, `SubAgents`, `Skills`, MCP toolsets) and official SDKs (`mcp`,
   `a2a-sdk`); do not hand-roll what they provide. On-device equivalents mirror the
   same public contracts so both render identically:
   - sub-agents: one `delegate_task(agent_name, task)` tool, roster as a static instruction;
   - skills: `load_capability(id)`, returning `# Skill: <name>` + body;
   - capabilities: instructions + tools (`core/agent/Capability.kt`).
5. **Every protocol change ships with a fixture test** recorded from a real
   implementation (official SDK or reference server), plus a live test when a public
   endpoint or the local server can exercise it.

### Artifact layering

`ai-elements-chat` (packages `model`, `chat`: models, `ChatEvent`, `ChatBackend`, `ChatController`; no
networking, no Compose) ← `ai-elements-core` (protocols, providers, agent, MCP, skills, auth) and
`ai-elements-ui` (Compose elements; depends on `ai-elements-chat` **only**) ← optional artifacts
(`ai-elements-a2a`, `ai-elements-koog`, `harness/*`). A new protocol is a `ChatBackend` in core or in
its own artifact; it never needs a UI change. UI formats that need more than the chat model sit on
top of `ai-elements-ui`: `ai-elements-genui` (A2UI, JSX; ui only) and `ai-elements-mcp-apps` (ui +
core, since a view talks to its MCP server). They plug in through `AiElementsRenderers`.

### Package layering (ai-elements-core)

`chat` (controller, events, reducer) · `model` · `protocol.aisdk` / `protocol.agui` · `provider.*`
(on-device model APIs) · `http` (internal transport helpers) · `agent` (tools, capabilities, loop,
sub-agents) · `mcp` · `skills` · `auth` · `config`. Dependencies point downwards (`protocol` and
`provider` use `chat`, `agent`, `http`, `model`; never the reverse). Optional capabilities live in their
own artifacts: `ai-elements-a2a`, `harness/*`.

### UI elements are protocol-independent (ai-elements-ui)

Elements render the in-process models only (`core.model`, `core.chat`); they never import
`protocol`, `provider`, `agent`, `mcp`, `skills`, `http` or other artifacts, and never interpret
tool names or protocol conventions. What a part *means* is a model field set upstream:
`ToolPart.kind` (`Function` / `Delegation` / `Skill`) and `ToolPart.source` are declared by
on-device tools (`AgentTool.kindFor` / `source`) or mapped from a protocol in `core`
(`protocol.ToolConventions`, AG-UI `SUBAGENT_*`); shared agent state is `DataPart.STATE`.
If an element needs more, extend the model — do not special-case a name in the UI.
`LayeringTest` (ai-elements-ui unit tests) enforces this. Apps customise rendering through
`LocalAiElementsRenderers` (tools by name or predicate, data parts by name) and loading through
`LocalFileLoader`, without forking elements.

## 2. Best practices over bespoke code

- Prefer the official SDK / reference implementation of a spec for the server side and
  tests; prefer upgrading to its latest release over patching around old behaviour.
- Keep dependencies current (`server/`: `uv add <pkg>@latest`; Android: version catalog).
- Security defaults: tools that change data require approval unless the user opts out;
  MCP tool annotations are untrusted hints; PKCE `S256` is mandatory; never log secrets.

## 3. Build, test, verify

```bash
./gradlew testDebugUnitTest lintDebug                 # unit + fixture tests
./gradlew :demo:connectedDebugAndroidTest            # UI end-to-end (emulator/device)
cd server && uv run uvicorn main:app --port 8788      # reference agent/MCP/A2A server for live tests
```

A change is done when it builds, its tests (including live ones it affects) pass on the
emulator, and the README/CHANGELOG describe it.
