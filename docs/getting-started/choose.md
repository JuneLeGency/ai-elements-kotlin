# Choose your setup

Pick the row that matches where your agent runs, then the level of UI you want. Every row renders
with the same elements, so you can change the backend later without touching the UI.

## 1. Where does the agent run?

| Your situation | Add | Backend | Start here |
|---|---|---|---|
| You have a web backend built with the Vercel AI SDK, or Pydantic AI's `VercelAIAdapter` | `ai-elements-ui`, `ai-elements-core` | `UiMessageStreamBackend` | [AI SDK](../protocols/ai-sdk.md) |
| Your agent server speaks AG-UI (Pydantic AI, LangGraph, CrewAI, Mastra, …) | `ai-elements-ui`, `ai-elements-core` | `AgUiBackend` | [AG-UI](../protocols/ag-ui.md) |
| You talk to a coding agent (Claude Code, Codex, Gemini CLI, a Pydantic AI Harness agent) | `ai-elements-ui`, `ai-elements-acp` | `AcpBackend` | [ACP](../protocols/acp.md) |
| You call remote agents that publish an A2A agent card | `ai-elements-ui`, `ai-elements-a2a` | `A2aBackend` | [A2A](../protocols/a2a.md) |
| The agent runs on the phone, over a model API (OpenAI, Anthropic, Gemini, Ollama) | `ai-elements-ui`, `ai-elements-core`, `harness-*` | `AgentHarness` | [In-app agent](in-app-agent.md) |
| You have your own protocol | `ai-elements-ui`, `ai-elements-chat` | your `ChatBackend` | [Custom backend](../guides/custom-backend.md) |

Tools from MCP servers work in every row ([MCP](../protocols/mcp.md)).

## 2. How much UI do you want?

| Level | Use | You keep control of |
|---|---|---|
| Everything | `Chat(controller)` | the theme |
| Your own screen | `Conversation` + `PromptInput` (+ `AgentComputerScaffold` for the agent's computer) | layout, app bars, input, where state lives |
| Your own rendering of some parts | `LocalAiElementsRenderers` (tools by name, data parts by name) | how one tool or data part looks |
| Single elements | `ToolCall`, `Terminal`, `CodeBlock`, `Plan`, `AgentComputerPanel`, … | everything else |

See [Quick start: pure client](pure-client.md#in-a-viewmodel) and [Customizing](../guides/customizing.md).

## 3. Agents that work on a computer (Manus-style)

Replies that browse, run commands or edit files get a live preview card and the agent's computer
panel ([Steps and replay](../guides/steps-and-replay.md)). What each setup gives it:

| Setup | Step categories (terminal, diff, browser…) | Screenshots | Replay a run |
|---|---|---|---|
| AI SDK | Harness file and shell tools by name; others as plain steps | `file` parts after a tool output | from stored messages |
| AG-UI | Harness file and shell tools by name | media parts in tool results | **event by event** from the AG-UI event log (`AgUiBackend(eventLog = …)`) |
| ACP | the agent's `kind` and `locations` | image content | **the agent's own record**, through `session/load` (`AcpBackend.replayOf`) |
| A2A | — | image parts | from stored messages |
| In-app agent | declared by each tool (`categoryFor`) | `ToolCallContext.file` (the browser attaches one per page change) | from stored messages |

Terminal output keeps its colours, progress bars and links. The terminal renders what programs write
for a terminal: ECMA-48 / xterm escape codes, `\r` redraws and OSC 8 links.

### Phones, tablets and foldables

- **The computer panel fits the space the chat has.** It is a side pane when the chat is at least
  720 dp wide (tablets, unfolded foldables, phones in landscape) and a bottom sheet below that
  width. It measures its own container, so it also works inside a list–detail layout. With Material
  3's `ListDetailPaneScaffold`, host it in the extra pane instead: on tablets it then opens on the
  right, beside the chat, in place of the history list
  ([details](../guides/steps-and-replay.md#the-agents-computer)).
- **Long content stays fast.**
  - A step view shows at most 100 000 characters.
  - Terminal output keeps a scrollback of 1 000 lines and says how many earlier lines it left out.
  - Long lines scroll horizontally, and titles and paths are shortened with an ellipsis.
- **Long runs stay navigable.** Steps are a lazy list. The timeline marks each step with a tick up
  to 20 steps and scrubs continuously beyond that. Screenshots are a lazy row.

## 4. Storing, compacting, replaying and reconnecting

The library provides the mechanisms for these standards; your app decides the policy (where to
store, when to compact, what to keep). The demo app shows one policy.

| Concern | Standard | In the library | Your app decides |
|---|---|---|---|
| Keep conversations | AI SDK `UIMessage[]`; AG-UI messages | `Message` is serializable; a store is up to you | where to save and for how long (the demo uses files) |
| Keep AG-UI runs | AG-UI serialization (append-only event log) | `AgUiEventLog` (`Files`, `InMemory`, or your own); `AgUiBackend(eventLog = …)`; `delete` | where logs live and for how long (the demo deletes a conversation's logs with it) |
| Compact logs | AG-UI `compactEvents` | `AgUiEventLog.compact` (ported from the reference implementation) | when to compact, e.g. when archiving a thread. Compaction reorders a run's state to its end, so keep logs uncompacted while you still want event-level replay. |
| Replay a run | AG-UI event log; ACP `session/load` | `AgUiEventLog.replayOf`, `AcpBackend.replayOf`; `Chat(replay = …)` | which replies offer it |
| Reconnect a dropped stream | AI SDK resumable streams; SSE `Last-Event-ID` | not yet: it needs a server that keeps the stream (for example the `resumable-stream` package), and the reference server does not | For now, a dropped turn shows an error with **Retry**, which asks again. An ACP connection is reopened on the next turn and continues the same session (`session/load`). |

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:replay"
```
