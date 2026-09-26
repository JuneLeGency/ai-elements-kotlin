package dev.ai.elements.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing

/**
 * Collapsible "thinking" block. Opens automatically while the model reasons
 * and collapses to "Thought for Ns" when it's done — like AI Elements'
 * `<Reasoning>`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Reasoning(part: ReasoningPart, modifier: Modifier = Modifier) {
    var open by rememberSaveable(part.id) { mutableStateOf(part.isStreaming) }
    LaunchedEffect(part.isStreaming) { open = part.isStreaming }
    val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")

    Surface(
        onClick = { open = !open },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("reasoning"),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
                if (part.isStreaming) {
                    LoadingIndicator(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Outlined.Psychology, null, Modifier.size(AiSize.compactIcon), MaterialTheme.colorScheme.primary)
                }
                ShimmerText(
                    text = if (part.isStreaming) "Thinking…" else thoughtLabel(part.durationMs),
                    active = part.isStreaming,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (open) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            }
            AnimatedVisibility(
                visible = open && part.text.isNotBlank(),
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                val scroll = rememberScrollState()
                if (part.isStreaming) LaunchedEffect(part.text.length) { scroll.scrollTo(scroll.maxValue) }
                Text(
                    text = part.text.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = AiSpacing.m)
                        .heightIn(max = 280.dp)
                        .verticalScroll(scroll),
                )
            }
        }
    }
}

private fun thoughtLabel(durationMs: Long?): String {
    val seconds = ((durationMs ?: 0) + 500) / 1000
    return if (seconds < 1) "Thought for a moment" else "Thought for ${seconds}s"
}
