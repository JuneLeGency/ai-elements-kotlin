package dev.ai.elements.ui.chat

import dev.ai.elements.ui.icons.AiIcons
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.Suggestion

/** Prompt suggestion chips (AI Elements `<Suggestions>`), wrapping across lines. */
@Composable
fun Suggestions(
    suggestions: List<Suggestion>,
    onSelect: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            Surface(
                onClick = { onSelect(suggestion) },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("suggestion"),
            ) {
                Text(
                    suggestion.label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * Empty-conversation hero: a slowly turning expressive shape, a greeting and
 * suggestions. Pass `showHero = false` on short windows (phone landscape).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatEmptyState(
    title: String,
    /** A line under the title; `null` for none. */
    subtitle: String? = null,
    suggestions: List<Suggestion>,
    onSelect: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
    showHero: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val angle by rememberInfiniteTransition(label = "hero").animateFloat(
        0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "hero-rotation",
    )
    Column(
        modifier = modifier.widthIn(max = 640.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHero) Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(96.dp)
                    .graphicsLayer { rotationZ = angle }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
            )
            Icon(AiIcons.AutoAwesome, null, Modifier.size(40.dp), scheme.onPrimary)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Suggestions(suggestions, onSelect, Modifier.padding(top = 8.dp))
    }
}
