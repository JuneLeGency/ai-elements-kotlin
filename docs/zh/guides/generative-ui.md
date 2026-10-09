# 生成式界面

ai-elements-genui 提供原生 A2UI v1.0 与受支持的 JSX 子集；ai-elements-mcp-apps 则承载 MCP 工具提供的交互网页。
两者都通过 renderer 注册到聊天界面，按需要选择。

## A2UI 与 JSX

A2uiState 接收标准 A2UI 事件，A2uiSurfaceView 展示对应 surface；自定义 catalog 可扩展原生组件。
a2uiRenderer 用于聊天接入，用户 action 应通过对应协议的绑定发送回去。
JSX 不执行任意 JavaScript，bindings 提供数据，onAction 回传操作名称及当前模型。

图册中提供[表单与流式示例](../components/generative-ui.md)，包含所需 imports 和可编译代码。
请把 state 或 renderer 放在稳定的生命周期内，避免列表滚动时重置用户输入。

## MCP Apps

MCP Apps 使用 ui:// 资源、标准 ui/* 桥接与沙箱 WebView，需要与原 MCP 服务器保持连接。
在宿主根部提供 McpAppsHost，并协商客户端能力：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:mcp-apps"
```

只允许调用对应视图所属服务器中对 App 可见的工具。写操作应保留审批；资源的 CSP、来源隔离和链接策略由 host 执行。
普通 WebPreview 只做预览，不能替代 MCP Apps 的标准桥接。
