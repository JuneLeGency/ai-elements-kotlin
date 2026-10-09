# 人工确认与信息收集

Agent 可以请求用户批准工具，也可以要求补充信息。库通过 ToolDecision、InputRequest、InputResponse 表达这些需求，
UI 不需要知道请求来自 AI SDK、AG-UI、MCP 还是 ACP。

## 推荐接法

Chat(controller) 已接好审批与表单。自己组合 Conversation 时，将 onToolDecision 传给 controller.respondToApproval，
将 onInputResponse 传给 controller.respondToInput。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:hitl"
```

## 只展示后端支持的选项

| 来源 | 能力说明 |
|---|---|
| AI SDK | 工具审批，可返回拒绝理由 |
| AG-UI | 按 interrupt/resume 继续，支持服务端约定的参数编辑及表单 |
| MCP | 工具审批由 host 管理，支持标准 elicitation |
| ACP | 使用代理返回的权限选项，包括允许一次或持续允许 |
| 端侧 Agent | 按 AgentTool.requiresApproval 请求审批 |

ApprovalAnswers 决定是否显示理由和编辑参数。不要让 UI 提供后端无法接收的选项。
“记住决定”限定在当前会话，按工具名称与来源区分。只读展示不等于自动批准。

## 后端实现约定

先发出 ToolApprovalRequest 事件，再等待 ToolApprover.decide；表单调用 ToolApprover.input。
停止运行时应取消等待。返回用户决定后继续原任务，不要伪造一条普通用户消息代替协议的审批或 resume。
