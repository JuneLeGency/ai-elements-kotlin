# 只使用开放协议

跨进程通信严格遵循公开、版本化规范。本库是规范的客户端，不定义自己的协议。

| 用途 | 规范 |
|---|---|
| Agent 与 UI 流式通信 | Vercel AI SDK UI Message Stream v5 / v6、v4 Data Stream；AG-UI 1.x |
| 服务端工具与上下文 | MCP 2026-07-28 Streamable HTTP，回退兼容 2025-xx session |
| 交互工具视图 | MCP Apps 2026-01-26 |
| 生成式 UI | A2UI v1.0 |
| Agent 之间 | A2A 1.0 JSON-RPC，兼容 0.3 |
| 编码 Agent 客户端 | Agent Client Protocol v1 |
| 技能 | Agent Skills，`SKILL.md` 与 YAML frontmatter |
| 登录 | OAuth 2.1；RFC 6749、7636、8252、8628、8414、9728、7591、8707 |
| 模型 API | OpenAI Chat Completions / Responses、Anthropic Messages、Gemini、Ollama |

## 规则

1. **不创造 wire format**：不添加自定义事件、临时 JSON envelope、私有 header 或 SSE framing。使用规范扩展点：AI SDK 的 `data-*`、`message-metadata`、临时工具输出和审批；AG-UI 的 `STATE_SNAPSHOT` / `STATE_DELTA`、`ACTIVITY_*`、`SUBAGENT_*`、interrupt / `RunAgentInput.resume`，最后才使用 `CUSTOM`；MCP 的 `_meta`、annotation、`notifications/progress`；A2A 的 `metadata`、`DataPart`、artifact；ACP 的 `_meta`。
2. 使用 **当前规范版本**，保留已记录的旧版本回退。
3. **内部类型只是映射**：`ChatEvent`、`Message` / `Part`、`ToolCallContext` 是进程内模型，由各 backend 映射，不序列化到网络。
4. 优先 **官方 SDK 与参考实现**：AG-UI `kotlin-core`、`a2a-java-sdk`、ACP Kotlin SDK；服务端使用 Pydantic AI / Harness 和官方 `mcp`、`a2a-sdk`、ACP SDK。
5. **协议变更必须有真实 fixture**，从真实实现录制；有公开端点或参考服务可验证时，还需 live test。

## 服务端约定

设备能力对应 [Pydantic AI Harness](https://github.com/pydantic/pydantic-ai-harness)，使两端呈现一致：

- 子 Agent：`delegate_task(agent_name, task)`，名单作为静态 instruction。
- 技能：`load_capability(id)` 返回 `# Skill: <name>` 与正文。
- 能力：说明与工具的组合。

## 默认安全策略

修改数据需要审批，除非用户关闭。MCP annotation 是不可信提示，PKCE `S256` 必须使用，不记录 secret。
