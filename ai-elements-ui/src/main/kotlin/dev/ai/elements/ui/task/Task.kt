package dev.ai.elements.ui.task

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The "Task" block, mirroring the web `Task` component (a `Collapsible`-backed
 * research / multi-step task with a tappable trigger).
 *
 * Compose has no `Collapsible` primitive, so — exactly like [dev.ai.elements.ui.reasoning.Reasoning]
 * and [dev.ai.elements.ui.chainofthought.ChainOfThought] — the open/closed state is a
 * [mutableStateOf] and the body is wrapped in [AnimatedVisibility].
 *
 * Usage (the web "composable" form):
 * ```
 * Task(title = "Researching the market", defaultOpen = true) {
 *     TaskItem { Text("Reading the latest earnings reports") }
 *     TaskItem { Text("Cross-checking with analyst notes") }
 *     TaskItemFile("earnings-2025.pdf")
 * }
 * ```
 *
 * @param title the label shown in the trigger (with the search icon).
 * @param defaultOpen initial open state.
 * @param content the task items / notes shown when open.
 */
@Composable
fun Task(
    title: String,
    modifier: Modifier = Modifier,
    defaultOpen: Boolean = true,
    onOpenChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var isOpen by remember { mutableStateOf(defaultOpen) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Trigger (tappable header row).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isOpen = !isOpen
                    onOpenChange?.invoke(isOpen)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
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
            // Web TaskContent: `mt-4 space-y-2 border-l-2 pl-4`.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                    )
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * A single line of task text (muted, small) — the web `TaskItem`.
 */
@Composable
fun TaskItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A small file "chip" shown inside a task — the web `TaskItemFile`
 * (`rounded-md border bg-secondary px-1.5 py-0.5 text-xs`).
 *
 * @param label the file name / label shown inside the chip.
 */
@Composable
fun TaskItemFile(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
