# 组件图册

这里有 65 个展示场景，分为 10 个类别。截图由 Demo 的组件测试实际渲染，
每项都包含依赖模块、imports、可编译示例、交互说明和 API 链接。
多个场景可能使用同一个 API；完整公开接口见 [API 参考（英文）](/ai-elements-kotlin/api/index.html)。

先完成[安装](../getting-started/installation.md)，再把示例放进 activity 的 `setContent`，
并由 `AiElementsTheme` 包裹。示例函数参数表示由应用提供的状态或回调。
状态工厂、独立表单及其他入口见[辅助 API](supporting.md)。

| 类别 | 组件 |
|---|---|
| [对话组件](conversation.md) | [对话列表](conversation.md#conversation)、[单条消息](conversation.md#messages)、[消息输入框](conversation.md#prompt-input)、[建议提问](conversation.md#suggestions)、[空对话欢迎页](conversation.md#empty-state)、[回复版本切换](conversation.md#branch)、[对话检查点](conversation.md#checkpoint)、[待发送队列](conversation.md#queue)、[在其他聊天应用打开](conversation.md#open-in-chat)、[模型选择器](conversation.md#model-selector)、[Token 使用量](conversation.md#context-token-usage)、[加载指示器](conversation.md#loading-indicators)、[流动高亮文字](conversation.md#shimmer) |
| [消息内容](content.md) | [流式 Markdown](content.md#markdown)、[代码块](content.md#code-block)、[数学公式](content.md#math-katex)、[Mermaid 流程图](content.md#mermaid-flowchart)、[Mermaid 时序图](content.md#mermaid-sequence)、[Mermaid 类图](content.md#mermaid-class)、[Mermaid 饼图](content.md#mermaid-pie)、[流式 Mermaid](content.md#mermaid-streaming)、[思考摘要](content.md#reasoning)、[信息来源](content.md#sources)、[正文引用标记](content.md#inline-citation) |
| [工具与工作面板](tools.md) | [工具调用](tools.md#tool-calls)、[子 Agent](tools.md#sub-agents)、[Agent 工作面板](tools.md#agents-computer)、[回复中的工作预览](tools.md#agents-computer-in-a-reply)、[执行步骤视图](tools.md#step-views)、[Agent 信息卡](tools.md#agent) |
| [人工确认](human-in-the-loop.md) | [工具审批](human-in-the-loop.md#confirmation-tool-approval)、[选择题](human-in-the-loop.md#question)、[信息收集表单](human-in-the-loop.md#input-request-form) |
| [Agent 执行结构](agent-structure.md) | [任务计划](agent-structure.md#plan)、[任务条目](agent-structure.md#task)、[推理步骤](agent-structure.md#chain-of-thought)、[结构化数据片段](agent-structure.md#data-parts-data-plan-data-task) |
| [生成式界面](generative-ui.md) | [JSX 文本与布局](generative-ui.md#jsx-text-and-layout)、[JSX 表单与操作](generative-ui.md#jsx-form-with-bindings-and-an-action)、[JSX 选择控件](generative-ui.md#jsx-choices-slider-date-and-tabs)、[JSX 数据卡片](generative-ui.md#jsx-a-row-of-cards-from-data)、[流式 JSX](generative-ui.md#jsx-streaming)、[A2UI 界面](generative-ui.md#a2ui-surface)、[生成产物](generative-ui.md#artifact)、[网页预览](generative-ui.md#web-preview) |
| [附件与媒体](attachments-and-media.md) | [图片](attachments-and-media.md#image)、[附件列表](attachments-and-media.md#attachments)、[视频](attachments-and-media.md#video)、[PDF 与文档](attachments-and-media.md#document-pdf)、[音频播放器与转写](attachments-and-media.md#audio-player-transcription) |
| [语音交互](voice.md) | [语音对话模式](voice.md#voice-mode)、[语音状态形象](voice.md#persona)、[语音输入](voice.md#speech-input)、[麦克风与音色选择](voice.md#mic-voice-selectors) |
| [开发者工具](developer-tools.md) | [终端输出](developer-tools.md#terminal)、[异常堆栈](developer-tools.md#stack-trace)、[测试结果](developer-tools.md#test-results)、[文件树](developer-tools.md#file-tree)、[提交信息](developer-tools.md#commit)、[接口结构](developer-tools.md#schema-display)、[依赖信息](developer-tools.md#package-info)、[环境变量](developer-tools.md#environment-variables)、[代码与运行结果](developer-tools.md#sandbox)、[可复制命令](developer-tools.md#snippet) |
| [工作流](workflow.md) | [工作流画布](workflow.md#canvas-node-edge) |

MCP Apps 的交互视图需要连接 MCP 服务，未包含在静态图册中。
请看[生成式界面指南](../guides/generative-ui.md)。
