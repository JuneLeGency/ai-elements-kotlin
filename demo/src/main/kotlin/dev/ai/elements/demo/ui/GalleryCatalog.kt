package dev.ai.elements.demo.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import dev.ai.elements.demo.R

/**
 * The groups of the Components screen and of the docs site's component catalog (the same as
 * `docs/guides/elements.md`).
 */
enum class GalleryCategory(@StringRes val label: Int, val slug: String, val title: String) {
    CONVERSATION(R.string.gallery_cat_conversation, "conversation", "Conversation"),
    CONTENT(R.string.gallery_cat_content, "content", "Message content"),
    TOOLS(R.string.gallery_cat_tools, "tools", "Tools and the agent's computer"),
    HITL(R.string.gallery_cat_hitl, "human-in-the-loop", "Human in the loop"),
    STRUCTURE(R.string.gallery_cat_structure, "agent-structure", "Agent structure"),
    GENUI(R.string.gallery_cat_genui, "generative-ui", "Generative UI"),
    MEDIA(R.string.gallery_cat_media, "attachments-and-media", "Attachments and media"),
    VOICE(R.string.gallery_cat_voice, "voice", "Voice"),
    DEVTOOLS(R.string.gallery_cat_devtools, "developer-tools", "Developer tools"),
    WORKFLOW(R.string.gallery_cat_workflow, "workflow", "Workflow"),
}

/**
 * One sample of the Components screen, with what the docs catalog says about it: what it is for,
 * the Vercel AI Elements component it corresponds to, and its main API.
 */
class GallerySample(
    val id: String,
    val title: String,
    val category: GalleryCategory,
    val summary: String,
    val api: String,
    val elements: String? = null,
    val content: @Composable () -> Unit,
)

private val samples: Map<String, @Composable () -> Unit> by lazy { BaseSamples + ExtraSamples }

private fun sample(id: String, title: String, category: GalleryCategory, summary: String, api: String, elements: String? = null) =
    GallerySample(id, title, category, summary, api, elements, samples[title] ?: error("No gallery sample '$title'"))

/** Every sample, in display order: by category, then as listed. */
val GalleryCatalog: List<GallerySample> by lazy {
    listOf(
        // Conversation
        sample("conversation", "Conversation", GalleryCategory.CONVERSATION, "The scrolling message list: follows the stream, stops when you scroll up, jumps back to the latest.", "Conversation(state, onRegenerate, onToolApproval, …)", "Conversation"),
        sample("messages", "Messages", GalleryCategory.CONVERSATION, "A user or assistant message with its parts and actions (copy, read aloud, regenerate, …).", "MessageItem(message, onRegenerate)", "Message"),
        sample("prompt-input", "Prompt input", GalleryCategory.CONVERSATION, "The composer: text, attachments, dictation, send and stop, queueing while the agent works.", "PromptInput(value, onValueChange, onSubmit, onStop, busy)", "PromptInput"),
        sample("suggestions", "Suggestions", GalleryCategory.CONVERSATION, "Tappable prompt suggestions.", "Suggestions(suggestions, onSelect)", "Suggestion"),
        sample("empty-state", "Empty state", GalleryCategory.CONVERSATION, "The start of a conversation: a greeting and suggestions.", "ChatEmptyState(title, subtitle, suggestions, onSelect)"),
        sample("branch", "Branch", GalleryCategory.CONVERSATION, "Switch between regenerated versions of a reply.", "BranchSelector(index, count, onSelect)", "Branch"),
        sample("checkpoint", "Checkpoint", GalleryCategory.CONVERSATION, "Restore the conversation to an earlier point, after confirming.", "Checkpoint(onRestore)", "Checkpoint"),
        sample("queue", "Queue", GalleryCategory.CONVERSATION, "Messages waiting to be sent while the agent is busy.", "Queue(items, paused, onRemove, onSendNow)", "Queue"),
        sample("open-in-chat", "Open in chat", GalleryCategory.CONVERSATION, "Continue a prompt in another chat app.", "OpenInChat(prompt)", "OpenInChat"),
        sample("model-selector", "Model selector", GalleryCategory.CONVERSATION, "Pick a model, with its provider, capabilities and context window.", "ModelSelector(models, selectedId, onSelect)", "ModelSelector"),
        sample("context", "Context (token usage)", GalleryCategory.CONVERSATION, "Tokens used by the conversation, against the model's context window.", "ContextUsage(usage, contextWindow)", "Context"),
        sample("loading", "Loading indicators", GalleryCategory.CONVERSATION, "Material 3 Expressive loading indicators while a reply starts.", "LoadingIndicator(), ContainedLoadingIndicator()", "Loader"),
        sample("shimmer", "Shimmer", GalleryCategory.CONVERSATION, "Text with a moving highlight, for work in progress.", "ShimmerText(text, active)", "Shimmer"),
        // Message content
        sample("markdown", "Markdown", GalleryCategory.CONTENT, "GitHub-flavoured Markdown, rendered as it streams.", "MarkdownContent(markdown)", "Response"),
        sample("code-block", "Code block", GalleryCategory.CONTENT, "Code with syntax highlighting, a language label and copy.", "CodeBlock(code, language)", "CodeBlock"),
        sample("math", "Math (KaTeX)", GalleryCategory.CONTENT, "Inline and display math, offline.", "MarkdownContent(\"\$…\$\")"),
        sample("mermaid-flowchart", "Mermaid · flowchart", GalleryCategory.CONTENT, "Mermaid diagrams, bundled and offline: a flowchart.", "MermaidDiagram(source)"),
        sample("mermaid-sequence", "Mermaid · sequence", GalleryCategory.CONTENT, "A sequence diagram.", "MermaidDiagram(source)"),
        sample("mermaid-class", "Mermaid · class", GalleryCategory.CONTENT, "A class diagram.", "MermaidDiagram(source)"),
        sample("mermaid-pie", "Mermaid · pie", GalleryCategory.CONTENT, "A pie chart.", "MermaidDiagram(source)"),
        sample("mermaid-streaming", "Mermaid · streaming", GalleryCategory.CONTENT, "A diagram still streaming: the source, until it is complete.", "MermaidDiagram(source, complete = false)"),
        sample("reasoning", "Reasoning", GalleryCategory.CONTENT, "The model's thinking: one quiet line that expands.", "Reasoning(part)", "Reasoning"),
        sample("sources", "Sources", GalleryCategory.CONTENT, "The sources a reply used.", "Sources(sources)", "Sources"),
        sample("inline-citation", "Inline citation", GalleryCategory.CONTENT, "[n] markers in the text, linked to their sources.", "MarkdownContent(text, citations), InlineCitation(sources)", "InlineCitation"),
        // Tools and the agent's computer
        sample("tool-calls", "Tool calls", GalleryCategory.TOOLS, "A tool call with its input, output or error, progress, and what provides it.", "ToolCall(part, onApproval)", "Tool"),
        sample("sub-agents", "Sub-agents", GalleryCategory.TOOLS, "A delegated agent's run, nested in the call that started it.", "ToolPartView(part)"),
        sample("agent-computer", "Agent's computer", GalleryCategory.TOOLS, "Each step as the agent saw it (screenshot, terminal, diff, file), with a timeline, autoplay, back to live and replay.", "AgentComputerPanel(state, message), AgentComputerScaffold(state, messages, layout)"),
        sample("agent-computer-reply", "Agent's computer · in a reply", GalleryCategory.TOOLS, "The live preview card under a reply that works on a computer; it opens the panel.", "Conversation / Chat (automatic)"),
        sample("step-views", "Step views", GalleryCategory.TOOLS, "A step by its category: a browser frame with the screenshot, a terminal, a diff.", "StepView(step)"),
        sample("agent", "Agent", GalleryCategory.TOOLS, "An agent's card: model, instructions, tools and output schema.", "Agent(name, model, instructions, tools, outputSchema)", "Agent"),
        // Human in the loop
        sample("confirmation", "Confirmation (tool approval)", GalleryCategory.HITL, "Approve or deny a tool call, with a reason or edited arguments.", "ToolCall(part, onApproval)", "Confirmation"),
        sample("question", "Question", GalleryCategory.HITL, "A multiple-choice question from the agent.", "Question(prompt, options, multiple, onSubmit)"),
        sample("input-request", "Input request (form)", GalleryCategory.HITL, "A form the agent asks the user to fill, from a JSON Schema (AG-UI interrupts, MCP elicitation).", "InputRequestCard(request, onRespond)"),
        // Agent structure
        sample("plan", "Plan", GalleryCategory.STRUCTURE, "The agent's plan and the step it is on.", "Plan(title, description, steps)", "Plan"),
        sample("task", "Task", GalleryCategory.STRUCTURE, "A task and the files it touched.", "Task(title, steps)", "Task"),
        sample("chain-of-thought", "Chain of thought", GalleryCategory.STRUCTURE, "The steps of the agent's reasoning, with their sources.", "ChainOfThought(steps)", "ChainOfThought"),
        sample("data-parts", "Data parts (data-plan, data-task)", GalleryCategory.STRUCTURE, "Custom data parts: known names render as elements, others as data.", "DataPartView(part)"),
        // Generative UI
        sample("jsx-typography", "JSX · Text and layout", GalleryCategory.GENUI, "Model-written JSX rendered natively: headings, text, lists, dividers.", "JsxPreview(jsx), jsxCodeBlocks()", "JSXPreview"),
        sample("jsx-form", "JSX · Form with bindings and an action", GalleryCategory.GENUI, "Inputs bound to the data model; `onClick={subscribe}` sends an action with it.", "JsxPreview(jsx, bindings, onAction)", "JSXPreview"),
        sample("jsx-controls", "JSX · Choices, slider, date and tabs", GalleryCategory.GENUI, "`<select>` / `<option>`, `<Slider>`, `<DateTimeInput>` and `<Tabs>` in JSX.", "JsxPreview(jsx, bindings)", "JSXPreview"),
        sample("jsx-cards", "JSX · A row of cards from data", GalleryCategory.GENUI, "A layout of cards whose values come from bindings, and a link.", "JsxPreview(jsx, bindings)", "JSXPreview"),
        sample("jsx-streaming", "JSX · Streaming", GalleryCategory.GENUI, "JSX rendered while it streams in: half-written tags wait, what the user typed stays.", "JsxPreview(partialJsx)", "JSXPreview"),
        sample("a2ui", "A2UI surface", GalleryCategory.GENUI, "An A2UI v1.0 surface (the reference server's booking form) rendered natively.", "A2uiSurfaceView(surface, onAction), a2uiRenderer()"),
        sample("artifact", "Artifact", GalleryCategory.GENUI, "A generated artifact with its actions.", "Artifact(title, description, actions) { … }", "Artifact"),
        sample("web-preview", "Web preview", GalleryCategory.GENUI, "A generated page or URL in a sandboxed WebView.", "WebPreview(url, html)", "WebPreview"),
        // Attachments and media
        sample("image", "Image", GalleryCategory.MEDIA, "An image, upright by EXIF; tap for the viewer (zoom, pan, rotate).", "FileAttachment(file), FileImage(file), ImageViewer(file, onDismiss)", "Image"),
        sample("attachments", "Attachments", GalleryCategory.MEDIA, "Files attached to a prompt, removable, and images that open full screen.", "AttachmentStrip(attachments, onRemove), FileAttachment(file)", "Attachments"),
        sample("video", "Video", GalleryCategory.MEDIA, "A video's first frame and duration; plays full screen.", "VideoAttachment(file)"),
        sample("document", "Document (PDF)", GalleryCategory.MEDIA, "A PDF's first page and page count; every page in the viewer. Other formats open in an app.", "DocumentAttachment(file)"),
        sample("audio", "Audio player + Transcription", GalleryCategory.MEDIA, "Audio with a transcript that follows it and seeks on tap.", "AudioPlayer(state), Transcription(segments, currentTimeMs, onSeek)", "AudioPlayer, Transcription"),
        // Voice
        sample("voice-mode", "Voice mode", GalleryCategory.VOICE, "A hands-free conversation over the chat's controller.", "VoiceMode(controller, onClose)"),
        sample("persona", "Persona", GalleryCategory.VOICE, "The assistant's animated presence: idle, listening, thinking, speaking.", "Persona(state, size, level)", "Persona"),
        sample("speech-input", "Speech input", GalleryCategory.VOICE, "Dictation with the platform speech recognizer.", "SpeechInput(onTranscript, state)", "SpeechInput"),
        sample("voice-selectors", "Mic & voice selectors", GalleryCategory.VOICE, "Choose the microphone and the reading voice.", "MicSelector(selected, onSelect), VoiceSelector(voices, selectedId, onSelect)", "MicSelector, VoiceSelector"),
        // Developer tools
        sample("terminal", "Terminal", GalleryCategory.DEVTOOLS, "Command output as a terminal shows it: colours, progress bars, links.", "Terminal(output, title, status, exitCode)", "Terminal"),
        sample("stack-trace", "Stack trace", GalleryCategory.DEVTOOLS, "A stack trace with app frames first and library frames folded.", "StackTrace(trace)", "StackTrace"),
        sample("test-results", "Test results", GalleryCategory.DEVTOOLS, "Test suites with passed, failed and skipped cases.", "TestResults(suites, durationMs)", "TestResults"),
        sample("file-tree", "File tree", GalleryCategory.DEVTOOLS, "A project's files, with change badges.", "FileTree(nodes, expanded, selectedPath, onSelect)", "FileTree"),
        sample("commit", "Commit", GalleryCategory.DEVTOOLS, "A commit with its message and changed files.", "Commit(hash, message, author, timestampMs, files)", "Commit"),
        sample("schema-display", "Schema display", GalleryCategory.DEVTOOLS, "An API endpoint: parameters, request and response.", "SchemaDisplay(method, path, parameters, requestBody, responseBody)", "SchemaDisplay"),
        sample("package-info", "Package info", GalleryCategory.DEVTOOLS, "A dependency change and its size.", "PackageInfo(name, fromVersion, toVersion, change)", "PackageInfo"),
        sample("environment-variables", "Environment variables", GalleryCategory.DEVTOOLS, "Environment variables with secrets masked until revealed.", "EnvironmentVariables(variables)", "EnvironmentVariables"),
        sample("sandbox", "Sandbox", GalleryCategory.DEVTOOLS, "Code and its output in tabs.", "Sandbox(title, tabs)", "Sandbox"),
        sample("snippet", "Snippet", GalleryCategory.DEVTOOLS, "A one-line command to copy.", "Snippet(text, prefix)", "Snippet"),
        // Workflow
        sample("workflow-canvas", "Canvas / Node / Edge", GalleryCategory.WORKFLOW, "An agent run as a graph: nodes, edges, labels, pan and zoom.", "WorkflowCanvas(nodes, edges), agentRunGraph(message)", "Canvas, Node, Edge, Controls, Panel, Toolbar"),
    )
}
