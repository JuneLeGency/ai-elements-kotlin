package dev.ai.elements.demo.ui

import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.R
import dev.ai.elements.demo.data.Conversation
import dev.ai.elements.demo.data.imageAttachment
import dev.ai.elements.ui.chat.AgentComputerLayout
import dev.ai.elements.ui.chat.AgentComputerPanel
import dev.ai.elements.ui.chat.AgentComputerScaffold
import dev.ai.elements.ui.chat.AgentComputerState
import dev.ai.elements.ui.chat.ChatEmptyState
import dev.ai.elements.ui.chat.ContextUsage
import dev.ai.elements.ui.chat.Conversation
import dev.ai.elements.ui.chat.PromptInput
import dev.ai.elements.ui.chat.Queue
import dev.ai.elements.ui.chat.RunReplay
import dev.ai.elements.ui.chat.rememberAgentComputerState
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing
import dev.ai.elements.ui.voice.SpeechInput
import dev.ai.elements.ui.voice.VoiceMode
import kotlinx.coroutines.launch

/** Starter prompts, in the UI language (the model answers in kind). */
@Composable
private fun demoSuggestions(): List<Suggestion> {
    val resources = LocalResources.current
    return remember(resources) {
        listOf(
            R.string.sugg_agent_loop_prompt to R.string.sugg_agent_loop,
            R.string.sugg_browse_prompt to R.string.sugg_browse,
            R.string.sugg_jsx_prompt to R.string.sugg_jsx,
            R.string.sugg_time_prompt to R.string.sugg_time,
            R.string.sugg_calc_prompt to R.string.sugg_calc,
            R.string.sugg_kotlin_prompt to R.string.sugg_kotlin,
            R.string.sugg_clipboard_prompt to R.string.sugg_clipboard,
            R.string.sugg_long_prompt to R.string.sugg_long,
        ).map { (prompt, label) -> Suggestion(resources.getString(prompt), resources.getString(label)) }
    }
}

/**
 * Chat with its conversation history.
 *
 * Phones get the history in a modal drawer. Larger windows use the M3
 * list-detail scaffold: history and chat side by side (resizable with the drag
 * handle) when [twoPane], otherwise one at a time with predictive back.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    widthClass: WidthClass,
    twoPane: Boolean,
    compactHeight: Boolean,
    onOpenSettings: () -> Unit,
    onOpenComponents: () -> Unit = {},
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentId by viewModel.conversationId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // The agent's computer; AG-UI runs replay from their event log.
    val eventLog = viewModel.runtime.agUiEventLog
    val computer = rememberAgentComputerState(remember(eventLog) { RunReplay { eventLog.replayOf(it) } })

    if (widthClass != WidthClass.COMPACT) {
        val navigator = rememberListDetailNavigator<Any>(twoPane)
        // The chat is the primary destination; the list sits "behind" it when single-pane.
        LaunchedEffect(navigator) {
            if (navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            }
        }
        fun showChat() {
            if (!navigator.isDetailVisible) scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
        }
        // The agent's computer is the scaffold's extra pane (M3 canonical list-detail with an extra
        // pane): on expanded windows it opens beside the chat in place of the history, elsewhere
        // over the chat, and Back returns to the list and chat.
        val onExtra = navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Extra
        LaunchedEffect(computer.messageId) {
            if (computer.isOpen && !onExtra) navigator.navigateTo(ListDetailPaneScaffoldRole.Extra)
            if (!computer.isOpen && onExtra) navigator.navigateBack()
        }
        var wasOnExtra by remember { mutableStateOf(false) }
        LaunchedEffect(onExtra) {
            if (wasOnExtra && !onExtra && computer.isOpen) computer.close() // left with Back
            wasOnExtra = onExtra
        }
        val chat by viewModel.chatState.collectAsStateWithLifecycle()
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = if (twoPane) MaterialTheme.shapes.extraLarge else RectangleShape,
                        modifier = Modifier.fillMaxHeight().then(if (twoPane) Modifier.padding(vertical = 8.dp) else Modifier),
                    ) {
                        HistoryPane(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
                            conversations = conversations,
                            currentId = currentId,
                            onNew = { viewModel.newChat(); showChat() },
                            onOpen = { viewModel.open(it); showChat() },
                            onDelete = viewModel::delete,
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    ChatPane(
                        viewModel,
                        // Expanded layouts keep a 24dp window margin on the trailing edge (M3).
                        modifier = if (twoPane) Modifier.padding(end = 24.dp) else Modifier,
                        showMenu = !navigator.isListVisible,
                        computer = computer,
                        computerLayout = AgentComputerLayout.Hosted,
                        compactHeight = compactHeight,
                        onMenu = { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.List) } },
                        onOpenSettings = onOpenSettings,
                    )
                }
            },
            extraPane = {
                AnimatedPane(Modifier.preferredWidth(480.dp)) {
                    val message = computer.messageId?.let { id -> chat.messages.firstOrNull { it.id == id } }
                    if (message != null) {
                        AgentComputerPanel(
                            computer,
                            message,
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
                                .then(if (twoPane) Modifier.padding(vertical = 8.dp).clip(MaterialTheme.shapes.extraLarge) else Modifier),
                        )
                    }
                }
            },
            paneExpansionDragHandle = if (twoPane) { state -> PaneDragHandle(state) } else null,
        )
    } else {
        val drawer = rememberDrawerState(DrawerValue.Closed)
        // Back closes the open drawer before it leaves the screen.
        BackHandler(enabled = drawer.isOpen || drawer.isAnimationRunning) { scope.launch { drawer.close() } }
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(Modifier.width(320.dp)) {
                    HistoryPane(
                        conversations = conversations,
                        currentId = currentId,
                        onNew = { viewModel.newChat(); scope.launch { drawer.close() } },
                        onOpen = { viewModel.open(it); scope.launch { drawer.close() } },
                        onDelete = viewModel::delete,
                        // Phones have no navigation bar: the rest of the app lives in the drawer.
                        footer = {
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.nav_components)) },
                                icon = { Icon(DemoIcons.Widgets, null) },
                                selected = false,
                                onClick = { scope.launch { drawer.close() }; onOpenComponents() },
                                modifier = Modifier.testTag("drawer-components"),
                            )
                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.settings)) },
                                icon = { Icon(DemoIcons.Settings, null) },
                                selected = false,
                                onClick = { scope.launch { drawer.close() }; onOpenSettings() },
                                modifier = Modifier.testTag("drawer-settings"),
                            )
                        },
                    )
                }
            },
        ) {
            ChatPane(
                viewModel,
                showMenu = true,
                computer = computer,
                compactHeight = compactHeight,
                onMenu = { scope.launch { drawer.open() } },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatPane(
    viewModel: ChatViewModel,
    showMenu: Boolean,
    computer: AgentComputerState,
    modifier: Modifier = Modifier,
    computerLayout: AgentComputerLayout = AgentComputerLayout.Auto,
    compactHeight: Boolean,
    onMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.chatState.collectAsStateWithLifecycle()
    val profiles by viewModel.providers.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.providers.selectedId.collectAsStateWithLifecycle()
    val provider = profiles.firstOrNull { it.id == selectedId } ?: profiles.first()
    var input by rememberSaveable { mutableStateOf("") }
    var dictationBase by remember { mutableStateOf<String?>(null) }
    val appContext = LocalContext.current
    var talking by rememberSaveable { mutableStateOf(false) }
    val canTalk = remember { SpeechRecognizer.isRecognitionAvailable(appContext) }
    if (talking) {
        Dialog(onDismissRequest = { talking = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            VoiceMode(viewModel.controller, onClose = { talking = false })
        }
    }
    var attachments by remember { mutableStateOf<List<FilePart>>(emptyList()) }
    var providerSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        scope.launch { attachments = attachments + uris.mapNotNull { context.imageAttachment(it) } }
    }
    // Context used by the conversation so far ≈ the last turn's input + output.
    val lastUsage = state.messages.lastOrNull { it.usage != null }?.usage

    fun submit(text: String) {
        if (viewModel.send(text, attachments)) {
            input = ""
            attachments = emptyList()
        }
    }

    // Short windows (phone landscape) with the keyboard up: give every pixel to the conversation.
    val hideTopBar = compactHeight && WindowInsets.isImeVisible
    val compactWidth = LocalConfiguration.current.screenWidthDp < 600
    Scaffold(containerColor = Color.Transparent, 
        modifier = modifier.noAutoFocusInTouchMode(),
        topBar = {
            if (!hideTopBar) TopAppBar(
                navigationIcon = {
                    if (showMenu) IconButton(onClick = onMenu, shapes = IconButtonDefaults.shapes()) {
                        Icon(DemoIcons.Menu, stringResource(R.string.conversations))
                    }
                },
                title = { ProviderButton(provider, onClick = { providerSheet = true }) },
                actions = {
                    IconButton(onClick = viewModel::newChat, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("new-chat")) {
                        Icon(DemoIcons.EditNote, stringResource(R.string.new_chat))
                    }
                },
                colors = transparentAppBarColors(),
                expandedHeight = 56.dp,
            )
        },
    ) { padding ->
        AgentComputerScaffold(
            computer,
            state.messages,
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            layout = computerLayout,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.messages.isEmpty() && !state.isBusy) {
                        ChatEmptyState(
                            title = stringResource(R.string.empty_title),
                            subtitle = listOf(provider.name, provider.kind.label).distinct().joinToString(" · "),
                            suggestions = demoSuggestions(),
                            onSelect = { submit(it.text) },
                            showHero = !compactHeight,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    } else {
                        Conversation(
                            state = state,
                            onRegenerate = viewModel::regenerate,
                            onDismissError = viewModel::dismissError,
                            onToolApproval = viewModel::respondToApproval,
                            onToolDecision = viewModel::respondToDecision,
                            onInputResponse = viewModel::respondToInput,
                            onSelectVersion = viewModel::selectVersion,
                            onRestoreCheckpoint = viewModel::restoreCheckpoint,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Queue(
                    items = state.queue,
                    paused = state.queuePaused,
                    onRemove = { viewModel.removeQueued(it.id) },
                    onSendNow = { viewModel.sendQueuedNow(it.id) },
                    modifier = Modifier.widthIn(max = 840.dp).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
                PromptInput(
                    allowQueue = true,
                    value = input,
                    onValueChange = { input = it },
                    onSubmit = { submit(input) },
                    onStop = viewModel::stop,
                    busy = state.isBusy,
                    placeholder = stringResource(R.string.message_placeholder, provider.name),
                    attachments = attachments,
                    onAddAttachment = {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemoveAttachment = { removed -> attachments = attachments.filterNot { it.id == removed.id } },
                    onVoiceMode = if (canTalk) ({ talking = true }) else null,
                    toolbar = {
                        // Dictation appends to what's typed; partial results update in place.
                        SpeechInput(onTranscript = { text, isFinal ->
                            val base = dictationBase ?: input.also { dictationBase = it }
                            input = listOf(base, text).filter { it.isNotBlank() }.joinToString(" ")
                            if (isFinal) dictationBase = null
                        }, onCancel = {
                            // Cancelled: back to what was typed before dictating.
                            dictationBase?.let { input = it }
                            dictationBase = null
                        })
                        ModelChip(viewModel, provider)
                        CapabilitiesButton(viewModel, provider, onManage = onOpenSettings)
                        // Phones already show usage under each reply; the composer has no room for it.
                        if (!compactWidth) lastUsage?.let { ContextUsage(it) }
                    },
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }

    if (providerSheet) {
        ProviderSheet(
            profiles = profiles,
            selectedId = provider.id,
            onSelect = { viewModel.providers.select(it); providerSheet = false },
            onManage = { providerSheet = false; onOpenSettings() },
            onDismiss = { providerSheet = false },
        )
    }
}

/** The provider as the chat's title, "Name ▾" (the model is on the composer's chip). */
@Composable
private fun ProviderButton(provider: ProviderProfile, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(start = 8.dp, end = 4.dp)
            .testTag("provider-button"),
    ) {
        Text(provider.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(DemoIcons.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSheet(
    profiles: List<ProviderProfile>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Text(
            stringResource(R.string.agent_provider),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(profiles, key = { it.id }) { profile ->
                ListItem(
                    selected = profile.id == selectedId,
                    onClick = { onSelect(profile.id) },
                    supportingContent = {
                        Text("${profile.kind.label} · ${profile.model.ifBlank { stringResource(R.string.default_model) }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { RadioButton(selected = profile.id == selectedId, onClick = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("provider-${profile.id}"),
                ) { Text(profile.name) }
            }
            item {
                TextButton(onClick = onManage, modifier = Modifier.padding(16.dp)) {
                    Icon(DemoIcons.Tune, null, Modifier.size(AiSize.compactIcon))
                    Text("  " + stringResource(R.string.manage_providers))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelChip(viewModel: ChatViewModel, provider: ProviderProfile) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var models by remember(provider.id) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember(provider.id) { mutableStateOf<String?>(null) }

    Box {
        Surface(
            onClick = {
                open = true
                if (models.isEmpty()) scope.launch {
                    loading = true
                    error = null
                    runCatching { viewModel.listModels(provider) }
                        .onSuccess { models = it }
                        .onFailure { error = it.message }
                    loading = false
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("model-chip"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AiSpacing.s),
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    provider.model.ifBlank { stringResource(R.string.default_model) },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                Icon(DemoIcons.ArrowDropDown, null, Modifier.size(AiSize.compactIcon))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            when {
                loading -> Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp))
                }
                error != null -> Text(
                    stringResource(R.string.list_models_failed, error.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp).widthIn(max = 280.dp),
                )
                models.isEmpty() -> Text(stringResource(R.string.no_models), Modifier.padding(16.dp))
            }
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        viewModel.providers.upsert(provider.copy(model = model))
                        open = false
                    },
                    trailingIcon = if (model == provider.model) ({ RadioButton(selected = true, onClick = null) }) else null,
                )
            }
        }
    }
}
