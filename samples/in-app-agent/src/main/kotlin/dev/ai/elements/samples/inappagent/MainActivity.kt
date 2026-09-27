package dev.ai.elements.samples.inappagent

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.harness.AgentHarness
import dev.ai.elements.harness.filesystem.FileSystem
import dev.ai.elements.harness.model
import dev.ai.elements.harness.planning.Planning
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.theme.AiElementsTheme
import java.io.File

/**
 * The agent runs in the app: a model API (here a local Ollama, reached from the emulator at
 * 10.0.2.2) plus harness capabilities — workspace files and a live plan. Add `Shell`,
 * `Memory`, `WebBrowser`, `DeviceTools`, `Skills`, MCP servers or sub-agents the same way.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val harness = AgentHarness(
        model = ProviderProfile("ollama", "Ollama", ProviderKind.OLLAMA, "http://10.0.2.2:11434", "qwen3:4b").model(),
        capabilities = { listOf(FileSystem(File(app.filesDir, "workspace")), Planning()) },
    )
    val chat = ChatController(backend = harness::backend, scope = viewModelScope)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiElementsTheme {
                Chat(viewModel<ChatViewModel>().chat, Modifier.safeDrawingPadding())
            }
        }
    }
}
