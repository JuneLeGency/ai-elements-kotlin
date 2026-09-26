package dev.ai.elements.demo.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.markdown.MermaidDiagram
import dev.ai.elements.ui.markdown.NativeMermaid

/** Evaluation screen: the same sources rendered natively (cmp-mermaid) and by the WebView renderer. */
internal val CompareSamples = listOf(
    "flowchart" to "flowchart LR\n    U([User]) --> C[ChatController]\n    C --> B{Backend}\n    B -->|tool_calls| T[[Agent tools]]\n    T -->|results| B\n    B -->|text / reasoning| R[Markdown + Mermaid UI]",
    "sequence" to "sequenceDiagram\n    autonumber\n    participant U as User\n    participant A as App\n    participant M as Model\n    U->>A: Ask question\n    A->>M: messages + tools\n    M-->>A: tool_call\n    Note over A: run tool\n    A->>M: tool result\n    M-->>A: streamed text",
    "class" to "classDiagram\n    class ChatBackend {\n      <<interface>>\n      +stream(history) Flow\n    }\n    ChatBackend <|.. OpenAiChatBackend\n    ChatBackend <|.. AnthropicBackend\n    ChatController --> ChatBackend",
    "state" to "stateDiagram-v2\n    [*] --> Ready\n    Ready --> Submitted: send\n    Submitted --> Streaming: first token\n    Streaming --> Ready: finish\n    Streaming --> Error: failure\n    Error --> Ready: retry",
    "er" to "erDiagram\n    CONVERSATION ||--o{ MESSAGE : contains\n    MESSAGE ||--|{ PART : has\n    PART }o--|| SOURCE : cites",
    "gantt" to "gantt\n    title Release plan\n    dateFormat YYYY-MM-DD\n    section Build\n    Core      :a1, 2026-09-01, 10d\n    UI        :after a1, 12d\n    section Ship\n    Beta      :2026-09-25, 7d",
    "pie" to "pie title Tokens by part\n    \"Text\" : 62\n    \"Reasoning\" : 28\n    \"Tool I/O\" : 10",
    "mindmap" to "mindmap\n  root((AI Elements))\n    Chat\n      Streaming\n      Scroll\n    Protocols\n      AI SDK\n      AG-UI\n    Rendering\n      Markdown\n      Mermaid",
    "cjk" to "flowchart TD\n    A[用户提问] --> B{需要工具吗?}\n    B -->|是| C[调用工具]\n    B -->|否| D[直接回答]\n    C --> D",
    "streaming (incomplete)" to "flowchart LR\n    A --> B\n    B --> C[Unfinis",
    "invalid" to "flowchart LR\n    A -->> -- B ((",
)

@Composable
fun MermaidCompareScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().testTag("mermaid-compare"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(CompareSamples, key = { it.first }) { (name, source) ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Native (cmp-mermaid)", style = MaterialTheme.typography.labelMedium)
                    NativeMermaid(source, Modifier.fillMaxWidth()) { ms, err -> Log.i("MERMAID_CMP", "$name native ${ms}ms error=$err") }
                    Text("WebView (mermaid.js 12)", style = MaterialTheme.typography.labelMedium)
                    MermaidDiagram(source)
                }
            }
        }
    }
}
