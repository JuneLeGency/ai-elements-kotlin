# 辅助 API

除图册中的主要场景，下表列出了公开的 composable 和状态工厂。点击名称可查看完整签名与 KDoc（英文）。

在 composition 中使用 `rememberAudioPlayerState`、`rememberSpeechInputState` 和
`rememberSpeechOutputState`，让播放器或语音服务随界面离开而释放。
`ChatController` 由 ViewModel 持有，界面或播放器状态由 Compose 状态工厂管理。

`SchemaForm` 可单独渲染 JSON Schema 表单，需要提供 onSubmit 和 onDecline。
`Confirmation` 可在聊天之外使用，支持普通批准/拒绝及完整 onDecide 回调。
图片、PDF 和视频对话框的 onDismiss 应负责从 composition 中移除对话框。

| API | 依赖模块 |
|---|---|
| [Agent](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-agent.html) | `ai-elements-ui` |
| [rememberAgentComputerState](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/remember-agent-computer-state.html) | `ai-elements-ui` |
| [AgentComputerScaffold](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-computer-scaffold.html) | `ai-elements-ui` |
| [AgentComputerPanel](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-computer-panel.html) | `ai-elements-ui` |
| [StepView](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-step-view.html) | `ai-elements-ui` |
| [FileImage](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-file-image.html) | `ai-elements-ui` |
| [FileAttachment](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-file-attachment.html) | `ai-elements-ui` |
| [AttachmentStrip](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-attachment-strip.html) | `ai-elements-ui` |
| [rememberChat](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/remember-chat.html) | `ai-elements-ui` |
| [Chat](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-chat.html) | `ai-elements-ui` |
| [Confirmation](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-confirmation.html) | `ai-elements-ui` |
| [ContextUsage](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-context-usage.html) | `ai-elements-ui` |
| [Conversation](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-conversation.html) | `ai-elements-ui` |
| [BranchSelector](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-branch-selector.html) | `ai-elements-ui` |
| [Checkpoint](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-checkpoint.html) | `ai-elements-ui` |
| [Queue](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-queue.html) | `ai-elements-ui` |
| [OpenInChat](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-open-in-chat.html) | `ai-elements-ui` |
| [DataPartView](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-data-part-view.html) | `ai-elements-ui` |
| [DocumentAttachment](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-document-attachment.html) | `ai-elements-ui` |
| [PdfViewerDialog](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-pdf-viewer-dialog.html) | `ai-elements-ui` |
| [ImageViewer](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-image-viewer.html) | `ai-elements-ui` |
| [InlineCitation](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-inline-citation.html) | `ai-elements-ui` |
| [CitationSheet](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-citation-sheet.html) | `ai-elements-ui` |
| [InputRequestCard](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-input-request-card.html) | `ai-elements-ui` |
| [SchemaForm](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-schema-form.html) | `ai-elements-ui` |
| [VideoAttachment](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-video-attachment.html) | `ai-elements-ui` |
| [VideoPlayerDialog](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-video-player-dialog.html) | `ai-elements-ui` |
| [MessageItem](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-message-item.html) | `ai-elements-ui` |
| [UserMessage](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-user-message.html) | `ai-elements-ui` |
| [AssistantMessage](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-assistant-message.html) | `ai-elements-ui` |
| [AssistantAvatar](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-assistant-avatar.html) | `ai-elements-ui` |
| [AgentRunDialog](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-run-dialog.html) | `ai-elements-ui` |
| [ModelSelector](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-model-selector.html) | `ai-elements-ui` |
| [PromptInput](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-prompt-input.html) | `ai-elements-ui` |
| [Question](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-question.html) | `ai-elements-ui` |
| [Reasoning](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-reasoning.html) | `ai-elements-ui` |
| [ShimmerText](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-shimmer-text.html) | `ai-elements-ui` |
| [Sources](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-sources.html) | `ai-elements-ui` |
| [rememberStickToBottomState](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/remember-stick-to-bottom-state.html) | `ai-elements-ui` |
| [Subagent](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-subagent.html) | `ai-elements-ui` |
| [ToolPartView](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-part-view.html) | `ai-elements-ui` |
| [Suggestions](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-suggestions.html) | `ai-elements-ui` |
| [ChatEmptyState](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-chat-empty-state.html) | `ai-elements-ui` |
| [ToolCall](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-call.html) | `ai-elements-ui` |
| [ChainOfThought](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-chain-of-thought.html) | `ai-elements-ui` |
| [Plan](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-plan.html) | `ai-elements-ui` |
| [Task](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-task.html) | `ai-elements-ui` |
| [Artifact](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-artifact.html) | `ai-elements-ui` |
| [WebPreview](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-web-preview.html) | `ai-elements-ui` |
| [Snippet](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-snippet.html) | `ai-elements-ui` |
| [Commit](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-commit.html) | `ai-elements-ui` |
| [SchemaDisplay](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-schema-display.html) | `ai-elements-ui` |
| [PackageInfo](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-package-info.html) | `ai-elements-ui` |
| [EnvironmentVariables](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-environment-variables.html) | `ai-elements-ui` |
| [Sandbox](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-sandbox.html) | `ai-elements-ui` |
| [FileTree](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-file-tree.html) | `ai-elements-ui` |
| [StackTrace](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-stack-trace.html) | `ai-elements-ui` |
| [Terminal](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-terminal.html) | `ai-elements-ui` |
| [TestResults](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-test-results.html) | `ai-elements-ui` |
| [rememberCustomTabsUriHandler](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.link/remember-custom-tabs-uri-handler.html) | `ai-elements-ui` |
| [CodeBlock](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-code-block.html) | `ai-elements-ui` |
| [MarkdownContent](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-markdown-content.html) | `ai-elements-ui` |
| [MathBlock](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-math-block.html) | `ai-elements-ui` |
| [MermaidDiagram](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) | `ai-elements-ui` |
| [AiElementsTheme](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.theme/-ai-elements-theme.html) | `ai-elements-ui` |
| [rememberAudioPlayerState](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/remember-audio-player-state.html) | `ai-elements-ui` |
| [AudioPlayer](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/-audio-player.html) | `ai-elements-ui` |
| [Persona](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/-persona.html) | `ai-elements-ui` |
| [MicSelector](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/-mic-selector.html) | `ai-elements-ui` |
| [VoiceSelector](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/-voice-selector.html) | `ai-elements-ui` |
| [rememberSpeechInputState](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/remember-speech-input-state.html) | `ai-elements-ui` |
| [SpeechInput](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/-speech-input.html) | `ai-elements-ui` |
| [rememberSpeechOutputState](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/remember-speech-output-state.html) | `ai-elements-ui` |
| [Transcription](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/-transcription.html) | `ai-elements-ui` |
| [VoiceMode](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.voice/-voice-mode.html) | `ai-elements-ui` |
| [WorkflowCanvas](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.workflow/-workflow-canvas.html) | `ai-elements-ui` |
| [rememberAgentRunLabels](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.workflow/remember-agent-run-labels.html) | `ai-elements-ui` |
| [A2uiSurfaceView](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.a2ui/-a2ui-surface-view.html) | `ai-elements-genui` |
| [JsxPreview](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) | `ai-elements-genui` |
| [McpAppsHost](/ai-elements-kotlin/api/ai-elements-mcp-apps/dev.ai.elements.mcpapps/-mcp-apps-host.html) | `ai-elements-mcp-apps` |
| [rememberMcpAppSessions](/ai-elements-kotlin/api/ai-elements-mcp-apps/dev.ai.elements.mcpapps/remember-mcp-app-sessions.html) | `ai-elements-mcp-apps` |
| [McpAppView](/ai-elements-kotlin/api/ai-elements-mcp-apps/dev.ai.elements.mcpapps/-mcp-app-view.html) | `ai-elements-mcp-apps` |
