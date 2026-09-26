package dev.ai.elements.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle

/** Text with a sweeping highlight, used for in-progress labels ("Thinking…"). */
@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    active: Boolean = true,
) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    if (!active) {
        Text(text, modifier, color = base, style = style)
        return
    }
    val highlight = MaterialTheme.colorScheme.primary
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-x",
    )
    val width = 400f
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(progress * width - width / 2, 0f),
        end = Offset(progress * width + width / 2, 0f),
    )
    Text(text, modifier, style = style.copy(brush = brush))
}
