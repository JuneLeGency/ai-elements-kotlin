package dev.ai.elements.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * A tool invocation card (AI Elements `<Tool>`): tool name, a status pill, and
 * an expandable section with the JSON input and the output / error. While the
 * call awaits approval it shows a [Confirmation] with approve / deny actions.
 *
 * @param onApproval answers an approval request; null hides the actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolCall(part: ToolPart, modifier: Modifier = Modifier, onApproval: ((Boolean) -> Unit)? = null) {
    var open by rememberSaveable(part.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = { open = !open },
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("tool-${part.name}"),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(AiSize.avatar)
                        .clip(MaterialShapes.Cookie6Sided.toShape())
                        .background(scheme.tertiaryContainer),
                ) {
                    Icon(Icons.Outlined.Build, null, Modifier.size(AiSize.compactIcon), scheme.onTertiaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        part.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = part.input.compactJson().ifBlank { "…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(part.state)
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (open) "Collapse" else "Expand",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (open) 180f else 0f),
                )
            }
            if (part.state == ToolState.APPROVAL_REQUESTED) {
                Confirmation(
                    title = "Allow ${part.name}?",
                    description = part.input.compactJson().ifBlank { "No arguments" },
                    onApprove = onApproval?.let { { it(true) } },
                    onDeny = onApproval?.let { { it(false) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section("Input", part.input.prettyJson().ifBlank { "{}" })
                    when {
                        part.state == ToolState.OUTPUT_DENIED -> Section("Output", "Denied by user", scheme.error)
                        part.errorText != null -> Section("Error", part.errorText!!, scheme.error)
                        part.output != null -> Section("Output", part.output!!.prettyJson())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusPill(state: ToolState) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (state) {
        ToolState.INPUT_STREAMING, ToolState.INPUT_AVAILABLE -> Triple("Running", scheme.secondaryContainer, scheme.onSecondaryContainer)
        ToolState.OUTPUT_AVAILABLE -> Triple("Done", scheme.primaryContainer, scheme.onPrimaryContainer)
        ToolState.APPROVAL_REQUESTED -> Triple("Approval", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ToolState.OUTPUT_ERROR -> Triple("Error", scheme.errorContainer, scheme.onErrorContainer)
        ToolState.OUTPUT_DENIED -> Triple("Denied", scheme.errorContainer, scheme.onErrorContainer)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = AiSpacing.m, vertical = AiSpacing.xs),
        ) {
            when (state) {
                ToolState.OUTPUT_AVAILABLE -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(AiSize.badgeIcon))
                ToolState.OUTPUT_ERROR -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(AiSize.badgeIcon))
                ToolState.OUTPUT_DENIED -> Icon(Icons.Outlined.Block, null, Modifier.size(AiSize.badgeIcon))
                ToolState.APPROVAL_REQUESTED -> Icon(Icons.Outlined.PanTool, null, Modifier.size(AiSize.badgeIcon))
                else -> LoadingIndicator(Modifier.size(16.dp), color = content)
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Section(title: String, body: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            SelectionContainer {
                Text(
                    text = body,
                    color = color,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                )
            }
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

private fun String.parseJsonOrNull(): JsonElement? =
    if (isBlank()) null else runCatching { Json.parseToJsonElement(this) }.getOrNull()

private fun String.prettyJson(): String =
    parseJsonOrNull()?.let { prettyJson.encodeToString(JsonElement.serializer(), it) } ?: this

private fun String.compactJson(): String = parseJsonOrNull()?.toString() ?: this
