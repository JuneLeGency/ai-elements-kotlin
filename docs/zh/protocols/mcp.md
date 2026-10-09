# MCP

`ai-elements-core` 中的 [Model Context Protocol](https://modelcontextprotocol.io) 客户端从 MCP 服务器获取工具、资源和 prompt。使用 **2026-07-28** Streamable HTTP，并为旧服务器回退到 **2025-xx session** 修订版。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:mcp-client"
```

## 用于 Agent

`McpServerStore` 保存用户配置的服务器并加密凭证，将已启用的服务器转换为能力：

```kotlin
AgentHarness(model = …, capabilities = { listOf(mcpServers.toolset()) })
```

`McpToolset` 并行获取服务器工具列表，每个服务器都有超时。失败的服务器在本轮跳过并通过 `status` 报告，不会使整轮失败；工具列表会缓存。

MCP 工具以服务器名称作为来源显示。非只读工具执行前需要用户批准。工具 annotation 是不可信提示，只有 `readOnlyHint` 会放宽默认审批要求。

## 功能

- 工具（`tools/list`、`tools/call`）、资源、prompt 和分页。
- `notifications/progress` 在工具调用中实时显示。
- **Elicitation**：服务器向用户提问。2026-07-28 的 `InputRequiredResult`（`resultType: "input_required"`）通过携带 `inputResponses` 和 `requestState` 重试调用来回答，最多 10 轮；旧 session 使用流中的 `elicitation/create`。声明支持 form 和 URL 模式；聊天中问题以表单显示，见 [人工参与](../guides/human-in-the-loop.md)。
- **OAuth**：支持 protected resource metadata（RFC 9728）、authorization server metadata（RFC 8414）、dynamic client registration（RFC 7591）、resource indicators（RFC 8707）和 PKCE `S256`。需要登录的服务器标记为 `NeedsSignIn`。
- **MCP Apps** 工具视图，见 [MCP Apps](mcp-apps.md)。

未声明支持 sampling 和 roots。
