# Elements

The elements are Compose counterparts of [Vercel AI Elements](https://elements.ai-sdk.dev). All 50
AI Elements are covered except the web-only `JSXPreview`, which `ai-elements-genui` replaces with a
native `JsxPreview`. The demo app's **Components** screen shows each one.

| Group (package) | Elements |
|---|---|
| Chat (`ui.chat`) | `Chat` · `Conversation` · `MessageItem` · `PromptInput` · `Suggestions` · `Reasoning` · `ToolCall` + `Confirmation` · `InputRequestCard` · `Subagent` · `Sources` · `InlineCitation` · `ContextUsage` · `BranchSelector` · `Checkpoint` · `Queue` · `OpenInChat` · `ModelSelector` · `Question` · `Agent` |
| Agent structure (`ui.chat`) | `ChainOfThought` · `Plan` · `Task` · `DataPartView` |
| Workflow (`ui.workflow`) | `WorkflowCanvas` · `agentRunGraph` |
| Content (`ui.markdown`) | `MarkdownContent` · `CodeBlock` · `MermaidDiagram` · `MathBlock` · attachments |
| Voice (`ui.voice`) | `Persona` · `SpeechInput` · `AudioPlayer` · `Transcription` · `MicSelector` · `VoiceSelector` |
| Vibe coding (`ui.code`) | `Artifact` · `WebPreview` · `Terminal` · `StackTrace` · `TestResults` · `FileTree` · `Commit` · `SchemaDisplay` · `PackageInfo` · `EnvironmentVariables` · `Sandbox` · `Snippet` |

## Conversation

`Chat` is the whole screen: `Conversation` (the message list) and `PromptInput`, wired to a
`ChatController`. Use the parts directly when you need your own layout; see
[Quick start: pure client](../getting-started/pure-client.md#in-a-viewmodel).

`Conversation` renders each message part with the matching element:

| Part | Element |
|---|---|
| `TextPart` | `MarkdownContent`: GitHub-flavoured Markdown, code with highlighting, Mermaid, KaTeX |
| `ReasoningPart` | `Reasoning`: one quiet line ("Thought for 2s") that expands |
| `ToolPart` | `ToolCall`, `Subagent` for delegations, a skill badge for skill loads, `Confirmation` while it waits for approval |
| `SourcePart` | `Sources`, and `InlineCitation` for `[n]` markers |
| `FilePart` | image and file attachments |
| `DataPart` | `Plan`, `Task`, A2UI surfaces, MCP Apps views, or your own renderer |

Long conversations stay at the bottom while they stream, unless the user scrolls up.

## Layout

Elements adapt to the window: phones get a compact single column; tablets and foldables show the
conversation history as a list–detail pane in the demo.

![The conversation on a tablet, with the history pane](../assets/screenshots/tablet-light-en.webp){ loading=lazy }

## Theme

`AiElementsTheme` applies Material 3 Expressive with dynamic color by default. Pass your own
`colorScheme`, `typography` and `shapes` to use your brand; `AiSpacing` and `AiType` are the
elements' spacing and chat type scale. Icons are Material Symbols Rounded, generated as
`ImageVector`s (`AiIcons`), so the libraries add no icon dependency.

## Languages and accessibility

Strings ship in English, 简体中文, 繁體中文 and 日本語. Elements have content descriptions,
48 dp touch targets and TalkBack semantics.

<div class="grid" markdown>

![简体中文](../assets/screenshots/phone-light-zh-CN.webp){ loading=lazy width="240" }
![日本語](../assets/screenshots/phone-light-ja.webp){ loading=lazy width="240" }

</div>
