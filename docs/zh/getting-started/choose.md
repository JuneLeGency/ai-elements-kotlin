# 选择接入方式

先按 Agent 运行位置选择，再决定 UI 接入层次。所有方式使用同一组组件，之后更换 backend 不必修改 UI。

## 1. Agent 在哪里运行？

| 场景 | 添加模块 | Backend | 起点 |
|---|---|---|---|
| Vercel AI SDK 或 Pydantic AI `VercelAIAdapter` 后端 | `ai-elements-ui`、`ai-elements-core` | `UiMessageStreamBackend` | [AI SDK](../protocols/ai-sdk.md) |
| AG-UI 服务（Pydantic AI、LangGraph、CrewAI、Mastra 等） | `ai-elements-ui`、`ai-elements-core` | `AgUiBackend` | [AG-UI](../protocols/ag-ui.md) |
| 编码 Agent：Claude Code、Codex、Gemini CLI、Pydantic AI Harness | `ai-elements-ui`、`ai-elements-acp` | `AcpBackend` | [ACP](../protocols/acp.md) |
| 发布 A2A Agent card 的远程 Agent | `ai-elements-ui`、`ai-elements-a2a` | `A2aBackend` | [A2A](../protocols/a2a.md) |
| 手机通过 OpenAI、Anthropic、Gemini、Ollama API 运行 Agent | `ai-elements-ui`、`ai-elements-core`、`harness-*` | `AgentHarness` | [应用内 Agent](in-app-agent.md) |
| 自己的协议 | `ai-elements-ui`、`ai-elements-chat` | 自定义 `ChatBackend` | [自定义 backend](../guides/custom-backend.md) |

所有方式均可使用 [MCP](../protocols/mcp.md) 服务器工具。

## 2. 需要多少 UI？

| 层次 | 使用 | 自己控制 |
|---|---|---|
| 完整聊天 | `Chat(controller)` | 主题 |
| 自己的页面 | `Conversation` + `PromptInput`，需要 Agent 电脑时加 `AgentComputerScaffold` | 布局、app bar、输入和状态位置 |
| 定制部分渲染 | `LocalAiElementsRenderers`，按工具或数据 part 名称选择 | 指定工具或数据 part 外观 |
| 单个组件 | `ToolCall`、`Terminal`、`CodeBlock`、`Plan`、`AgentComputerPanel` 等 | 其余全部 |

参见 [纯客户端快速开始](pure-client.md#in-a-viewmodel) 和 [自定义](../guides/customizing.md)。

## 3. 操作电脑的 Agent（Manus 风格）

浏览、运行命令或编辑文件的回复有实时预览卡片和 Agent 电脑面板，见 [步骤与回放](../guides/steps-and-replay.md)。

| 接入 | 步骤分类（terminal、diff、browser 等） | 截图 | 回放 |
|---|---|---|---|
| AI SDK | 按名称映射 Harness 文件和 shell 工具，其他为普通步骤 | 工具输出后的 `file` part | 保存的消息 |
| AG-UI | 按名称映射 Harness 文件和 shell 工具 | 工具结果中的 media part | `AgUiBackend(eventLog = …)` 保存的事件日志，可逐事件回放 |
| ACP | Agent 的 `kind` 和 `locations` | image content | 使用 `session/load` 和 `AcpBackend.replayOf` 读取 Agent 自己的记录 |
| A2A | — | image part | 保存的消息 |
| 应用内 Agent | 每个工具通过 `categoryFor` 声明 | `ToolCallContext.file`；浏览器每次页面变化附带一张 | 保存的消息 |

Terminal 保留颜色、进度条和链接，支持 ECMA-48 / xterm 转义、`\r` 重绘和 OSC 8 链接。

### 手机、平板和折叠屏

- 电脑面板按聊天容器宽度适配：至少 720 dp 时为侧栏，较窄时为 bottom sheet；适用于平板、展开的折叠屏、横屏手机及 list–detail 布局。使用 Material 3 `ListDetailPaneScaffold` 时可放在 extra pane，平板上在聊天右侧替换历史列表，见 [详情](../guides/steps-and-replay.md#the-agents-computer)。
- 长内容保持流畅：步骤最多显示 100 000 字符；Terminal 保留 1 000 行 scrollback 并提示省略行数；长行横向滚动，标题与路径用省略号缩短。
- 长运行可导航：步骤为 lazy list；时间轴在最多 20 步时逐步标记，更多步骤连续 scrub；截图为 lazy row。

### 通知

长运行通过 Android 16 Live Update 在状态栏 chip 和锁屏显示进度，结束后通知回复就绪，见 [运行通知](../guides/notifications.md)。

## 4. 保存、压缩、回放和重连 { #4-storing-compacting-replaying-and-reconnecting }

库提供标准机制，应用决定保存位置、压缩时间和保留策略。Demo 展示一种策略。

| 事项 | 标准 | 库能力 | 应用决定 |
|---|---|---|---|
| 保存会话 | AI SDK `UIMessage[]`；AG-UI messages | `Message` 可序列化，store 自行实现 | 保存位置与期限；Demo 使用文件 |
| 保存 AG-UI 运行 | AG-UI serialization，append-only event log | `AgUiEventLog`（`Files`、`InMemory` 或自定义）、`AgUiBackend(eventLog = …)`、`delete` | 日志位置与期限；Demo 删除会话时一并删除日志 |
| 压缩日志 | AG-UI `compactEvents` | 从参考实现移植的 `AgUiEventLog.compact` | 何时压缩，例如归档时。压缩会将运行状态重排到末尾，仍需逐事件回放时不要压缩 |
| 回放运行 | AG-UI event log；ACP `session/load` | `AgUiEventLog.replayOf`、`AcpBackend.replayOf`、`Chat(replay = …)` | 哪些回复提供回放 |
| 断流重连 | AI SDK resumable streams；SSE `Last-Event-ID` | 尚未支持；需要保存流的服务（例如 `resumable-stream`），参考服务尚未提供 | 当前断流显示错误和 Retry，重试会重新请求。ACP 下一轮重连，通过 `session/load` 继续同一 session |

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:replay"
```
