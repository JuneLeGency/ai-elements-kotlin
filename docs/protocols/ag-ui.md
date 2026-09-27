# AG-UI

`AgUiBackend` implements the client half of [AG-UI](https://docs.ag-ui.com) 1.x on the official
AG-UI `kotlin-core` types: it `POST`s a `RunAgentInput` and receives the run's events over SSE.
Pydantic AI, LangGraph, CrewAI and Mastra serve it; the
[reference server](../develop/reference-server.md) does at `/api/agui`.

```kotlin
ChatController(backend = { approver ->
    AgUiBackend(
        "https://agents.example.com/api/agui",
        approver = approver,
        tools = listOf(myDeviceTool),                      // frontend tools, run on the device
        context = listOf("Client capabilities" to "…"),   // RunAgentInput.context
    )
}, scope = viewModelScope)
```

## What is supported

- **Frontend tools.** `tools` are advertised in `RunAgentInput.tools`. When the agent calls one, the
  run finishes with the call pending; the tool runs on the device (after approval, if it needs one)
  and a follow-up run carries the `tool` message.
- **Interrupts** (human in the loop). A run that finishes with `outcome: interrupt` asks the user:
    - with a `toolCallId`: approve, deny with a reason, or edit the arguments. The resume payload is
      `{approved, reason?, editedArgs?}`, AG-UI's approve-with-edits pattern and Pydantic AI's schema;
    - without one, with a `responseSchema`: a form built from the schema. Its answer resumes the run.

    The run resumes with `RunAgentInput.resume` (`resolved` with the payload, or `cancelled`).
    Interrupts past their `expiresAt` are not shown.
- **Sub-agents.** `SUBAGENT_STARTED` and the events that carry its `subagentRunId` fold into the
  delegating tool call's nested run, shown by the `Subagent` element.
- **Shared state.** `STATE_SNAPSHOT` / `STATE_DELTA` (JSON Patch) become the `state` data part and go
  back as `RunAgentInput.state` on the next turn. A `plan` or `task` key in the state renders as the
  Plan / Task elements.
- **Activities.** `ACTIVITY_SNAPSHOT` / `ACTIVITY_DELTA` become data parts named by their
  `activityType`; `a2ui-surface` activities render as [generative UI](../guides/generative-ui.md).
- **Steps** become a chain of thought; **usage** in `RUN_FINISHED` feeds `ContextUsage`.
- **Model context.** MCP Apps' `ui/update-model-context` goes to `RunAgentInput.context`.

## Extending

Use AG-UI's own extension points: state (JSON Patch), activities, sub-agents, interrupts, and
`CUSTOM` events as a last resort.
