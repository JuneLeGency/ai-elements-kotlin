package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PanTool
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
 * JSON [input] before approving (AG-UI approve-with-edits, AI SDK `reason`).
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
) {
    var mode by rememberSaveable { mutableStateOf(DecisionMode.NONE) }
    var reason by rememberSaveable { mutableStateOf("") }
    var edited by rememberSaveable(input) { mutableStateOf(input.orEmpty().ifBlank { "{}" }) }
    val editedJson = remember(edited) { runCatching { Json.parseToJsonElement(edited) as? JsonObject }.getOrNull() }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("confirmation"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AiSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PanTool, null, Modifier.size(AiSize.compactIcon))
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
                else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { mode = DecisionMode.REASON }, modifier = Modifier.testTag("deny-with-reason")) { Text(stringResource(R.string.ai_deny_with_reason)) }
                    if (input != null) TextButton(onClick = { mode = DecisionMode.EDIT }, modifier = Modifier.testTag("edit-and-approve")) { Text(stringResource(R.string.ai_edit_and_approve)) }
                }
            }
            if (onApprove != null && onDeny != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDeny, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("deny")) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.ai_deny), Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    Button(onClick = onApprove, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("approve")) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.ai_approve), Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
            }
        }
    }
}

private enum class DecisionMode { NONE, REASON, EDIT }
