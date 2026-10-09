# 自定义

组件与协议无关：它们渲染聊天模型（`ToolPart.kind` / `source`、数据 part 和消息），不论模型由哪个 backend 生成。可在整个应用中定制渲染，不必 fork `Conversation`。

## 渲染器

`LocalAiElementsRenderers` 可替换 part 的渲染方式：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:renderers"
```

| 键 | 替换对象 |
|---|---|
| `tools` | 按工具名称选择工具调用 |
| `tool` | 任意匹配的工具调用；返回 `null` 保留内置渲染 |
| `data` | 按名称选择数据 part（`data-chart` → `"chart"`），包括 Agent 共享状态的键 |
| `codeBlocks` | 按语言选择 fenced code block |

工具渲染器收到完整的 `ToolDecision` 回调，包括理由、修改后的参数和记住选择。回调为 null 表示只读渲染。内部使用 `ToolCall` 保留标准审批控件；不要调用 `ToolPartView`，否则会递归选择同一个渲染器。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:custom-tool-decisions"
```

`LocalFileLoader` 可使用自己的 HTTP 栈、缓存或认证加载附件预览。

## 主题

```kotlin
AiElementsTheme(colorScheme = brandColors, typography = brandType, shapes = brandShapes) {
    Chat(controller)
}
```

## 链接

引用、来源、Markdown、A2UI 和 MCP Apps 中的 Web 链接默认打开 [Custom Tab](https://developer.chrome.com/docs/android/custom-tabs)：在应用内显示用户的浏览器，使用主题颜色并支持返回聊天。`mailto:`、`tel:` 等 scheme 交给对应应用。所有组件通过 Compose 的 `LocalUriHandler` 打开链接，`AiElementsTheme` 将其设置为 `CustomTabsUriHandler`。

- `AiElementsTheme(openLinksInCustomTabs = false)` 保留平台 handler，即默认浏览器。
- `CustomTabsUriHandler(context, preferNativeApp = true)` 优先打开能处理链接的已安装应用（例如视频应用），再回退到 Custom Tab。
- 在主题内部提供自己的 `LocalUriHandler`，可将链接路由到应用页面。

## 工具声明自己的语义

工具含义来自模型字段，不从名称猜测：`ToolKind.Function`、`ToolKind.Delegation`（显示为 `Subagent`）、`ToolKind.Skill`（技能徽章），以及 `source`（例如以 chip 显示的 MCP 服务器名）。设备端工具通过 `AgentTool.kindFor`、`AgentTool.source` 声明；`ai-elements-core` 的协议映射为服务端工具设置这些字段。

如果组件需要更多信息，应扩展模型，而不是在 UI 中匹配工具名称。

## 原生 Mermaid

默认 Mermaid 渲染器内置且可离线使用。要使用可选的原生渲染器，添加 `ai-elements-mermaid-native` 并通过 `LocalMermaidRenderer` 提供 `NativeMermaidRenderer`。引用它的 composable 需要 `@OptIn(dev.ai.elements.mermaid.ExperimentalNativeMermaidApi::class)`。

原生布局及接口仍为实验性，因此需要 opt-in；稳定的默认渲染器不需要。
