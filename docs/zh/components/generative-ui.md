# 生成式界面

原生 JSX、A2UI、生成产物与网页预览。

## JSX 文本与布局 { #jsx-text-and-layout }

把生成的 JSX 标题、段落与布局渲染为原生组件。

![JSX 文本与布局](../assets/components/jsx-typography.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx), jsxCodeBlocks()` |
| AI Elements | `JSXPreview` |
| 依赖模块 | `ai-elements-genui` |
| API 文档 | [JsxPreview](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

需要 ai-elements-genui，minSdk 26。受支持的 JSX 会编译为原生 A2UI 组件，不执行任意 JavaScript。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-typography"
```

## JSX 表单与操作 { #jsx-form-with-bindings-and-an-action }

用数据绑定和 action 连接表单输入与提交。

![JSX 表单与操作](../assets/components/jsx-form.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings, onAction)` |
| AI Elements | `JSXPreview` |
| 依赖模块 | `ai-elements-genui` |
| API 文档 | [JsxPreview](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

onAction 接收操作名称和当前数据模型。应用负责校验和处理，渲染器不会自动执行服务端写入。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-form"
```

## JSX 选择控件 { #jsx-choices-slider-date-and-tabs }

展示选项、滑块、日期和标签页等控件。

![JSX 选择控件](../assets/components/jsx-controls.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings)` |
| AI Elements | `JSXPreview` |
| 依赖模块 | `ai-elements-genui` |
| API 文档 | [JsxPreview](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

需要 ai-elements-genui，minSdk 26。受支持的 JSX 会编译为原生 A2UI 组件，不执行任意 JavaScript。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-controls"
```

## JSX 数据卡片 { #jsx-a-row-of-cards-from-data }

将绑定数据展示为一组卡片。

![JSX 数据卡片](../assets/components/jsx-cards.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings)` |
| AI Elements | `JSXPreview` |
| 依赖模块 | `ai-elements-genui` |
| API 文档 | [JsxPreview](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

需要 ai-elements-genui，minSdk 26。受支持的 JSX 会编译为原生 A2UI 组件，不执行任意 JavaScript。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-cards"
```

## 流式 JSX { #jsx-streaming }

边生成边渲染 JSX，并保留用户已填写的内容。

![流式 JSX](../assets/components/jsx-streaming.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(partialJsx)` |
| AI Elements | `JSXPreview` |
| 依赖模块 | `ai-elements-genui` |
| API 文档 | [JsxPreview](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

传入累计 JSX，并保持 composable 在布局中的位置稳定。不完整标签会等待后续内容；初始 bindings 保持稳定以保留编辑状态。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-streaming"
```

## A2UI 界面 { #a2ui-surface }

原生渲染符合 A2UI v1.0 的界面。

![A2UI 界面](../assets/components/a2ui.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `A2uiSurfaceView(surface, onAction), a2uiRenderer()` |
| 依赖模块 | `ai-elements-genui` |
| API 文档 | [A2uiSurfaceView](/ai-elements-kotlin/api/ai-elements-genui/dev.ai.elements.genui.a2ui/-a2ui-surface-view.html) |

先让 A2uiState 处理标准事件，再取得 surface。接入聊天时使用 a2uiRenderer，并按传输协议的绑定方式发送 action。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.a2ui.A2uiSurface
import dev.ai.elements.genui.a2ui.A2uiSurfaceView
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-a2ui"
```

## 生成产物 { #artifact }

展示文件或生成内容及相关操作。

![生成产物](../assets/components/artifact.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Artifact(title, description, actions) { … }` |
| AI Elements | `Artifact` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Artifact](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-artifact.html) |

操作按钮通过 actions 插槽提供。保存、分享以及所需权限由应用负责。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.Artifact
import dev.ai.elements.ui.markdown.CodeBlock
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-artifact"
```

## 网页预览 { #web-preview }

在受限 WebView 中预览生成的 HTML 或 URL。

![网页预览](../assets/components/web-preview.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `WebPreview(url, html)` |
| AI Elements | `WebPreview` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [WebPreview](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-web-preview.html) |

预览内容不能当作可信应用界面；需要标准服务端交互桥接时使用 MCP Apps。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.WebPreview
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-web-preview"
```

[Read this page in English](/ai-elements-kotlin/components/generative-ui/)
