package dev.ai.elements.ui.chat

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.ChatState
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.launch

/**
 * The scrolling message list (AI Elements `<Conversation>`).
 *
 * Uses a reversed layout so the newest content is anchored to the bottom: a
 * streaming reply grows upward while the user is at the bottom, and the list
 * stays put if they scrolled back to read. A jump-to-latest button appears
 * when scrolled away.
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
) {
    val scope = rememberCoroutineScope()
    val messages = state.messages.asReversed()
    val lastAssistantId = state.messages.lastOrNull()?.takeIf { it.role == Role.ASSISTANT }?.id

    // Jump to the newest message whenever the user sends one.
    val userCount = state.messages.count { it.role == Role.USER }
    LaunchedEffect(userCount) { if (userCount > 0) listState.animateScrollToItem(0) }

    Box(modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().testTag("conversation"),
        ) {
            if (state.status == ChatStatus.ERROR && state.error != null) {
                item(key = "error") {
                    ErrorCard(state.error!!, onRetry, onDismissError, Modifier.widthIn(max = maxContentWidth).animateItem())
                }
            }
            if (state.status == ChatStatus.SUBMITTED) {
                item(key = "pending") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.widthIn(max = maxContentWidth).fillMaxWidth().animateItem().testTag("pending"),
                    ) {
                        AssistantAvatar(active = true)
                        LoadingIndicator(Modifier.size(36.dp))
                    }
                }
            }
            itemsIndexed(messages, key = { _, m -> m.id }, contentType = { _, m -> m.role }) { _, message ->
                MessageItem(
                    message = message,
                    onRegenerate = if (message.id == lastAssistantId && !state.isBusy) onRegenerate else null,
                    onToolApproval = onToolApproval,
                    modifier = Modifier.widthIn(max = maxContentWidth).fillMaxWidth().animateItem(),
                )
            }
        }

        val awayFromLatest by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 } }
        AnimatedVisibility(
            visible = awayFromLatest,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Scroll to latest")
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
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
