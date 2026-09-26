package dev.ai.elements.demo.ui

import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.data.Appearance
import dev.ai.elements.demo.data.ThemeMode
import dev.ai.elements.ui.theme.AiSize
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Settings as an M3 list-detail: providers on the left, the selected one's
 * editor on the right when [twoPane] (resizable), otherwise one pane at a time
 * with predictive back.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, twoPane: Boolean) {
    val profiles by viewModel.providers.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.providers.selectedId.collectAsStateWithLifecycle()
    val navigator = rememberListDetailNavigator<String>(twoPane, listWidth = 400.dp)
    val scope = rememberCoroutineScope()
    val editing = profiles.firstOrNull { it.id == navigator.currentDestination?.contentKey }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                SettingsList(
                    viewModel = viewModel,
                    profiles = profiles,
                    selectedId = selectedId,
                    highlightedId = if (navigator.isDetailVisible) editing?.id else null,
                    onEdit = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                if (editing != null) {
                    ProviderEditor(
                        viewModel,
                        editing,
                        modifier = if (twoPane) Modifier.padding(end = 24.dp) else Modifier,
                        showBack = !navigator.isListVisible,
                        onClose = { scope.launch { navigator.navigateBack() } },
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select a provider to edit its endpoint, model and API key.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }
        },
        paneExpansionDragHandle = if (twoPane) { state -> PaneDragHandle(state) } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsList(
    viewModel: ChatViewModel,
    profiles: List<ProviderProfile>,
    selectedId: String,
    highlightedId: String?,
    onEdit: (String) -> Unit,
) {
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Text("Settings") }, colors = transparentAppBarColors()) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("Appearance") }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    val modes = listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Outlined.PhoneAndroid),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
                    )
                    modes.forEachIndexed { index, (mode, label, icon) ->
                        ToggleButton(
                            checked = appearance.themeMode == mode,
                            onCheckedChange = { viewModel.settings.update(appearance.copy(themeMode = mode)) },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f).testTag("theme-${mode.name.lowercase()}"),
                        ) {
                            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
                            Text(label, Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        checked = appearance.dynamicColor,
                        onCheckedChange = { viewModel.settings.update(Appearance(appearance.themeMode, it)) },
                        supportingContent = { Text("Use colors from your wallpaper") },
                        trailingContent = { Switch(checked = appearance.dynamicColor, onCheckedChange = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text("Dynamic color") }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Agent providers", Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { addMenu = true }, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(AiSize.compactIcon))
                            Text("Add", Modifier.padding(start = 4.dp))
                        }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            ProviderKind.entries.filter { it != ProviderKind.MOCK }.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(kind.label) },
                                    leadingIcon = { Icon(kind.icon, null) },
                                    onClick = {
                                        addMenu = false
                                        val profile = ProviderProfile(
                                            id = "custom-" + UUID.randomUUID().toString().take(8),
                                            name = "Custom ${kind.label}",
                                            kind = kind,
                                            baseUrl = when (kind) {
                                                ProviderKind.AGENT_SERVER, ProviderKind.AG_UI -> "http://10.0.2.2:8788"
                                                ProviderKind.ANTHROPIC -> "https://api.anthropic.com"
                                                ProviderKind.GEMINI -> "https://generativelanguage.googleapis.com"
                                                ProviderKind.OLLAMA -> "http://10.0.2.2:11434"
                                                else -> "http://10.0.2.2:11434/v1"
                                            },
                                            model = if (kind.serverSideAgent) "" else "qwen3:4b",
                                        )
                                        viewModel.providers.upsert(profile)
                                        onEdit(profile.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            items(profiles, key = { it.id }) { profile ->
                val hasKey = remember(profile.id, highlightedId) { viewModel.providers.apiKey(profile.id).isNotBlank() }
                ListItem(
                    selected = profile.id == highlightedId,
                    onClick = { onEdit(profile.id) },
                    supportingContent = {
                        Text(
                            buildString {
                                append(if (profile.kind.label == profile.name) "No network needed" else profile.kind.label)
                                if (profile.kind.needsKey) append(if (hasKey) " · key set" else " · no key")
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        Icon(profile.kind.icon, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        if (profile.id == selectedId) {
                            Icon(Icons.Outlined.CheckCircle, "Active", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("settings-provider-${profile.id}"),
                ) { Text(profile.name) }
            }
            item {
                Text(
                    "10.0.2.2 is the Android emulator's alias for your computer. On a physical device, use your computer's LAN IP instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderEditor(
    viewModel: ChatViewModel,
    profile: ProviderProfile,
    showBack: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val store = viewModel.providers
    var draft by remember(profile.id) { mutableStateOf(profile) }
    var apiKey by remember(profile.id) { mutableStateOf(store.apiKey(profile.id)) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember(profile.id) { mutableStateOf<Result<List<String>>?>(null) }
    var modelMenu by remember { mutableStateOf(false) }
    val dirty = draft != profile || apiKey != store.apiKey(profile.id)

    fun save() {
        store.upsert(draft)
        store.setApiKey(draft.id, apiKey)
    }

    Scaffold(containerColor = Color.Transparent, 
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = transparentAppBarColors(),
                title = { Text(draft.name.ifBlank { "Provider" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = dirty, modifier = Modifier.testTag("provider-save")) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(draft.kind.icon, null)
                    Text(draft.kind.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.kind != ProviderKind.MOCK) {
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it.trim()) },
                    label = { Text("Base URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag("provider-base-url"),
                )
                Box {
                    OutlinedTextField(
                        value = draft.model,
                        onValueChange = { draft = draft.copy(model = it.trim()) },
                        label = { Text("Model") },
                        placeholder = { if (draft.kind.serverSideAgent) Text("server default") },
                        singleLine = true,
                        trailingIcon = {
                            if (testResult?.getOrNull()?.isNotEmpty() == true) {
                                IconButton(onClick = { modelMenu = true }) { Icon(Icons.Outlined.Hub, "Pick a model") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("provider-model"),
                    )
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        testResult?.getOrNull().orEmpty().forEach { model ->
                            DropdownMenuItem(text = { Text(model) }, onClick = { draft = draft.copy(model = model); modelMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (draft.kind.needsKey) "API key" else "API key (optional)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show key")
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Stored encrypted with the Android Keystore.") },
                    modifier = Modifier.fillMaxWidth().testTag("provider-api-key"),
                )
            }
            if (draft.kind != ProviderKind.MOCK && !draft.kind.serverSideAgent) {
                ListItem(
                    checked = draft.useTools,
                    onCheckedChange = { draft = draft.copy(useTools = it) },
                    supportingContent = { Text("get_current_time, calculate, copy_to_clipboard (asks first)") },
                    trailingContent = { Switch(checked = draft.useTools, onCheckedChange = null) },
                ) { Text("On-device tools") }
                OutlinedTextField(
                    value = draft.systemPrompt,
                    onValueChange = { draft = draft.copy(systemPrompt = it) },
                    label = { Text("System prompt") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            testing = true
                            testResult = runCatching { draft.listModels(apiKey) }
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.testTag("provider-test"),
                ) { Text("Test connection") }
                Button(
                    onClick = {
                        save()
                        store.select(draft.id)
                    },
                    modifier = Modifier.testTag("provider-use"),
                ) { Text("Save & use") }
                if (testing) LoadingIndicator(Modifier.size(32.dp))
            }
            testResult?.let { result ->
                val ok = result.isSuccess
                Surface(
                    color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("provider-test-result"),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null)
                        Text(
                            if (ok) "Connected · ${result.getOrThrow().size} model(s) available"
                            else result.exceptionOrNull()?.message ?: "Failed",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.builtIn) {
                    TextButton(onClick = {
                        store.reset(profile.id)
                        draft = ProviderProfile.Presets.first { it.id == profile.id }
                    }) { Text("Reset to defaults") }
                } else {
                    TextButton(
                        onClick = { store.remove(profile.id); onClose() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete provider") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

private val ProviderKind.icon: ImageVector
    get() = when (this) {
        ProviderKind.MOCK -> Icons.Outlined.OfflineBolt
        ProviderKind.AGENT_SERVER -> Icons.Outlined.SmartToy
        ProviderKind.AG_UI -> Icons.Outlined.Hub
        ProviderKind.OPENAI, ProviderKind.OPENAI_RESPONSES -> Icons.Outlined.CloudQueue
        ProviderKind.ANTHROPIC -> Icons.Outlined.Psychology
        ProviderKind.GEMINI -> Icons.Outlined.AutoAwesome
        ProviderKind.OLLAMA -> Icons.Outlined.Computer
    }

private val ProviderKind.description: String
    get() = when (this) {
        ProviderKind.MOCK -> "Scripted offline agent: reasoning, a real on-device tool call, Markdown and Mermaid. No network."
        ProviderKind.AGENT_SERVER -> "A server-side agent (e.g. server/main.py with PydanticAI) at {base}/api/chat, streaming the Vercel AI SDK UI Message Stream (v5) or Data Stream (v4). Tools run on the server."
        ProviderKind.AG_UI -> "An AG-UI agent at {base}/api/agui (PydanticAI, LangGraph, CrewAI, Mastra…). Tools run on the server."
        ProviderKind.OPENAI -> "Any OpenAI-compatible /chat/completions endpoint. The agent loop and tools run on this device."
        ProviderKind.OPENAI_RESPONSES -> "OpenAI Responses API (/responses), stateless with encrypted reasoning. The agent loop and tools run on this device."
        ProviderKind.ANTHROPIC -> "Anthropic Messages API (/v1/messages). The agent loop and tools run on this device."
        ProviderKind.GEMINI -> "Google Gemini native API (streamGenerateContent) with thought summaries. The agent loop and tools run on this device."
        ProviderKind.OLLAMA -> "Ollama native API (/api/chat) with thinking. The agent loop and tools run on this device."
    }
