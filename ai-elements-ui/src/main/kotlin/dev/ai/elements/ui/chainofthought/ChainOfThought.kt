package dev.ai.elements.ui.chainofthought

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
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
import dev.ai.elements.core.theme.AiTokens

/**
 * The "Chain of Thought" container, mirroring the web `ChainOfThought` group
 * (`ChainOfThought` / `ChainOfThoughtHeader` / `ChainOfThoughtContent`).
 *
 * A labelled, collapsible block that wraps a model's visible reasoning steps.
 * This is the higher-level sibling of [dev.ai.elements.ui.reasoning.Reasoning]:
 * use [ChainOfThought] when you want to compose the header and content yourself,
 * use [Reasoning] for the fully-managed auto-open/auto-close behaviour.
 *
 * @param label the header label (defaults to "Chain of Thought").
 * @param defaultOpen initial open state.
 * @param content the reasoning steps to show when open.
 */
@Composable
fun ChainOfThought(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Chain of Thought",
    defaultOpen: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null,
) {
    val tokens = AiTokens()
    var isOpen by remember { mutableStateOf(defaultOpen) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isOpen = !isOpen
                    onOpenChange?.invoke(isOpen)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
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

        if (isOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}
