package dev.ai.elements.ui.suggestion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.core.theme.AiTokens

/**
 * The "Suggestions" horizontal chip rail, mirroring the web `Suggestions` /
 * `Suggestion` pair.
 *
 * A horizontally scrollable row of tappable chips. Selecting a chip invokes
 * [onSelect] with the chosen [Suggestion].
 *
 * @param suggestions the chips to show.
 * @param onSelect invoked with the tapped suggestion.
 */
@Composable
fun Suggestions(
    suggestions: List<Suggestion>,
    onSelect: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(suggestion = suggestion, onClick = { onSelect(suggestion) })
        }
    }
}

/**
 * A single suggestion chip.
 */
@Composable
fun SuggestionChip(
    suggestion: Suggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Text(
        text = label ?: suggestion.text,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
