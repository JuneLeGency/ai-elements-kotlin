# MCP Apps

MCP 工具可通过 `_meta.ui.resourceUri` 声明交互式 [MCP App](https://github.com/modelcontextprotocol/ext-apps)。引入 `ai-elements-mcp-apps` 后，聊天在工具调用下显示视图。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:mcp-apps"
```

`McpAppsHost` 在 screen 层显示工具审批和全屏等对话框，应包围整个聊天。

## 沙箱

每个 MCP 服务器的视图使用独立 origin 的 WebView，遵循 resource 声明的 Content-Security-Policy，通过 message port 传输 JSON-RPC。无 JavaScript interface，视图不能导航宿主页、读取文件或 content URI，也不能获得相机、定位等权限。

## 视图能力

- 调用自己服务器的工具，要求 `visibility` 包含 `app`；非只读工具先请求审批。
- `ui/message`：通过 `McpAppActions.message` 发送用户消息。
- `ui/update-model-context`：为下一轮提供上下文，映射为 `DataPart.MODEL_CONTEXT`；设备模型按文本读取，AG-UI 作为 `RunAgentInput.context` 发送。
- `ui/open-link`：打开 `http(s)` 链接。
- 调整尺寸和请求全屏。

`visibility: ["app"]` 的工具不提供给模型。

滚出屏幕时视图继续运行；被 evict 或 host 离开 composition 时通过 `ui/resource-teardown` 销毁。
