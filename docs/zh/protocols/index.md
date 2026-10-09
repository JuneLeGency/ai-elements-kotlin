# 协议支持

库使用公开、版本化协议，并保留已记录的兼容路径。UI 只渲染统一消息模型，因此更换协议不需要重写组件。

| 需求 | 协议 | 模块 | 说明 |
|---|---|---|---|
| 服务端流式对话 | AI SDK v5/v6，兼容 v4 Data Stream | core | UIMessage、工具与审批、metadata |
| Agent 执行事件 | AG-UI 1.x | core | 状态、活动、子 Agent、interrupt/resume |
| 远程工具与上下文 | MCP 2026-07-28，兼容旧 session | core | 工具、资源、进度、OAuth、elicitation |
| 工具交互视图 | MCP Apps 2026-01-26 | mcp-apps | ui:// 资源、沙箱与标准桥接 |
| 远程 Agent | A2A 1.0，兼容 0.3 | a2a | 官方 Java SDK，Agent Card 与任务 |
| 编码 Agent | ACP v1 | acp | 官方 Kotlin SDK 的 stdio 与 WebSocket 传输 |
| 原生生成式 UI | A2UI v1.0 | genui | Basic Catalog 与传输绑定 |
| 模型 API | OpenAI、Anthropic、Gemini、Ollama | core | 在设备端运行 Agent 循环 |

core、ui 等缩写对应 ai-elements-* 模块。ACP 的 WebSocket 是当前官方 Kotlin SDK 提供的传输，不等同于标准远程传输已经定案。

进一步阅读：[AI SDK](ai-sdk.md)、[AG-UI](ag-ui.md)、[MCP](mcp.md)、[MCP Apps](mcp-apps.md)、
[A2A](a2a.md)、[ACP](acp.md)、[模型 API](model-apis.md)。API 名称及 wire 字段保留原文，代码示例与英文共用编译源。
