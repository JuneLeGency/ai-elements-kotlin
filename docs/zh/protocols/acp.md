# Agent Client Protocol（ACP）

`ai-elements-acp` 将应用作为 [Agent Client Protocol](https://agentclientprotocol.com) 客户端，类似 Zed 或 JetBrains 编辑器。Claude Code、Codex、Gemini CLI 的 ACP adapter，以及基于 ACP SDK 的 Agent（例如 Pydantic AI Harness `run_acp_stdio`）均可作为聊天 provider。此模块使用官方 [ACP Kotlin SDK](https://github.com/agentclientprotocol/kotlin-sdk)。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:acp-chat"
```

## 连接

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:acp-agents"
```

- **stdio** 是 ACP 规范定义的传输。`AcpAgent.process` 启动子进程，适合桌面 JVM 或能运行 Agent 的设备，例如 harness 沙箱。
- **WebSocket** 连接其他机器。ACP 的远程传输 Streamable HTTP 仍是草案，因此使用 Kotlin SDK 的 WebSocket 传输：每个 text frame 一条 JSON-RPC 消息，与 stdio 相同。[参考服务](../develop/reference-server.md) 位于 `ws://…/acp`。

!!! warning "安全"
    ACP Agent 能读写其运行机器上的文件并执行命令。只连接可信 Agent；在自己的网络之外使用带认证的 `wss://`。

`cwd` 是 Agent 机器上的工作目录，按 ACP `session/new` 要求必须为绝对路径。同一 Agent 的会话共享连接，失败后重新连接；使用完毕时关闭 Agent。

## Session

每个聊天会话对应一个 ACP session。ID 保存在回复 `Message.metadata` 的 `acp` 字段，下一轮只发送新用户消息，由 Agent 保持历史。应用重启等原因使 session 丢失时，若 Agent 支持则使用 `session/load` 恢复，否则以之前的对话为上下文新建 session。

## 映射

| ACP | 聊天中的表示 |
|---|---|
| `agent_message_chunk` | 文本；图片映射为文件，resource link 映射为来源 |
| `agent_thought_chunk` | 推理 |
| `tool_call`、`tool_call_update` | `title` 作为工具名、`rawInput` 作为输入；`content` 的文本、unified diff、terminal 或 `rawOutput` 作为输出；`failed` 为错误 |
| `plan` | Plan 组件 |
| `session/request_permission` | 批准选择 allow-once，始终允许选择 allow-always，拒绝选择 reject-once |
| 停止 | `session/cancel` |
| `PromptResponse` | 用量；`refusal`、`max_tokens`、`max_turn_requests` 以错误结束本轮 |

ACP 权限回答只携带选中的 option，因此提供批准、拒绝和始终允许，不支持理由或修改参数。见 [人工参与](../guides/human-in-the-loop.md)。

## 提供文件

客户端默认不声明文件系统。传入 `AcpFileSystem` 可处理 `fs/read_text_file`，非只读时也处理 `fs/write_text_file`，例如访问 harness 工作区或用户分享的文件夹：

```kotlin
AcpAgent.webSocket(url, files = object : AcpFileSystem {
    override suspend fun read(path: String, line: Int?, limit: Int?) = workspaceFile(path).readText()
})
```

不提供 terminal 和仍不稳定的 ACP elicitation。
