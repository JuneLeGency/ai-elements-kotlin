# Message content

What a reply contains: Markdown, code, math, diagrams, reasoning and sources.

## Markdown

GitHub-flavoured Markdown, rendered as it streams.

![Markdown](../assets/components/markdown.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent(markdown)` |
| AI Elements | `Response` |
| Artifact | `ai-elements-ui` |
| Reference | [MarkdownContent](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-markdown-content.html) |

Pass the accumulated text, not just the latest token. streaming defers expensive rendering of incomplete content.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MarkdownContent
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-markdown"
```

## Code block

Code with syntax highlighting, a language label and copy.

![Code block](../assets/components/code-block.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `CodeBlock(code, language)` |
| AI Elements | `CodeBlock` |
| Artifact | `ai-elements-ui` |
| Reference | [CodeBlock](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-code-block.html) |

language controls syntax highlighting. The element provides copy; it does not execute code.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.CodeBlock
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-code-block"
```

## Math (KaTeX)

Inline and display math, offline.

![Math (KaTeX)](../assets/components/math.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent("$…$")` |
| Artifact | `ai-elements-ui` |
| Reference | [MathBlock](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-math-block.html) |

Pass TeX without Markdown fences. For mixed prose use MarkdownContent with dollar delimiters. KaTeX assets are bundled offline.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MathBlock
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-math"
```

## Mermaid · flowchart

Mermaid diagrams, bundled and offline: a flowchart.

![Mermaid · flowchart](../assets/components/mermaid-flowchart.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| Artifact | `ai-elements-ui` |
| Reference | [MermaidDiagram](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

Pass complete Mermaid source. The default WebView renderer works offline; the native renderer requires its optional artifact and experimental opt-in.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-flowchart"
```

## Mermaid · sequence

A sequence diagram.

![Mermaid · sequence](../assets/components/mermaid-sequence.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| Artifact | `ai-elements-ui` |
| Reference | [MermaidDiagram](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

Pass complete Mermaid source. The default WebView renderer works offline; the native renderer requires its optional artifact and experimental opt-in.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.chat.Agent
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-sequence"
```

## Mermaid · class

A class diagram.

![Mermaid · class](../assets/components/mermaid-class.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| Artifact | `ai-elements-ui` |
| Reference | [MermaidDiagram](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

Pass complete Mermaid source. The default WebView renderer works offline; the native renderer requires its optional artifact and experimental opt-in.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.Message
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-class"
```

## Mermaid · pie

A pie chart.

![Mermaid · pie](../assets/components/mermaid-pie.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |
| Artifact | `ai-elements-ui` |
| Reference | [MermaidDiagram](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

Pass complete Mermaid source. The default WebView renderer works offline; the native renderer requires its optional artifact and experimental opt-in.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-pie"
```

## Mermaid · streaming

A diagram still streaming: the source, until it is complete.

![Mermaid · streaming](../assets/components/mermaid-streaming.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source, complete = false)` |
| Artifact | `ai-elements-ui` |
| Reference | [MermaidDiagram](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-mermaid-diagram.html) |

Keep complete=false until the diagram closes; partial source is shown instead of repeatedly parsing an unfinished diagram.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.markdown.MermaidDiagram
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-mermaid-streaming"
```

## Reasoning

The model's thinking: one quiet line that expands.

![Reasoning](../assets/components/reasoning.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Reasoning(part)` |
| AI Elements | `Reasoning` |
| Artifact | `ai-elements-ui` |
| Reference | [Reasoning](../api/ai-elements-ui/dev.ai.elements.ui.chat/-reasoning.html) |

Use a stable part id. Set isStreaming while reasoning arrives; durationMs is elapsed time in milliseconds.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.ui.chat.Reasoning
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-reasoning"
```

## Sources

The sources a reply used.

![Sources](../assets/components/sources.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Sources(sources)` |
| AI Elements | `Sources` |
| Artifact | `ai-elements-ui` |
| Reference | [Sources](../api/ai-elements-ui/dev.ai.elements.ui.chat/-sources.html) |

Source ids must remain stable. URLs open through LocalUriHandler; override it to route links in your app.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.chat.Sources
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-sources"
```

## Inline citation

[n] markers in the text, linked to their sources.

![Inline citation](../assets/components/inline-citation.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent(text, citations), InlineCitation(sources)` |
| AI Elements | `InlineCitation` |
| Artifact | `ai-elements-ui` |
| Reference | [MarkdownContent](../api/ai-elements-ui/dev.ai.elements.ui.markdown/-markdown-content.html) |

Use the same source list for the answer and its citations. Citation markers are linked to source entries; this does not verify generated claims.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.markdown.MarkdownContent
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-inline-citation"
```
