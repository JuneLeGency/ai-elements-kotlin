package dev.ai.elements.samples.uionly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.chat.rememberChat
import dev.ai.elements.ui.theme.AiElementsTheme
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiElementsTheme {
                Chat(rememberChat { ChatBackend { flowOf(ChatEvent.TextDelta("answer", "Published library works"), ChatEvent.Finish) } })
            }
        }
    }
}
