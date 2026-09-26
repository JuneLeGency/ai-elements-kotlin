package dev.ai.elements.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.theme.AiTokens
import dev.ai.elements.ui.message.Message

/**
 * The scrollable conversation surface, mirroring the web `Conversation` /
 * `ConversationContent` / `ConversationEmptyState` / `ConversationScrollButton`
 * group.
 *
 * Implements the `use-stick-to-bottom` behaviour natively: the list stays pinned
 * to the latest message while the user is at the bottom, and a floating
 * "scroll to latest" button appears when they scroll up.
 *
 * @param messages the ordered messages to render.
 * @param onAction optional per-message action callback.
 * @param emptyState an optional composable shown when [messages] is empty.
 */
@Composable
fun Conversation(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    onAction: ((dev.ai.elements.ui.message.MessageActionKind) -> Unit)? = null,
    emptyState: @Composable () -> Unit = { ConversationEmptyState() },
) {
    val listState = rememberLazyListState()

    // Whether we are "stuck" to the bottom (last item visible).
    val isAtBottom by remember {
        derivedStateOf {
            val last = messages.lastOrNull()
            if (last == null) true
            else {
                val lastIdx = messages.lastIndex
                listState.layoutInfo.visibleItemsInfo.any { it.index == lastIdx } ||
                    listState.firstVisibleItemIndex >= lastIdx - 1
            }
        }
    }

    // Stick-to-bottom: when a new message arrives and we were already at the
    // bottom, animate to the new last item.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isAtBottom && messages.size > 1) {
            listState.animateScrollToItem(index = messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            emptyState()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    Message(message = message, onAction = onAction)
                }
            }
        }

        // Floating "scroll to latest" button, visible only when scrolled up.
        AnimatedVisibility(
            visible = messages.isNotEmpty() && !isAtBottom,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FilledIconButton(
                onClick = {
                    CoroutineScope(Dispatchers.Main.immediate).launch {
                        listState.animateScrollToItem(index = messages.lastIndex)
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .padding(bottom = 16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    Icons.Filled.ArrowDownward,
                    contentDescription = "Scroll to latest",
                )
            }
        }
    }
}

/**
 * The empty-conversation placeholder, mirroring the web `ConversationEmptyState`.
 */
@Composable
fun ConversationEmptyState(
    modifier: Modifier = Modifier,
    title: String = "No messages yet",
    description: String = "Start a conversation to see messages here",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
