package dev.ai.elements.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.ChatController
import dev.ai.elements.core.auth.OAuthManager
import dev.ai.elements.core.auth.OAuthManagerFactory
import dev.ai.elements.core.auth.OAuthTokenStore
import dev.ai.elements.core.backend.backendFor
import dev.ai.elements.core.config.Credential
import dev.ai.elements.core.config.GatewayConfig
import dev.ai.elements.core.config.GatewayConfigStore
import dev.ai.elements.core.config.GatewayProvider
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.Suggestion
import dev.ai.elements.core.theme.AiElementsTheme
import dev.ai.elements.ui.conversation.Conversation
import dev.ai.elements.ui.promptinput.PromptInput
import dev.ai.elements.ui.suggestion.Suggestions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiElementsTheme(darkTheme = false) {
                Root()
            }
        }
    }
}

/**
 * Top-level screen. Owns the [GatewayConfigStore] and rebuilds the
 * [ChatController] whenever the gateway settings change, so the selected
 * provider (AI Elements gateway / OpenAI-compatible / mock) applies on the
 * next `send()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { GatewayConfigStore(context) }
    val oauthTokenStore = remember { OAuthTokenStore(context) }
    val oauthManager = remember {
        OAuthManagerFactory(context, oauthTokenStore).create(OAuthProviderSpecs.openAi())
    }
    val gateway by store.config.collectAsState()

    // Rebuild the controller ONLY when the endpoint identity changes
    // (provider + base URL). Model/temperature/key edits are read live per
    // request via `configRef`, so they don't wipe the conversation.
    val endpointId = gateway.provider.name + "|" + gateway.baseUrl
    val controller = remember(endpointId) {
        ChatController(
            backend = backendFor(store.config.value, { store.config.value }, store, oauthTokenStore),
        )
    }
    DisposableEffect(endpointId) { onDispose { controller.close() } }

    val chatState by controller.state.collectAsState()

    var input by remember { mutableStateOf("") }
    var selectedModel by remember(gateway) { mutableStateOf(gateway.model) }
    var showGallery by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val suggestions = remember {
        listOf(
            Suggestion("s1", "How do I set up the project?"),
            Suggestion("s2", "What is the roadmap for Q4?"),
            Suggestion("s3", "Add dark mode support"),
            Suggestion("s4", "Optimise database queries"),
        )
    }

    val models = remember { listOf("gpt-4o", "gpt-4o-mini", "claude-sonnet", "gemini-2.5", "llama-3.3") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            showSettings -> "Gateway settings"
                            showGallery -> "Components"
                            else -> "AI Elements for Kotlin"
                        },
                        modifier = Modifier.clickable { showGallery = if (showSettings) showGallery else !showGallery },
                    )
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Settings (gateway)
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .testTag("topbar_settings")
                                .clickable { showSettings = !showSettings; showGallery = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        // Components / Chat toggle
                        Text(
                            text = if (showGallery && !showSettings) "Chat" else "Components",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .testTag("topbar_chat_toggle")
                                .clickable { showGallery = !showGallery; showSettings = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        when {
            showSettings -> SettingsScreen(
                store = store,
                oauthManager = oauthManager,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            showGallery -> ComponentsGallery(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
            ) {
                if (chatState.messages.isEmpty()) {
                    Suggestions(
                        suggestions = suggestions,
                        onSelect = { s ->
                            input = s.text
                            controller.send(s.text)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                Conversation(
                    messages = chatState.messages,
                    modifier = Modifier.weight(1f),
                    onAction = { },
                )

                PromptInput(
                    value = input,
                    onValueChange = {
                        android.util.Log.i("AIElems", "onValueChange='$it' (input was '$input')")
                        input = it
                    },
                    onSend = {
                        val prompt = input
                        android.util.Log.i("AIElems", "onSend fired, prompt='$prompt'")
                        controller.send(prompt)
                        input = ""
                    },
                    status = chatState.status,
                    onStop = { controller.stop() },
                    model = selectedModel,
                    models = models,
                    onModelSelect = { selectedModel = it },
                    onAttach = { },
                    onMic = { },
                )

                chatState.error?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Gateway settings: pick provider, base URL, API key, model. Persisted to
 * SharedPreferences via [GatewayConfigStore].
 */
@Composable
fun SettingsScreen(
    store: GatewayConfigStore,
    oauthManager: OAuthManager,
    modifier: Modifier = Modifier,
) {
    val gateway by store.config.collectAsState()
    val providers = remember { GatewayProvider.entries }
    val scope = rememberCoroutineScope()
    var oauthBusy by remember { mutableStateOf(false) }
    var oauthError by remember { mutableStateOf<String?>(null) }

    val useOAuth = gateway.credential is Credential.OAuth
    val oauthSupported = gateway.provider.supportsOAuth

    fun signInWithOAuth() {
        scope.launch {
            oauthBusy = true
            oauthError = null
            runCatching { oauthManager.startLogin() }
                .onSuccess { store.update { it.copy(credential = Credential.OAuth(oauthManager.spec.instanceId)) } }
                .onFailure { e -> oauthError = "Sign-in failed: ${e.message}" }
            oauthBusy = false
        }
    }

    fun signOut() {
        scope.launch {
            oauthManager.logout()
            store.update { it.copy(credential = Credential.None) }
            oauthError = null
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Provider", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            providers.forEachIndexed { index, p ->
                SegmentedButton(
                    selected = gateway.provider == p,
                    onClick = {
                        val defaults = p
                        store.update { cfg ->
                            cfg.copy(
                                provider = defaults,
                                baseUrl = defaults.baseUrl,
                                model = defaults.defaultModel,
                            )
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, providers.size),
                    modifier = Modifier.testTag("provider_${p.name}"),
                ) {
                    Text(p.label)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (gateway.provider != GatewayProvider.MOCK) {
            OutlinedTextField(
                value = gateway.baseUrl,
                onValueChange = { text -> store.update { cfg -> cfg.copy(baseUrl = text) } },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("settings_base_url"),
            )
            Spacer(Modifier.height(12.dp))

            // API-key credential (hidden when OAuth is the active credential).
            if (!useOAuth) {
                val hasKey = store.secretFor(gateway.credential).isNotBlank()
                var keyDraft by remember { mutableStateOf("") }
                LaunchedEffect(hasKey) { if (!hasKey) keyDraft = "" }

                OutlinedTextField(
                    value = if (keyDraft.isEmpty() && hasKey) "••••••••" else keyDraft,
                    onValueChange = { text ->
                        keyDraft = text
                        store.writeApiKey(text)
                        store.update { cfg ->
                            cfg.copy(credential = if (text.isBlank()) Credential.None else Credential.ApiKey())
                        }
                    },
                    label = { Text("API key") },
                    placeholder = { Text("Bearer token / key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("settings_api_key"),
                )
                Spacer(Modifier.height(12.dp))
            }

            // OAuth credential (authorization code + PKCE) — available only for
            // providers that support it.
            if (oauthSupported) {
                Text("Sign in with OAuth", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { if (useOAuth) signOut() else signInWithOAuth() },
                        enabled = !oauthBusy,
                        modifier = Modifier.weight(1f).testTag("settings_oauth_signin"),
                    ) {
                        Text(if (oauthBusy) "Signing in…" else if (useOAuth) "Sign out" else "Sign in")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (useOAuth) {
                    Text(
                        text = "Signed in via OAuth (token auto-refreshes).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                oauthError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        OutlinedTextField(
            value = gateway.model,
            onValueChange = { text -> store.update { cfg -> cfg.copy(model = text) } },
            label = { Text("Model") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth().testTag("settings_model"),
        )

        Spacer(Modifier.height(20.dp))
        val authed = when (val c = gateway.credential) {
            is Credential.ApiKey -> store.secretFor(c).isNotBlank()
            is Credential.OAuth -> oauthManager.isAuthenticated()
            Credential.None -> false
        }
        Text(
            text = if (gateway.provider == GatewayProvider.MOCK) {
                "Offline mock — no network needed."
            } else {
                "Endpoint: ${gateway.url}\nAuth: ${if (authed) "configured" else "none"}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
