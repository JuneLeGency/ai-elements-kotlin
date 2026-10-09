# AG-UI

`AgUiBackend` 基于官方 AG-UI `kotlin-core` 类型实现 [AG-UI](https://docs.ag-ui.com) 1.x 客户端：以 `POST` 发送 `RunAgentInput`，通过 SSE 接收运行事件。Pydantic AI、LangGraph、CrewAI 和 Mastra 均可提供该协议；[参考服务](../develop/reference-server.md) 的入口为 `/api/agui`。

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

## 支持范围

- **前端工具**：在 `RunAgentInput.tools` 中声明 `tools`。Agent 调用时，本次运行以待执行工具结束；设备执行工具（需要时先审批），下一次运行携带 `tool` 消息。
- **中断与人工参与**：运行以 `outcome: interrupt` 结束时向用户提问。有 `toolCallId` 时可批准、附理由拒绝或修改参数；恢复 payload 为 `{approved, reason?, editedArgs?}`，对应 AG-UI 的 approve-with-edits 模式和 Pydantic AI schema。没有工具 ID 而有 `responseSchema` 时，按 schema 生成表单并用回答恢复运行。恢复通过 `RunAgentInput.resume`（携带 payload 的 `resolved` 或 `cancelled`）；超过 `expiresAt` 的中断不再显示。
- **子 Agent**：`SUBAGENT_STARTED` 及携带 `subagentRunId` 的事件归入委派工具的嵌套运行，由 `Subagent` 显示。
- **共享状态**：`STATE_SNAPSHOT` / `STATE_DELTA`（JSON Patch）映射为 `state` 数据 part，下一轮作为 `RunAgentInput.state` 返回。状态中的 `plan` 或 `task` 显示为 Plan / Task 组件。
- **活动**：`ACTIVITY_SNAPSHOT` / `ACTIVITY_DELTA` 映射为以 `activityType` 命名的数据 part；`a2ui-surface` 活动通过 [生成式 UI](../guides/generative-ui.md) 渲染。
- **步骤** 显示为 chain of thought；`RUN_FINISHED` 的 **用量** 用于 `ContextUsage`。
- **模型上下文**：MCP Apps 的 `ui/update-model-context` 进入 `RunAgentInput.context`。

## 扩展

使用协议自身的状态（JSON Patch）、活动、子 Agent、中断等扩展点，最后才考虑 `CUSTOM` 事件。
