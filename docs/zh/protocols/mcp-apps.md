# MCP Apps

工具可通过 _meta.ui.resourceUri 声明一个 ui:// 交互视图。ai-elements-mcp-apps 在工具调用下方展示该视图。
McpAppsHost 应包裹整个聊天界面，以便在页面层处理审批与全屏：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:mcp-apps"
```

## 沙箱与权限

每个 MCP 服务器使用独立 origin，按资源声明应用 CSP，通过 message port 传递 JSON-RPC。
不暴露 JavaScript interface，不允许访问文件或 content URI，不授予相机、定位等系统权限。

视图只能调用自己服务器中 visibility 包含 app 的工具；写操作仍需审批。
ui/message 可发送用户消息，ui/update-model-context 为下一轮提供上下文，ui/open-link 仅打开 http(s) 链接。
视图也可以请求调整大小或全屏。只对 app 可见的工具不会暴露给模型。

滚出可见区域时视图可以继续运行；缓存淘汰或 host 离开 composition 时发送标准 teardown 并释放。
