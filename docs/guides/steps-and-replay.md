# Steps and replay

Agents that browse, run commands or edit files are easier to trust when the user can watch them
work, as in Manus's "computer" view. AI Elements shows each step for what it is and keeps a live
preview while the agent runs. The whole view expands into a panel with a timeline. Any run can be
stepped through afterwards, and runs with a recorded event log can be replayed event by event.

## What a step does: tool categories

A tool call carries two model fields, `ToolPart.category` and `ToolPart.location`. The category
uses the Agent Client Protocol's `ToolKind` vocabulary: read, edit, delete, move, search, execute,
think, fetch, switch_mode and other. The location is the path or URL the call acts on. Like every
model field, both are set upstream, never guessed by the UI:

| Where the call comes from | How it gets its category |
|---|---|
| ACP agent | `tool_call.kind` and `locations[0].path` |
| AI SDK or AG-UI server running Pydantic AI Harness tools | The tool name. `ToolConventions` maps the Harness `FileSystem` and `Shell` tools exactly as the Harness ACP adapter presents them (`default_coding_presenter`): `read_file` → read, `edit_file` / `write_file` → edit, `search_files` → search, `run_command` → execute. |
| On-device tool | The tool declares it with `AgentTool.categoryFor` / `locationFor`. The harness `FileSystem`, `Shell` and `WebBrowser` tools already do. |

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:categories"
```

## Screenshots of each step

A tool call's screenshots are the image files that follow it in the reply. Each protocol has a
standard way to carry them, and no extension is needed:

| Where the tool runs | How its screenshot arrives |
|---|---|
| AI SDK server | a `file` part after the tool output (Pydantic AI: a `FileChunk` in `ToolReturn.metadata`) |
| AG-UI server | an `image` content part in `TOOL_CALL_RESULT.content` (AG-UI 1.x media parts, `data` or `url` source) |
| MCP server | `image` content in the `CallToolResult` |
| On the device | `ToolCallContext.file(mediaType, url)` from the tool, or `content(…)` when the model should see it too. `WebBrowser` attaches the viewport, with the acted-on element outlined, after every call that changes the page, and its `screenshot` tool returns the page to the model ([The browser](harness.md#the-browser)). |

In the conversation, a step's screenshots show as a small strip under its tool call. Tapping one
opens the agent's computer at that step. If you build your own view, `agentSteps(message)` returns
the steps of a reply: each tool call with its image files.

## The agent's computer

A reply whose steps have a category or a screenshot gets a live preview card
(`AgentComputerCard`). The card shows:

- the latest screenshot, or an icon for what the current step does;
- what the agent is doing, such as "Running command · ls -la" or "Reading · src/App.kt";
- the number of steps;
- a live marker while the reply streams.

Tapping the card opens `AgentComputerPanel`. It shows the selected step by its category, reusing
the existing elements:

| Step | View |
|---|---|
| has a screenshot | the screenshot, under an address bar when the location is a URL |
| execute | `Terminal` with the command and its output: colours (16, 256 and 24-bit), bold, italic, underline, inverse, `\r` progress redraws, cursor moves and erases, OSC 8 links (ECMA-48 / xterm); a scrollback of 1 000 lines |
| edit | the unified diff, with added and removed lines coloured (ACP diffs arrive as one), else the file in a `CodeBlock` |
| read, search, delete, move | the path and the output in a `CodeBlock` |
| fetch | an address bar and the page text |
| anything else | its output |

Below the view the panel has:

- the step's status;
- a timeline slider;
- previous, play and next buttons;
- a chip for every step.

While the reply streams, the panel follows the newest step. Picking an earlier step stops
following, and **Back to live** resumes it.

The layout adapts to the space the chat actually has (`AgentComputerScaffold`). At 720 dp or wider,
the panel is a side pane next to the conversation, as in Material 3's supporting-pane layout. This
also works inside a list–detail layout, because the width checked is the chat's own, not the
window's. When narrower, the panel is a `ModalBottomSheet`. `Chat` and `Conversation` set this up.
To share one panel across your own layout, create a state with `rememberAgentComputerState` and
wrap your layout in `AgentComputerScaffold`.

Stepping through works on any saved conversation, because it only reads the stored message.

## Replay

Replay has two levels.

- **Steps, from the messages.** This is how the AI SDK persists chats: `UIMessage[]`, as `useChat`
  stores them in `onFinish`. It is also how AG-UI's `MESSAGES_SNAPSHOT` carries them. The panel
  above needs nothing more, and it works for every backend.
- **Events, from AG-UI's event log.** This is the standard for event-level fidelity: the timing,
  every delta and every state change.

### AG-UI event logs

AG-UI [serialization](https://docs.ag-ui.com/concepts/serialization) defines the log. Give
`AgUiBackend` an `eventLog` and it keeps one:

- **Events as streamed.** One append-only log per `threadId`, stored as a JSON array of events.
  Events without a `timestamp` get the time they arrived; `BaseEvent.timestamp` is the spec's own
  field.
- **Runs linked by `parentRunId`.** Each run names the one before it, in both `RunAgentInput` and
  the logged `RUN_STARTED`. This is the spec's branching lineage.
- **What the client added, in `RUN_STARTED.input`.** When the server did not echo the input, the
  log records it there, keeping only messages not already in the log, as the spec's compaction
  rule says. This matters for frontend tools: their results exist only on the client.
- **The runs that produced a reply, in its metadata.** They are stored under the `agui` key of
  `Message.metadata`, so a stored conversation still knows which runs to replay.

`AgUiEventLog.replayOf(message)` plays a reply's runs back through the same AG-UI event parser the
live stream used, at the recorded pace, and rebuilds the reply as it happened. Pass it to `Chat`,
and the panel offers **Replay run**:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:replay"
```

`AgUiEventLog.Files` keeps one file per thread; `AgUiEventLog.InMemory` keeps the log for the
life of the process. Implement the interface to store logs elsewhere, for example in your own
database or on your server. Screenshots make logs large, so prefer `url` sources to inline data
for runs you keep.

### ACP: the agent's own record

An ACP agent that can load sessions (`loadSession`) replays a session on `session/load`: it streams
the whole conversation back as `session/update`s. Pydantic AI Harness does this when the adapter
has a `SessionStore`. `AcpBackend.replayOf(message)` does the following:

1. It opens a connection of its own, so the session you are chatting in is left alone.
2. It loads the session.
3. It takes the reply's turn (its metadata records which turn it was).
4. It plays that turn through the same mapping as a live turn.

ACP updates carry no timestamps, so a replay plays at a steady pace. An agent that runs as a local
process only has sessions its store keeps across processes.

### Compacting logs

`AgUiEventLog.compact` implements AG-UI's compaction, ported from the reference `compactEvents`:

- a message's or tool call's deltas become one event;
- each run's state becomes one `STATE_SNAPSHOT`;
- events that arrived mid-stream move after that stream.

Compaction reorders events, so compact a thread when you archive it. Keep the recorded log for as
long as you want event-level replay.

### Other protocols

- **AI SDK.** The AI SDK has no client-side event log. `resumeStream` continues an in-flight
  response from the server's stream store; it does not replay one. Use message-level replay.
- **On-device agents.** Their replies are stored as messages; use message-level replay.
- **Server side.** Pydantic AI Harness `StepPersistence` keeps an agent's append-only step log for
  recovering, continuing and forking runs.
- **Traces.** For debugging and evaluation, use OpenTelemetry's GenAI semantic conventions (Pydantic
  Logfire, Langfuse and similar).

See [Choose your setup](../getting-started/choose.md#4-storing-compacting-replaying-and-reconnecting)
for which of these the library provides and which your app decides.
