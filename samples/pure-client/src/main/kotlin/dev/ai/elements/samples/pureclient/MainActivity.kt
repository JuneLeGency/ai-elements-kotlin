package dev.ai.elements.samples.pureclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.protocol.agui.AgUiBackend
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.theme.AiElementsTheme

/**
 * The app only renders: the agent runs on a server that speaks AG-UI (here the repo's
 * `server/`, reached from the emulator at 10.0.2.2). Swap in `UiMessageStreamBackend`
 * for an AI SDK server or `A2aBackend` (ai-elements-a2a) for an A2A agent.
 */
class ChatViewModel : ViewModel() {
    val chat = ChatController(
        backend = { approver -> AgUiBackend("http://10.0.2.2:8788/api/agui", approver = approver) },
        scope = viewModelScope,
    )
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
