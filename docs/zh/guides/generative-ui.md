# 生成式 UI

Agent 的回复可以包含交互界面。本库支持两种开放格式：

- **[A2UI](https://a2ui.org) v1.0** surface，由 Compose 原生渲染（`ai-elements-genui`）。
- **[MCP Apps](../protocols/mcp-apps.md)** 工具视图，在沙箱 WebView 中渲染（`ai-elements-mcp-apps`）。

## A2UI

不同协议的 surface 均映射为 `DataPart.A2UI`：AG-UI `a2ui-surface` activity、A2A `application/a2ui+json` part、AI SDK `data-a2ui` part。用户操作通过同一 binding 返回（`forwardedProps.a2uiAction`、A2UI A2A part 或 `data-a2ui` part）。

注册渲染器：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:genui"
```

`controller.send(action)` 是 `dev.ai.elements.genui.a2ui` 中的扩展，发送 action 的用户消息及数据 part。

- **Catalog**：内置 Basic Catalog。通过 `A2uiCatalog.Basic.extend(id = "https://example.com/catalog", components = …)` 加入自己的设计系统。
- **状态**：消息继续流入或表单滚出屏幕时，用户输入仍保留。Session 在渲染器而非 lazy list 中维护。
- **聊天之外**：`A2uiSurfaceView` 和 `A2uiState` 可在任意位置显示 surface。
- **生命周期**：携带 `{"status": "building" | "retrying" | "failed"}` 的 part（AG-UI middleware 的 pre-paint lifecycle）显示占位状态。

[参考服务](../develop/reference-server.md) 的酒店 concierge（`hotel`）通过 AG-UI、AI SDK 和 A2A 返回 A2UI 预订表单。

## JSX

`JsxPreview` 原生渲染 JSX（对应 AI Elements 的 `JSXPreview`）：编译为 A2UI 组件，与 surface 共用 catalog，与应用整体外观一致。只处理数据，不执行代码，并支持流式渲染。

```kotlin
JsxPreview(
    jsx = source,                                            // what the model wrote
    bindings = buildJsonObject { put("email", "") },         // the data model {name} reads
    onAction = { action -> controller.send(action) },        // onClick={subscribe} → "subscribe"
)
```

聊天中 `jsxCodeBlocks` 将回复中的 ```` ```jsx ```` 和 ```` ```tsx ```` fence 渲染为可切换源码的实时预览。使用 `AiElementsRenderers(codeBlocks = mapOf("jsx" to jsxCodeBlocks(), "tsx" to jsxCodeBlocks()))` 注册。

### 支持的 JSX

| JSX | 渲染结果 |
|---|---|
| `h1`–`h6`、`p`、`span`、`b` / `strong`、`i` / `em`、`code`、`small` | Text；标题与内联样式按 Markdown 处理 |
| `div`、`section`、`main` 等；`className="flex …"` 或 `flex-row` | Column / Row |
| `ul` / `ol` 与 `li` | 项目符号或编号 Column |
| `hr` | Divider |
| `img src alt` | Image |
| `a href` | 打开 URL 的链接按钮 |
| `button` / `<Button variant>` | Button，`onClick` 为其 action |
| `input`（`type` 为 text、`email`、`password`、`number`）、`textarea` | TextField，`name="x"` 绑定到 `/x` |
| `input type="checkbox" checked={x}` | CheckBox |
| `select name` 与 `option value` | ChoicePicker；`multiple` 支持多选 |
| `<Tabs>` 与 `<Tab title>` | Tabs |
| 按名称使用 Basic Catalog 组件：`Card`、`Row`、`Column`、`List`、`Text`、`Image`、`Icon`、`Video`、`AudioPlayer`、`Slider`、`CheckBox`、`ChoicePicker`、`DateTimeInput`、`TextField`、`Modal` 等 | 对应组件及其属性 |
| 未知标签（`<Fragment>`、`<section>` 等） | 显示子节点 |

### 值与操作

- **绑定**：`{name}`、`{a.b}` 读取 `bindings` 数据模型。绑定路径的输入在本地写回模型，action 携带当前模型。
- **字面量**：字符串、数字、`true` / `false`、JSON 数组与对象，如 `options={[{"label": "S", "value": "s"}]}`。
- **不执行其他代码**：丢弃 `{alert(1)}`、箭头函数等内容，不执行模型输出。
- **操作**：`onClick={save}` 或 `onClick="save"` 向 `onAction` 发送 `save`。

### 流式输出

未写完的标签等待闭合，流结束时关闭未闭合标签。整个流保持同一个 surface，因此更多 JSX 到达时用户输入仍保留。

全部 JSX 示例及源码见 [生成式 UI 组件](../components/generative-ui.md)。Demo 中点击 **Generative UI (JSX form)**，离线 Agent 会返回 JSX 预订表单。
