package dev.ai.elements.ui.chat

import dev.ai.elements.ui.icons.AiIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import dev.ai.elements.core.chat.ToolDecision
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing

/** Richer approval answers for the tool calls below (see [Conversation]'s `onToolDecision`). */
internal val LocalToolDecision = staticCompositionLocalOf<((toolCallId: String, decision: ToolDecision) -> Unit)?> { null }

/**
 * Human-in-the-loop approval (AI Elements `<Confirmation>`): what the agent
 * wants to do, with approve / deny actions. Null callbacks render read-only.
 * With [onDecide] the user can also deny with a reason or edit the call's
 * JSON [input] before approving (AG-UI approve-with-edits, AI SDK `reason`); pass a null [input]
 * or `withReason = false` where the backend cannot carry them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Confirmation(
    title: String,
    description: String,
    onApprove: (() -> Unit)?,
    onDeny: (() -> Unit)?,
    modifier: Modifier = Modifier,
    input: String? = null,
    onDecide: ((ToolDecision) -> Unit)? = null,
    withReason: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        ConfirmationContent(title, description, onApprove, onDeny, Modifier.padding(16.dp), input, onDecide, withReason)
    }
}

/** [Confirmation] without its container, for a host that already is one (the tool call's card). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ConfirmationContent(
    title: String,
    description: String,
    onApprove: (() -> Unit)?,
    onDeny: (() -> Unit)?,
    modifier: Modifier = Modifier,
    input: String? = null,
    onDecide: ((ToolDecision) -> Unit)? = null,
    withReason: Boolean = true,
) {
    var mode by rememberSaveable { mutableStateOf(DecisionMode.NONE) }
    var reason by rememberSaveable { mutableStateOf("") }
    var edited by rememberSaveable(input) { mutableStateOf(input.orEmpty().ifBlank { "{}" }) }
    val editedJson = remember(edited) { runCatching { Json.parseToJsonElement(edited) as? JsonObject }.getOrNull() }
    // The hand in the accent colour marks it as waiting on the user.
    Column(modifier.fillMaxWidth().testTag("confirmation"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AiSpacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(AiIcons.PanTool, null, Modifier.size(AiSize.compactIcon), MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            onDecide == null -> Unit
            mode == DecisionMode.REASON -> {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.ai_deny_reason)) },
                    modifier = Modifier.fillMaxWidth().testTag("deny-reason"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { mode = DecisionMode.NONE }) { Text(stringResource(R.string.ai_cancel)) }
                    Button(onClick = { onDecide(ToolDecision(false, reason = reason.trim().ifEmpty { null })) }, modifier = Modifier.testTag("deny-with-reason-send")) {
                        Text(stringResource(R.string.ai_deny))
                    }
                }
                return@Column
            }
            mode == DecisionMode.EDIT -> {
                OutlinedTextField(
                    value = edited, onValueChange = { edited = it },
                    label = { Text(stringResource(R.string.ai_edit_arguments)) },
                    isError = editedJson == null,
                    supportingText = if (editedJson == null) { { Text(stringResource(R.string.ai_invalid_json)) } } else null,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().testTag("edit-arguments"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { mode = DecisionMode.NONE }) { Text(stringResource(R.string.ai_cancel)) }
                    Button(
                        onClick = { editedJson?.let { onDecide(ToolDecision(true, editedInput = it)) } },
                        enabled = editedJson != null,
                        modifier = Modifier.testTag("edit-and-approve-send"),
                    ) { Text(stringResource(R.string.ai_approve)) }
                }
                return@Column
            }
            else -> Unit
        }
        // One row of actions when it fits (secondary answers first), wrapping on narrow screens.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onDecide != null) {
                if (withReason) TextButton(onClick = { mode = DecisionMode.REASON }, contentPadding = CompactPadding, modifier = Modifier.testTag("deny-with-reason")) { Text(stringResource(R.string.ai_deny_with_reason), maxLines = 1) }
                if (input != null) TextButton(onClick = { mode = DecisionMode.EDIT }, contentPadding = CompactPadding, modifier = Modifier.testTag("edit-and-approve")) { Text(stringResource(R.string.ai_edit_and_approve), maxLines = 1) }
                // Allow this tool for the rest of the conversation.
                TextButton(onClick = { onDecide(ToolDecision(true, remember = true)) }, contentPadding = CompactPadding, modifier = Modifier.testTag("approve-always")) { Text(stringResource(R.string.ai_approve_always), maxLines = 1) }
            }
            if (onApprove != null && onDeny != null) {
                // Text-only and tight, so all four answers fit one row on a phone.
                OutlinedButton(onClick = onDeny, shapes = ButtonDefaults.shapes(), contentPadding = CompactPadding, modifier = Modifier.testTag("deny")) {
                    Text(stringResource(R.string.ai_deny), maxLines = 1)
                }
                Button(onClick = onApprove, shapes = ButtonDefaults.shapes(), contentPadding = CompactPadding, modifier = Modifier.testTag("approve")) {
                    Text(stringResource(R.string.ai_approve), maxLines = 1)
                }
            }
        }
    }
}

private val CompactPadding = PaddingValues(horizontal = 12.dp)

private enum class DecisionMode { NONE, REASON, EDIT }
