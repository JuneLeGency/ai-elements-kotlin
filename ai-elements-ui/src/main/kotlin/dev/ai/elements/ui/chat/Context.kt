package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.Usage
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing
import java.util.Locale

/**
 * Token / context-window usage (AI Elements `<Context>`): a compact chip that
 * opens a breakdown. With [contextWindow] it shows how full the window is.
 */
@Composable
fun ContextUsage(
    usage: Usage,
    modifier: Modifier = Modifier,
    contextWindow: Int? = null,
) {
    var open by remember { mutableStateOf(false) }
    val fraction = contextWindow?.let { (usage.totalTokens.toFloat() / it).coerceIn(0f, 1f) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("context-usage"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AiSpacing.s),
                modifier = Modifier.padding(horizontal = AiSpacing.m, vertical = 6.dp),
            ) {
                if (fraction != null) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(AiSize.badgeIcon), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.DataUsage, null, Modifier.size(AiSize.badgeIcon))
                }
                Text(
                    fraction?.let { "${(it * 100).toInt()}%" } ?: compact(usage.totalTokens),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_token_usage), style = MaterialTheme.typography.titleSmall)
                UsageRow(stringResource(R.string.ai_usage_input), usage.inputTokens)
                UsageRow(stringResource(R.string.ai_usage_output), usage.outputTokens)
                UsageRow(stringResource(R.string.ai_usage_total), usage.totalTokens)
                contextWindow?.let { UsageRow(stringResource(R.string.ai_usage_context_window), it) }
            }
        }
    }
}

@Composable
private fun UsageRow(label: String, value: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        Text(String.format(Locale.US, "%,d", value), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun compact(tokens: Int): String = stringResource(
    R.string.ai_tokens,
    when {
        tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000f)
        tokens >= 1_000 -> String.format(Locale.US, "%.1fk", tokens / 1_000f)
        else -> tokens.toString()
    },
)
