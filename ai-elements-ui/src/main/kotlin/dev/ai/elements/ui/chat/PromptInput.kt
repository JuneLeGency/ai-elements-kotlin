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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * The composer (AI Elements `<PromptInput>`): a roomy rounded field with a
 * toolbar row. The submit button morphs into a spinning cookie-shaped stop
 * button while a reply is streaming.
 *
 * @param toolbar extra controls on the left of the toolbar (e.g. a model chip).
 */
@Composable
fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask anything",
    toolbar: @Composable RowScope.() -> Unit = {},
) {
    val canSend = value.isNotBlank() && !busy
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).heightIn(min = 24.dp).testTag("prompt-input"),
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
                toolbar()
                Box(Modifier.weight(1f))
                SubmitButton(busy = busy, enabled = canSend || busy, onClick = if (busy) onStop else onSubmit)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmitButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
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
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (busy) "Stop" else "Send", onClick = onClick)
            .testTag(if (busy) "stop-button" else "send-button"),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(container),
        )
        AnimatedContent(
            targetState = busy,
            transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "submit-icon",
        ) { isBusy ->
            Icon(
                if (isBusy) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,
                contentDescription = if (isBusy) "Stop" else "Send",
                tint = content,
            )
        }
    }
}
