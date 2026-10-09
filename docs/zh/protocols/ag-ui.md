# AG-UI

AgUiBackend 使用官方 kotlin-core 类型处理 AG-UI 1.x，POST RunAgentInput 并接收 SSE 事件。
参考服务端点为 /api/agui，也可以连接兼容的 Pydantic AI、LangGraph 等服务。

## 前端工具与人工确认

工具通过 RunAgentInput.tools 声明。服务端把调用留为 pending 后，设备执行工具，再用后续 run 回传 tool 消息。
需要审批时，先等待用户决定。

RUN_FINISHED 的 outcome=interrupt 可请求审批或表单：有 toolCallId 时处理工具决定，
有 responseSchema 时展示表单；通过 RunAgentInput.resume 返回 resolved 或 cancelled。
过期的 expiresAt 请求不会展示。

## 其他映射

| 事件或数据 | 在聊天中的表现 |
|---|---|
| SUBAGENT_* / subagentRunId | 嵌套子 Agent 运行 |
| STATE_SNAPSHOT / STATE_DELTA | 共享 state，delta 使用 JSON Patch |
| state.plan / state.task | Plan / Task 组件 |
| ACTIVITY_* | 按 activityType 命名的数据片段 |
| a2ui-surface activity | 生成式界面 |
| steps | 步骤摘要 |
| RUN_FINISHED.usage | token 使用量 |
| RunAgentInput.context | 客户端或 MCP Apps 提供的模型上下文 |

扩展时优先使用状态、活动、子 Agent、中断等标准机制，CUSTOM 作为最后选择。
[步骤与回放](../guides/steps-and-replay.md)说明事件日志的保存和回放。
