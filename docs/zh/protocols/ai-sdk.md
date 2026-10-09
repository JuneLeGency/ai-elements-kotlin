# Vercel AI SDK

`UiMessageStreamBackend` 可连接任何输出 [AI SDK UI Message Stream](https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol) 的服务；这也是 Web 端 `useChat` 消费的格式。

```kotlin
ChatController(backend = { approver ->
    UiMessageStreamBackend("https://agents.example.com/api/chat", approver = approver)
}, scope = viewModelScope)
```

## 服务端

支持返回 `toUIMessageStreamResponse()` 的 AI SDK 路由，或 Pydantic AI 的 `VercelAIAdapter`（工具审批需 `sdk_version=6`）。[参考服务](../develop/reference-server.md) 在 `/api/chat` 使用该适配器。

请求为 `POST endpoint`，内容是 `{trigger, id, messages: UIMessage[]}`，与 `useChat` 一样包含带工具 part 的完整历史。设置 `model` 后通过 `?model=` 发送。

## 支持范围

- **v5 / v6 UI Message Stream**：SSE `data: {"type": "text-delta", …}`，以 `[DONE]` 结束。
- **v4 Data Stream**：逐行检测 `0:"text"`、`g:"reasoning"`、`9:{toolCall}`，支持 `toDataStreamResponse()`。
- **工具审批（AI SDK 6）**：`tool-approval-request` 显示审批，回答作为 `approval-responded` 工具 part 返回；用户给出理由时携带 `approval.reason`。协议不支持修改参数，因此 UI 不提供此操作。
- **客户端工具**：服务端调用传入的 `tools` 且未提供结果时，在设备上执行并携带结果继续本轮，类似 `sendAutomaticallyWhen: lastAssistantMessageIsCompleteWithToolCalls`。
- **临时工具输出**：后续结果替换临时输出。如果输出本身是 `UIMessage`（通过临时结果流式输出的子 Agent），显示为工具内部的嵌套运行。
- **`data-*` part**：相同 ID 的新 part 替换旧 part。`data-plan`、`data-task`、`data-a2ui` 有内置渲染器，其他名称可自行注册。
- **`message-metadata`** 合并进 `Message.metadata`（用量显示为 `ContextUsage`），并支持 `source-url` part。

## 扩展

使用 `data-*` part 传输应用数据，并通过 `DataRenderer` 渲染，见 [自定义](../guides/customizing.md)。不要新增 chunk 类型。
