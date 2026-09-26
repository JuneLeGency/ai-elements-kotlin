package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.horizontalFadingEdges

/** "Used N sources" with a row of link chips (AI Elements `<Sources>`). */
@Composable
fun Sources(sources: List<SourcePart>, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            pluralStringResource(R.plurals.ai_used_sources, sources.size, sources.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val row = rememberLazyListState()
        LazyRow(
            state = row,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalFadingEdges({ row.canScrollBackward }, { row.canScrollForward }),
        ) {
            items(sources, key = { it.id }) { source ->
                AssistChip(
                    onClick = { runCatching { uriHandler.openUri(source.url) } },
                    label = {
                        Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingIcon = { Icon(Icons.Outlined.Link, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
        }
    }
}
