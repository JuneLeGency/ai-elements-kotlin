package dev.ai.elements.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.ai.elements.ui.theme.AiType
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing
import dev.ai.elements.ui.theme.LocalCodeFontFamily
import dev.ai.elements.ui.theme.fadingHorizontalScroll
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

    // One line — icon, name, a preview of the arguments (or live progress), status — that opens
    // to the input and output, the way assistant apps list tool calls without taking the screen.
    Surface(
        onClick = { open = !open },
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag("tool-${part.name}"),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(min = 24.dp)) {
                Icon(Icons.Outlined.Build, null, Modifier.size(16.dp), scheme.onSurfaceVariant)
                ToolTitle(part)
                Text(
                    text = subtitle(part),
                    style = AiType.small.copy(fontFamily = LocalCodeFontFamily.current),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(part.state)
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (open) stringResource(R.string.ai_collapse) else stringResource(R.string.ai_expand),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                )
            }
            if (part.state == ToolState.APPROVAL_REQUESTED) {
                Confirmation(
                    title = stringResource(R.string.ai_allow_tool, part.displayName),
                    description = part.input.compactJson().ifBlank { stringResource(R.string.ai_no_arguments) },
                    onApprove = onApproval?.let { { it(true) } },
                    onDeny = onApproval?.let { { it(false) } },
                    modifier = Modifier.padding(top = 12.dp),
                    input = part.input,
                    onDecide = onApproval?.let { LocalToolDecision.current }?.let { cb -> { decision -> cb(part.id, decision) } },
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section(stringResource(R.string.ai_tool_input), part.input.prettyJson().ifBlank { "{}" })
                    when {
                        part.state == ToolState.OUTPUT_DENIED -> Section(stringResource(R.string.ai_tool_output), stringResource(R.string.ai_denied_by_user), scheme.error)
                        part.errorText != null -> Section(stringResource(R.string.ai_tool_error), part.errorText!!, scheme.error)
                        part.output != null -> Section(stringResource(R.string.ai_tool_output), part.output!!.prettyJson())
                    }
                }
            }
        }
    }
}

/**
 * The tool's name line: its title when it has one, else the raw name in code type, with a chip
 * for what it is — a Skill for skill loads, the provider (e.g. an MCP server) for [ToolPart.source].
 */
@Composable
private fun ToolTitle(part: ToolPart) {
    val kind = part.kind
    val (title, badge) = when {
        kind is ToolKind.Skill -> (kind.skill ?: part.displayName) to stringResource(R.string.ai_skill)
        else -> part.displayName to part.source
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = if (part.title == null && kind is ToolKind.Function) MaterialTheme.typography.labelLarge.copy(fontFamily = LocalCodeFontFamily.current, fontSize = 13.sp) else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        badge?.let {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
    }
}

/** Live progress while a long tool reports it, else the arguments. */
private fun subtitle(part: ToolPart): String =
    if (part.preliminary && part.isStreaming && !part.output.isNullOrBlank()) part.output!!.lineSequence().last { it.isNotBlank() }
    else part.input.compactJson().ifBlank { "…" }


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatusPill(state: ToolState) {
    val scheme = MaterialTheme.colorScheme
    // Quiet when all is well; colour only where the user should look (approval, error, denial).
    val (label, content) = when (state) {
        ToolState.INPUT_STREAMING, ToolState.INPUT_AVAILABLE -> stringResource(R.string.ai_tool_running) to scheme.onSurfaceVariant
        ToolState.OUTPUT_AVAILABLE -> stringResource(R.string.ai_tool_done) to scheme.onSurfaceVariant
        ToolState.APPROVAL_REQUESTED -> stringResource(R.string.ai_tool_approval) to scheme.tertiary
        ToolState.OUTPUT_ERROR -> stringResource(R.string.ai_tool_error) to scheme.error
        ToolState.OUTPUT_DENIED -> stringResource(R.string.ai_tool_denied) to scheme.error
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (state) {
                ToolState.OUTPUT_AVAILABLE -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(AiSize.badgeIcon))
                ToolState.OUTPUT_ERROR -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(AiSize.badgeIcon))
                ToolState.OUTPUT_DENIED -> Icon(Icons.Outlined.Block, null, Modifier.size(AiSize.badgeIcon))
                ToolState.APPROVAL_REQUESTED -> Icon(Icons.Outlined.PanTool, null, Modifier.size(AiSize.badgeIcon))
                else -> LoadingIndicator(Modifier.size(16.dp), color = content)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = content)
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
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalCodeFontFamily.current),
                    softWrap = false,
                    modifier = Modifier.fadingHorizontalScroll().padding(12.dp),
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
