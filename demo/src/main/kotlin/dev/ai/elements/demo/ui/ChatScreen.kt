package dev.ai.elements.demo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.data.Conversation
import dev.ai.elements.demo.data.imageAttachment
import dev.ai.elements.ui.chat.ChatEmptyState
import dev.ai.elements.ui.chat.ContextUsage
import dev.ai.elements.ui.chat.Conversation
import dev.ai.elements.ui.chat.PromptInput
import kotlinx.coroutines.launch

private val DemoSuggestions = listOf(
    Suggestion("Explain how an AI agent loop works, with a Mermaid sequence diagram", "Agent loop diagram"),
    Suggestion("What time is it in Tokyo right now?", "What time is it in Tokyo?"),
    Suggestion("Calculate (1234 * 5678) / 9 and show the steps in a table", "Calculate with a tool"),
    Suggestion("Write a Kotlin data class for a chat message and explain each field in a table", "Kotlin + table"),
    Suggestion("Copy the text 'Hello from AI Elements' to my clipboard", "Clipboard (needs approval)"),
)

@Composable
fun ChatScreen(viewModel: ChatViewModel, widthClass: WidthClass, compactHeight: Boolean, onOpenSettings: () -> Unit) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentId by viewModel.conversationId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (widthClass == WidthClass.EXPANDED) {
        Row(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(320.dp).fillMaxHeight()) {
                HistoryPane(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
                    conversations = conversations,
                    currentId = currentId,
                    onNew = viewModel::newChat,
                    onOpen = viewModel::open,
                    onDelete = viewModel::delete,
                )
            }
            VerticalDivider()
            ChatPane(viewModel, showMenu = false, compactHeight = compactHeight, onMenu = {}, onOpenSettings = onOpenSettings)
        }
    } else {
        val drawer = rememberDrawerState(DrawerValue.Closed)
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
                    )
                }
            },
        ) {
            ChatPane(
                viewModel,
                showMenu = true,
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
    compactHeight: Boolean,
    onMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.chatState.collectAsStateWithLifecycle()
    val profiles by viewModel.providers.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.providers.selectedId.collectAsStateWithLifecycle()
    val provider = profiles.firstOrNull { it.id == selectedId } ?: profiles.first()
    var input by rememberSaveable { mutableStateOf("") }
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
    Scaffold(
        topBar = {
            if (!hideTopBar) TopAppBar(
                navigationIcon = {
                    if (showMenu) IconButton(onClick = onMenu, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.Menu, "Conversations")
                    }
                },
                title = { ProviderButton(provider, onClick = { providerSheet = true }) },
                actions = {
                    IconButton(onClick = viewModel::newChat, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("new-chat")) {
                        Icon(Icons.Outlined.EditNote, "New chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.messages.isEmpty() && !state.isBusy) {
                    ChatEmptyState(
                        title = "What shall we explore?",
                        subtitle = "Chatting with ${provider.name} · ${provider.kind.label}",
                        suggestions = DemoSuggestions,
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            PromptInput(
                value = input,
                onValueChange = { input = it },
                onSubmit = { submit(input) },
                onStop = viewModel::stop,
                busy = state.isBusy,
                placeholder = "Message ${provider.name}",
                attachments = attachments,
                onAddAttachment = {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveAttachment = { removed -> attachments = attachments.filterNot { it.id == removed.id } },
                toolbar = {
                    ModelChip(viewModel, provider)
                    lastUsage?.let { ContextUsage(it) }
                },
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
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

@Composable
private fun ProviderButton(provider: ProviderProfile, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("provider-button"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(provider.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    provider.model.ifBlank { "server default model" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
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
            "Agent provider",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(profiles, key = { it.id }) { profile ->
                ListItem(
                    selected = profile.id == selectedId,
                    onClick = { onSelect(profile.id) },
                    supportingContent = {
                        Text("${profile.kind.label} · ${profile.model.ifBlank { "default" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { RadioButton(selected = profile.id == selectedId, onClick = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("provider-${profile.id}"),
                ) { Text(profile.name) }
            }
            item {
                TextButton(onClick = onManage, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                    Text("  Manage providers & API keys")
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
            modifier = Modifier.testTag("model-chip"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(Icons.Outlined.Memory, null, Modifier.size(16.dp))
                Text(
                    provider.model.ifBlank { "default" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            when {
                loading -> Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp))
                }
                error != null -> Text(
                    "Couldn't list models:\n$error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp).widthIn(max = 280.dp),
                )
                models.isEmpty() -> Text("No models reported", Modifier.padding(16.dp))
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

@Composable
private fun HistoryPane(
    conversations: List<Conversation>,
    currentId: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Outlined.EditNote, null) },
            text = { Text("New chat") },
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
        )
        Text(
            "Recent",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (conversations.isEmpty()) {
            Text(
                "Your conversations will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(conversations, key = { it.id }) { conversation ->
                NavigationDrawerItem(
                    label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = conversation.id == currentId,
                    onClick = { onOpen(conversation.id) },
                    icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
                    badge = {
                        IconButton(onClick = { onDelete(conversation.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete", Modifier.size(18.dp))
                        }
                    },
                )
            }
        }
    }
}
