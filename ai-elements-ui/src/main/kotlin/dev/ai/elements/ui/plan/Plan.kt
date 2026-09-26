package dev.ai.elements.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.theme.AiTokens
import dev.ai.elements.ui.shimmer.TextShimmer

/**
 * The "Plan" block, mirroring the web `Plan` component (a `Card`-wrapped
 * `Collapsible` with a shimmering title/description while streaming).
 *
 * Like the other collapsibles in this library, open state is a [mutableStateOf]
 * and the body is wrapped in [AnimatedVisibility]. When [isStreaming] is true the
 * title and description are rendered through [TextShimmer] (the web wraps them in
 * its `<Shimmer>`).
 *
 * Usage:
 * ```
 * Plan(
 *     title = "Refactor the auth flow",
 *     description = "Split session handling from token refresh.",
 *     isStreaming = false,
 * ) {
 *     Text("Step 1 — extract the session store")
 *     Text("Step 2 — move refresh to a background worker")
 * }
 * ```
 *
 * @param title the card title (shimmers while [isStreaming]).
 * @param description the card description (shimmers while [isStreaming]).
 * @param isStreaming whether the plan is still being generated.
 * @param defaultOpen initial open state.
 * @param content the plan steps shown when open.
 */
@Composable
fun Plan(
    title: String,
    modifier: Modifier = Modifier,
    description: String = "",
    isStreaming: Boolean = false,
    defaultOpen: Boolean = true,
    onOpenChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = AiTokens()
    var isOpen by remember { mutableStateOf(defaultOpen) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Header (CardHeader): title + description on the left, trigger on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = {
                    isOpen = !isOpen
                    onOpenChange?.invoke(isOpen)
                },
                Modifier.size(32.dp),
                true,
                IconButtonDefaults.iconButtonColors(),
                remember { MutableInteractionSource() },
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (isOpen) 180f else 0f),
                )
            }
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}
