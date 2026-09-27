package dev.ai.elements.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.R

/**
 * Composer button with the number of active capabilities; opens a sheet to
 * switch skills, MCP servers and agents for the next messages. Remote agent
 * servers bring their own skills and sub-agents, so those rows are shown only
 * for in-app agents.
 */
@Composable
internal fun CapabilitiesButton(viewModel: ChatViewModel, provider: ProviderProfile, onManage: () -> Unit) {
    val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
    val servers by viewModel.mcpServers.servers.collectAsStateWithLifecycle()
    val skills by viewModel.skills.skills.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val inApp = !provider.kind.serverSideAgent
    val active = (if (settings.mcpEnabled) servers.count { it.enabled } else 0) +
        (if (inApp && settings.skillsEnabled) skills.count { it.skill.name !in settings.disabledSkills } else 0) +
        (if (inApp) settings.subAgents.count { it.enabled } + settings.remoteAgents.count { it.enabled } else 0)

    IconButton(onClick = { open = true }, modifier = Modifier.testTag("capabilities-button")) {
        BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) {
            Icon(DemoIcons.Extension, stringResource(R.string.capabilities))
        }
    }
    if (open) CapabilitiesSheet(viewModel, inApp, onManage = { open = false; onManage() }, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapabilitiesSheet(viewModel: ChatViewModel, inApp: Boolean, onManage: () -> Unit, onDismiss: () -> Unit) {
    val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
    val servers by viewModel.mcpServers.servers.collectAsStateWithLifecycle()
    val skills by viewModel.skills.skills.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.capabilities), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SheetHeader(stringResource(R.string.cap_mcp))
            if (servers.isEmpty()) Hint(stringResource(R.string.mcp_empty))
            servers.forEach { server ->
                Toggle(DemoIcons.Hub, server.name, server.url, settings.mcpEnabled && server.enabled, "sheet-mcp-${server.id}") { on ->
                    if (on && !settings.mcpEnabled) viewModel.agents.update { it.copy(mcpEnabled = true) }
                    viewModel.mcpServers.upsert(server.copy(enabled = on))
                }
            }
            if (inApp) {
                SheetHeader(stringResource(R.string.cap_skills))
                skills.forEach { entry ->
                    val name = entry.skill.name
                    Toggle(DemoIcons.AutoStories, name, entry.skill.description, settings.skillsEnabled && name !in settings.disabledSkills, "sheet-skill-$name") { on ->
                        viewModel.agents.update { s -> s.copy(skillsEnabled = s.skillsEnabled || on, disabledSkills = if (on) s.disabledSkills - name else s.disabledSkills + name) }
                    }
                }
                SheetHeader(stringResource(R.string.cap_agents))
                settings.subAgents.forEach { def ->
                    Toggle(DemoIcons.SmartToy, def.name, def.description, def.enabled, "sheet-agent-${def.name}") { on ->
                        viewModel.agents.update { s -> s.copy(subAgents = s.subAgents.map { if (it.id == def.id) it.copy(enabled = on) else it }) }
                    }
                }
                settings.remoteAgents.forEach { def ->
                    Toggle(DemoIcons.Groups, def.name.ifBlank { def.url }, "A2A · ${def.url}", def.enabled, "sheet-remote-${def.id}") { on ->
                        viewModel.agents.update { s -> s.copy(remoteAgents = s.remoteAgents.map { if (it.id == def.id) it.copy(enabled = on) else it }) }
                    }
                }
            }
            TextButton(onClick = onManage, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("capabilities-manage")) { Text(stringResource(R.string.manage)) }
        }
    }
}

@Composable
private fun SheetHeader(text: String) = SectionHeader(text)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))

@Composable
private fun Toggle(icon: ImageVector, title: String, description: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    ListItem(
        checked = checked,
        onCheckedChange = onChange,
        leadingContent = { Icon(icon, null) },
        supportingContent = { Text(description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = switchRowColors(),
        modifier = Modifier.padding(horizontal = 12.dp).testTag(tag),
    ) { Text(title) }
}
