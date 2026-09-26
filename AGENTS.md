# AGENTS.md

Instructions for AI coding agents (and humans) working in this repository. The goal and order of work live in
[docs/GOAL.md](docs/GOAL.md), workstream status in [docs/ROADMAP.md](docs/ROADMAP.md); update it as work lands. These rules are
binding; `CONTRIBUTING.md` has the day-to-day conventions.

## 1. Open protocols only — no private protocols

This library is a client for **public, versioned specifications**. Everything that
crosses a process boundary must follow one of them, exactly as specified:

| Concern | Specification | Implemented in |
|---|---|---|
| Agent ↔ UI streaming | Vercel **AI SDK** UI Message Stream (v5/v6), v4 Data Stream | `core/backend/UiMessageStreamBackend.kt` |
| Agent ↔ UI streaming | **AG-UI** 1.x (events, subagents, interrupts/resume, state, activities) | `core/backend/AgUiBackend.kt` |
| Tools and context from servers | **MCP** 2026-07-28 Streamable HTTP, with fallback to the 2025-xx session revisions | `core/mcp/` |
| Agent ↔ agent | **A2A** 1.0 JSON-RPC binding, with 0.3 compatibility | `ai-elements-a2a` (official `a2a-java-sdk`) |
| Skills | **Agent Skills** (`SKILL.md` + YAML frontmatter) | `core/skills/` |
| Sign-in | OAuth 2.1 / RFC 6749, 7636, 8252, 8628, 8414, 9728, 7591, 8707 | `core/auth/`, `core/mcp/McpAuth.kt` |
| Model APIs | OpenAI Chat Completions / Responses, Anthropic Messages, Gemini, Ollama | `core/backend/` |

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
