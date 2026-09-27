package dev.ai.elements.demo.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.mcp.McpApproval
import dev.ai.elements.core.mcp.McpServerConfig
import dev.ai.elements.core.mcp.McpServerStatus
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.R
import dev.ai.elements.demo.data.RemoteAgentDef
import dev.ai.elements.demo.data.SubAgentDef
import dev.ai.elements.ui.chat.Agent
import dev.ai.elements.ui.chat.AgentToolSpec
import dev.ai.elements.ui.markdown.MarkdownContent
import kotlinx.coroutines.launch
import java.util.UUID

/** Detail keys of the capability pages in the settings list–detail layout. */
internal object CapabilityPage {
    const val MCP = "cap:mcp"
    const val SKILLS = "cap:skills"
    const val AGENTS = "cap:agents"
    fun isCapability(key: String?) = key?.startsWith("cap:") == true
}

/** The "Agent capabilities" rows of the settings list. */
internal fun LazyListScope.capabilityItems(viewModel: ChatViewModel, highlighted: String?, onOpen: (String) -> Unit) {
    item { SectionHeader(stringResource(R.string.capabilities)) }
    item {
        val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
        val servers by viewModel.mcpServers.servers.collectAsStateWithLifecycle()
        val skills by viewModel.skills.skills.collectAsStateWithLifecycle()
        val rows = listOf(
            Triple(CapabilityPage.MCP, Icons.Outlined.Hub, R.string.cap_mcp) to servers.count { it.enabled }.takeIf { settings.mcpEnabled },
            Triple(CapabilityPage.SKILLS, Icons.Outlined.AutoStories, R.string.cap_skills) to skills.count { it.skill.name !in settings.disabledSkills }.takeIf { settings.skillsEnabled },
            Triple(CapabilityPage.AGENTS, Icons.Outlined.Groups, R.string.cap_agents) to (settings.subAgents.count { it.enabled } + settings.remoteAgents.count { it.enabled }),
        )
        Column {
            rows.forEach { (row, count) ->
                val (key, icon, title) = row
                ListItem(
                    selected = key == highlighted,
                    onClick = { onOpen(key) },
                    leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(if (count == null) stringResource(R.string.off) else stringResource(R.string.cap_enabled_count, count)) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("settings-$key"),
                ) { Text(stringResource(title)) }
            }
            InAppAgentItems(viewModel, settings)
            ListItem(
                checked = settings.builtinTools,
                onCheckedChange = { on -> viewModel.agents.update { it.copy(builtinTools = on) } },
                leadingContent = { Icon(Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = { Text(stringResource(R.string.cap_builtin_tools_desc)) },
                trailingContent = { Switch(checked = settings.builtinTools, onCheckedChange = null) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            ) { Text(stringResource(R.string.cap_builtin_tools)) }
        }
    }
}

/** Harness capabilities of the in-app agent, with the sandbox's install state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InAppAgentItems(viewModel: ChatViewModel, settings: dev.ai.elements.demo.data.CapabilitySettings) {
    val sandbox by viewModel.runtime.sandbox.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SectionHeader(stringResource(R.string.cap_in_app))
    CapabilitySwitch(Icons.Outlined.FolderOpen, R.string.cap_workspace, stringResource(R.string.cap_workspace_desc), settings.workspaceFiles, "cap-workspace") { on -> viewModel.agents.update { it.copy(workspaceFiles = on) } }
    CapabilitySwitch(
        Icons.Outlined.Terminal, R.string.cap_sandbox,
        when (val s = sandbox) {
            dev.ai.elements.harness.sandbox.AlpineSandbox.State.NotInstalled -> stringResource(R.string.sandbox_not_installed)
            is dev.ai.elements.harness.sandbox.AlpineSandbox.State.Installing -> stringResource(R.string.sandbox_installing, s.progress?.let { "${(it * 100).toInt()}%" } ?: s.step)
            dev.ai.elements.harness.sandbox.AlpineSandbox.State.Ready -> stringResource(R.string.sandbox_ready)
            is dev.ai.elements.harness.sandbox.AlpineSandbox.State.Failed -> stringResource(R.string.sandbox_failed, s.message)
        },
        settings.sandboxShell, "cap-sandbox",
        extra = {
            if (sandbox is dev.ai.elements.harness.sandbox.AlpineSandbox.State.Installing) LoadingIndicator(Modifier.size(24.dp))
            else if (sandbox == dev.ai.elements.harness.sandbox.AlpineSandbox.State.Ready) TextButton(onClick = { scope.launch { viewModel.runtime.sandbox.reset() } }) { Text(stringResource(R.string.sandbox_reset)) }
        },
    ) { on -> viewModel.agents.update { it.copy(sandboxShell = on) } }
    CapabilitySwitch(Icons.Outlined.Psychology, R.string.cap_memory, stringResource(R.string.cap_memory_desc), settings.memory, "cap-memory") { on -> viewModel.agents.update { it.copy(memory = on) } }
    CapabilitySwitch(Icons.Outlined.Language, R.string.cap_browser, stringResource(R.string.cap_browser_desc), settings.webBrowser, "cap-browser") { on -> viewModel.agents.update { it.copy(webBrowser = on) } }
    CapabilitySwitch(Icons.Outlined.PhoneAndroid, R.string.cap_device, stringResource(R.string.cap_device_desc), settings.deviceTools, "cap-device") { on -> viewModel.agents.update { it.copy(deviceTools = on) } }
    CapabilitySwitch(Icons.Outlined.Checklist, R.string.cap_planning, stringResource(R.string.cap_planning_desc), settings.planning, "cap-planning") { on -> viewModel.agents.update { it.copy(planning = on) } }
    CapabilitySwitch(Icons.Outlined.RecordVoiceOver, R.string.cap_speech, stringResource(R.string.cap_speech_desc), settings.speech, "cap-speech") { on -> viewModel.agents.update { it.copy(speech = on) } }
    CapabilitySwitch(Icons.Outlined.Schedule, R.string.cap_schedule, stringResource(R.string.cap_schedule_desc), settings.scheduledTasks, "cap-schedule") { on -> viewModel.agents.update { it.copy(scheduledTasks = on) } }
    if (settings.scheduledTasks) ScheduledTaskItems(viewModel.runtime.scheduler)
}

/** Tasks the agent scheduled, with their last result; each can be cancelled. */
@Composable
private fun ScheduledTaskItems(scheduler: dev.ai.elements.harness.scheduler.Scheduler) {
    val tasks by scheduler.tasks.collectAsStateWithLifecycle()
    tasks.forEach { task ->
        ListItem(
            onClick = {},
            supportingContent = {
                val next = task.everyMinutes?.let { stringResource(R.string.schedule_every, it) } ?: task.firstRunAt
                Text(listOfNotNull(next, task.lastError ?: task.lastResult).joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = { TextButton(onClick = { scheduler.cancel(task.id) }) { Text(stringResource(R.string.schedule_cancel)) } },
            modifier = Modifier.padding(start = 56.dp, end = 12.dp).testTag("scheduled-${task.id}"),
        ) { Text(task.title) }
    }
}

@Composable
private fun CapabilitySwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, description: String, checked: Boolean, tag: String, extra: @Composable () -> Unit = {}, onChange: (Boolean) -> Unit) {
    ListItem(
        checked = checked,
        onCheckedChange = onChange,
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        supportingContent = { Text(description) },
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { extra(); Switch(checked = checked, onCheckedChange = null) } },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag(tag),
    ) { Text(stringResource(title)) }
}

/** The detail page for [key] (one of [CapabilityPage]). */
@Composable
internal fun CapabilityPane(viewModel: ChatViewModel, key: String, showBack: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    when (key) {
        CapabilityPage.MCP -> McpServersPane(viewModel, showBack, onClose, modifier)
        CapabilityPage.SKILLS -> SkillsPane(viewModel, showBack, onClose, modifier)
        CapabilityPage.AGENTS -> AgentsPane(viewModel, showBack, onClose, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaneScaffold(
    title: String,
    showBack: Boolean,
    onClose: () -> Unit,
    modifier: Modifier,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.noAutoFocusInTouchMode(),
        topBar = {
            TopAppBar(
                colors = transparentAppBarColors(),
                title = { Text(title) },
                navigationIcon = { if (showBack) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { actions() },
            )
        },
    ) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), content = content) }
}

@Composable
private fun MasterSwitch(title: String, description: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ListItem(
            checked = checked,
            onCheckedChange = onChange,
            supportingContent = { Text(description) },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            modifier = Modifier.testTag(tag),
        ) { Text(title, style = MaterialTheme.typography.titleMedium) }
    }
}

// --- MCP servers ------------------------------------------------------------------------------

@Composable
private fun McpServersPane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
    val servers by viewModel.mcpServers.servers.collectAsStateWithLifecycle()
    val status by viewModel.mcpStatus.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(servers) { servers.filter { it.enabled && it.id !in status }.forEach(viewModel::checkMcp) }

    if (adding) AddMcpServerDialog(onDismiss = { adding = false }) { server, token ->
        viewModel.mcpServers.upsert(server)
        if (token.isNotBlank()) viewModel.mcpServers.setBearerToken(server.id, token)
        viewModel.checkMcp(server)
        adding = false
    }

    PaneScaffold(
        stringResource(R.string.cap_mcp), showBack, onClose, modifier,
        actions = { TextButton(onClick = { adding = true }, modifier = Modifier.testTag("mcp-add")) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add), Modifier.padding(start = 4.dp)) } },
    ) {
        item {
            MasterSwitch(stringResource(R.string.mcp_use), stringResource(R.string.mcp_empty).takeIf { servers.isEmpty() } ?: stringResource(R.string.cap_enabled_count, servers.count { it.enabled }), settings.mcpEnabled, "mcp-enabled") { on ->
                viewModel.agents.update { it.copy(mcpEnabled = on) }
            }
        }
        items(servers, key = { it.id }) { server -> McpServerCard(viewModel, server, status[server.id], status.containsKey(server.id)) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun McpServerCard(viewModel: ChatViewModel, server: McpServerConfig, status: McpServerStatus?, checked: Boolean) {
    var open by rememberSaveable(server.id) { mutableStateOf(false) }
    val activity = LocalActivity.current
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("mcp-server-${server.id}")) {
        Column(Modifier.animateContentSize()) {
            ListItem(
                onClick = { open = !open },
                leadingContent = {
                    when {
                        checked && status == null -> LoadingIndicator(Modifier.size(24.dp))
                        status is McpServerStatus.Connected -> Icon(Icons.Outlined.CheckCircle, null, tint = scheme.primary)
                        status is McpServerStatus.NeedsSignIn -> Icon(Icons.Outlined.Lock, null, tint = scheme.tertiary)
                        status is McpServerStatus.Failed -> Icon(Icons.Outlined.ErrorOutline, null, tint = scheme.error)
                        else -> Icon(Icons.Outlined.Hub, null, tint = scheme.onSurfaceVariant)
                    }
                },
                supportingContent = {
                    Text(
                        when {
                            checked && status == null -> stringResource(R.string.mcp_checking)
                            status is McpServerStatus.Connected -> stringResource(R.string.mcp_connected, status.tools.size, status.protocolVersion.orEmpty())
                            status is McpServerStatus.NeedsSignIn -> stringResource(R.string.mcp_needs_sign_in)
                            status is McpServerStatus.Failed -> status.message
                            else -> server.url
                        },
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = { Switch(checked = server.enabled, onCheckedChange = { viewModel.mcpServers.upsert(server.copy(enabled = it)) }) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { Text(server.name, style = MaterialTheme.typography.titleMedium) }

            if (open) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(server.url, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(stringResource(R.string.mcp_approval), style = MaterialTheme.typography.labelLarge)
                ConnectedChoices(
                    options = McpApproval.entries,
                    selected = server.approval,
                    onSelect = { viewModel.mcpServers.upsert(server.copy(approval = it)) },
                    tag = { "mcp-approval-${it.name.lowercase()}" },
                ) {
                    Text(
                        stringResource(when (it) { McpApproval.ALWAYS -> R.string.mcp_approval_always; McpApproval.UNLESS_READ_ONLY -> R.string.mcp_approval_writes; McpApproval.NEVER -> R.string.mcp_approval_never }),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (status is McpServerStatus.Connected && status.tools.isNotEmpty()) {
                    Text(stringResource(R.string.mcp_tools), style = MaterialTheme.typography.labelLarge)
                    Column {
                        status.tools.forEach { tool ->
                            val enabled = tool.name !in server.disabledTools
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(enabled, onCheckedChange = {
                                    viewModel.mcpServers.upsert(server.copy(disabledTools = if (it) server.disabledTools - tool.name else server.disabledTools + tool.name))
                                })
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tool.displayName, style = MaterialTheme.typography.bodyLarge)
                                        if (tool.annotations?.readOnlyHint == true) Text(stringResource(R.string.mcp_read_only), style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
                                    }
                                    tool.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.checkMcp(server) }) { Icon(Icons.Outlined.Refresh, null); Text(stringResource(R.string.mcp_test), Modifier.padding(start = 6.dp)) }
                    if (status is McpServerStatus.NeedsSignIn || server.auth == McpServerConfig.Auth.OAUTH) {
                        FilledTonalButton(onClick = { activity?.let { viewModel.signInMcp(it, server.copy(auth = McpServerConfig.Auth.OAUTH)); viewModel.mcpServers.upsert(server.copy(auth = McpServerConfig.Auth.OAUTH)) } }) {
                            Icon(Icons.AutoMirrored.Outlined.Login, null); Text(stringResource(R.string.sign_in), Modifier.padding(start = 6.dp))
                        }
                    }
                    TextButton(onClick = { viewModel.removeMcp(server.id) }) { Icon(Icons.Outlined.Delete, null); Text(stringResource(R.string.remove), Modifier.padding(start = 6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AddMcpServerDialog(onDismiss: () -> Unit, onAdd: (McpServerConfig, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("http://10.0.2.2:8788/mcp") }
    var auth by rememberSaveable { mutableStateOf(McpServerConfig.Auth.NONE) }
    var token by rememberSaveable { mutableStateOf("") }
    val valid = url.startsWith("http://") || url.startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Hub, null) },
        title = { Text(stringResource(R.string.mcp_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.mcp_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("mcp-name"))
                OutlinedTextField(url, { url = it.trim() }, label = { Text(stringResource(R.string.mcp_url)) }, singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth().testTag("mcp-url"))
                Text(stringResource(R.string.mcp_auth), style = MaterialTheme.typography.labelLarge)
                ConnectedChoices(McpServerConfig.Auth.entries, auth, { auth = it }, { "mcp-auth-${it.name.lowercase()}" }) {
                    Text(stringResource(when (it) { McpServerConfig.Auth.NONE -> R.string.mcp_auth_none; McpServerConfig.Auth.BEARER -> R.string.mcp_auth_bearer; McpServerConfig.Auth.OAUTH -> R.string.mcp_auth_oauth }), maxLines = 1)
                }
                if (auth == McpServerConfig.Auth.BEARER) {
                    OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.mcp_token)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
                val id = (name.ifBlank { host }.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "mcp" }) + "-" + UUID.randomUUID().toString().take(4)
                onAdd(McpServerConfig(id, name.ifBlank { host.ifBlank { "MCP" } }, url, auth = auth), token)
            }, modifier = Modifier.testTag("mcp-add-confirm")) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- Skills -------------------------------------------------------------------------------------

@Composable
private fun SkillsPane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
    val skills by viewModel.skills.skills.collectAsStateWithLifecycle()
    val errors by viewModel.skills.errors.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val installed = stringResource(R.string.skills_installed_toast)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { String.format(installed, viewModel.installSkill(uri)) }.getOrElse { it.message ?: it.javaClass.simpleName }
        }
    }
    PaneScaffold(
        stringResource(R.string.cap_skills), showBack, onClose, modifier,
        actions = { TextButton(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Icon(Icons.Outlined.FileUpload, null); Text(stringResource(R.string.skills_import), Modifier.padding(start = 4.dp)) } },
    ) {
        item {
            MasterSwitch(stringResource(R.string.skills_use), stringResource(R.string.skills_desc), settings.skillsEnabled, "skills-enabled") { on ->
                viewModel.agents.update { it.copy(skillsEnabled = on) }
            }
        }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) } }
        items(skills, key = { it.skill.name }) { entry ->
            var open by rememberSaveable(entry.skill.name) { mutableStateOf(false) }
            val enabled = entry.skill.name !in settings.disabledSkills
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("skill-${entry.skill.name}")) {
                Column(Modifier.animateContentSize()) {
                    ListItem(
                        onClick = { open = !open },
                        leadingContent = { Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary) },
                        overlineContent = { Text(stringResource(if (entry.bundled) R.string.skills_bundled else R.string.skills_installed)) },
                        supportingContent = { Text(entry.skill.description, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = { on ->
                                viewModel.agents.update { it.copy(disabledSkills = if (on) it.disabledSkills - entry.skill.name else it.disabledSkills + entry.skill.name) }
                            })
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) { Text(entry.skill.name, style = MaterialTheme.typography.titleMedium) }
                    if (open) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        MarkdownContent(entry.skill.instructions)
                        if (!entry.bundled) TextButton(onClick = { viewModel.uninstallSkill(entry.skill.name) }) { Icon(Icons.Outlined.Delete, null); Text(stringResource(R.string.remove), Modifier.padding(start = 6.dp)) }
                    }
                }
            }
        }
        if (errors.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.skills_errors)) }
            items(errors) { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)) }
        }
    }
}

// --- Agents -------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentsPane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SubAgentDef?>(null) }
    var addingRemote by rememberSaveable { mutableStateOf(false) }

    editing?.let { def -> SubAgentDialog(viewModel, def, onDismiss = { editing = null }) { saved ->
        viewModel.agents.update { s -> s.copy(subAgents = if (s.subAgents.any { it.id == saved.id }) s.subAgents.map { if (it.id == saved.id) saved else it } else s.subAgents + saved) }
        editing = null
    } }
    if (addingRemote) RemoteAgentDialog(onDismiss = { addingRemote = false }) { url ->
        viewModel.agents.update { it.copy(remoteAgents = it.remoteAgents + RemoteAgentDef("a2a-" + UUID.randomUUID().toString().take(6), url)) }
        addingRemote = false
    }

    PaneScaffold(stringResource(R.string.cap_agents), showBack, onClose, modifier) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionHeader(stringResource(R.string.agents_local), Modifier.weight(1f))
                TextButton(onClick = { editing = SubAgentDef(UUID.randomUUID().toString().take(8), "", "", "") }, modifier = Modifier.padding(end = 8.dp).testTag("agents-add-local")) {
                    Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add), Modifier.padding(start = 4.dp))
                }
            }
            Text(stringResource(R.string.agents_local_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(settings.subAgents, key = { it.id }) { def ->
            ListItem(
                onClick = { editing = def },
                leadingContent = { Icon(Icons.Outlined.SmartToy, null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = { Text(def.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Switch(checked = def.enabled, onCheckedChange = { on -> viewModel.agents.update { s -> s.copy(subAgents = s.subAgents.map { if (it.id == def.id) it.copy(enabled = on) else it }) } })
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("subagent-${def.name}"),
            ) { Text(def.name) }
        }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionHeader(stringResource(R.string.agents_remote), Modifier.weight(1f))
                TextButton(onClick = { addingRemote = true }, modifier = Modifier.padding(end = 8.dp).testTag("agents-add-remote")) {
                    Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add), Modifier.padding(start = 4.dp))
                }
            }
        }
        items(settings.remoteAgents, key = { it.id }) { def -> RemoteAgentCard(viewModel, def) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteAgentCard(viewModel: ChatViewModel, def: RemoteAgentDef) {
    var card by remember(def.url) { mutableStateOf<Result<org.a2aproject.sdk.spec.AgentCard>?>(null) }
    LaunchedEffect(def.url) { card = runCatching { viewModel.agentCard(def.url) } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("remote-agent-${def.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val loaded = card?.getOrNull()
        if (loaded != null) {
            Agent(
                name = loaded.name(),
                model = "A2A ${loaded.supportedInterfaces().firstOrNull()?.protocolVersion().orEmpty()}".trim(),
                description = loaded.description(),
                tools = loaded.skills().map { AgentToolSpec(it.name(), it.description(), it.examples()?.joinToString("\n")?.ifBlank { null }) },
                toolsTitle = stringResource(dev.ai.elements.ui.R.string.ai_skills_count, loaded.skills().size),
            )
        } else {
            ListItem(
                leadingContent = { if (card == null) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
                supportingContent = { card?.exceptionOrNull()?.let { Text(stringResource(R.string.agent_unreachable, it.message ?: it.javaClass.simpleName), maxLines = 2, overflow = TextOverflow.Ellipsis) } },
            ) { Text(def.name.ifBlank { def.url }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Switch(checked = def.enabled, onCheckedChange = { on -> viewModel.agents.update { s -> s.copy(remoteAgents = s.remoteAgents.map { if (it.id == def.id) it.copy(enabled = on) else it }) } })
            IconButton(onClick = { viewModel.agents.update { s -> s.copy(remoteAgents = s.remoteAgents.filterNot { it.id == def.id }) } }) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubAgentDialog(viewModel: ChatViewModel, initial: SubAgentDef, onDismiss: () -> Unit, onSave: (SubAgentDef) -> Unit) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val profiles by viewModel.providers.profiles.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val nameOk = Regex("^[A-Za-z0-9_-]{1,64}$").matches(draft.name)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SmartToy, null) },
        title = { Text(stringResource(R.string.agents_add_local)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draft.name, { draft = draft.copy(name = it.trim()) }, label = { Text(stringResource(R.string.mcp_name)) }, supportingText = { Text(stringResource(R.string.agent_name_hint)) }, isError = draft.name.isNotEmpty() && !nameOk, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("subagent-name"))
                OutlinedTextField(draft.description, { draft = draft.copy(description = it) }, label = { Text(stringResource(R.string.agent_description)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.instructions, { draft = draft.copy(instructions = it) }, label = { Text(stringResource(R.string.agent_instructions)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedTextField(
                        value = profiles.firstOrNull { it.id == draft.providerId }?.name ?: stringResource(R.string.agent_same_model),
                        onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.agent_model)) },
                        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().clickable(role = Role.DropdownList) { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.agent_same_model)) }, onClick = { draft = draft.copy(providerId = null); menu = false })
                        profiles.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { draft = draft.copy(providerId = p.id); menu = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = nameOk && draft.description.isNotBlank(), onClick = { onSave(draft) }, modifier = Modifier.testTag("subagent-save")) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
            Row {
                if (settings.subAgents.any { it.id == initial.id }) {
                    TextButton(onClick = { viewModel.agents.update { s -> s.copy(subAgents = s.subAgents.filterNot { it.id == initial.id }) }; onDismiss() }) { Text(stringResource(R.string.remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun RemoteAgentDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("http://10.0.2.2:8788") }
    val valid = url.startsWith("http://") || url.startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Hub, null) },
        title = { Text(stringResource(R.string.agents_add_remote)) },
        text = { OutlinedTextField(url, { url = it.trim() }, label = { Text(stringResource(R.string.agent_url)) }, singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth().testTag("remote-agent-url")) },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(url) }) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
