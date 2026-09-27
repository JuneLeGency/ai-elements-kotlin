# Vercel AI SDK

`UiMessageStreamBackend` talks to any server that streams the
[AI SDK UI Message Stream](https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol), the format `useChat`
consumes on the web.

```kotlin
ChatController(backend = { approver ->
    UiMessageStreamBackend("https://agents.example.com/api/chat", approver = approver)
}, scope = viewModelScope)
```

## The server

Any AI SDK route that returns `toUIMessageStreamResponse()`, or Pydantic AI's `VercelAIAdapter`
(`sdk_version=6` for tool approval), which the [reference server](../develop/reference-server.md)
uses at `/api/chat`.

The request is `POST endpoint` with `{trigger, id, messages: UIMessage[]}`, the whole history
including tool parts, as `useChat` sends it. `model` is sent as `?model=` when set.

## What is supported

- **v5 / v6 UI Message Stream**: SSE `data: {"type": "text-delta", …}` chunks ending in `[DONE]`.
- **v4 Data Stream** (`0:"text"`, `g:"reasoning"`, `9:{toolCall}` lines, `toDataStreamResponse()`),
  detected per line.
- **Tool approval** (AI SDK 6): a `tool-approval-request` shows an approval; the answer goes back as
  an `approval-responded` tool part, with `approval.reason` when the user gives one. The protocol has
  no edited arguments, so the UI does not offer them.
- **Client-side tools**: when the server calls one of the `tools` you pass and has no output for it,
  the tool runs on the device and the turn continues with its output, like
  `sendAutomaticallyWhen: lastAssistantMessageIsCompleteWithToolCalls`.
- **Preliminary tool output**, replaced by later results. A tool output that is itself a
  `UIMessage` (a sub-agent streaming through preliminary results) renders as that tool's nested
  run.
- **`data-*` parts** (a later part with the same id replaces the earlier one): `data-plan`,
  `data-task` and `data-a2ui` have built-in renderers; register your own for other names.
- **`message-metadata`**, merged into `Message.metadata` (usage shows in `ContextUsage`), and
  `source-url` parts.

## Extending

Send app data as `data-*` parts and render them with a `DataRenderer`
([Customizing](../guides/customizing.md)); do not add chunk types.
