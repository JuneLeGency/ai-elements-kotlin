package dev.ai.elements.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.theme.AiSpacing

/** Progress of a plan / task / chain-of-thought step. */
enum class StepStatus { PENDING, ACTIVE, COMPLETE }

data class WorkflowStep(
    val label: String,
    val description: String? = null,
    val status: StepStatus = StepStatus.PENDING,
    /** Small chips under the step: search results, files touched… */
    val badges: List<String> = emptyList(),
)

/**
 * A multi-step reasoning trace (AI Elements `<ChainOfThought>`): a vertical
 * timeline of steps with status, descriptions and result badges.
 */
@Composable
fun ChainOfThought(steps: List<WorkflowStep>, modifier: Modifier = Modifier, title: String = "Chain of thought") {
    Collapsible(title, Icons.Outlined.Route, streaming = steps.any { it.status == StepStatus.ACTIVE }, modifier = modifier) {
        steps.forEachIndexed { index, step -> TimelineStep(step, isLast = index == steps.lastIndex) }
    }
}

/**
 * An agent's plan (AI Elements `<Plan>`): title, summary and the planned
 * steps; shimmers while it is still being written.
 */
@Composable
fun Plan(
    title: String,
    description: String,
    steps: List<WorkflowStep>,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    Collapsible(title, Icons.Outlined.Checklist, streaming = streaming, subtitle = description, modifier = modifier) {
        steps.forEach { step ->
            Row(horizontalArrangement = Arrangement.spacedBy(AiSpacing.s), verticalAlignment = Alignment.Top) {
                StatusIcon(step.status, Modifier.padding(top = 2.dp))
                Text(step.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A unit of agent work (AI Elements `<Task>`): a collapsible title with the
 * items done so far; file names render as monospace chips.
 */
@Composable
fun Task(title: String, items: List<WorkflowStep>, modifier: Modifier = Modifier) {
    Collapsible(title, Icons.Outlined.Checklist, streaming = items.any { it.status == StepStatus.ACTIVE }, modifier = modifier) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Badges(item.badges)
            }
        }
    }
}

@Composable
private fun TimelineStep(step: WorkflowStep, isLast: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StatusIcon(step.status)
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(Modifier.padding(bottom = if (isLast) 0.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(step.label, style = MaterialTheme.typography.titleSmall)
            step.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Badges(step.badges)
        }
    }
}

@Composable
private fun Badges(badges: List<String>) {
    if (badges.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AiSpacing.s), verticalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
        badges.forEach { badge ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = AiSpacing.xs),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusIcon(status: StepStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        StepStatus.COMPLETE -> Icon(Icons.Outlined.CheckCircle, "Done", modifier.size(18.dp), scheme.primary)
        StepStatus.ACTIVE -> LoadingIndicator(modifier.size(18.dp))
        StepStatus.PENDING -> Icon(Icons.Outlined.RadioButtonUnchecked, "Pending", modifier.size(18.dp), scheme.outline)
    }
}

/** Shared collapsible container for the workflow components. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Collapsible(
    title: String,
    icon: ImageVector,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(true) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
    Surface(
        onClick = { open = !open },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                ) { Icon(icon, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimaryContainer) }
                Column(Modifier.weight(1f)) {
                    ShimmerText(title, active = streaming, style = MaterialTheme.typography.titleSmall)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Outlined.ExpandMore, if (open) "Collapse" else "Expand", Modifier.rotate(rotation))
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                Column(Modifier.padding(top = AiSpacing.m).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AiSpacing.s)) {
                    content()
                }
            }
        }
    }
}
