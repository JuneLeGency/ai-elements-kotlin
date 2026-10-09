# 自定义 backend

每个 backend 都是 `ChatBackend`，将会话转换为 `ChatEvent` 流。把协议映射为这些事件，即可使用流式 Markdown、工具、审批、子 Agent 和计划等组件，无需修改 UI。只需 `ai-elements-chat` 和 `ai-elements-ui`。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:custom-backend"
```

## 契约

- `stream(history)` 接收完整会话，末尾是新用户消息。
- Flow 是 cold flow，由 `ChatController` 收集；用户停止导致取消时，必须取消实际请求。
- 传输失败应抛出异常，`ChatBackendException` 可携带 HTTP status，controller 显示错误。服务端流内报告的错误使用 `ChatEvent.Error`。
- `ChatEvent.Finish` 可省略，flow 正常完成也会结束本轮。
- thread、task、session ID 等每会话状态通过 `ChatEvent.Metadata` 保存在回复中，下一轮从最后一条 assistant 消息的 `metadata` 读取。

## 事件

事件对应 AI SDK UI Message Stream chunk 类型：

| 事件 | 映射结果 |
|---|---|
| `TextDelta` / `TextEnd` | 按 ID 组织的文本 part |
| `ReasoningDelta` / `ReasoningEnd` | 推理 part |
| `ToolInputStart` / `ToolInputDelta` / `ToolInputAvailable` | 工具调用及参数，包括 `title`、`kind`、`source` |
| `ToolOutput`（可带 `preliminary`） / `ToolError` | 工具结果 |
| `ToolApprovalRequest` / `ToolApproved` / `ToolDenied` | 审批，见 [人工参与](human-in-the-loop.md) |
| `SubagentUpdate` | 委派工具内部的嵌套运行 |
| `SourceUrl`、`File` | 来源及生成的文件 |
| `Data` | 数据 part，相同 ID 的后续 part 替换之前的内容 |
| `Metadata`、`Usage` | 消息元数据及 token 用量 |

这些是进程内类型，不能作为自定义协议发送到网络。
