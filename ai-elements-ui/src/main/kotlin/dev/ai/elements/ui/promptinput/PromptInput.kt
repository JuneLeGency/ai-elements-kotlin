package dev.ai.elements.ui.promptinput

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.core.theme.AiTokens

/**
 * The prompt input bar, mirroring the web `PromptInput` group.
 *
 * Features:
 * - Auto-growing multi-line input (max [maxLines] lines).
 * - **Send** button (or a **Stop** button while streaming).
 * - Optional **attach** (paperclip) and **mic** (voice) buttons.
 * - Optional **model selector** dropdown.
 * - Enter submits; Shift+Enter inserts a newline.
 *
 * @param value the current input text.
 * @param onValueChange called as the user types.
 * @param onSend called when the user submits (Enter / send button).
 * @param onStop called when the user taps stop while streaming.
 * @param status the chat [ChatStatus]; drives send/stop button state.
 * @param model the currently selected model name (if any).
 * @param models available models for the selector (if any).
 * @param onModelSelect called when a model is picked.
 * @param onAttach called when the attach button is tapped.
 * @param onMic called when the mic button is tapped.
 */
@Composable
fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    status: ChatStatus = ChatStatus.IDLE,
    onStop: (() -> Unit)? = null,
    model: String? = null,
    models: List<String> = emptyList(),
    onModelSelect: ((String) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    onMic: (() -> Unit)? = null,
    maxLines: Int = 6,
) {
    val tokens = AiTokens()
    val isStreaming = status == ChatStatus.STREAMING || status == ChatStatus.SUBMITTED
    val canSend = value.trim().isNotEmpty() && !isStreaming

    val focusRequester = remember { FocusRequester() }
    var isModelMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Attach button (optional)
            onAttach?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Attach", tint = MaterialTheme.colorScheme.outline)
                }
            }

            // Auto-growing input
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .heightIn(max = 160.dp)
                    .padding(vertical = 8.dp)
                    .testTag("prompt_input"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (value.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (canSend) onSend()
                }),
                singleLine = false,
                maxLines = maxLines,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "Message…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        inner()
                    }
                },
            )

            // Mic button (optional)
            onMic?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice", tint = MaterialTheme.colorScheme.outline)
                }
            }

            // Send / Stop button
            if (isStreaming) {
                IconButton(
                    onClick = { onStop?.invoke() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                androidx.compose.material3.FilledIconButton(
                    onClick = { if (canSend) { onSend(); onValueChange("") } },
                    enabled = canSend,
                    modifier = Modifier.size(36.dp).testTag("prompt_send"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledContentColor = MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }

        // Footer row: model selector + hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Model selector
            if (models.isNotEmpty() && onModelSelect != null) {
                Box {
                    TextButton(onClick = { isModelMenuOpen = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = model ?: "Select model",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    DropdownMenu(
                        expanded = isModelMenuOpen,
                        onDismissRequest = { isModelMenuOpen = false },
                    ) {
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    onModelSelect(m)
                                    isModelMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
