# MCP

core 中的 McpClient 为 Agent 提供工具、资源与 prompts，使用 MCP 2026-07-28 Streamable HTTP，
并为旧服务器保留 2025-xx session 回退。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:mcp-client"
```

## 加入 Agent

McpServerStore 保存已配置服务器并加密凭据；其 toolset 可作为 Capability 加入 harness。
McpToolset 并行读取各服务的工具，按服务设置超时、缓存结果，并通过 status 报告失败，不让单个不可达服务导致整轮失败。
工具的 source 显示服务器名称。非只读工具默认需要审批；annotations 是不可信提示，只有 readOnlyHint 放宽默认审批。

## 功能边界

支持 tools/list、tools/call、资源、prompts、分页和 notifications/progress。
2026-07-28 的 InputRequiredResult 通过携带 inputResponses / requestState 重试回答，最多 10 轮；
旧 session 处理 elicitation/create。表单与 URL 两种 elicitation 都有声明。

受保护服务器使用资源元数据、授权服务器发现、DCR、resource indicators 和 PKCE S256。
需要登录时报告 NeedsSignIn。当前不声明 sampling 和 roots。
交互视图见 [MCP Apps](mcp-apps.md)。
