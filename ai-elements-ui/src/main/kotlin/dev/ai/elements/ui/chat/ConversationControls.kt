package dev.ai.elements.ui.chat

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.QueuedMessage
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.compactIconButton
import java.net.URLEncoder

/**
 * Switch between versions of a reply (AI Elements `<Branch>`): ‹ 2 / 3 ›.
 * Hidden when there is only one version.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BranchSelector(index: Int, count: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (count < 2) return
    Row(modifier.testTag("branch-selector"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onSelect(index - 1) },
            enabled = enabled && index > 0,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.compactIconButton().testTag("branch-previous"),
        ) { Icon(Icons.Outlined.ChevronLeft, "Previous version") }
        Text("${index + 1} / $count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("branch-label"))
        IconButton(
            onClick = { onSelect(index + 1) },
            enabled = enabled && index < count - 1,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.compactIconButton().testTag("branch-next"),
        ) { Icon(Icons.Outlined.ChevronRight, "Next version") }
    }
}

/**
 * A restore point between turns (AI Elements `<Checkpoint>`): a divider with
 * a "Restore" action that rewinds the conversation to here, after confirming.
 */
@Composable
fun Checkpoint(onRestore: () -> Unit, modifier: Modifier = Modifier, label: String = "Checkpoint") {
    var confirm by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { confirm = true }, modifier = Modifier.testTag("checkpoint-restore")) { Text("Restore") }
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Outlined.BookmarkBorder, null) },
            title = { Text("Restore checkpoint?") },
            text = { Text("Messages after this point will be removed from the conversation.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRestore() }, modifier = Modifier.testTag("checkpoint-confirm")) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Prompts waiting for the agent (AI Elements `<Queue>`). Each can be removed;
 * when the queue is [paused] (after stop or an error) each can be sent now.
 */
@Composable
fun Queue(
    items: List<QueuedMessage>,
    paused: Boolean,
    onRemove: (QueuedMessage) -> Unit,
    onSendNow: (QueuedMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(true) }
    AnimatedVisibility(items.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(), modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().testTag("queue"),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                ) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Text(
                        if (paused) "Queued · paused (${items.size})" else "Queued (${items.size})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Show") }
                }
                if (open) items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).testTag("queue-item"),
                    ) {
                        Text(
                            item.text.ifBlank { "${item.attachments.size} attachment(s)" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (paused) {
                            IconButton(onClick = { onSendNow(item) }, modifier = Modifier.compactIconButton().testTag("queue-send")) {
                                Icon(Icons.AutoMirrored.Outlined.Send, "Send now", Modifier.size(AiSize.compactIcon))
                            }
                        }
                        IconButton(onClick = { onRemove(item) }, modifier = Modifier.compactIconButton().testTag("queue-remove")) {
                            Icon(Icons.Outlined.Close, "Remove from queue", Modifier.size(AiSize.compactIcon))
                        }
                    }
                }
            }
        }
    }
}

/** A destination for [OpenInChat]; `{q}` in [urlTemplate] is replaced by the encoded prompt. */
data class OpenInTarget(val label: String, val urlTemplate: String)

val DefaultOpenInTargets = listOf(
    OpenInTarget("ChatGPT", "https://chatgpt.com/?q={q}"),
    OpenInTarget("Claude", "https://claude.ai/new?q={q}"),
    OpenInTarget("Perplexity", "https://www.perplexity.ai/search?q={q}"),
    OpenInTarget("T3 Chat", "https://t3.chat/new?q={q}"),
    OpenInTarget("Scira", "https://scira.ai/?q={q}"),
)

/**
 * Continue a prompt in another assistant (AI Elements `<OpenIn>`), or hand it
 * to any app through the Android share sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenInChat(prompt: String, modifier: Modifier = Modifier, targets: List<OpenInTarget> = DefaultOpenInTargets) {
    var open by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    androidx.compose.foundation.layout.Box(modifier) {
        IconButton(
            onClick = { open = true },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.compactIconButton().testTag("open-in"),
        ) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in…", Modifier.size(AiSize.compactIcon)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Open in",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            targets.forEach { target ->
                DropdownMenuItem(
                    text = { Text(target.label) },
                    onClick = {
                        open = false
                        val q = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
                        runCatching { uriHandler.openUri(target.urlTemplate.replace("{q}", q)) }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Share…") },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = {
                    open = false
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        }
    }
}
