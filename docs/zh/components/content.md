# 消息内容

Markdown、代码、数学公式、图表、思考摘要与引用。

## 流式 Markdown { #markdown }

随着内容生成渲染 GitHub 风格的 Markdown。

![流式 Markdown](../../assets/components/markdown.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent(markdown)` |
| AI Elements | `Response` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MarkdownContent](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-markdown-content.html) |

传入累计文本，而不是最后一个 token。streaming 会延后处理尚未完整的复杂内容。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MarkdownContent
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-markdown"
```

## 代码块 { #code-block }

带语法高亮、语言标签与复制按钮的代码块。

![代码块](../../assets/components/code-block.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `CodeBlock(code, language)` |
| AI Elements | `CodeBlock` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [CodeBlock](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-code-block.html) |

language 控制高亮。组件提供复制功能，不会执行代码。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.CodeBlock
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-code-block"
```

## 数学公式 { #math-katex }

使用离线 KaTeX 渲染公式。

![数学公式](../../assets/components/math.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent("$…$")` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MathBlock](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-math-block.html) |

MathBlock 接收不带 Markdown 围栏的 TeX。正文和公式混排时使用 MarkdownContent 与美元符号分隔；KaTeX 资源已随库打包。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MathBlock
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-math"
```

## Mermaid 流程图 { #mermaid-flowchart }

离线展示流程图和步骤关系。

![Mermaid 流程图](../../assets/components/mermaid-flowchart.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MermaidDiagram](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

传入完整 Mermaid 源码。默认 WebView 渲染器支持离线；原生渲染器需要可选模块及实验性 opt-in。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-flowchart"
```

## Mermaid 时序图 { #mermaid-sequence }

展示参与方之间的请求与响应。

![Mermaid 时序图](../../assets/components/mermaid-sequence.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MermaidDiagram](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

传入完整 Mermaid 源码。默认 WebView 渲染器支持离线；原生渲染器需要可选模块及实验性 opt-in。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.Agent
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-sequence"
```

## Mermaid 类图 { #mermaid-class }

展示类及其关系。

![Mermaid 类图](../../assets/components/mermaid-class.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MermaidDiagram](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

传入完整 Mermaid 源码。默认 WebView 渲染器支持离线；原生渲染器需要可选模块及实验性 opt-in。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.Message
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-class"
```

## Mermaid 饼图 { #mermaid-pie }

展示分类占比。

![Mermaid 饼图](../../assets/components/mermaid-pie.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MermaidDiagram](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

传入完整 Mermaid 源码。默认 WebView 渲染器支持离线；原生渲染器需要可选模块及实验性 opt-in。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-pie"
```

## 流式 Mermaid { #mermaid-streaming }

图表尚未生成完毕时先展示源码。

![流式 Mermaid](../../assets/components/mermaid-streaming.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source, complete = false)` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MermaidDiagram](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

在图表闭合前保持 complete=false，避免反复解析不完整的图表。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-streaming"
```

## 思考摘要 { #reasoning }

用可展开的简洁条目展示模型返回的思考内容。

![思考摘要](../../assets/components/reasoning.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Reasoning(part)` |
| AI Elements | `Reasoning` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Reasoning](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-reasoning.html) |

保持 part id 稳定。生成时设置 isStreaming；durationMs 的单位为毫秒。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.ui.chat.Reasoning
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-reasoning"
```

## 信息来源 { #sources }

展示回复引用的来源与链接。

![信息来源](../../assets/components/sources.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Sources(sources)` |
| AI Elements | `Sources` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Sources](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.chat/-sources.html) |

来源 id 应保持稳定。链接通过 LocalUriHandler 打开，应用可替换为自己的路由。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.chat.Sources
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-sources"
```

## 正文引用标记 { #inline-citation }

把正文中的引用编号关联到来源。

![正文引用标记](../../assets/components/inline-citation.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent(text, citations), InlineCitation(sources)` |
| AI Elements | `InlineCitation` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [MarkdownContent](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.markdown/-markdown-content.html) |

正文与引用应使用同一份来源列表。组件负责关联来源，不会核实模型生成内容的真实性。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.markdown.MarkdownContent
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-inline-citation"
```

[Read this page in English](/ai-elements-kotlin/components/content/)
