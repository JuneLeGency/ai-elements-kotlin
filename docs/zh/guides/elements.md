# 使用组件

可以使用完整 Chat，也可以组合 Conversation、PromptInput 或只使用一个独立元素。
[组件图册](../components/index.md)列出十组共 65 个场景，每项都有真实截图、imports 和编译示例。

| 消息片段 | 默认展示 |
|---|---|
| TextPart | Markdown、代码、公式、Mermaid |
| ReasoningPart | 可展开的思考内容 |
| ToolPart | 工具、子 Agent、进度和审批 |
| SourcePart | 来源与正文引用 |
| FilePart | 图片、视频、音频、PDF 或外部文档 |
| DataPart | 计划、任务、A2UI、MCP Apps 或自定义数据 |

## 主题、语音与适配

AiElementsTheme 使用 Material 3 Expressive，可传入品牌配色、文字和形状。
组件资源支持 English、简体中文、繁體中文与日本語，提供触控尺寸与无障碍语义。
语音使用系统识别/TTS 引擎，App 仍要声明并请求 RECORD_AUDIO；没有识别服务时语音入口会隐藏。

工作面板根据实际容器宽度选择侧栏或底部抽屉。终端输出支持常用 ANSI / ECMA-48 格式、重绘和链接，但不是交互式 shell。
PDF 可直接预览；Word、Excel、PowerPoint 交给设备已安装的查看应用。
