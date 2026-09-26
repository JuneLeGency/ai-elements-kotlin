package dev.ai.elements.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Usage
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import dev.ai.elements.core.QueuedMessage
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.ui.chat.BranchSelector
import dev.ai.elements.ui.chat.CanvasEdge
import dev.ai.elements.ui.chat.CanvasNode
import dev.ai.elements.ui.chat.Checkpoint
import dev.ai.elements.ui.chat.DataPartView
import dev.ai.elements.ui.chat.InlineCitation
import dev.ai.elements.ui.chat.NodeTone
import dev.ai.elements.ui.chat.OpenInChat
import dev.ai.elements.ui.chat.Queue
import dev.ai.elements.ui.chat.WorkflowCanvas
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.serialization.json.Json
import dev.ai.elements.ui.chat.Artifact
import dev.ai.elements.ui.chat.ChainOfThought
import dev.ai.elements.ui.chat.ContextUsage
import dev.ai.elements.ui.chat.FileAttachment
import dev.ai.elements.ui.chat.MessageItem
import dev.ai.elements.ui.chat.Plan
import dev.ai.elements.ui.chat.StepStatus
import dev.ai.elements.ui.chat.Task
import dev.ai.elements.ui.chat.WebPreview
import dev.ai.elements.ui.chat.WorkflowStep
import dev.ai.elements.ui.chat.PromptInput
import dev.ai.elements.ui.chat.Reasoning
import dev.ai.elements.ui.chat.Sources
import dev.ai.elements.ui.chat.Suggestions
import dev.ai.elements.ui.chat.ToolCall
import dev.ai.elements.ui.markdown.CodeBlock
import dev.ai.elements.ui.markdown.MarkdownContent
import dev.ai.elements.ui.markdown.MermaidDiagram

/** Every AI Elements component with sample data; a 1–3 column staggered grid by width. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Components") }) }) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(360.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            GallerySamples.forEach { (title, content) ->
                item(key = title) { GalleryCard(title) { content() } }
            }
        }
    }
}

@Composable
private fun GalleryCard(title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private const val SampleMarkdown = """
### Markdown
Supports **bold**, *italic*, ~~strike~~, `inline code` and [links](https://m3.material.io).

- Bullet lists
  - with nesting
1. Numbered lists

> Block quotes for callouts.

| Component | Web | Compose |
|---|---|---|
| Message | ✅ | ✅ |
| Reasoning | ✅ | ✅ |
| Mermaid | — | ✅ |
"""

private const val FlowchartSample = """flowchart TD
    A[User prompt] --> B{Needs a tool?}
    B -->|yes| C[Call tool]
    C --> D[Observe result]
    D --> B
    B -->|no| E[Stream answer]"""

private const val SequenceSample = """sequenceDiagram
    autonumber
    participant U as User
    participant A as App
    participant M as Model
    U->>A: Ask question
    A->>M: messages + tools
    M-->>A: tool_call
    A->>A: run tool
    A->>M: tool result
    M-->>A: streamed text
    A-->>U: Markdown + diagram"""

private const val ClassSample = """classDiagram
    class ChatBackend {
      <<interface>>
      +stream(history) Flow~ChatEvent~
    }
    ChatBackend <|.. OpenAiChatBackend
    ChatBackend <|.. AnthropicBackend
    ChatBackend <|.. UiMessageStreamBackend
    ChatBackend <|.. MockAgentBackend
    ChatController --> ChatBackend"""

private const val PieSample = """pie title Tokens by part
    "Text" : 62
    "Reasoning" : 28
    "Tool I/O" : 10"""

private val GallerySources = listOf(
    SourcePart("s1", "https://elements.ai-sdk.dev", "AI Elements"),
    SourcePart("s2", "https://m3.material.io/blog/building-with-m3-expressive", "M3 Expressive"),
    SourcePart("s3", "https://developer.android.com/develop/ui/compose/designsystems/material3", "Material 3 in Compose"),
)

private val sampleAssistant = Message(
    id = "gallery-a",
    role = Role.ASSISTANT,
    parts = listOf(
        ReasoningPart("r", "The user wants a summary; I'll keep it short.", durationMs = 2400),
        TextPart("t", "Here's a **short** answer with `inline code`."),
        SourcePart("s1", "https://elements.ai-sdk.dev", "AI Elements"),
        SourcePart("s2", "https://m3.material.io", "Material 3"),
    ),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val GallerySamples: List<Pair<String, @Composable () -> Unit>> = listOf(
    "Messages" to {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MessageItem(Message("gallery-u", Role.USER, listOf(TextPart("u", "Summarise AI Elements in one line"))))
            MessageItem(sampleAssistant, onRegenerate = {})
        }
    },
    "Reasoning" to {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Reasoning(ReasoningPart("g-r1", "Comparing the options…\nOption A is simpler.", isStreaming = true))
            Reasoning(ReasoningPart("g-r2", "Weighed three approaches and picked the simplest.", durationMs = 4200))
        }
    },
    "Tool calls" to {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolCall(ToolPart("g-t1", "get_current_time", ToolState.INPUT_AVAILABLE, """{"timezone":"Asia/Tokyo"}"""))
            ToolCall(ToolPart("g-t2", "calculate", ToolState.OUTPUT_AVAILABLE, """{"expression":"6*7"}""", output = "42"))
            ToolCall(ToolPart("g-t3", "fetch_url", ToolState.OUTPUT_ERROR, """{"url":"https://x"}""", errorText = "Timed out"))
        }
    },
    "Confirmation (tool approval)" to {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolCall(
                ToolPart("g-t4", "copy_to_clipboard", ToolState.APPROVAL_REQUESTED, """{"text":"Hello"}"""),
                onApproval = {},
            )
            ToolCall(ToolPart("g-t5", "delete_files", ToolState.OUTPUT_DENIED, """{"path":"/"}"""))
        }
    },
    "Context (token usage)" to {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextUsage(Usage(inputTokens = 12_480, outputTokens = 1_730))
            ContextUsage(Usage(inputTokens = 96_000, outputTokens = 4_000), contextWindow = 128_000)
        }
    },
    "Chain of thought" to {
        ChainOfThought(
            listOf(
                WorkflowStep("Searching the web", "Looked for Compose adaptive layouts", StepStatus.COMPLETE, listOf("developer.android.com", "m3.material.io")),
                WorkflowStep("Reading sources", "Comparing window size classes", StepStatus.COMPLETE),
                WorkflowStep("Drafting the answer", status = StepStatus.ACTIVE),
            ),
        )
    },
    "Plan" to {
        Plan(
            title = "Add tablet support",
            description = "Three steps, about an hour",
            steps = listOf(
                WorkflowStep("Measure window width classes", status = StepStatus.COMPLETE),
                WorkflowStep("Swap bottom bar for a navigation rail", status = StepStatus.ACTIVE),
                WorkflowStep("Show history as a side pane"),
            ),
        )
    },
    "Task" to {
        Task(
            "Refactor the chat screen",
            listOf(
                WorkflowStep("Read files", badges = listOf("ChatScreen.kt", "DemoApp.kt"), status = StepStatus.COMPLETE),
                WorkflowStep("Edited 2 files", badges = listOf("Conversation.kt"), status = StepStatus.ACTIVE),
            ),
        )
    },
    "Math (KaTeX)" to {
        MarkdownContent("Inline: \$\\pi r^2\$, \$a \\times b \\leq c\$\n\n\$\$\n\\int_0^\\infty e^{-x^2}\\,dx = \\frac{\\sqrt{\\pi}}{2}\n\$\$")
    },
    "Artifact" to {
        Artifact(
            title = "fibonacci.kt",
            description = "Generated code",
            actions = {
                IconButton(onClick = {}) { Icon(Icons.Outlined.ContentCopy, "Copy") }
                IconButton(onClick = {}) { Icon(Icons.Outlined.Download, "Download") }
            },
        ) {
            CodeBlock("fun fib(n: Int): Long =\n    if (n < 2) n.toLong() else fib(n - 1) + fib(n - 2)", "kotlin")
        }
    },
    "Web preview" to {
        WebPreview(
            url = "about:blank",
            html = "<html><body style='font-family:sans-serif;padding:16px'><h2>Generated page</h2>" +
                "<p>Agents can emit HTML that previews here.</p><button onclick=\"this.innerText='Clicked!'\">Try me</button></body></html>",
            height = 200.dp,
        )
    },
    "Image" to {
        FileAttachment(FilePart("g-img", "image/png", SampleImageDataUrl), imageHeight = 160.dp)
    },
    "Branch" to {
        var version by remember { mutableStateOf(1) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(listOf("A concise answer.", "A longer, more detailed answer.", "A playful answer!")[version])
            BranchSelector(version, 3, onSelect = { version = it })
        }
    },
    "Checkpoint" to { Checkpoint(onRestore = {}) },
    "Queue" to {
        Queue(
            items = listOf(
                QueuedMessage("q1", "Also add a dark theme"),
                QueuedMessage("q2", "Then write the release notes"),
            ),
            paused = true,
            onRemove = {},
            onSendNow = {},
        )
    },
    "Inline citation" to {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MarkdownContent(
                "AI Elements ships chat components [1], styled here with M3 Expressive [2][3].",
                citations = GallerySources,
            )
            InlineCitation(GallerySources)
        }
    },
    "Open in chat" to {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Continue this prompt elsewhere", Modifier.weight(1f))
            OpenInChat("Explain Material 3 Expressive in three bullet points")
        }
    },
    "Data parts (data-plan, data-task)" to {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DataPartView(
                DataPart(
                    "d1", "plan",
                    Json.parseToJsonElement(
                        """{"title":"Ship v1","description":"streamed by the agent","streaming":true,""" +
                            """"steps":[{"label":"Write code","status":"complete"},{"label":"Test","status":"active"},"Release"]}""",
                    ),
                ),
            )
            DataPartView(DataPart("d2", "weather", Json.parseToJsonElement("""{"city":"Tokyo","tempC":21}""")))
        }
    },
    "Canvas / Node / Edge" to {
        val nodes = listOf(
            CanvasNode("a", "Prompt", "User question", DpOffset(80.dp, 0.dp), Icons.Outlined.Person, NodeTone.NEUTRAL),
            CanvasNode("b", "search_docs", "3 results", DpOffset(0.dp, 120.dp), Icons.Outlined.Build, NodeTone.TERTIARY),
            CanvasNode("c", "calculate", "42", DpOffset(220.dp, 120.dp), Icons.Outlined.Build, NodeTone.TERTIARY),
            CanvasNode("d", "Answer", "Streaming…", DpOffset(80.dp, 240.dp), Icons.Outlined.AutoAwesome, status = StepStatus.ACTIVE),
        )
        val edges = listOf(
            CanvasEdge("a", "b"), CanvasEdge("a", "c"),
            CanvasEdge("b", "d", label = "sources", animated = true), CanvasEdge("c", "d", animated = true),
        )
        WorkflowCanvas(nodes, edges, Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.large), showControls = false)
    },
    "Sources" to {
        Sources(sampleAssistant.parts.filterIsInstance<SourcePart>())
    },
    "Markdown" to { MarkdownContent(SampleMarkdown.trim()) },
    "Code block" to {
        CodeBlock(
            "data class Message(\n    val id: String,\n    val role: Role,\n    val parts: List<Part>,\n)",
            "kotlin",
        )
    },
    "Mermaid · flowchart" to { MermaidDiagram(FlowchartSample) },
    "Mermaid · sequence" to { MermaidDiagram(SequenceSample) },
    "Mermaid · class" to { MermaidDiagram(ClassSample) },
    "Mermaid · pie" to { MermaidDiagram(PieSample) },
    "Mermaid · streaming" to { MermaidDiagram("flowchart LR\n  A --> B", complete = false) },
    "Suggestions" to {
        Suggestions(
            listOf(Suggestion("Plan a trip"), Suggestion("Explain transformers"), Suggestion("Draw an ER diagram")),
            onSelect = {},
        )
    },
    "Prompt input" to {
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        PromptInput(
            value = text,
            onValueChange = { text = it },
            onSubmit = { busy = true },
            onStop = { busy = false },
            busy = busy,
        )
    },
    "Loading indicators" to {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(48.dp))
            ContainedLoadingIndicator(Modifier.size(48.dp))
        }
    },
)

/** A tiny generated gradient PNG, as a model-returned image would arrive. */
private val SampleImageDataUrl: String by lazy {
    val bitmap = android.graphics.Bitmap.createBitmap(320, 160, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 320f, 160f, 0xFF6750A4.toInt(), 0xFF7D5260.toInt(), android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, 320f, 160f, paint)
    paint.shader = null
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 28f
    canvas.drawText("Generated image", 60f, 90f, paint)
    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}
