package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R

/** What a model can do; shown as small tags. */
enum class ModelCapability { REASONING, TOOLS, VISION, AUDIO, FAST }

@Immutable
data class ModelOption(
    val id: String,
    val name: String = id,
    val provider: String? = null,
    val description: String? = null,
    val capabilities: Set<ModelCapability> = emptySet(),
    val contextWindow: Int? = null,
)

/**
 * Pick a model (AI Elements `<ModelSelector>`): a chip showing the current
 * model opens a searchable sheet grouped by provider, with capability tags
 * and context window per model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    models: List<ModelOption>,
    selectedId: String?,
    onSelect: (ModelOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = models.firstOrNull { it.id == selectedId }
    AssistChip(
        onClick = { open = true },
        label = { Text(selected?.name ?: selectedId ?: stringResource(R.string.ai_select_model), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Memory, null, Modifier.size(AssistChipDefaults.IconSize)) },
        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(AssistChipDefaults.IconSize)) },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.testTag("model-selector"),
    )
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ModelList(models, selectedId) { onSelect(it); open = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelList(models: List<ModelOption>, selectedId: String?, onSelect: (ModelOption) -> Unit) {
    val query = rememberTextFieldState()
    val q = query.text.toString().trim()
    val groups = remember(models, q) {
        models.filter { q.isEmpty() || it.name.contains(q, true) || it.id.contains(q, true) || it.provider.orEmpty().contains(q, true) }
            .groupBy { it.provider.orEmpty() }
    }
    Column(Modifier.navigationBarsPadding()) {
        SearchBarDefaults.InputField(
            state = query,
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            placeholder = { Text(stringResource(R.string.ai_search_models)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (groups.isEmpty()) {
            Text(stringResource(R.string.ai_no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            groups.forEach { (provider, list) ->
                if (provider.isNotEmpty()) item(key = "h-$provider") {
                    Text(provider, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { it.id }) { m ->
                    ListItem(
                        onClick = { onSelect(m) },
                        selected = m.id == selectedId,
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                m.description?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                if (m.capabilities.isNotEmpty() || m.contextWindow != null) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        m.capabilities.forEach { Tag(stringResource(it.label)) }
                                        m.contextWindow?.let { Tag(stringResource(R.string.ai_context_window_short, compactCount(it))) }
                                    }
                                }
                            }
                        },
                        trailingContent = if (m.id == selectedId) ({ Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("model-option-${m.id}"),
                    ) { Text(m.name) }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private val ModelCapability.label: Int
    get() = when (this) {
        ModelCapability.REASONING -> R.string.ai_cap_reasoning
        ModelCapability.TOOLS -> R.string.ai_cap_tools
        ModelCapability.VISION -> R.string.ai_cap_vision
        ModelCapability.AUDIO -> R.string.ai_cap_audio
        ModelCapability.FAST -> R.string.ai_cap_fast
    }

private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}
