# Human in the loop

Where the agent asks the user: approvals, questions and forms.

## Confirmation (tool approval)

Approve or deny a tool call, with a reason or edited arguments.

![Confirmation (tool approval)](../assets/components/confirmation.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `ToolCall(part, onApproval)` |
| AI Elements | `Confirmation` |

## Question

A multiple-choice question from the agent.

![Question](../assets/components/question.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Question(prompt, options, multiple, onSubmit)` |

## Input request (form)

A form the agent asks the user to fill, from a JSON Schema (AG-UI interrupts, MCP elicitation).

![Input request (form)](../assets/components/input-request.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `InputRequestCard(request, onRespond)` |
