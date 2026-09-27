# Custom backend

Every backend is a `ChatBackend`: a function from the conversation to a stream of `ChatEvent`s.
Map your protocol onto those events and all the elements work unchanged: streaming Markdown, tools,
approvals, sub-agents, plans. `ai-elements-chat` and `ai-elements-ui` are all you need.

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:custom-backend"
```

## The contract

- `stream(history)` receives the whole conversation, ending with the new user message.
- The flow is cold and collected by `ChatController`; cancelling it (the user pressed stop) must
  cancel the request.
- Throw on transport failures (`ChatBackendException` carries an HTTP status); the controller shows
  the error. Emit `ChatEvent.Error` for errors the server reports in the stream.
- `ChatEvent.Finish` is optional: a completed flow also ends the turn.
- Keep per-conversation protocol state (a thread, task or session id) in the reply with
  `ChatEvent.Metadata`; the next turn finds it in the last assistant message's `metadata`.

## Events

The events map onto the AI SDK UI Message Stream chunk types:

| Event | Becomes |
|---|---|
| `TextDelta` / `TextEnd` | a text part (by id) |
| `ReasoningDelta` / `ReasoningEnd` | a reasoning part |
| `ToolInputStart` / `ToolInputDelta` / `ToolInputAvailable` | a tool call and its arguments (`title`, `kind`, `source`) |
| `ToolOutput` (optionally `preliminary`) / `ToolError` | its result |
| `ToolApprovalRequest` / `ToolApproved` / `ToolDenied` | its approval; see [Human in the loop](human-in-the-loop.md) |
| `SubagentUpdate` | a nested run inside a delegating tool call |
| `SourceUrl`, `File` | sources and generated files |
| `Data` | a data part; a later one with the same id replaces it |
| `Metadata`, `Usage` | message metadata and token usage |

These are in-process types: never send them over the network as a protocol of their own.
