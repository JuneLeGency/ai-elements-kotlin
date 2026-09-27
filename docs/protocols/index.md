# Protocols

AI Elements speaks **public, versioned specifications** only: no private wire formats. Every
protocol is mapped onto one in-process model (`ChatEvent`, `Message` / `Part`), so the elements
render all of them the same way and switching protocols never changes the UI.

## Choose a protocol

| Your agent | Backend | Artifact | Agent loop |
|---|---|---|---|
| An AI SDK route (`toUIMessageStreamResponse()`), Pydantic AI `VercelAIAdapter` | [`UiMessageStreamBackend`](ai-sdk.md) | `ai-elements-core` | server |
| Pydantic AI, LangGraph, CrewAI, Mastra, … over AG-UI | [`AgUiBackend`](ag-ui.md) | `ai-elements-core` | server |
| A remote agent with an A2A agent card | [`A2aBackend`](a2a.md) | `ai-elements-a2a` | remote agent |
| A coding agent: Claude Code, Codex, Gemini CLI, a Pydantic AI Harness agent over ACP | [`AcpBackend`](acp.md) | `ai-elements-acp` | remote or local process |
| A model API, with the agent loop on the device | [OpenAI, Anthropic, Gemini, Ollama](model-apis.md) | `ai-elements-core` | device |

Tools and context come from [MCP](mcp.md) servers in any of these modes, and MCP tools can bring
interactive views through [MCP Apps](mcp-apps.md).

## What each protocol carries

| | AI SDK 6 | AG-UI 1.x | A2A 1.0 | ACP | Model APIs |
|---|---|---|---|---|---|
| Text, reasoning | ✓ | ✓ | text | ✓ | ✓ |
| Tool calls | ✓ | ✓ | — | ✓ (with diffs) | ✓ |
| Tools on the device | client-side tools | frontend tools | — | client file system | all tools |
| Approvals | yes / no + reason | yes / no + reason + edited arguments | — | yes / no | yes / no + reason + edited arguments |
| Forms for the user | — | interrupts with a `responseSchema` | `input-required` | — | through MCP elicitation |
| Sub-agents | `UIMessage` tool output | `SUBAGENT_*` | as sub-agents | — | `delegate_task` |
| Plans and state | `data-*` parts | `STATE_*`, `ACTIVITY_*` | progress | `plan` | `Planning` |
| Generative UI | `data-a2ui` | `a2ui-surface` activity | A2UI parts | — | — |

## Versions and fallbacks

| Spec | Current revision | Also accepted |
|---|---|---|
| Vercel AI SDK UI Message Stream | v6 | v5; v4 Data Stream (detected per line) |
| AG-UI | 1.x (official `kotlin-core` types) | — |
| MCP | 2026-07-28 Streamable HTTP | 2025-xx session revisions |
| MCP Apps | 2026-01-26 | — |
| A2A | 1.0 JSON-RPC (official `a2a-java-sdk`) | 0.3 |
| Agent Client Protocol | v1 (official Kotlin SDK) | — |
| A2UI | v1.0 Basic Catalog | — |

## Bring your own

If your agent speaks something else, map it onto `ChatEvent`s in a few lines and every element works
unchanged. See [Custom backend](../guides/custom-backend.md). How to extend a protocol without
inventing a wire format is described in [Open protocols](../concepts/open-protocols.md).
