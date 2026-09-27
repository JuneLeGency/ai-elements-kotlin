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
import dev.ai.elements.ui.theme.AiType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.ui.R
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
    var open by rememberSaveable(part.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // A quiet line, not a card: "Thinking…" while it streams, "Thought for 3s ›" after;
    // the thoughts open under a hairline rule (as the mainstream assistant apps show them).
    Column(modifier.fillMaxWidth().testTag("reasoning")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = part.text.isNotBlank()) { open = !open }
                .heightIn(min = 32.dp)
                .padding(end = 4.dp),
        ) {
            if (part.isStreaming) LoadingIndicator(Modifier.size(18.dp))
            else Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), muted)
            ShimmerText(
                text = if (part.isStreaming) stringResource(R.string.ai_thinking) else thoughtLabel(part.durationMs),
                active = part.isStreaming,
                style = MaterialTheme.typography.labelLarge.copy(color = muted),
            )
            if (part.text.isNotBlank()) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (open) stringResource(R.string.ai_collapse) else stringResource(R.string.ai_expand),
                    tint = muted,
                    modifier = Modifier.size(18.dp).rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = open && part.text.isNotBlank(),
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            val scroll = rememberScrollState()
            if (part.isStreaming) LaunchedEffect(part.text.length) { scroll.scrollTo(scroll.maxValue) }
            val rule = MaterialTheme.colorScheme.outlineVariant
            Text(
                text = part.text.trim(),
                style = AiType.small,
                color = muted,
                modifier = Modifier
                    .padding(top = 4.dp, start = 8.dp)
                    .drawBehind { drawRect(rule, size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)) }
                    .padding(start = 12.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(scroll),
            )
        }
    }
}

@Composable
private fun thoughtLabel(durationMs: Long?): String {
    val seconds = (((durationMs ?: 0) + 500) / 1000).toInt()
    return if (seconds < 1) stringResource(R.string.ai_thought_moment) else stringResource(R.string.ai_thought_seconds, seconds)
}
