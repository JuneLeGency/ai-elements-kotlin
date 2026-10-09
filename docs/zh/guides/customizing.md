# 自定义界面

所有组件只渲染统一消息模型，与具体协议无关。通过 CompositionLocal 替换工具、数据片段、代码块或附件加载方式。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:renderers"
```

| 入口 | 用途 |
|---|---|
| tools | 按工具名称替换 renderer |
| tool | 按条件选择工具 renderer，返回 null 保留默认 |
| data | 按 data part 名称替换，包括共享状态字段 |
| codeBlocks | 按代码围栏语言替换 |
| LocalFileLoader | 接入自己的下载、缓存与鉴权 |

## 完整审批决定

ToolRenderer 的回调接收 ToolDecision，可包含理由、编辑参数和记住决定。回调为 null 时只读展示。
renderer 内可用 ToolCall 保留默认审批控件；不要在同一 renderer 内递归调用 ToolPartView。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:custom-tool-decisions"
```

## 主题和链接

AiElementsTheme 可接收自己的 colorScheme、typography 和 shapes，AiSpacing / AiType 提供间距和文字规则。
默认网页链接通过 Custom Tabs 打开；可使用 openLinksInCustomTabs=false，或在主题内部提供自己的 LocalUriHandler。

## 原生 Mermaid

默认 Mermaid 使用随库打包的离线 WebView 渲染器。可选 ai-elements-mermaid-native 提供 NativeMermaidRenderer，
通过 LocalMermaidRenderer 替换。引用处需要 `@OptIn(dev.ai.elements.mermaid.ExperimentalNativeMermaidApi::class)`，
因为原生布局和接口仍为实验性。
