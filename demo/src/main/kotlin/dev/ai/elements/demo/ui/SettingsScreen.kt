package dev.ai.elements.demo.ui

import dev.ai.elements.demo.data.CodeFont
import dev.ai.elements.demo.data.speech
import dev.ai.elements.ui.voice.rememberSpeechOutputState
import dev.ai.elements.ui.voice.isOnDeviceRecognitionAvailable
import dev.ai.elements.ui.voice.recognitionServices
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.auth.OAuthProvider
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.R
import dev.ai.elements.demo.data.AppFont
import dev.ai.elements.demo.data.AppLanguage
import dev.ai.elements.demo.data.AppLocale
import dev.ai.elements.demo.data.DiagramSize
import dev.ai.elements.demo.data.TextSize
import dev.ai.elements.demo.data.ThemeMode
import dev.ai.elements.ui.theme.AiContrast
import dev.ai.elements.ui.theme.AiSize
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Settings as an M3 list-detail: providers on the left, the selected one's
 * editor on the right when [twoPane] (resizable), otherwise one pane at a time
 * with predictive back.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, twoPane: Boolean, onBack: (() -> Unit)? = null) {
    val navigator = rememberListDetailNavigator<String>(twoPane, listWidth = 360.dp)
    val scope = rememberCoroutineScope()
    // Two panes: a page is always open, the first one until the user picks another.
    val key = navigator.currentDestination?.contentKey ?: SettingsPage.PROVIDERS.takeIf { twoPane }
    val open = { page: String -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, page) }; Unit }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                SettingsHome(viewModel, highlighted = if (navigator.isDetailVisible) key else null, onOpen = open, onBack = onBack)
            }
        },
        detailPane = {
            AnimatedPane {
                val showBack = !navigator.isListVisible
                val close = { scope.launch { navigator.navigateBack() }; Unit }
                val paneModifier = if (twoPane) Modifier.padding(end = 24.dp) else Modifier
                when {
                    key == null -> Unit
                    CapabilityPage.isCapability(key) -> CapabilityPane(viewModel, key, showBack, close, paneModifier)
                    key == SettingsPage.PROVIDERS -> ProvidersPane(viewModel, showBack, close, paneModifier)
                    key == SettingsPage.DEVICE -> OnDevicePane(viewModel, showBack, close, paneModifier)
                    key == SettingsPage.APPEARANCE -> AppearancePane(viewModel, showBack, close, paneModifier)
                    key == SettingsPage.TEXT -> TextPane(viewModel, showBack, close, paneModifier)
                    key == SettingsPage.DIAGRAMS -> DiagramsPane(viewModel, showBack, close, paneModifier)
                    key == SettingsPage.VOICE -> VoicePane(viewModel, showBack, close, paneModifier)
                }
            }
        },
        paneExpansionDragHandle = if (twoPane) { state -> PaneDragHandle(state) } else null,
    )
}

/** The settings pages: the first level of settings, each opening its page (the second level). */
internal object SettingsPage {
    const val PROVIDERS = "page:providers"
    const val DEVICE = "page:device"
    const val APPEARANCE = "page:appearance"
    const val TEXT = "page:text"
    const val DIAGRAMS = "page:diagrams"
    const val VOICE = "page:voice"
}

/** Settings, first level: what can be set, grouped, each with where it stands now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHome(viewModel: ChatViewModel, highlighted: String?, onOpen: (String) -> Unit, onBack: (() -> Unit)?) {
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    val profiles by viewModel.providers.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.providers.selectedId.collectAsStateWithLifecycle()
    val settings by viewModel.agents.settings.collectAsStateWithLifecycle()
    val servers by viewModel.mcpServers.servers.collectAsStateWithLifecycle()
    val skills by viewModel.skills.skills.collectAsStateWithLifecycle()
    val active = profiles.firstOrNull { it.id == selectedId }
    val onDevice = listOf(settings.builtinTools, settings.workspaceFiles, settings.sandboxShell, settings.memory, settings.planning, settings.webBrowser, settings.deviceTools, settings.speech, settings.scheduledTasks).count { it }
    val off = stringResource(R.string.off)
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = { onBack?.let { BackArrow(it) } },
            colors = transparentAppBarColors(),
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("settings-list")) {
            item { SectionHeader(stringResource(R.string.settings_agent)) }
            item { HomeRow(SettingsPage.PROVIDERS, DemoIcons.SmartToy, R.string.agent_providers, active?.name ?: "", highlighted, onOpen) }
            item { HomeRow(CapabilityPage.MCP, DemoIcons.Hub, R.string.cap_mcp, if (settings.mcpEnabled) stringResource(R.string.cap_enabled_count, servers.count { it.enabled }) else off, highlighted, onOpen) }
            item { HomeRow(CapabilityPage.SKILLS, DemoIcons.AutoStories, R.string.cap_skills, if (settings.skillsEnabled) stringResource(R.string.cap_enabled_count, skills.count { it.skill.name !in settings.disabledSkills }) else off, highlighted, onOpen) }
            item { HomeRow(CapabilityPage.AGENTS, DemoIcons.Groups, R.string.cap_agents, stringResource(R.string.cap_enabled_count, settings.subAgents.count { it.enabled } + settings.remoteAgents.count { it.enabled }), highlighted, onOpen) }
            item { HomeRow(SettingsPage.DEVICE, DemoIcons.PhoneAndroid, R.string.cap_in_app, stringResource(R.string.cap_enabled_count, onDevice), highlighted, onOpen) }
            item { SectionHeader(stringResource(R.string.settings_app)) }
            item {
                val theme = stringResource(when (appearance.themeMode) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark })
                val color = if (appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stringResource(R.string.dynamic_color) else stringResource(appearance.palette.label)
                HomeRow(SettingsPage.APPEARANCE, DemoIcons.Palette, R.string.appearance, "$theme · $color", highlighted, onOpen)
            }
            item {
                val size = stringResource(when (appearance.textSize) { TextSize.SMALL -> R.string.text_small; TextSize.DEFAULT -> R.string.text_default; TextSize.LARGE -> R.string.text_large; TextSize.EXTRA_LARGE -> R.string.text_extra_large })
                HomeRow(SettingsPage.TEXT, DemoIcons.TextFields, R.string.settings_text_language, "${currentLanguageLabel()} · $size", highlighted, onOpen)
            }
            item {
                val context = LocalContext.current
                val recognizer = remember(appearance.speechRecognizer) {
                    appearance.speechRecognizer?.let { id -> recognitionServices(context).firstOrNull { it.component.flattenToString() == id }?.label }
                }
                val summary = when {
                    appearance.onDeviceRecognition -> stringResource(R.string.on_device_recognition)
                    recognizer != null -> recognizer
                    else -> stringResource(R.string.system_default)
                }
                HomeRow(SettingsPage.VOICE, DemoIcons.RecordVoiceOver, R.string.settings_voice, summary, highlighted, onOpen)
            }
            item {
                val size = stringResource(when (appearance.diagramSize) { DiagramSize.SMALL -> R.string.size_small; DiagramSize.MEDIUM -> R.string.size_medium; DiagramSize.LARGE -> R.string.size_large })
                HomeRow(SettingsPage.DIAGRAMS, DemoIcons.AccountTree, R.string.settings_diagrams, size, highlighted, onOpen)
            }
        }
    }
}

@Composable
private fun HomeRow(key: String, icon: ImageVector, title: Int, summary: String, highlighted: String?, onOpen: (String) -> Unit) {
    ListItem(
        selected = key == highlighted,
        onClick = { onOpen(key) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        supportingContent = { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("settings-$key"),
    ) { Text(stringResource(title)) }
}

/** Theme, color and contrast. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearancePane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    PaneScaffold(stringResource(R.string.appearance), showBack, onClose, modifier) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    val modes = listOf(
                        Triple(ThemeMode.SYSTEM, stringResource(R.string.theme_system), DemoIcons.PhoneAndroid),
                        Triple(ThemeMode.LIGHT, stringResource(R.string.theme_light), DemoIcons.LightMode),
                        Triple(ThemeMode.DARK, stringResource(R.string.theme_dark), DemoIcons.DarkMode),
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
                        colors = switchRowColors(),
        onCheckedChange = { viewModel.settings.update(appearance.copy(dynamicColor = it)) },
                        supportingContent = { Text(stringResource(R.string.dynamic_color_desc)) },
                        trailingContent = { Switch(checked = appearance.dynamicColor, onCheckedChange = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(stringResource(R.string.dynamic_color)) }
                }
            }
            item {
                val dynamicActive = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SettingLabel(
                    stringResource(R.string.color_palette),
                    if (dynamicActive) stringResource(R.string.palette_dynamic_hint) else stringResource(appearance.palette.label),
                )
                PalettePicker(
                    selected = appearance.palette,
                    enabled = !dynamicActive,
                    onSelect = { viewModel.settings.update(appearance.copy(palette = it)) },
                )
            }
            item {
                SettingLabel(stringResource(R.string.contrast), null)
                ConnectedChoices(
                    options = AiContrast.entries,
                    selected = appearance.contrast,
                    onSelect = { viewModel.settings.update(appearance.copy(contrast = it)) },
                    tag = { "contrast-${it.name.lowercase()}" },
                ) { level ->
                    Text(
                        stringResource(
                            when (level) {
                                AiContrast.STANDARD -> R.string.contrast_standard
                                AiContrast.MEDIUM -> R.string.contrast_medium
                                AiContrast.HIGH -> R.string.contrast_high
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
    }
}

/** Language, font and text size. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextPane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    PaneScaffold(stringResource(R.string.settings_text_language), showBack, onClose, modifier) {
        item { LanguageItem() }
        item {
            SettingLabel(stringResource(R.string.font), stringResource(R.string.font_desc))
            ConnectedChoices(
                options = AppFont.entries,
                selected = appearance.font,
                onSelect = { viewModel.settings.update(appearance.copy(font = it)) },
                tag = { "font-${it.name.lowercase()}" },
            ) { font ->
                // Each option is set in its own font: the picker is the preview.
                Text(
                    when (font) {
                        AppFont.SYSTEM -> stringResource(R.string.font_system)
                        AppFont.SERIF -> stringResource(R.string.font_serif)
                        AppFont.GEIST -> "Geist"
                        AppFont.INTER -> "Inter"
                        AppFont.WENKAI -> stringResource(R.string.font_wenkai)
                    },
                    fontFamily = font.family,
                    maxLines = 1,
                )
            }
        }
        item {
            SettingLabel(stringResource(R.string.text_size), null)
            ConnectedChoices(
                options = TextSize.entries,
                selected = appearance.textSize,
                onSelect = { viewModel.settings.update(appearance.copy(textSize = it)) },
                tag = { "text-size-${it.name.lowercase()}" },
            ) { size ->
                Text(
                    stringResource(
                        when (size) {
                            TextSize.SMALL -> R.string.text_small
                            TextSize.DEFAULT -> R.string.text_default
                            TextSize.LARGE -> R.string.text_large
                            TextSize.EXTRA_LARGE -> R.string.text_extra_large
                        },
                    ),
                    maxLines = 1,
                )
            }
        }
        item {
            SettingLabel(stringResource(R.string.code_font), stringResource(R.string.code_font_desc))
            CodeFont.entries.forEach { option ->
                val family = option.family(appearance.font)
                ListItem(
                    onClick = { viewModel.settings.update(appearance.copy(codeFont = option)) },
                    leadingContent = { RadioButton(selected = appearance.codeFont == option, onClick = null) },
                    supportingContent = { Text("val 你好 = listOf(0, O, 1, l, I) != null", fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("code-font-${option.name.lowercase()}"),
                ) {
                    Text(
                        when (option) {
                            CodeFont.FOLLOW -> stringResource(R.string.code_font_follow)
                            CodeFont.SYSTEM -> stringResource(R.string.code_font_system)
                            CodeFont.GEIST_MONO -> "Geist Mono"
                            CodeFont.JETBRAINS_MONO -> "JetBrains Mono"
                            CodeFont.FIRA_CODE -> "Fira Code"
                        },
                    )
                }
            }
        }
    }
}

/** The device's speech engines: recognition, reading aloud, voice and rate (the voice elements use them). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoicePane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val services = remember { recognitionServices(context) }
    val onDeviceAvailable = remember { isOnDeviceRecognitionAvailable(context) }
    val speech = rememberSpeechOutputState(appearance.speech)
    val language = LocalConfiguration.current.locales[0].language
    var voiceMenu by remember { mutableStateOf(false) }
    val sample = stringResource(R.string.voice_sample)
    fun update(transform: (dev.ai.elements.demo.data.Appearance) -> dev.ai.elements.demo.data.Appearance) = viewModel.settings.update(transform(appearance))

    PaneScaffold(stringResource(R.string.settings_voice), showBack, onClose, modifier) {
        item { SectionHeader(stringResource(R.string.voice_recognition)) }
        item {
            ChoiceRow(stringResource(R.string.system_default), null, appearance.speechRecognizer == null && !appearance.onDeviceRecognition, "recognizer-default") {
                update { it.copy(speechRecognizer = null, onDeviceRecognition = false) }
            }
        }
        items(services, key = { it.component.flattenToString() }) { service ->
            val id = service.component.flattenToString()
            ChoiceRow(service.label, service.component.packageName, appearance.speechRecognizer == id && !appearance.onDeviceRecognition, "recognizer-$id") {
                update { it.copy(speechRecognizer = id, onDeviceRecognition = false) }
            }
        }
        if (onDeviceAvailable) item {
            ChoiceRow(stringResource(R.string.on_device_recognition), stringResource(R.string.on_device_recognition_desc), appearance.onDeviceRecognition, "recognizer-on-device") {
                update { it.copy(onDeviceRecognition = true) }
            }
        }
        item { SectionHeader(stringResource(R.string.voice_output)) }
        item {
            ChoiceRow(stringResource(R.string.system_default), null, appearance.ttsEngine == null, "tts-default") {
                update { it.copy(ttsEngine = null, ttsVoice = null) }
            }
        }
        items(speech.engines, key = { it.name }) { engine ->
            ChoiceRow(engine.label, engine.name, appearance.ttsEngine == engine.name, "tts-${engine.name}") {
                update { it.copy(ttsEngine = engine.name, ttsVoice = null) }
            }
        }
        item {
            val voices = speech.voices.filter { it.locale.language == language }.ifEmpty { speech.voices }
            Box {
                ListItem(
                    onClick = { voiceMenu = true },
                    supportingContent = { Text(appearance.ttsVoice ?: stringResource(R.string.system_default), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(DemoIcons.RecordVoiceOver, null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("tts-voice"),
                ) { Text(stringResource(R.string.voice_voice)) }
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }, offset = DpOffset(24.dp, 0.dp)) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.system_default)) }, onClick = { voiceMenu = false; update { it.copy(ttsVoice = null) } })
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = if (voice.name == appearance.ttsVoice) ({ Icon(DemoIcons.Check, null) }) else null,
                            onClick = { voiceMenu = false; update { it.copy(ttsVoice = voice.name) } },
                        )
                    }
                }
            }
        }
        item {
            SettingLabel(stringResource(R.string.voice_rate), "%.1f×".format(appearance.speechRate))
            Slider(
                value = appearance.speechRate,
                onValueChange = { rate -> update { it.copy(speechRate = (rate * 10).toInt() / 10f) } },
                valueRange = 0.5f..2f,
                steps = 14,
                modifier = Modifier.padding(horizontal = 28.dp).testTag("speech-rate"),
            )
        }
        item {
            TextButton(
                onClick = { if (speech.speakingId == "preview") speech.stop() else speech.speak("preview", sample) },
                enabled = speech.isAvailable,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("voice-preview"),
            ) {
                Icon(if (speech.speakingId == "preview") DemoIcons.Stop else DemoIcons.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.voice_preview), Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, description: String?, selected: Boolean, tag: String, onSelect: () -> Unit) {
    ListItem(
        onClick = onSelect,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = description?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag(tag),
    ) { Text(title) }
}

/** How Mermaid diagrams render. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiagramsPane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    PaneScaffold(stringResource(R.string.settings_diagrams), showBack, onClose, modifier) {
            item {
                ListItem(
                    checked = appearance.nativeMermaid,
                    colors = switchRowColors(),
        onCheckedChange = { viewModel.settings.update(appearance.copy(nativeMermaid = it)) },
                    supportingContent = { Text(stringResource(R.string.native_mermaid_desc)) },
                    trailingContent = { Switch(checked = appearance.nativeMermaid, onCheckedChange = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("native-mermaid"),
                ) { Text(stringResource(R.string.native_mermaid)) }
            }
            item {
                Text(
                    stringResource(R.string.diagram_size),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    val sizes = listOf(DiagramSize.SMALL to stringResource(R.string.size_small), DiagramSize.MEDIUM to stringResource(R.string.size_medium), DiagramSize.LARGE to stringResource(R.string.size_large))
                    sizes.forEachIndexed { index, (size, label) ->
                        ToggleButton(
                            checked = appearance.diagramSize == size,
                            onCheckedChange = { viewModel.settings.update(appearance.copy(diagramSize = size)) },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                sizes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f).testTag("diagram-size-${size.name.lowercase()}"),
                        ) { Text(label) }
                    }
                }
            }
    }
}

/** The providers: which to use, adding one (with a key or by signing in), and editing one in a dialog. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProvidersPane(viewModel: ChatViewModel, showBack: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
    val profiles by viewModel.providers.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.providers.selectedId.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val highlightedId = editingId
    val onEdit = { id: String -> editingId = id }
    var addMenu by remember { mutableStateOf(false) }
    val customName = stringResource(R.string.custom_provider)
    val noNetwork = stringResource(R.string.no_network_needed)
    val keySet = stringResource(R.string.key_set)
    val noKey = stringResource(R.string.no_key)
    val signedInLabel = stringResource(R.string.signed_in)
    val notSignedInLabel = stringResource(R.string.not_signed_in)
    var confirmSubscription by remember { mutableStateOf(false) }

    if (confirmSubscription) {
        AlertDialog(
            onDismissRequest = { confirmSubscription = false },
            icon = { Icon(DemoIcons.WarningAmber, null) },
            title = { Text(stringResource(R.string.subscription_signin)) },
            text = { Text(stringResource(R.string.subscription_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSubscription = false
                    viewModel.settings.update(appearance.copy(subscriptionSignIn = true))
                }, modifier = Modifier.testTag("subscription-confirm")) { Text(stringResource(R.string.enable_anyway)) }
            },
            dismissButton = { TextButton(onClick = { confirmSubscription = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    profiles.firstOrNull { it.id == editingId }?.let { profile ->
        EditorDialog(onDismiss = { editingId = null }) {
            ProviderEditor(viewModel, profile, showBack = true, onClose = { editingId = null }, inDialog = true)
        }
    }
    PaneScaffold(stringResource(R.string.agent_providers), showBack, onClose, modifier) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.provider_list), Modifier.weight(1f))
                Box {
                    TextButton(onClick = { addMenu = true }, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(DemoIcons.Add, null, Modifier.size(AiSize.compactIcon))
                        Text(stringResource(R.string.add), Modifier.padding(start = 4.dp))
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
                                        name = String.format(customName, kind.label),
                                        kind = kind,
                                        baseUrl = when (kind) {
                                            ProviderKind.AGENT_SERVER, ProviderKind.AG_UI, ProviderKind.A2A -> "http://10.0.2.2:8788"
                                            ProviderKind.ACP -> "ws://10.0.2.2:8788/acp"
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
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.sign_in_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        OAuthProvider.entries.filter { !it.experimental || appearance.subscriptionSignIn }.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_in_with, provider.label)) },
                                leadingIcon = { Icon(DemoIcons.Login, null) },
                                trailingIcon = if (provider.experimental) ({
                                    Text(stringResource(R.string.experimental), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }) else null,
                                modifier = Modifier.testTag("add-oauth-${provider.name.lowercase()}"),
                                onClick = {
                                    addMenu = false
                                    val profile = ProviderProfile(
                                        id = "oauth-${provider.name.lowercase()}-" + UUID.randomUUID().toString().take(6),
                                        name = provider.label,
                                        kind = provider.kind,
                                        baseUrl = provider.baseUrl,
                                        model = provider.defaultModel,
                                        oauth = provider,
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
            val tokenVersion by viewModel.providers.tokenVersion.collectAsStateWithLifecycle()
            val hasKey = remember(profile.id, highlightedId, tokenVersion) {
                if (profile.usesTokens) viewModel.providers.tokenStore(profile.id).load() != null
                else viewModel.providers.apiKey(profile.id).isNotBlank()
            }
            ListItem(
                selected = profile.id == highlightedId,
                onClick = { onEdit(profile.id) },
                supportingContent = {
                    Text(
                        buildString {
                            append(if (profile.kind.label == profile.name) noNetwork else profile.kind.label)
                            when {
                                profile.oauth != null -> append(" · ").append(if (hasKey) signedInLabel else notSignedInLabel)
                                profile.kind.needsKey -> append(" · ").append(if (hasKey) keySet else noKey)
                            }
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
                        Icon(DemoIcons.CheckCircle, stringResource(R.string.active), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("settings-provider-${profile.id}"),
            ) { Text(profile.name) }
        }
        item {
            Text(
                stringResource(R.string.emulator_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            ListItem(
                checked = appearance.subscriptionSignIn,
                colors = switchRowColors(),
                onCheckedChange = {
                    if (it) confirmSubscription = true else viewModel.settings.update(appearance.copy(subscriptionSignIn = false))
                },
                supportingContent = { Text(stringResource(R.string.subscription_signin_desc)) },
                trailingContent = { Switch(checked = appearance.subscriptionSignIn, onCheckedChange = null) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("subscription-sign-in"),
            ) { Text(stringResource(R.string.subscription_signin)) }
        }
    }
}

/** A full-screen dialog on phones, a large centered one on wider windows. */
@Composable
private fun EditorDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 600.dp
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
                modifier = if (compact) Modifier.fillMaxSize() else Modifier.widthIn(max = 640.dp).fillMaxWidth(0.9f).fillMaxHeight(0.9f),
            ) {
                // A dialog is its own window: expose test tags there too (UI Automator, tests).
                Box((if (compact) Modifier.systemBarsPadding() else Modifier).semantics { testTagsAsResourceId = true }) { content() }
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
    inDialog: Boolean = false,
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
        modifier = modifier.noAutoFocusInTouchMode(),
        topBar = {
            TopAppBar(
                colors = transparentAppBarColors(),
                title = { Text(draft.name.ifBlank { stringResource(R.string.provider) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (inDialog) IconButton(onClick = onClose, modifier = Modifier.testTag("provider-close")) { Icon(DemoIcons.Close, stringResource(R.string.close)) }
                    else if (showBack) IconButton(onClick = onClose) { Icon(DemoIcons.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = dirty, modifier = Modifier.testTag("provider-save")) { Text(stringResource(R.string.save)) }
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
                    Text(stringResource(draft.kind.description), style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.kind != ProviderKind.MOCK) {
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it.trim()) },
                    label = { Text(stringResource(R.string.base_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag("provider-base-url"),
                )
                Box {
                    OutlinedTextField(
                        value = draft.model,
                        onValueChange = { draft = draft.copy(model = it.trim()) },
                        label = { Text(stringResource(R.string.model)) },
                        placeholder = { if (draft.kind.serverSideAgent) Text(stringResource(R.string.server_default)) },
                        singleLine = true,
                        trailingIcon = {
                            if (testResult?.getOrNull()?.isNotEmpty() == true) {
                                IconButton(onClick = { modelMenu = true }) { Icon(DemoIcons.Hub, stringResource(R.string.pick_model)) }
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
                if (draft.oauth != null) AccountCard(viewModel, draft) else OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (draft.kind.needsKey) stringResource(R.string.api_key) else stringResource(R.string.api_key_optional)) },
                    singleLine = true,
                    leadingIcon = { Icon(DemoIcons.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) DemoIcons.VisibilityOff else DemoIcons.Visibility, stringResource(R.string.show_key))
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(stringResource(R.string.key_stored)) },
                    modifier = Modifier.fillMaxWidth().testTag("provider-api-key"),
                )
            }
            if (draft.kind != ProviderKind.MOCK && !draft.kind.serverSideAgent) {
                ListItem(
                    checked = draft.useTools,
                    colors = switchRowColors(),
        onCheckedChange = { draft = draft.copy(useTools = it) },
                    supportingContent = { Text(stringResource(R.string.on_device_tools_desc)) },
                    trailingContent = { Switch(checked = draft.useTools, onCheckedChange = null) },
                ) { Text(stringResource(R.string.on_device_tools)) }
                OutlinedTextField(
                    value = draft.systemPrompt,
                    onValueChange = { draft = draft.copy(systemPrompt = it) },
                    label = { Text(stringResource(R.string.system_prompt)) },
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
                            testResult = runCatching { if (draft.oauth != null) viewModel.listModels(draft) else draft.listModels(apiKey) }
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.testTag("provider-test"),
                ) { Text(stringResource(R.string.test_connection)) }
                Button(
                    onClick = {
                        save()
                        store.select(draft.id)
                    },
                    modifier = Modifier.testTag("provider-use"),
                ) { Text(stringResource(R.string.save_use)) }
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
                        Icon(if (ok) DemoIcons.CheckCircle else DemoIcons.ErrorOutline, null)
                        Text(
                            if (ok) pluralStringResource(R.plurals.connected_models, result.getOrThrow().size, result.getOrThrow().size)
                            else result.exceptionOrNull()?.message ?: stringResource(R.string.failed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.builtIn) {
                    TextButton(onClick = {
                        store.reset(profile.id)
                        draft = store.presets.first { it.id == profile.id }
                    }) { Text(stringResource(R.string.reset_defaults)) }
                } else {
                    TextButton(
                        onClick = { store.remove(profile.id); onClose() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.delete_provider)) }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

private val ProviderKind.icon: ImageVector
    get() = when (this) {
        ProviderKind.MOCK -> DemoIcons.OfflineBolt
        ProviderKind.AGENT_SERVER -> DemoIcons.SmartToy
        ProviderKind.AG_UI -> DemoIcons.Hub
        ProviderKind.OPENAI, ProviderKind.OPENAI_RESPONSES -> DemoIcons.CloudQueue
        ProviderKind.ANTHROPIC -> DemoIcons.Psychology
        ProviderKind.GEMINI -> DemoIcons.AutoAwesome
        ProviderKind.OLLAMA -> DemoIcons.Computer
        ProviderKind.A2A -> DemoIcons.Hub
        ProviderKind.ACP -> DemoIcons.Terminal
    }

@get:StringRes
private val ProviderKind.description: Int
    get() = when (this) {
        ProviderKind.MOCK -> R.string.kind_mock
        ProviderKind.AGENT_SERVER -> R.string.kind_agent_server
        ProviderKind.AG_UI -> R.string.kind_ag_ui
        ProviderKind.OPENAI -> R.string.kind_openai
        ProviderKind.OPENAI_RESPONSES -> R.string.kind_openai_responses
        ProviderKind.ANTHROPIC -> R.string.kind_anthropic
        ProviderKind.GEMINI -> R.string.kind_gemini
        ProviderKind.OLLAMA -> R.string.kind_ollama
        ProviderKind.A2A -> R.string.kind_a2a
        ProviderKind.ACP -> R.string.kind_acp
    }

/** Per-app language: follow the system or pick one of the shipped translations. */
/** The app language's name, for the settings home. */
@Composable
private fun currentLanguageLabel(): String {
    val activity = LocalActivity.current ?: return stringResource(R.string.language_system)
    return AppLocale.current(activity).autonym ?: stringResource(R.string.language_system)
}

@Composable
private fun LanguageItem() {
    val activity = LocalActivity.current ?: return
    var current by remember { mutableStateOf(AppLocale.current(activity)) }
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            onClick = { menu = true },
            supportingContent = { Text(current.autonym ?: stringResource(R.string.language_system)) },
            leadingContent = { Icon(DemoIcons.Language, null) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("language"),
        ) { Text(stringResource(R.string.language)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(24.dp, 0.dp)) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.autonym ?: stringResource(R.string.language_system)) },
                    trailingIcon = if (language == current) ({ Icon(DemoIcons.Check, null) }) else null,
                    onClick = {
                        menu = false
                        if (language != current) {
                            current = language
                            AppLocale.set(activity, language)
                        }
                    },
                    modifier = Modifier.testTag("language-${language.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
internal fun SettingLabel(title: String, description: String?) {
    Column(Modifier.padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** An M3 Expressive connected button group for a single choice. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ConnectedChoices(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    tag: (T) -> String,
    label: @Composable (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).testTag(tag(option)),
            ) { label(option) }
        }
    }
}
