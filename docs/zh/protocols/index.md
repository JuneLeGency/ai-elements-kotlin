# 协议

本库只使用公开、版本化规范，不使用私有 wire format。所有协议映射为进程内 `ChatEvent`、`Message` / `Part`，由相同组件渲染，更换协议不改变 UI。

## 选择协议

| Agent | Backend | 模块 | 循环位置 |
|---|---|---|---|
| 返回 `toUIMessageStreamResponse()` 的 AI SDK route，或 Pydantic AI `VercelAIAdapter` | [`UiMessageStreamBackend`](ai-sdk.md) | `ai-elements-core` | 服务端 |
| Pydantic AI、LangGraph、CrewAI、Mastra 等 AG-UI 服务 | [`AgUiBackend`](ag-ui.md) | `ai-elements-core` | 服务端 |
| 发布 A2A card 的远程 Agent | [`A2aBackend`](a2a.md) | `ai-elements-a2a` | 远程 Agent |
| Claude Code、Codex、Gemini CLI、Pydantic AI Harness ACP Agent | [`AcpBackend`](acp.md) | `ai-elements-acp` | 远程或本地进程 |
| 设备端循环调用模型 API | [OpenAI、Anthropic、Gemini、Ollama](model-apis.md) | `ai-elements-core` | 设备 |

各模式可从 [MCP](mcp.md) 获取工具和上下文；工具可通过 [MCP Apps](mcp-apps.md) 提供交互视图。

## 各协议能力

| 能力 | AI SDK 6 | AG-UI 1.x | A2A 1.0 | ACP | 模型 API |
|---|---|---|---|---|---|
| 文本、推理 | ✓ | ✓ | 文本 | ✓ | ✓ |
| 工具 | ✓ | ✓ | — | ✓，含 diff | ✓ |
| 设备工具 | client-side tool | frontend tool | — | client file system | 全部工具 |
| 审批 | 批准/拒绝及理由 | 批准/拒绝、理由、修改参数 | — | 批准/拒绝 | 批准/拒绝、理由、修改参数 |
| 问题与表单 | client-side `ask_user_question` | frontend `ask_user_question`、带 `responseSchema` 的 interrupt | `input-required` | — | `AskUser`、MCP elicitation |
| 子 Agent | `UIMessage` 工具输出 | `SUBAGENT_*` | 作为子 Agent | — | `delegate_task` |
| 计划与状态 | `data-*` | `STATE_*`、`ACTIVITY_*` | 进度 | `plan` | `Planning` |
| 生成式 UI | `data-a2ui` | `a2ui-surface` activity | A2UI part | — | — |

## 版本与回退

| 规范 | 当前版本 | 同时支持 |
|---|---|---|
| AI SDK UI Message Stream | v6 | v5；逐行检测 v4 Data Stream |
| AG-UI | 1.x，官方 `kotlin-core` | — |
| MCP | 2026-07-28 Streamable HTTP | 2025-xx session |
| MCP Apps | 2026-01-26 | — |
| A2A | 1.0 JSON-RPC，官方 `a2a-java-sdk` | 0.3 |
| ACP | v1，官方 Kotlin SDK | — |
| A2UI | v1.0 Basic Catalog | — |

## 自定义接入

其他协议可映射为 `ChatEvent`，复用所有组件，见 [自定义 backend](../guides/custom-backend.md)。规范扩展方式见 [开放协议](../concepts/open-protocols.md)。
