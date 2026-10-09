# 步骤与回放

浏览、执行命令、编辑文件的 Agent 通过实时步骤和类似 Manus 的电脑视图让用户观察工作过程。实时预览可展开为带时间轴的面板，保存的运行可逐步查看，有事件日志的运行可逐事件回放。

## 工具分类

`ToolPart.category` 使用 ACP `ToolKind` 词汇：read、edit、delete、move、search、execute、think、fetch、switch_mode、other。`ToolPart.location` 为操作的路径或 URL。由上游设置，UI 不猜测。

| 来源 | 分类映射 |
|---|---|
| ACP | `tool_call.kind` 和 `locations[0].path` |
| AI SDK / AG-UI 上的 Pydantic AI Harness 工具 | core 的 `ToolConventions` 按 Harness ACP adapter 的 `default_coding_presenter` 映射 `FileSystem` / `Shell`：`read_file` → read，`edit_file` / `write_file` → edit，`search_files` → search，`run_command` → execute |
| 设备工具 | 通过 `AgentTool.categoryFor` / `locationFor` 声明；`FileSystem`、`Shell`、`WebBrowser` 已实现 |

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:categories"
```

## 每步截图

截图是回复中跟随工具调用的图片文件，各协议使用标准内容，无需扩展：

| 执行位置 | 截图来源 |
|---|---|
| AI SDK 服务 | 工具输出后的 `file` part；Pydantic AI 为 `ToolReturn.metadata` 中的 `FileChunk` |
| AG-UI 服务 | `TOOL_CALL_RESULT.content` 的 `image` media part，以 `data` 或 `url` 提供 |
| MCP 服务 | `CallToolResult` 的 `image` content |
| 设备 | 工具调用 `ToolCallContext.file(mediaType, url)`；模型也需要图片时使用 `content(…)`。`WebBrowser` 在页面变化后附带勾勒操作元素的 viewport，`screenshot` 向模型返回页面，见 [浏览器](harness.md#the-browser) |

截图条显示在工具调用下，点击打开对应步骤的电脑视图。自定义视图可用 `agentSteps(message)` 获取每个工具调用及图片。

## Agent 的电脑 { #the-agents-computer }

有分类或截图的回复显示 `AgentComputerCard`：最新截图或当前步骤图标、当前操作（如“Running command · ls -la”或“Reading · src/App.kt”）、步骤数量，以及流式回复时的实时标记。

点击打开 `AgentComputerPanel`，按分类复用组件：

| 步骤 | 视图 |
|---|---|
| 有截图 | 截图；URL location 时带地址栏 |
| execute | `Terminal` 显示命令与输出：16 / 256 / 24-bit 色、粗体、斜体、下划线、反色、`\r` 重绘、光标移动与擦除、OSC 8 链接（ECMA-48 / xterm），保留 1 000 行 |
| edit | 有 unified diff 时彩色显示增删行（ACP diff 是单条），否则 `CodeBlock` 显示文件 |
| read、search、delete、move | 路径与 `CodeBlock` 输出 |
| fetch | 地址栏和页面文本 |
| 其他 | 工具输出 |

下面显示步骤状态、时间轴 slider、上一项 / 播放 / 下一项按钮，以及每步 chip。流式回复时跟随最新步骤，选择旧步骤停止跟随，**Back to live** 恢复。

`AgentComputerScaffold` 按聊天实际容器宽度适配：至少 720 dp 时侧栏，较窄时 `ModalBottomSheet`。不是按整个窗口判断，因此也适用于 list–detail。`Chat` 和 `Conversation` 已接入；自定义布局用 `rememberAgentComputerState` 创建状态并包入 scaffold，`layout` 可为 `Auto`、`SidePane`、`BottomSheet`。

已有 Material 3 adaptive `ListDetailPaneScaffold` 的应用应使用 extra pane：`layout = AgentComputerLayout.Hosted`，在 `extraPane` 放 `AgentComputerPanel`。展开窗口（平板、展开折叠屏）在聊天右侧显示电脑，历史列表让位；单栏时覆盖聊天；返回恢复列表与聊天。Demo 在进入 extra pane 时打开电脑，离开时关闭。

逐步查看只读取保存的消息，适用于任何已保存会话。

## 回放

- **消息级步骤**：AI SDK 用 `onFinish` 保存 `useChat` 的 `UIMessage[]`，AG-UI 用 `MESSAGES_SNAPSHOT` 携带消息。上面的面板无需更多数据，适用于所有 backend。
- **AG-UI 事件级回放**：事件日志保存时序、每个 delta 和状态变化。

### AG-UI 日志

[AG-UI serialization](https://docs.ag-ui.com/concepts/serialization) 定义日志。为 `AgUiBackend` 提供 `eventLog`：

- 每个 `threadId` 一份 append-only JSON 事件数组，无 `timestamp` 时补到达时间；`BaseEvent.timestamp` 是规范字段。
- `RunAgentInput` 和日志 `RUN_STARTED` 通过 `parentRunId` 连接前一运行，保留标准分支关系。
- 服务端未回显 input 时，在 `RUN_STARTED.input` 保存客户端添加的内容，按规范压缩规则只保留尚未出现的消息。前端工具结果仅存在于客户端，因此必须记录。
- 回复 `Message.metadata` 的 `agui` 字段保存生成该回复的运行，已保存会话仍知道要回放哪些运行。

`AgUiEventLog.replayOf(message)` 按录制速度通过同一 AG-UI parser 重建回复。传给 `Chat` 后面板提供 **Replay run**：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:replay"
```

`AgUiEventLog.Files` 每个 thread 一个文件；`AgUiEventLog.InMemory` 在进程生命周期中保存。可实现接口接入数据库或服务端。截图使日志增大，长期保存时优先使用 `url` 而非内联图片。

### ACP：Agent 的记录

支持 `loadSession` 的 ACP Agent 在 `session/load` 时通过 `session/update` 重放完整会话。Pydantic AI Harness adapter 配置 `SessionStore` 后支持此行为。`AcpBackend.replayOf(message)`：

1. 打开独立连接，不影响当前聊天 session。
2. 加载 session。
3. 根据回复 metadata 选择对应 turn。
4. 通过与 live turn 相同的映射播放。

ACP update 无时间戳，以固定速度播放。本地进程 Agent 只能访问其 store 跨进程保留的 session。

### 压缩日志

`AgUiEventLog.compact` 移植参考实现 `compactEvents`：消息或工具 delta 合为一个事件，每次运行的状态合为 `STATE_SNAPSHOT`，流中途到达的事件移动到流结束后。

压缩会重排事件，适合归档时执行；仍需逐事件回放时保留原始日志。

### 其他协议

- **AI SDK** 没有客户端事件日志，`resumeStream` 从服务端 stream store 继续未完成回复，不是回放；使用消息级回放。
- **设备端 Agent** 保存回复消息，使用消息级回放。
- **服务端** Pydantic AI Harness `StepPersistence` 保存 append-only step log，用于恢复、继续和 fork。
- **Trace** 调试和评估使用 OpenTelemetry GenAI semantic conventions，例如 Pydantic Logfire、Langfuse。

哪些机制由库提供、哪些策略由应用决定，见 [选择接入方式](../getting-started/choose.md#4-storing-compacting-replaying-and-reconnecting)。
