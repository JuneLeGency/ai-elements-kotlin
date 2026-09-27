package dev.ai.elements.ui.chat

import dev.ai.elements.ui.voice.VoiceMode
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.chat.ToolApprover

/**
 * A [ChatController] kept for as long as this composition: for prototypes and previews.
 * In an app, create the controller in a `ViewModel` (with `viewModelScope`) so the
 * conversation survives configuration changes, and pass it to [Chat].
 *
 * ```kotlin
 * Chat(rememberChat { approver -> AgUiBackend("https://agents.example.com/api/agui", approver = approver) })
 * ```
 */
@Composable
fun rememberChat(backend: (ToolApprover) -> ChatBackend): ChatController {
    val scope = rememberCoroutineScope()
    return remember { ChatController(backend = backend, scope = scope) }
}

/**
 * A complete chat screen body: the [Conversation] (streaming Markdown, tools and approvals,
 * sub-agents, plans, branches, checkpoints) above a [PromptInput] with stop and queueing,
 * all wired to [controller]. With [voiceMode] (and speech recognition on the device), the send
 * button of an empty input starts a [VoiceMode] conversation. Wrap it in `AiElementsTheme`; add
 * your own buttons to the input with [toolbar].
 */
@Composable
fun Chat(
    controller: ChatController,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    voiceMode: Boolean = true,
    toolbar: @Composable RowScope.() -> Unit = {},
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var talking by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val canTalk = voiceMode && remember { SpeechRecognizer.isRecognitionAvailable(context) }
    if (talking) {
        Dialog(onDismissRequest = { talking = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            VoiceMode(controller, onClose = { talking = false })
        }
    }
    Column(Modifier.background(MaterialTheme.colorScheme.background).then(modifier).fillMaxSize().imePadding()) {
        Conversation(
            state = state,
            modifier = Modifier.weight(1f),
            onRegenerate = controller::regenerate,
            onToolApproval = controller::respondToApproval,
            onSelectVersion = controller::selectVersion,
            onRestoreCheckpoint = controller::restoreCheckpoint,
            onToolDecision = controller::respondToApproval,
            onInputResponse = controller::respondToInput,
        )
        val submit = { if (controller.send(input)) input = "" }
        val onChange = { text: String -> input = text }
        val voice = if (canTalk) ({ talking = true }) else null
        if (placeholder != null) {
            PromptInput(input, onChange, submit, controller::stop, state.isBusy, Modifier.padding(8.dp), placeholder = placeholder, allowQueue = true, onVoiceMode = voice, toolbar = toolbar)
        } else {
            PromptInput(input, onChange, submit, controller::stop, state.isBusy, Modifier.padding(8.dp), allowQueue = true, onVoiceMode = voice, toolbar = toolbar)
        }
    }
}
