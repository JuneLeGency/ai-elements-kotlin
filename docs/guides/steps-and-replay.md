# Steps and replay

Agents that browse, run code or operate apps are easier to trust when the user can see each step,
like Manus's "computer" view. AI Elements shows every tool call with what it produced, including
screenshots, and plays a run back step by step, live or from history.

## Screenshots of each step

A tool call's screenshots are the image files that follow it in the reply. Each protocol has a
standard way to carry them, and no extension is needed:

| Where the tool runs | How its screenshot arrives |
|---|---|
| AI SDK server | a `file` part after the tool output (Pydantic AI: a `FileChunk` in `ToolReturn.metadata`) |
| AG-UI server | an `image` content part in `TOOL_CALL_RESULT.content` (AG-UI 1.x media parts, `data` or `url` source) |
| MCP server | `image` content in the `CallToolResult` |
| On the device | `ToolCallContext.file(mediaType, url)` from the tool; `WebBrowser` attaches the viewport after every call that changes the page |

In the conversation, a step's screenshots show as a small strip under its tool call. Tapping one
opens the run playback at that step.

`agentSteps(message)` returns the steps of a reply (each tool call with its image files) if you
build your own view.

## Run playback

`AgentRunPlayback` shows a reply's run as a timeline. It opens from a step's screenshot or from the
reply's actions.

- The selected step's latest screenshot is shown large. A step without one shows its output (or
  error, or input).
- Below it: the step's title, status and input, then a timeline with previous, play and next, and
  every step as a chip.
- While the reply is streaming, the view follows the newest step until the user picks one.

Because it only reads the saved message, a past conversation plays back the same way as a live one.

## Replay: what to store

The standards for replaying agent runs, from the lightest to the most detailed:

1. **Messages.** Store the conversation as messages: AI SDK `UIMessage[]` (what `useChat` persists
   in `onFinish`), or AG-UI messages (`MESSAGES_SNAPSHOT`). Each message keeps its parts in order,
   including tool calls, their outputs and files, so the steps view and playback work from storage
   alone. The demo app stores conversations this way.
2. **Event logs.** For event-level fidelity (timing, deltas, state changes), AG-UI specifies
   [serialization](https://docs.ag-ui.com/concepts/serialization): an append-only JSON log of
   events per thread, indexed by `threadId`, `runId` and timestamps. `RunStarted.parentRunId` records
   branches for time travel, and compaction folds deltas into `MESSAGES_SNAPSHOT` and
   `STATE_SNAPSHOT`. Replaying the log through the same backend reproduces the run. The recorded
   fixtures in this repository are such logs.
3. **Resuming a live stream.** After a dropped connection, SSE reconnects with `Last-Event-ID`
   (HTML Living Standard), and the AI SDK resumes an in-flight response from a stream store
   (`resumeStream`). This continues a run rather than replaying it.
4. **Traces.** For debugging and evaluation, OpenTelemetry's GenAI semantic conventions record
   model calls and tool executions (Pydantic Logfire, Langfuse and similar tools replay those).

Screenshots make logs large: keep them as URLs to stored files rather than inline `data:` URLs
when you persist runs.
