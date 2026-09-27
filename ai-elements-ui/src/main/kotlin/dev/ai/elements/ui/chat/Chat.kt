package dev.ai.elements.ui.chat

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
 * all wired to [controller]. Wrap it in `AiElementsTheme`; add your own buttons to the
 * input with [toolbar].
 */
@Composable
fun Chat(
    controller: ChatController,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    toolbar: @Composable RowScope.() -> Unit = {},
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.background(MaterialTheme.colorScheme.background).then(modifier).fillMaxSize().imePadding()) {
        Conversation(
            state = state,
            modifier = Modifier.weight(1f),
            onRegenerate = controller::regenerate,
            onToolApproval = controller::respondToApproval,
            onSelectVersion = controller::selectVersion,
            onRestoreCheckpoint = controller::restoreCheckpoint,
        )
        val submit = { if (controller.send(input)) input = "" }
        val onChange = { text: String -> input = text }
        if (placeholder != null) {
            PromptInput(input, onChange, submit, controller::stop, state.isBusy, Modifier.padding(8.dp), placeholder = placeholder, allowQueue = true, toolbar = toolbar)
        } else {
            PromptInput(input, onChange, submit, controller::stop, state.isBusy, Modifier.padding(8.dp), allowQueue = true, toolbar = toolbar)
        }
    }
}
