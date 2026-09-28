# Message content

What a reply contains: Markdown, code, math, diagrams, reasoning and sources.

## Markdown

GitHub-flavoured Markdown, rendered as it streams.

![Markdown](../assets/components/markdown.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent(markdown)` |
| AI Elements | `Response` |

## Code block

Code with syntax highlighting, a language label and copy.

![Code block](../assets/components/code-block.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `CodeBlock(code, language)` |
| AI Elements | `CodeBlock` |

## Math (KaTeX)

Inline and display math, offline.

![Math (KaTeX)](../assets/components/math.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent("$…$")` |

## Mermaid · flowchart

Mermaid diagrams, bundled and offline: a flowchart.

![Mermaid · flowchart](../assets/components/mermaid-flowchart.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |

## Mermaid · sequence

A sequence diagram.

![Mermaid · sequence](../assets/components/mermaid-sequence.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |

## Mermaid · class

A class diagram.

![Mermaid · class](../assets/components/mermaid-class.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |

## Mermaid · pie

A pie chart.

![Mermaid · pie](../assets/components/mermaid-pie.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source)` |

## Mermaid · streaming

A diagram still streaming: the source, until it is complete.

![Mermaid · streaming](../assets/components/mermaid-streaming.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MermaidDiagram(source, complete = false)` |

## Reasoning

The model's thinking: one quiet line that expands.

![Reasoning](../assets/components/reasoning.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Reasoning(part)` |
| AI Elements | `Reasoning` |

## Sources

The sources a reply used.

![Sources](../assets/components/sources.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Sources(sources)` |
| AI Elements | `Sources` |

## Inline citation

[n] markers in the text, linked to their sources.

![Inline citation](../assets/components/inline-citation.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MarkdownContent(text, citations), InlineCitation(sources)` |
| AI Elements | `InlineCitation` |
