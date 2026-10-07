# Supporting APIs

The catalog groups common scenarios. These public composables and state factories also
have direct references below; click a name for its full parameter and lifecycle contract.
Use `rememberAudioPlayerState`, `rememberSpeechInputState` and `rememberSpeechOutputState`
inside composition so native resources are released when the screen leaves. A ViewModel
owns the ChatController; Compose state factories own the visual/player state.

`SchemaForm` renders a standalone JSON Schema form; pass onSubmit and onDecline.
`Confirmation` can be used outside chat with onApprove/onDeny and optional onDecide.
`ImageViewer`, `PdfViewerDialog` and `VideoPlayerDialog` require an onDismiss handler
that removes the dialog from composition. File access goes through the documented loader
or platform media APIs; requesting the file is the app's responsibility.

| API | Artifact |
|---|---|
| [Agent](../api/ai-elements-ui/dev.ai.elements.ui.chat/-agent.html) | `ai-elements-ui` |
| [rememberAgentComputerState](../api/ai-elements-ui/dev.ai.elements.ui.chat/remember-agent-computer-state.html) | `ai-elements-ui` |
| [AgentComputerScaffold](../api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-computer-scaffold.html) | `ai-elements-ui` |
| [AgentComputerPanel](../api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-computer-panel.html) | `ai-elements-ui` |
| [StepView](../api/ai-elements-ui/dev.ai.elements.ui.chat/-step-view.html) | `ai-elements-ui` |
| [FileImage](../api/ai-elements-ui/dev.ai.elements.ui.chat/-file-image.html) | `ai-elements-ui` |
| [FileAttachment](../api/ai-elements-ui/dev.ai.elements.ui.chat/-file-attachment.html) | `ai-elements-ui` |
| [AttachmentStrip](../api/ai-elements-ui/dev.ai.elements.ui.chat/-attachment-strip.html) | `ai-elements-ui` |
| [rememberChat](../api/ai-elements-ui/dev.ai.elements.ui.chat/remember-chat.html) | `ai-elements-ui` |
| [Chat](../api/ai-elements-ui/dev.ai.elements.ui.chat/-chat.html) | `ai-elements-ui` |
| [Confirmation](../api/ai-elements-ui/dev.ai.elements.ui.chat/-confirmation.html) | `ai-elements-ui` |
| [ContextUsage](../api/ai-elements-ui/dev.ai.elements.ui.chat/-context-usage.html) | `ai-elements-ui` |
| [Conversation](../api/ai-elements-ui/dev.ai.elements.ui.chat/-conversation.html) | `ai-elements-ui` |
| [BranchSelector](../api/ai-elements-ui/dev.ai.elements.ui.chat/-branch-selector.html) | `ai-elements-ui` |
| [Checkpoint](../api/ai-elements-ui/dev.ai.elements.ui.chat/-checkpoint.html) | `ai-elements-ui` |
| [Queue](../api/ai-elements-ui/dev.ai.elements.ui.chat/-queue.html) | `ai-elements-ui` |
| [OpenInChat](../api/ai-elements-ui/dev.ai.elements.ui.chat/-open-in-chat.html) | `ai-elements-ui` |
| [DataPartView](../api/ai-elements-ui/dev.ai.elements.ui.chat/-data-part-view.html) | `ai-elements-ui` |
| [DocumentAttachment](../api/ai-elements-ui/dev.ai.elements.ui.chat/-document-attachment.html) | `ai-elements-ui` |
| [PdfViewerDialog](../api/ai-elements-ui/dev.ai.elements.ui.chat/-pdf-viewer-dialog.html) | `ai-elements-ui` |
| [ImageViewer](../api/ai-elements-ui/dev.ai.elements.ui.chat/-image-viewer.html) | `ai-elements-ui` |
| [InlineCitation](../api/ai-elements-ui/dev.ai.elements.ui.chat/-inline-citation.html) | `ai-elements-ui` |
| [CitationSheet](../api/ai-elements-ui/dev.ai.elements.ui.chat/-citation-sheet.html) | `ai-elements-ui` |
| [InputRequestCard](../api/ai-elements-ui/dev.ai.elements.ui.chat/-input-request-card.html) | `ai-elements-ui` |
| [SchemaForm](../api/ai-elements-ui/dev.ai.elements.ui.chat/-schema-form.html) | `ai-elements-ui` |
| [VideoAttachment](../api/ai-elements-ui/dev.ai.elements.ui.chat/-video-attachment.html) | `ai-elements-ui` |
| [VideoPlayerDialog](../api/ai-elements-ui/dev.ai.elements.ui.chat/-video-player-dialog.html) | `ai-elements-ui` |
| [MessageItem](../api/ai-elements-ui/dev.ai.elements.ui.chat/-message-item.html) | `ai-elements-ui` |
| [UserMessage](../api/ai-elements-ui/dev.ai.elements.ui.chat/-user-message.html) | `ai-elements-ui` |
| [AssistantMessage](../api/ai-elements-ui/dev.ai.elements.ui.chat/-assistant-message.html) | `ai-elements-ui` |
| [AssistantAvatar](../api/ai-elements-ui/dev.ai.elements.ui.chat/-assistant-avatar.html) | `ai-elements-ui` |
| [AgentRunDialog](../api/ai-elements-ui/dev.ai.elements.ui.chat/-agent-run-dialog.html) | `ai-elements-ui` |
| [ModelSelector](../api/ai-elements-ui/dev.ai.elements.ui.chat/-model-selector.html) | `ai-elements-ui` |
| [PromptInput](../api/ai-elements-ui/dev.ai.elements.ui.chat/-prompt-input.html) | `ai-elements-ui` |
| [Question](../api/ai-elements-ui/dev.ai.elements.ui.chat/-question.html) | `ai-elements-ui` |
| [Reasoning](../api/ai-elements-ui/dev.ai.elements.ui.chat/-reasoning.html) | `ai-elements-ui` |
| [ShimmerText](../api/ai-elements-ui/dev.ai.elements.ui.chat/-shimmer-text.html) | `ai-elements-ui` |
| [Sources](../api/ai-elements-ui/dev.ai.elements.ui.chat/-sources.html) | `ai-elements-ui` |
| [rememberStickToBottomState](../api/ai-elements-ui/dev.ai.elements.ui.chat/remember-stick-to-bottom-state.html) | `ai-elements-ui` |
| [Subagent](../api/ai-elements-ui/dev.ai.elements.ui.chat/-subagent.html) | `ai-elements-ui` |
| [ToolPartView](../api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-part-view.html) | `ai-elements-ui` |
| [Suggestions](../api/ai-elements-ui/dev.ai.elements.ui.chat/-suggestions.html) | `ai-elements-ui` |
| [ChatEmptyState](../api/ai-elements-ui/dev.ai.elements.ui.chat/-chat-empty-state.html) | `ai-elements-ui` |
| [ToolCall](../api/ai-elements-ui/dev.ai.elements.ui.chat/-tool-call.html) | `ai-elements-ui` |
| [ChainOfThought](../api/ai-elements-ui/dev.ai.elements.ui.chat/-chain-of-thought.html) | `ai-elements-ui` |
| [Plan](../api/ai-elements-ui/dev.ai.elements.ui.chat/-plan.html) | `ai-elements-ui` |
| [Task](../api/ai-elements-ui/dev.ai.elements.ui.chat/-task.html) | `ai-elements-ui` |
| [Artifact](../api/ai-elements-ui/dev.ai.elements.ui.code/-artifact.html) | `ai-elements-ui` |
| [WebPreview](../api/ai-elements-ui/dev.ai.elements.ui.code/-web-preview.html) | `ai-elements-ui` |
| [Snippet](../api/ai-elements-ui/dev.ai.elements.ui.code/-snippet.html) | `ai-elements-ui` |
| [Commit](../api/ai-elements-ui/dev.ai.elements.ui.code/-commit.html) | `ai-elements-ui` |
| [SchemaDisplay](../api/ai-elements-ui/dev.ai.elements.ui.code/-schema-display.html) | `ai-elements-ui` |
| [PackageInfo](../api/ai-elements-ui/dev.ai.elements.ui.code/-package-info.html) | `ai-elements-ui` |
| [EnvironmentVariables](../api/ai-elements-ui/dev.ai.elements.ui.code/-environment-variables.html) | `ai-elements-ui` |
| [Sandbox](../api/ai-elements-ui/dev.ai.elements.ui.code/-sandbox.html) | `ai-elements-ui` |
| [FileTree](../api/ai-elements-ui/dev.ai.elements.ui.code/-file-tree.html) | `ai-elements-ui` |
| [StackTrace](../api/ai-elements-ui/dev.ai.elements.ui.code/-stack-trace.html) | `ai-elements-ui` |
| [Terminal](../api/ai-elements-ui/dev.ai.elements.ui.code/-terminal.html) | `ai-elements-ui` |
| [TestResults](../api/ai-elements-ui/dev.ai.elements.ui.code/-test-results.html) | `ai-elements-ui` |
| [rememberCustomTabsUriHandler](../api/ai-elements-ui/dev.ai.elements.ui.link/remember-custom-tabs-uri-handler.html) | `ai-elements-ui` |
| [CodeBlock](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-code-block.html) | `ai-elements-ui` |
| [MarkdownContent](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-markdown-content.html) | `ai-elements-ui` |
| [MathBlock](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-math-block.html) | `ai-elements-ui` |
| [MermaidDiagram](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) | `ai-elements-ui` |
| [AiElementsTheme](../api/ai-elements-ui/dev.ai.elements.ui.theme/-ai-elements-theme.html) | `ai-elements-ui` |
| [rememberAudioPlayerState](../api/ai-elements-ui/dev.ai.elements.ui.voice/remember-audio-player-state.html) | `ai-elements-ui` |
| [AudioPlayer](../api/ai-elements-ui/dev.ai.elements.ui.voice/-audio-player.html) | `ai-elements-ui` |
| [Persona](../api/ai-elements-ui/dev.ai.elements.ui.voice/-persona.html) | `ai-elements-ui` |
| [MicSelector](../api/ai-elements-ui/dev.ai.elements.ui.voice/-mic-selector.html) | `ai-elements-ui` |
| [VoiceSelector](../api/ai-elements-ui/dev.ai.elements.ui.voice/-voice-selector.html) | `ai-elements-ui` |
| [rememberSpeechInputState](../api/ai-elements-ui/dev.ai.elements.ui.voice/remember-speech-input-state.html) | `ai-elements-ui` |
| [SpeechInput](../api/ai-elements-ui/dev.ai.elements.ui.voice/-speech-input.html) | `ai-elements-ui` |
| [rememberSpeechOutputState](../api/ai-elements-ui/dev.ai.elements.ui.voice/remember-speech-output-state.html) | `ai-elements-ui` |
| [Transcription](../api/ai-elements-ui/dev.ai.elements.ui.voice/-transcription.html) | `ai-elements-ui` |
| [VoiceMode](../api/ai-elements-ui/dev.ai.elements.ui.voice/-voice-mode.html) | `ai-elements-ui` |
| [WorkflowCanvas](../api/ai-elements-ui/dev.ai.elements.ui.workflow/-workflow-canvas.html) | `ai-elements-ui` |
| [rememberAgentRunLabels](../api/ai-elements-ui/dev.ai.elements.ui.workflow/remember-agent-run-labels.html) | `ai-elements-ui` |
| [A2uiSurfaceView](../api/ai-elements-genui/dev.ai.elements.genui.a2ui/-a2ui-surface-view.html) | `ai-elements-genui` |
| [JsxPreview](../api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) | `ai-elements-genui` |
| [McpAppsHost](../api/ai-elements-mcp-apps/dev.ai.elements.mcpapps/-mcp-apps-host.html) | `ai-elements-mcp-apps` |
| [rememberMcpAppSessions](../api/ai-elements-mcp-apps/dev.ai.elements.mcpapps/remember-mcp-app-sessions.html) | `ai-elements-mcp-apps` |
| [McpAppView](../api/ai-elements-mcp-apps/dev.ai.elements.mcpapps/-mcp-app-view.html) | `ai-elements-mcp-apps` |
