package dev.ai.elements.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.SourcesPart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolCallPart
import dev.ai.elements.ui.reasoning.Reasoning
import dev.ai.elements.ui.shimmer.TextShimmer
import dev.ai.elements.ui.sources.Sources
import dev.ai.elements.core.theme.AiTokens

/**
 * A single chat message bubble, mirroring the web `Message` / `MessageContent`
 * pair.
 *
 * - **User** messages are right-aligned with a [secondary]-coloured rounded bubble.
 * - **Assistant** messages are left-aligned, full-width, and render their
 *   [Part]s in order (text, reasoning, tool calls, sources) with a shimmer on
 *   the last text part while it streams.
 *
 * @param message the [Message] to render.
 * @param onAction optional callback for message actions (copy / regenerate).
 */
@Composable
fun Message(
    message: Message,
    modifier: Modifier = Modifier,
    onAction: ((MessageActionKind) -> Unit)? = null,
) {
    val tokens = AiTokens()
    val isUser = message.role == Role.USER
    val bubbleRadius = tokens.messageBubbleRadius

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = (1200 * tokens.messageMaxWidthFraction).dp)
            .testTag("message_${message.role.name.lowercase()}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val textPart = message.parts.firstOrNull { it is TextPart } as? TextPart

        if (isUser) {
            if (textPart != null) {
                Text(
                    text = textPart.text,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            // Assistant: render parts in order.
            val lastTextPartId = (message.parts.lastOrNull { it is TextPart } as? TextPart)?.id
            message.parts.forEach { part ->
                when (part) {
                    is TextPart -> {
                        TextShimmer(
                            text = part.text,
                            active = part.isStreaming,
                            modifier = Modifier.testTag("assistant_text"),
                            baseColor = MaterialTheme.colorScheme.onBackground,
                            highlightColor = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    is ReasoningPart -> Reasoning(
                        steps = part.steps,
                        isStreaming = part.isStreaming,
                        durationMs = part.durationMs,
                    )

                    is ToolCallPart -> Tool(call = part.call)

                    is SourcesPart -> Sources(sources = part.sources)
                }
            }

            // Message actions row (copy / regenerate), shown when not streaming.
            if (onAction != null && textPart != null && !textPart.isStreaming) {
                MessageActions(
                    text = textPart.text,
                    onAction = onAction,
                )
            }
        }
    }
}

/**
 * Kinds of message-level actions, mirroring the web `MessageAction` tooltips.
 */
enum class MessageActionKind { COPY, REGENERATE }

/**
 * The little action row under an assistant message (copy / regenerate).
 */
@Composable
internal fun MessageActions(
    text: String,
    onAction: (MessageActionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                onAction(MessageActionKind.COPY)
            },
            modifier = Modifier.padding(2.dp),
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = { onAction(MessageActionKind.REGENERATE) }, modifier = Modifier.padding(2.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * Renders a [ToolCallPart] as the web `Tool` component — a labelled card showing
 * the tool name, its running/success/error state, and (optionally) its output.
 */
@Composable
fun Tool(call: dev.ai.elements.core.model.ToolCall) {
    val tokens = AiTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val stateColor = when (call.state) {
                dev.ai.elements.core.model.ToolCallState.RUNNING -> MaterialTheme.colorScheme.primary
                dev.ai.elements.core.model.ToolCallState.SUCCESS -> MaterialTheme.colorScheme.tertiary
                dev.ai.elements.core.model.ToolCallState.ERROR -> MaterialTheme.colorScheme.error
            }
            Icon(
                imageVector = when (call.state) {
                    dev.ai.elements.core.model.ToolCallState.RUNNING -> Icons.Filled.Refresh
                    dev.ai.elements.core.model.ToolCallState.SUCCESS -> Icons.Filled.Check
                    dev.ai.elements.core.model.ToolCallState.ERROR -> Icons.Filled.Close
                },
                contentDescription = null,
                tint = stateColor,
                modifier = Modifier,
            )
            Text(
                text = call.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
        val input = call.input
        if (!input.isNullOrEmpty()) {
            Text(
                text = input.entries.joinToString { "${it.key}: ${it.value}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        call.output?.let { out ->
            Text(
                text = out,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
