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
import dev.ai.elements.ui.chat.MessageItem
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
