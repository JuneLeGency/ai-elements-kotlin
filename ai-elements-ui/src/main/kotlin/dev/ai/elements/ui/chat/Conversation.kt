package dev.ai.elements.ui.chat

import dev.ai.elements.ui.voice.LocalSpeechOutput
import dev.ai.elements.ui.voice.rememberSpeechOutputState
import dev.ai.elements.ui.icons.AiIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.chat.ToolDecision
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSpacing
import java.util.IdentityHashMap
import kotlinx.coroutines.launch

/**
 * The scrolling message list (AI Elements `<Conversation>`).
 *
 * Scrolling is handled by [StickToBottomState]: the list follows the stream
 * while you are at the bottom, stops the instant you scroll up to read (the
 * content you are looking at never moves), and a jump-to-latest button
 * resumes following.
 *
 * @param maxContentWidth readable column width on large screens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Conversation(
    state: ChatState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    maxContentWidth: androidx.compose.ui.unit.Dp = 840.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onRegenerate: () -> Unit = {},
    onRetry: () -> Unit = onRegenerate,
    onDismissError: () -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
    onSelectVersion: ((messageId: String, index: Int) -> Unit)? = null,
    onRestoreCheckpoint: ((messageId: String) -> Unit)? = null,
    /** Richer answers to approvals — a reason, edited arguments; shown when set. */
    onToolDecision: ((toolCallId: String, decision: ToolDecision) -> Unit)? = null,
    /** Answers [ChatState.inputRequests], shown as [InputRequestCard]s after the last message. */
    onInputResponse: ((requestId: String, response: InputResponse) -> Unit)? = null,
) = CompositionLocalProvider(
    LocalToolDecision provides onToolDecision,
    // "Read aloud" works without setup; an app can provide its own engine to share it.
    LocalSpeechOutput provides (LocalSpeechOutput.current ?: rememberSpeechOutputState()),
) {
    val scope = rememberCoroutineScope()
    val stick = rememberStickToBottomState(listState)
    // The prompt each reply answers, for "Open in…".
    val prompts = remember(state.messages) {
        var lastPrompt: String? = null
        state.messages.associate { m ->
            if (m.role == Role.USER) lastPrompt = m.text
            m.id to lastPrompt
        }
    }
    val lastAssistantId = state.messages.lastOrNull()?.takeIf { it.role == Role.ASSISTANT }?.id

    // Rows per message *instance*: settled messages keep their instance, so only
    // the reply that is streaming is re-split on each update.
    val rowCache = remember { IdentityHashMap<Message, List<AssistantRow>>() }
    val rowsFor: (Message) -> List<AssistantRow> = { m -> rowCache.getOrPut(m) { assistantRows(m) } }
    SideEffect { rowCache.keys.retainAll(state.messages.toSet()) }

    // Sending a message, or opening another conversation, starts at the bottom and follows.
    val userCount = state.messages.count { it.role == Role.USER }
    val conversationKey = state.messages.firstOrNull()?.id
    LaunchedEffect(userCount, conversationKey) { if (userCount > 0) stick.jumpToLatest() }

    Box(modifier.nestedScroll(stick.connection)) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().testTag("conversation"),
        ) {
            state.messages.forEachIndexed { index, message ->
                // Space between turns; slices of one reply sit closer together.
                val turnGap = Modifier.padding(top = if (index == 0) 0.dp else AiSpacing.turn)
                if (message.role == Role.USER) {
                    item(key = message.id, contentType = "user") {
                        MessageItem(message, turnGap.widthIn(max = maxContentWidth).fillMaxWidth())
                    }
                } else {
                    rowsFor(message).forEach { row ->
                        item(key = row.key, contentType = row.contentType) {
                            AssistantRowItem(
                                row = row,
                                prompt = prompts[message.id],
                                onRegenerate = if (message.id == lastAssistantId && !state.isBusy) onRegenerate else null,
                                onToolApproval = onToolApproval,
                                onSelectVersion = onSelectVersion?.takeIf { !state.isBusy }?.let { cb -> { i -> cb(message.id, i) } },
                                modifier = (if (row.first) turnGap else Modifier.padding(top = AiSpacing.s))
                                    .widthIn(max = maxContentWidth)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
                // A checkpoint after every finished turn that has later messages.
                if (onRestoreCheckpoint != null && index < state.messages.lastIndex && message.role == Role.ASSISTANT && !state.isBusy) {
                    item(key = "checkpoint-${message.id}", contentType = "checkpoint") {
                        Checkpoint(
                            onRestore = { onRestoreCheckpoint(message.id) },
                            modifier = Modifier.padding(top = AiSpacing.s).widthIn(max = maxContentWidth),
                        )
                    }
                }
            }
            if (state.status == ChatStatus.SUBMITTED) {
                item(key = "pending", contentType = "pending") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = AiSpacing.turn).widthIn(max = maxContentWidth).fillMaxWidth().testTag("pending"),
                    ) {
                        AssistantAvatar(active = true)
                        LoadingIndicator(Modifier.size(36.dp))
                    }
                }
            }
            if (onInputResponse != null) {
                state.inputRequests.forEach { request ->
                    item(key = "input-${request.id}", contentType = "input-request") {
                        InputRequestCard(
                            request,
                            onRespond = { onInputResponse(request.id, it) },
                            modifier = Modifier.padding(top = AiSpacing.l).widthIn(max = maxContentWidth),
                        )
                    }
                }
            }
            if (state.status == ChatStatus.ERROR && state.error != null) {
                item(key = "error", contentType = "error") {
                    ErrorCard(state.error!!, onRetry, onDismissError, Modifier.padding(top = AiSpacing.l).widthIn(max = maxContentWidth))
                }
            }
        }

        AnimatedVisibility(
            visible = stick.showJumpToLatest,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            SmallFloatingActionButton(onClick = { scope.launch { stick.jumpToLatest(animated = true) } }) {
                Icon(AiIcons.KeyboardArrowDown, stringResource(R.string.ai_scroll_to_latest))
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("chat-error"),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = AiSpacing.l, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
                Icon(AiIcons.ErrorOutline, null, Modifier.size(20.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_dismiss)) }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.ai_retry)) }
            }
        }
    }
}
