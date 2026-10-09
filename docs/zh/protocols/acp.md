# Agent Client Protocol（ACP）

ai-elements-acp 基于官方 ACP Kotlin SDK，让 App 像编辑器一样连接编码 Agent。
Claude Code、Codex、Gemini CLI 等需要使用各自的 ACP adapter；支持 ACP SDK 的其他 Agent 也可以接入。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:acp-chat"
```

## 连接方式

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:acp-agents"
```

stdio 是 ACP 定义的传输，可启动子进程。设备需要具备运行该 Agent 的环境。
WebSocket 使用官方 Kotlin SDK 的实现，连接其他机器；标准远程传输仍在演进，不能把它表述为已经定案的 ACP HTTP 传输。
cwd 是 Agent 所在机器上的绝对路径。使用结束后关闭 AcpAgent。

## 会话、权限与映射

每个会话对应一个 ACP session。session id 保存在 metadata.acp，下一轮只发送新的用户消息。
断线后在支持时使用 session/load 恢复，否则以旧消息作为上下文创建新 session。

| ACP | 聊天表现 |
|---|---|
| agent_message_chunk | 文本、图片文件或来源链接 |
| agent_thought_chunk | 思考片段 |
| tool_call / tool_call_update | 工具标题、输入、文本、差异或终端输出 |
| plan | 计划 |
| session/request_permission | 允许一次、持续允许或拒绝 |
| session/cancel | 停止当前任务 |
| PromptResponse | usage 与完成状态 |

ACP 权限回答选择代理提供的选项，不传拒绝理由或编辑参数。
默认不向代理开放客户端文件系统；需要时提供 AcpFileSystem。当前不声明 terminals 和尚不稳定的 ACP elicitation。
只连接可信 Agent；跨网络部署需要 wss 与鉴权，因为 Agent 可以修改它运行机器上的文件。
