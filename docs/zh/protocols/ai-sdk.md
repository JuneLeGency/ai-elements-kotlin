# Vercel AI SDK

UiMessageStreamBackend 连接 AI SDK UI Message Stream 服务，也就是 Web useChat 使用的格式。
服务端可以是返回 toUIMessageStreamResponse() 的 AI SDK 路由，或 Pydantic AI 的 VercelAIAdapter。
参考服务的端点是 /api/chat，工具审批使用 sdk_version=6。

## 请求与支持范围

请求通过 POST 发送 trigger、id 和完整 UIMessage 历史，包含工具片段；设置 model 时使用查询参数。

- v5 / v6：SSE UI Message Stream，以 [DONE] 结束。
- v4：识别旧版 Data Stream 行格式。
- 工具审批：tool-approval-request 对应界面审批，回答作为 approval-responded 工具片段返回。
- 拒绝理由通过 approval.reason 返回；该协议不传编辑后的参数，因此界面不提供参数编辑。
- 客户端工具：服务端提出调用后由设备执行，再携带结果继续运行。
- preliminary 工具输出会被后续结果替换；UIMessage 类型的工具结果可以作为嵌套子 Agent 运行展示。
- data-* 扩展数据可渲染为计划、任务、A2UI 或应用自己的视图。
- message-metadata 合并到 Message.metadata，来源和 usage 映射到对应组件。

接入代码见[纯客户端](../getting-started/pure-client.md)。自定义数据使用 data-* 扩展点，不新增私有 chunk 类型。
