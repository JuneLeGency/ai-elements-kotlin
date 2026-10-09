# 人工参与

Agent 在执行工具前请求 **审批**，也会向用户请求只有用户才能提供的 **输入**。各协议映射到同一个中立模型，由相同组件处理。

| Agent 的请求 | AI SDK 6 | AG-UI | MCP | ACP | 设备端 |
|---|---|---|---|---|---|
| 执行工具 | `tool-approval-request` | 带 `toolCallId` 的 interrupt | —（宿主的 `McpApproval`） | `session/request_permission` | `requiresApproval` |
| 接收理由 | `approval.reason` | resume `reason` | — | — | 告知模型 |
| 接收修改后的参数 | — | resume `editedArgs` | — | — | 执行修改后的调用 |
| 向用户提选择题 | 客户端工具 `ask_user_question` | 前端工具 `ask_user_question` | — | — | `AskUser` |
| 请求用户输入 | — | 带 `responseSchema` 的 interrupt | form 或 URL elicitation | — | 通过 MCP 工具 |

## 向用户提问

`AskUser` 是设备端的 [Pydantic AI Harness](https://github.com/pydantic/pydantic-ai-harness) `AskUser`：同一个 `ask_user_question` 工具，schema、限制、说明和结果相同。模型一次提出 1–10 个问题，每题 2–6 个选项（标签及含义），允许多选时使用 `multi_select`。用户可选择或自行填写；工具按问题 `header` 返回选中标签，或告知模型用户拒绝回答。

- **应用内 Agent**：添加 `AskUser()` 能力。
- **服务端 Agent**：将 `AskUser.tool` 作为设备工具传入。AG-UI 通过 `RunAgentInput.tools` 发送；AI SDK 服务端声明不带 `execute` 的客户端工具（Pydantic AI 中使用含 Harness 工具定义的 `ExternalToolset`），应用返回工具输出。无需协议扩展。

问题以标准 JSON Schema 的 `InputRequest` 到达 UI：每题一个 property，选项为 `{const, title, description}` 的 `anyOf`，用户自填答案为 `{"type": "string"}`。表单以类似 `Question` 的列表显示这些选项。

## UI 接入

`Chat` 会连接全部交互：

- 审批显示在工具调用内，有 **Deny** 和 **Allow**。其“⋯”菜单按协议支持程度提供 **附理由拒绝**、**修改并批准**，以及 **始终允许**。后者通过 `ToolDecision.remember` 由 `ChatController` 保存，当前会话内不再询问；ACP Agent 收到自己的 `allow_always` 选项。Backend 用 `ApprovalAnswers` 声明支持的回答，UI 不会提供无法传回 Agent 的选项。
- 提问显示为 `InputRequestCard`：按 JSON Schema 构建文本、格式、带边界的数字、布尔、带描述的单选/多选与自填答案表单，或 URL elicitation 链接。

自定义页面时，将回调传给 `Conversation`：

```kotlin
Conversation(
    state = state,
    onToolApproval = chat::respondToApproval,   // yes / no
    onToolDecision = chat::respondToApproval,   // with a reason or edited arguments
    onInputResponse = chat::respondToInput,     // forms
)
```

也可以从代码回答：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:hitl"
```

待处理请求保存在 `ChatState`（工具 part 的 `APPROVAL_REQUESTED` 状态及 `inputRequests`）；停止本轮会取消这些请求。

## Backend 接入

通过传入的 `ToolApprover` 请求交互：

- `decide(toolCallId)` 返回 `ToolDecision(approved, reason, editedInput)`。
- `input(InputRequest)` 返回 `InputResponse.Accept(content)`、`Decline` 或 `Cancel`。

询问前发出 `ChatEvent.ToolApprovalRequest(id, answers)` 显示审批，之后发出 `ToolApproved` / `ToolDenied`。`ChatController` 通过显示请求并等待用户来实现 approver。

## 默认安全策略

改变数据的工具需要审批，除非用户选择关闭。MCP annotation 是不可信提示，只有 `readOnlyHint` 放宽默认要求。
