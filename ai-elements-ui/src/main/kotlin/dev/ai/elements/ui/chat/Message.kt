package dev.ai.elements.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.Usage
import dev.ai.elements.ui.markdown.MarkdownContent

/** Renders any [Message]: a bubble for the user, a full-width part list for the assistant. */
@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
) {
    when (message.role) {
        Role.USER -> UserMessage(message, modifier)
        Role.ASSISTANT -> AssistantMessage(message, modifier, onRegenerate, onToolApproval)
    }
}

@Composable
fun UserMessage(message: Message, modifier: Modifier = Modifier) {
    val files = message.parts.filterIsInstance<FilePart>()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val bubbleMax = maxWidth * 0.85f
        val imageMax = minOf(maxWidth * 0.7f, 320.dp)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            files.forEach { file -> FileAttachment(file, Modifier.widthIn(max = imageMax)) }
            if (message.text.isNotBlank()) UserBubble(message.text, Modifier.widthIn(max = bubbleMax))
        }
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp),
        modifier = modifier.testTag("user-message"),
    ) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun AssistantMessage(
    message: Message,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
) {
    val streaming = message.isStreaming
    Row(modifier.fillMaxWidth().testTag("assistant-message"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AssistantAvatar(active = streaming)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            message.parts.forEach { part ->
                when (part) {
                    is TextPart -> if (part.text.isNotBlank()) MarkdownContent(part.text)
                    is ReasoningPart -> Reasoning(part)
                    is ToolPart -> ToolCall(part, onApproval = onToolApproval?.let { cb -> { approved -> cb(part.id, approved) } })
                    is FilePart -> FileAttachment(part)
                    is SourcePart -> Unit
                }
            }
            Sources(message.parts.filterIsInstance<SourcePart>())
            if (!streaming && message.text.isNotBlank()) MessageActions(message.text, message.usage, onRegenerate)
        }
    }
}

/** The assistant's expressive "sparkle" avatar; spins while streaming. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AssistantAvatar(active: Boolean, modifier: Modifier = Modifier) {
    val rotation = if (active) {
        val angle by rememberInfiniteTransition(label = "avatar").animateFloat(
            0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "avatar-rotation",
        )
        angle
    } else 0f
    Box(modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.primary),
        )
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimary)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageActions(text: String, usage: Usage?, onRegenerate: (() -> Unit)?) {
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(text)) },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) { Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(18.dp)) }
        if (onRegenerate != null) {
            IconButton(
                onClick = onRegenerate,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp).testTag("regenerate"),
            ) { Icon(Icons.Outlined.Refresh, "Regenerate", Modifier.size(18.dp)) }
        }
        usage?.takeIf { it.totalTokens > 0 }?.let { ContextUsage(it, Modifier.padding(start = 4.dp)) }
    }
}
