package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R

@Immutable
data class QuestionOption(val id: String, val label: String, val description: String? = null)

/** What the user answered: chosen option ids and/or their own text. */
@Immutable
data class QuestionAnswer(val selected: List<String>, val text: String? = null)

/**
 * The agent asks the user something (AI Elements `<Question>`): one or
 * several of [options], and/or a free-text answer when [allowOther]. Once
 * [answered], the card shows the answer read-only.
 */
@Composable
fun Question(
    prompt: String,
    options: List<QuestionOption>,
    onSubmit: (QuestionAnswer) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    multiple: Boolean = false,
    allowOther: Boolean = true,
    answered: QuestionAnswer? = null,
) {
    var selected by rememberSaveable { mutableStateOf(answered?.selected.orEmpty()) }
    var other by rememberSaveable { mutableStateOf(answered?.text.orEmpty()) }
    val locked = answered != null
    val canSubmit = !locked && (selected.isNotEmpty() || other.isNotBlank())
    fun submit() { if (canSubmit) onSubmit(QuestionAnswer(selected, other.trim().ifEmpty { null })) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().testTag("question")) {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(prompt, style = MaterialTheme.typography.titleSmall)
                    description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Column(Modifier.padding(top = 4.dp).then(if (multiple) Modifier else Modifier.selectableGroup())) {
                options.forEach { option ->
                    val checked = option.id in selected
                    val toggle = { selected = if (multiple) (if (checked) selected - option.id else selected + option.id) else listOf(option.id) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (multiple) Modifier.toggleable(checked, enabled = !locked, role = Role.Checkbox) { toggle() }
                                else Modifier.selectable(checked, enabled = !locked, role = Role.RadioButton) { toggle() },
                            )
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 8.dp)
                            .testTag("question-option-${option.id}"),
                    ) {
                        if (multiple) Checkbox(checked, onCheckedChange = null, enabled = !locked, modifier = Modifier.padding(8.dp))
                        else RadioButton(checked, onClick = null, enabled = !locked, modifier = Modifier.padding(8.dp))
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            option.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            if (allowOther) {
                OutlinedTextField(
                    value = other,
                    onValueChange = { other = it; if (!multiple && it.isNotBlank()) selected = emptyList() },
                    enabled = !locked,
                    placeholder = { Text(stringResource(R.string.ai_other_answer)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("question-other"),
                )
            }
            if (!locked) {
                Button(onClick = ::submit, enabled = canSubmit, modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp, vertical = 4.dp).testTag("question-submit")) {
                    Text(stringResource(R.string.ai_submit))
                }
            }
        }
    }
}
