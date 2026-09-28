package dev.ai.elements.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.icons.AiIcons
import dev.ai.elements.ui.markdown.MarkdownContent
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.compactIconButton
import dev.ai.elements.ui.voice.LocalSpeechOutput
import dev.ai.elements.ui.voice.speakableText
import dev.ai.elements.ui.workflow.WorkflowCanvas
import dev.ai.elements.ui.workflow.agentRunGraph
import dev.ai.elements.ui.workflow.rememberAgentRunLabels

/**
 * Renders any [Message]: a bubble for the user, a full-width part list for the assistant.
 *
 * @param prompt the user prompt this reply answers (enables "Open in…").
 * @param onSelectVersion switch between reply versions (enables the branch selector).
 */
@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier,
    prompt: String? = null,
    onRegenerate: (() -> Unit)? = null,
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
    onSelectVersion: ((index: Int) -> Unit)? = null,
) {
    when (message.role) {
        Role.USER -> UserMessage(message, modifier)
        Role.ASSISTANT -> AssistantMessage(message, modifier, prompt, onRegenerate, onToolApproval, onSelectVersion)
    }
}

/** A user message: attachments above a right-aligned bubble. */
@Composable
fun UserMessage(message: Message, modifier: Modifier = Modifier) {
    val files = message.parts.filterIsInstance<FilePart>()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val bubbleMax = maxWidth * 0.85f
        val imageMax = minOf(maxWidth * 0.7f, 320.dp)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp),
        modifier = modifier.testTag("user-message"),
    ) {
        SelectionContainer {
            Text(
                text,
                style = AiType.body,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** A whole assistant message in one composable (for non-virtualized lists); [Conversation] renders replies block by block instead. */
@Composable
fun AssistantMessage(
    message: Message,
    modifier: Modifier = Modifier,
    prompt: String? = null,
    onRegenerate: (() -> Unit)? = null,
    onToolApproval: ((toolCallId: String, approved: Boolean) -> Unit)? = null,
    onSelectVersion: ((index: Int) -> Unit)? = null,
) {
    val streaming = message.isStreaming
    val sources = message.parts.filterIsInstance<SourcePart>()
    // Full width, as in the conversation: no avatar column.
    Row(modifier.fillMaxWidth().testTag("assistant-message")) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
            message.parts.forEach { part ->
                when (part) {
                    is TextPart -> if (part.text.isNotBlank()) MarkdownContent(part.text, citations = sources, streaming = part.isStreaming)
                    is ReasoningPart -> Reasoning(part)
                    is ToolPart -> ToolCall(part, onApproval = onToolApproval?.let { cb -> { approved -> cb(part.id, approved) } })
                    is FilePart -> FileAttachment(part)
                    is DataPart -> DataPartView(part)
                    is SourcePart -> Unit
                }
            }
            Sources(sources)
            if (!streaming && message.parts.isNotEmpty()) MessageActions(message, prompt, onRegenerate, onSelectVersion)
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
    Box(modifier.size(AiSize.avatar), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(AiSize.avatar)
                .graphicsLayer { rotationZ = rotation }
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.primary),
        )
        Icon(AiIcons.AutoAwesome, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onPrimary)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MessageActions(
    message: Message,
    prompt: String?,
    onRegenerate: (() -> Unit)?,
    onSelectVersion: ((Int) -> Unit)?,
) {
    val clipboard = LocalClipboardManager.current
    var showGraph by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (onSelectVersion != null) BranchSelector(message.versionIndex, message.versions.size, onSelectVersion)
        if (message.text.isNotBlank()) IconButton(
            onClick = { clipboard.setText(AnnotatedString(message.text)) },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.compactIconButton(),
        ) { Icon(AiIcons.ContentCopy, stringResource(R.string.ai_copy), Modifier.size(AiSize.compactIcon)) }
        val speech = LocalSpeechOutput.current
        if (speech != null && speech.isAvailable && message.text.isNotBlank()) {
            val speaking = speech.speakingId == message.id
            IconButton(
                onClick = { if (speaking) speech.stop() else speech.speak(message.id, speakableText(message.text)) },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.compactIconButton().testTag("read-aloud"),
            ) {
                Icon(
                    if (speaking) AiIcons.Stop else AiIcons.VolumeUp,
                    stringResource(if (speaking) R.string.ai_stop_reading else R.string.ai_read_aloud),
                    Modifier.size(AiSize.compactIcon),
                )
            }
        }
        if (onRegenerate != null) {
            IconButton(
                onClick = onRegenerate,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.compactIconButton().testTag("regenerate"),
            ) { Icon(AiIcons.Refresh, stringResource(R.string.ai_regenerate), Modifier.size(AiSize.compactIcon)) }
        }
        // Step through the run on the agent's computer, when it used tools.
        val computer = LocalAgentComputer.current
        if (computer != null && message.parts.any { it is dev.ai.elements.core.model.ToolPart }) IconButton(
            onClick = { computer.open(message.id, 0) },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.compactIconButton().testTag("run-playback-open"),
        ) { Icon(AiIcons.Slideshow, stringResource(R.string.ai_agent_computer), Modifier.size(AiSize.compactIcon)) }
        // Less frequent actions in one overflow menu: a reply row shows at most four icons.
        var more by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { more = true },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.compactIconButton().testTag("message-more"),
            ) { Icon(AiIcons.MoreHoriz, stringResource(R.string.ai_more_actions), Modifier.size(AiSize.compactIcon)) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_view_agent_run)) },
                    leadingIcon = { Icon(AiIcons.AccountTree, null) },
                    onClick = { more = false; showGraph = true },
                    modifier = Modifier.testTag("run-graph"),
                )
                if (!prompt.isNullOrBlank()) {
                    HorizontalDivider()
                    OpenInMenuItems(prompt, DefaultOpenInTargets, onDone = { more = false })
                }
            }
        }
        message.usage?.takeIf { it.totalTokens > 0 }?.let { ContextUsage(it, Modifier.padding(start = 4.dp)) }
    }
    if (showGraph) AgentRunDialog(message, prompt, onDismiss = { showGraph = false })
}

/** Full-screen [WorkflowCanvas] of one agent run. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentRunDialog(message: Message, prompt: String?, onDismiss: () -> Unit) {
    val labels = rememberAgentRunLabels()
    val (nodes, edges) = remember(message, labels) { agentRunGraph(message, prompt, labels = labels) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ai_agent_run), style = MaterialTheme.typography.titleLarge)
                        Text(
                            pluralStringResource(R.plurals.ai_agent_run_subtitle, nodes.size, nodes.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) { Icon(AiIcons.Close, stringResource(R.string.ai_close)) }
                }
                WorkflowCanvas(nodes, edges, Modifier.fillMaxSize().padding(top = 8.dp))
            }
        }
    }
}
