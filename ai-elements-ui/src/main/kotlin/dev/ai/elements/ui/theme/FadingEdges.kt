package dev.ai.elements.ui.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the left/right edge while [state] can still scroll that way, so wide
 * code or tables read as "there's more" instead of looking clipped. Apply
 * before `horizontalScroll(state)`.
 */
internal fun Modifier.horizontalFadingEdges(state: ScrollState, width: Dp = 28.dp): Modifier =
    horizontalFadingEdges({ state.canScrollBackward }, { state.canScrollForward }, width)

/** The same for any scrollable (e.g. a `LazyRow`'s `LazyListState`). */
internal fun Modifier.horizontalFadingEdges(canScrollBackward: () -> Boolean, canScrollForward: () -> Boolean, width: Dp = 28.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = width.toPx().coerceAtMost(size.width / 3)
            if (canScrollBackward()) {
                drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = 0f, endX = w), blendMode = BlendMode.DstOut)
            }
            if (canScrollForward()) {
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - w, endX = size.width), blendMode = BlendMode.DstOut)
            }
        }

/** `horizontalScroll` with [horizontalFadingEdges]: the default for overflowing code and rows. */
@androidx.compose.runtime.Composable
internal fun Modifier.fadingHorizontalScroll(): Modifier {
    val state = androidx.compose.foundation.rememberScrollState()
    return horizontalFadingEdges(state).then(Modifier.horizontalScroll(state))
}
