package dev.ai.elements.ui.shimmer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.ai.elements.core.theme.AiTokens

/**
 * A text shimmer that sweeps a soft highlight across the text while it streams.
 *
 * KMP equivalent of the web `TextShimmer` (which animates `background-position`
 * on a `bg-clip-text` gradient). The "latest Google" way to do this in Compose is
 * a fully declarative [rememberInfiniteTransition] driving an animated
 * [Brush.linearGradient], composited over the text with [BlendMode.Multiply] so
 * only the glyph pixels light up — no custom `Animatable` bookkeeping.
 *
 * @param text the text to shimmer.
 * @param active when false, renders plain text (no animation, cheaper).
 * @param durationMs one full sweep duration.
 * @param baseColor the resting text colour.
 * @param highlightColor the moving highlight colour.
 */
@Composable
fun TextShimmer(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    durationMs: Int = 1400,
    baseColor: Color = AiTokens().mutedForeground,
    highlightColor: Color = AiTokens().foreground,
) {
    if (!active || text.isEmpty()) {
        Text(text = text, modifier = modifier, color = baseColor)
        return
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    // A soft highlight band that travels across the text, driven by `progress`.
    // We build the band with the `colors` vararg overload of Brush.linearGradient
    // (the `colorStops` Map overload is not part of the stable API).
    val band = 0.22f
    val center = progress
    val start = (center - band).coerceIn(0f, 1f)
    val end = (center + band).coerceIn(0f, 1f)
    val gradient = Brush.linearGradient(
        0f to baseColor,
        start to baseColor,
        center to highlightColor,
        end to baseColor,
        1f to baseColor,
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, 0f),
    )

    // Draw the text (from the caller's content) and overlay the moving band so it
    // multiplies only where glyphs exist.
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(brush = gradient, blendMode = BlendMode.Multiply)
        },
    )
}
