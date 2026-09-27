package dev.ai.elements.ui.chat

import dev.ai.elements.ui.icons.AiIcons
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiType
import dev.ai.elements.ui.theme.fadingHorizontalScroll

/** A tool an agent can call; [schema] is its JSON input schema, if shown. */
@Immutable
data class AgentToolSpec(val name: String, val description: String? = null, val schema: String? = null)

/**
 * An agent's definition (AI Elements `<Agent>`): name and model, its
 * instructions, the tools it may call (each expands to its schema) and the
 * shape of its output. Also renders a remote agent's card (e.g. A2A): pass its
 * [description] and its skills as [tools] with [toolsTitle].
 *
 * @param description one-line summary under the name (no section label).
 * @param toolsTitle overrides the "Tools (n)" heading, e.g. "Skills (n)".
 */
@Composable
fun Agent(
    name: String,
    modifier: Modifier = Modifier,
    model: String? = null,
    instructions: String? = null,
    tools: List<AgentToolSpec> = emptyList(),
    outputSchema: String? = null,
    description: String? = null,
    toolsTitle: String? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().testTag("agent")) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(AiIcons.SmartToy, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                model?.let {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(it, style = AiType.code, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            instructions?.let {
                Label(stringResource(R.string.ai_instructions))
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (tools.isNotEmpty()) {
                Label(toolsTitle ?: stringResource(R.string.ai_tools_count, tools.size))
                Column {
                    tools.forEach { ToolSpecRow(it) }
                }
            }
            outputSchema?.let {
                Label(stringResource(R.string.ai_tool_output))
                Schema(it)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ToolSpecRow(tool: AgentToolSpec) {
    var open by rememberSaveable(tool.name) { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tool.schema != null) Modifier.clickable(role = Role.Button) { open = !open } else Modifier)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Icon(AiIcons.Function, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(tool.name, style = AiType.code)
                tool.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (tool.schema != null) {
                Icon(AiIcons.ExpandMore, stringResource(if (open) R.string.ai_collapse else R.string.ai_expand), Modifier.rotate(if (open) 180f else 0f))
            }
        }
        if (open) tool.schema?.let { Schema(it) }
    }
}

@Composable
private fun Schema(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SelectionContainer {
            Text(text, style = AiType.code, softWrap = false, modifier = Modifier.fadingHorizontalScroll().padding(12.dp))
        }
    }
}
