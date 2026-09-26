package dev.ai.elements.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Stop
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.compactIconButton

/**
 * The composer (AI Elements `<PromptInput>`): a roomy rounded field with a
 * toolbar row. The submit button morphs into a spinning cookie-shaped stop
 * button while a reply is streaming.
 *
 * @param attachments pending files shown above the field (removable).
 * @param onAddAttachment shows an add button when non-null.
 * @param toolbar extra controls on the left of the toolbar (e.g. a model chip).
 * @param allowQueue while [busy], a non-empty prompt can still be submitted
 *   (to be queued by the controller); an empty one shows the stop button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.ai_prompt_placeholder),
    attachments: List<FilePart> = emptyList(),
    onAddAttachment: (() -> Unit)? = null,
    onRemoveAttachment: (FilePart) -> Unit = {},
    allowQueue: Boolean = false,
    toolbar: @Composable RowScope.() -> Unit = {},
) {
    val hasInput = value.isNotBlank() || attachments.isNotEmpty()
    val mode = when {
        busy && allowQueue && hasInput -> SubmitMode.QUEUE
        busy -> SubmitMode.STOP
        else -> SubmitMode.SEND
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            AttachmentStrip(attachments, onRemoveAttachment, Modifier.padding(end = 12.dp, bottom = 12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .heightIn(min = 24.dp)
                    // Hardware keyboards (tablets, ChromeOS, DeX): Enter sends, Shift+Enter is a newline.
                    // Soft keyboards commit "\n" as text and keep inserting newlines.
                    .onPreviewKeyEvent { event ->
                        val physical = event.nativeKeyEvent.device?.isVirtual == false
                        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (!physical || !enter || event.isShiftPressed) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown && mode != SubmitMode.STOP && hasInput) onSubmit()
                        true
                    }
                    .testTag("prompt-input"),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                // Toolbar takes the remaining width and scrolls, so large font scales
                // can never push the submit button off screen.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    if (onAddAttachment != null) {
                        IconButton(
                            onClick = onAddAttachment,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.compactIconButton().testTag("add-attachment"),
                        ) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.ai_attach_image)) }
                    }
                    toolbar()
                }
                SubmitButton(
                    mode = mode,
                    enabled = mode == SubmitMode.STOP || hasInput,
                    onClick = if (mode == SubmitMode.STOP) onStop else onSubmit,
                )
            }
        }
    }
}

private enum class SubmitMode { SEND, QUEUE, STOP }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmitButton(mode: SubmitMode, enabled: Boolean, onClick: () -> Unit) {
    val busy = mode == SubmitMode.STOP
    val scheme = MaterialTheme.colorScheme
    val rotation = if (busy) {
        val angle by rememberInfiniteTransition(label = "stop").animateFloat(
            0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "stop-rotation",
        )
        angle
    } else 0f
    val container = when {
        busy -> scheme.tertiary
        enabled -> scheme.primary
        else -> scheme.onSurface.copy(alpha = 0.12f)
    }
    val content = when {
        busy -> scheme.onTertiary
        enabled -> scheme.onPrimary
        else -> scheme.onSurface.copy(alpha = 0.38f)
    }
    val shape = if (busy) MaterialShapes.Cookie9Sided.toShape() else CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(
                when (mode) {
                    SubmitMode.SEND -> "send-button"
                    SubmitMode.QUEUE -> "queue-button"
                    SubmitMode.STOP -> "stop-button"
                },
            ),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(container),
        )
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "submit-icon",
        ) { target ->
            Icon(
                when (target) {
                    SubmitMode.SEND -> Icons.Outlined.ArrowUpward
                    SubmitMode.QUEUE -> Icons.Outlined.PlaylistAdd
                    SubmitMode.STOP -> Icons.Outlined.Stop
                },
                contentDescription = when (target) {
                    SubmitMode.SEND -> stringResource(R.string.ai_send)
                    SubmitMode.QUEUE -> stringResource(R.string.ai_add_to_queue)
                    SubmitMode.STOP -> stringResource(R.string.ai_stop)
                },
                tint = content,
            )
        }
    }
}
