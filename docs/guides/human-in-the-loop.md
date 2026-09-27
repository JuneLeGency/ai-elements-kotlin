# Human in the loop

Agents involve the user in two ways: they ask **approval** before running a tool, and they ask the
user for **input** only the user can give. Every protocol's way of doing this maps onto one
neutral model, so the same elements answer them all.

| The agent… | AI SDK 6 | AG-UI | MCP | ACP | On-device |
|---|---|---|---|---|---|
| asks to run a tool | `tool-approval-request` | interrupt with a `toolCallId` | — (the host's `McpApproval`) | `session/request_permission` | `requiresApproval` |
| gets a reason back | `approval.reason` | resume `reason` | — | — | told to the model |
| gets edited arguments back | — | resume `editedArgs` | — | — | the edited call runs |
| asks the user for input | — | interrupt with a `responseSchema` | elicitation, form or URL | — | through MCP tools |

## In the UI

`Chat` wires it all:

- An approval shows inside the tool call, with approve and deny, plus **deny with a reason** and
  **edit and approve** when the protocol can carry them back. The backend declares what it
  supports (`ApprovalAnswers` on the approval request), so the UI never offers an answer the agent
  would not receive.
- A question shows as an `InputRequestCard`: a form built from the request's JSON Schema (text,
  formats, numbers with bounds, booleans, choices), or a link for URL elicitation.

With your own screen, pass the callbacks to `Conversation`:

```kotlin
Conversation(
    state = state,
    onToolApproval = chat::respondToApproval,   // yes / no
    onToolDecision = chat::respondToApproval,   // with a reason or edited arguments
    onInputResponse = chat::respondToInput,     // forms
)
```

Or answer from code:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:hitl"
```

Pending requests are in `ChatState` (tool parts in `APPROVAL_REQUESTED`, and `inputRequests`).
Stopping the turn cancels them.

## In a backend

Backends ask through the `ToolApprover` they are given:

- `decide(toolCallId)` returns a `ToolDecision(approved, reason, editedInput)`;
- `input(InputRequest)` returns `InputResponse.Accept(content)`, `Decline` or `Cancel`.

Emit `ChatEvent.ToolApprovalRequest(id, answers)` before asking, so the call shows its approval, and
`ToolApproved` / `ToolDenied` after. `ChatController` implements the approver by showing the request
and waiting for the user.

## Safety defaults

Tools that change data require approval unless the user opts out. MCP tool annotations are
untrusted hints: only `readOnlyHint` relaxes the default.
