package dev.ai.elements.ui.reasoning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.ReasoningStep
import dev.ai.elements.core.model.StepState
import dev.ai.elements.core.theme.AiTokens
import kotlinx.coroutines.delay

private const val AUTO_CLOSE_DELAY_MS = 1000L

/**
 * The "Reasoning" (extended thinking) block, mirroring the web `Reasoning`
 * component.
 *
 * Behaviour, matching the web version:
 * - **Auto-opens** while [isStreaming] is true.
 * - When streaming finishes, **waits [AUTO_CLOSE_DELAY_MS]** then collapses,
 *   showing a header like "Thought for 3.2s".
 * - The header is always tappable to toggle open/closed manually.
 *
 * @param steps the ordered reasoning steps.
 * @param isStreaming whether the model is still thinking.
 * @param durationMs final duration (shown after streaming ends); if null it is
 *   estimated from the number of steps.
 */
@Composable
fun Reasoning(
    steps: List<ReasoningStep>,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    durationMs: Long? = null,
    defaultOpen: Boolean? = null,
) {
    val tokens = AiTokens()
    val resolvedDefaultOpen = defaultOpen ?: isStreaming
    var isOpen by remember { mutableStateOf(resolvedDefaultOpen) }
    var hasAutoClosed by remember { mutableStateOf(false) }

    // Keep open while streaming; auto-close shortly after it ends.
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            isOpen = true
            hasAutoClosed = false
        } else {
            // Only auto-close if the user hasn't taken manual control and we
            // haven't already auto-closed (avoids fighting the user).
            delay(AUTO_CLOSE_DELAY_MS)
            if (!hasAutoClosed && isOpen) {
                isOpen = false
                hasAutoClosed = true
            }
        }
    }

    val headerLabel = remember(isStreaming, durationMs, steps.size) {
        when {
            isStreaming -> "Thinking…"
            durationMs != null -> "Thought for ${formatSeconds(durationMs)}"
            else -> "Chain of Thought"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Header (tappable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isOpen = !isOpen }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = headerLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isOpen) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (isOpen) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (steps.isEmpty()) {
                Text(
                    text = if (isStreaming) "Working on it…" else "No reasoning captured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    steps.forEach { step -> ReasoningStepRow(step) }
                }
            }
        }
    }
}

@Composable
private fun ReasoningStepRow(step: ReasoningStep) {
    val incomplete = step.state == StepState.INCOMPLETE
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Bullet dot: animated (indeterminate) while incomplete.
        val dotColor = if (incomplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodySmall,
                color = if (incomplete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            step.detail?.let { d ->
                if (d.isNotEmpty()) {
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

private fun formatSeconds(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds < 10) String.format("%.1fs", seconds) else "${(ms / 1000)}s"
}
