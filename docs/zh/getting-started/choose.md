# 选择接入方式

先决定 Agent 在哪里运行，再决定需要多少 UI。所有后端都输出同一套消息模型，可以共用组件。

| 你的情况 | 引入模块 | 后端或入口 |
|---|---|---|
| 已有 AI SDK 服务端 | ui + core | UiMessageStreamBackend |
| 已有 AG-UI 服务端 | ui + core | AgUiBackend |
| 连接远端 A2A Agent | ui + a2a | A2aBackend |
| 连接编码 Agent | ui + acp | AcpBackend |
| Agent 在设备上运行 | ui + harness-core + 所需能力 | AgentHarness |
| 自己实现消息来源 | ui（传递依赖 chat） | ChatBackend |

表中的模块均带 `ai-elements-` 前缀，harness 模块除外。完整坐标见[安装](installation.md)。

## 选择界面粒度

| 需要 | 使用 |
|---|---|
| 完整聊天界面 | Chat(controller) |
| 自己布局消息列表与输入框 | Conversation + PromptInput |
| 替换某种工具或数据的展示 | LocalAiElementsRenderers |
| 只使用一个组件 | ToolCall、Plan、CodeBlock、Terminal 等 |

第一次接入建议先用完整 Chat。[纯客户端](pure-client.md)连接参考服务即可验证流式回复与审批；
[端侧 Agent](in-app-agent.md)展示模型 API、文件和计划的组合。

## 状态由谁保存？

controller 管理当前会话。ViewModel 可以跨配置变化保留它，但进程被回收后需要应用从自己的存储恢复。
Message 可用于本地序列化；保存周期、清理规则和数据库结构由应用决定。
AG-UI 的事件回放与 ACP 的会话回放见[步骤与回放](../guides/steps-and-replay.md)。
