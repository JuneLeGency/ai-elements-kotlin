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
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * A tool invocation card (AI Elements `<Tool>`): tool name, a status pill, and
 * an expandable section with the JSON input and the output / error.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolCall(part: ToolPart, modifier: Modifier = Modifier) {
    var open by rememberSaveable(part.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = { open = !open },
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("tool-${part.name}"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialShapes.Cookie6Sided.toShape())
                        .background(scheme.tertiaryContainer),
                ) {
                    Icon(Icons.Outlined.Build, null, Modifier.size(18.dp), scheme.onTertiaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(part.name, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace))
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
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section("Input", part.input.prettyJson().ifBlank { "{}" })
                    when {
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
        ToolState.OUTPUT_ERROR -> Triple("Error", scheme.errorContainer, scheme.onErrorContainer)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            when (state) {
                ToolState.OUTPUT_AVAILABLE -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp))
                ToolState.OUTPUT_ERROR -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(14.dp))
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
