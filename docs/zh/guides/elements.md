# 组件

本库为 [Vercel AI Elements](https://elements.ai-sdk.dev) 的 49 个组件提供 Compose 对应实现。[组件图册](../components/index.md) 包含实图、用途和主要 API；Demo 的 Components 页面按相同分类实时展示，并支持分组筛选。

名称对应：

| AI Elements | 本库 |
|---|---|
| `Canvas`、`Node`、`Edge`、`Connection`、`Controls`、`Panel`、`Toolbar` | `WorkflowCanvas`，可自定义 node、toolbar、panel slot |
| `Image` | `FileImage`，消息内为 `FileAttachment` |
| `Attachments` | `FileAttachment`、`AttachmentStrip` |
| `Context` | `ContextUsage` |
| `Suggestion` | `Suggestions` |
| `Shimmer` | `ShimmerText` |
| `JSXPreview` | `ai-elements-genui` 的 `JsxPreview` |
| `Question` | `Question`，Agent 提问时为 `InputRequestCard` 选择列表 |

| 分类与 package | 组件 |
|---|---|
| 聊天 `ui.chat` | `Chat` · `Conversation` · `AgentComputerPanel` · `MessageItem` · `PromptInput` · `Suggestions` · `Reasoning` · `ToolCall` + `Confirmation` · `InputRequestCard` · `Subagent` · `Sources` · `InlineCitation` · `ContextUsage` · `BranchSelector` · `Checkpoint` · `Queue` · `OpenInChat` · `ModelSelector` · `Question` · `Agent` |
| Agent 结构 `ui.chat` | `ChainOfThought` · `Plan` · `Task` · `DataPartView` |
| 工作流 `ui.workflow` | `WorkflowCanvas` · `agentRunGraph` |
| 内容 `ui.markdown` | `MarkdownContent` · `CodeBlock` · `MermaidDiagram` · `MathBlock` · 附件 |
| 语音 `ui.voice` | `VoiceMode` · `Persona` · `SpeechInput` · 朗读 `SpeechOutputState` · `AudioPlayer` · `Transcription` · `MicSelector` · `VoiceSelector` |
| 编码 `ui.code` | `Artifact` · `WebPreview` · `Terminal` · `StackTrace` · `TestResults` · `FileTree` · `Commit` · `SchemaDisplay` · `PackageInfo` · `EnvironmentVariables` · `Sandbox` · `Snippet` |

## 会话

`Chat` 是完整页面，将消息列表 `Conversation` 与 `PromptInput` 连接到 `ChatController`。自定义布局可直接组合，见 [纯客户端](../getting-started/pure-client.md#in-a-viewmodel)。

| Part | 渲染 |
|---|---|
| `TextPart` | `MarkdownContent`：GitHub-flavoured Markdown、高亮代码、Mermaid、KaTeX |
| `ReasoningPart` | 可展开的一行 `Reasoning`，如“Thought for 2s” |
| `ToolPart` | `ToolCall`；委派为 `Subagent`、技能为徽章、待审批为 `Confirmation`；截图条跟随工具；浏览/执行/编辑回复显示实时 `AgentComputerCard` 和侧栏或 bottom sheet 的 `AgentComputerPanel`，见 [步骤与回放](steps-and-replay.md) |
| `SourcePart` | `Sources` 和 `[n]` 的 `InlineCitation` |
| `FilePart` | 按 EXIF 校正图片，点击 `ImageViewer` 缩放/平移/旋转；`VideoAttachment` 提供首帧、时长、全屏；音频 `AudioPlayer`；文档 `DocumentAttachment` 提供 PDF 预览/查看，其他格式用外部应用 |
| `DataPart` | `Plan`、`Task`、A2UI、MCP Apps 或自定义 renderer |

长会话流式输出时保持底部，用户向上滚动后不自动跟随。

## 语音

- **朗读**：每条回复有平台 TTS 操作，跳过代码、图和数学。`Conversation` 创建引擎，提供 `LocalSpeechOutput` 可共享。
- **听写**：composer 中的 `SpeechInput`，提供 `onCancel` 时显示输入音量、取消与完成。
- **语音模式**：未输入文本时，`Chat` 发送按钮或 `PromptInput(onVoiceMode = …)` 打开 `VoiceMode`，使用同一 controller，循环监听、发送、随流逐句朗读并重新监听。点 persona 中断，随时静音或结束；等待审批/回答时暂停并提示返回聊天。

`LocalSpeechSettings` 选择设备识别服务或 on-device recognition、TTS 引擎、voice、语速。`recognitionServices()`、`SpeechOutputState.engines` / `voices` 可用于设置页。

使用平台 `SpeechRecognizer`、`TextToSpeech`，所有 backend 通用，无协议依赖。`ai-elements-ui` 声明两个服务的 package visibility `<queries>`，应用声明 `RECORD_AUDIO`。无识别服务时隐藏语音功能。

## Terminal 输出

`Terminal` 和含控制码的工具输出按终端显示。`ai-elements-chat` 的 `TerminalText` 处理 ECMA-48 / xterm 输出子集：

- SGR 前景/背景 16、256、24-bit 色。
- 粗体、dim、斜体、下划线、反色、删除线。
- `\r`、`\b` 重绘，进度条保留最终状态。
- 行和屏幕的光标移动与擦除。
- OSC 8 超链接。

16 色与 VS Code 浅深色终端一致，scrollback 为 1 000 行。设备端 `Shell` 给模型纯文本结果，转义和进度重绘不消耗 token。沙箱设 `TERM=dumb`，多数程序本就输出纯文本。`vim`、`top` 等交互全屏程序需要 pseudo-terminal 和 terminal emulator，不属于命令输出支持范围。

## 文档

- **PDF**：平台 `PdfRenderer` 在消息中显示首屏与页数，`PdfViewerDialog` 显示所有页面。需要选择和搜索时可升级到 Android 9+ 的 `androidx.pdf` viewer。
- **Word / Excel / PowerPoint**：平台无 renderer，`openExternally` 通过库 `FileProvider` 的 `content:` URI 分享给 WPS、Office、Google Docs 等应用。Agent 能生成 PDF 时建议同时发送以便内联预览。
- 不嵌入在线文档 viewer：会向第三方发送 URL，要求公开地址，且部分地区不可用。

## 布局

手机使用紧凑单栏，平板/折叠屏在 Demo 中使用 list–detail 历史面板。

![平板上的会话与历史面板](../../assets/screenshots/tablet-light-en.webp){ loading=lazy }

## 主题

`AiElementsTheme` 使用 Material 3 Expressive，默认 dynamic color。可传入品牌 `colorScheme`、`typography`、`shapes`；`AiSpacing` / `AiType` 为间距和聊天字级。图标为生成的 Material Symbols Rounded `ImageVector`（`AiIcons`），不增加图标依赖。

## 语言与无障碍

字符串提供 English、简体中文、繁體中文、日本語。组件有 content description、48 dp 点击区域和 TalkBack semantics。

<div class="grid" markdown>

![简体中文](../../assets/screenshots/phone-light-zh-CN.webp){ loading=lazy width="240" }
![日本語](../../assets/screenshots/phone-light-ja.webp){ loading=lazy width="240" }

</div>
