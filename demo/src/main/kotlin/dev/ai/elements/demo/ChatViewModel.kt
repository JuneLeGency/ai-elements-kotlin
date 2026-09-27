package dev.ai.elements.demo

import dev.ai.elements.genui.a2ui.send
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.chat.ToolDecision
import dev.ai.elements.core.mcp.McpAuthRequiredException
import dev.ai.elements.core.mcp.McpServerConfig
import dev.ai.elements.core.mcp.McpServerStatus
import kotlinx.coroutines.flow.update
import dev.ai.elements.core.auth.OAuthProvider
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.mcpapps.McpAppModelContext
import dev.ai.elements.demo.auth.SignInController
import dev.ai.elements.demo.data.AppSettings
import dev.ai.elements.demo.data.Conversation
import dev.ai.elements.demo.data.ConversationRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide state: providers, conversation history and the active chat.
 *
 * The [ChatController] resolves the backend from the selected provider on each
 * turn, so switching provider or model mid-conversation just works.
 */
private val ChatGptModels = listOf("gpt-5.5", "gpt-5.3-codex")

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val demo = app as DemoApplication
    val providers = demo.providers
    val settings = AppSettings(app)
    private val repository = ConversationRepository(app)
    val conversations: StateFlow<List<Conversation>> = repository.conversations

    val mcpServers = demo.mcpServers
    val agents = demo.agents
    val skills = demo.skills

    /** Each turn: the selected provider with the enabled skills, MCP servers and sub-agents. */
    val runtime = demo.runtime

    private val chat = ChatController(backend = runtime::backend, scope = viewModelScope)
    val chatState: StateFlow<ChatState> = chat.state

    private val _conversationId = MutableStateFlow(newId())
    val conversationId: StateFlow<String> = _conversationId.asStateFlow()

    init {
        viewModelScope.launch { skills.refresh() }
        viewModelScope.launch {
            repository.load()
            // Persist every settled turn (not each streamed delta).
            chat.state.collect { state ->
                if (!state.isBusy && state.messages.isNotEmpty()) persist(state)
            }
        }
    }

    fun send(text: String, attachments: List<FilePart> = emptyList()): Boolean = chat.send(text, attachments, data = takeAppContext())

    // MCP Apps: each view's latest `ui/update-model-context`, sent with the next user turn.
    private val appContext = mutableMapOf<String, DataPart>()

    fun setMcpAppContext(context: McpAppModelContext) = synchronized(appContext) { appContext[context.toolCallId] = context.toDataPart() }

    private fun takeAppContext(): List<DataPart> = synchronized(appContext) { appContext.values.toList().also { appContext.clear() } }
    fun respondToApproval(toolCallId: String, approved: Boolean) = chat.respondToApproval(toolCallId, approved)
    fun respondToDecision(toolCallId: String, decision: ToolDecision) = chat.respondToApproval(toolCallId, decision)
    fun respondToInput(requestId: String, response: InputResponse) = chat.respondToInput(requestId, response)

    /** A user's action on an agent-sent A2UI surface, back to the agent over the provider's protocol. */
    fun sendA2uiAction(action: dev.ai.elements.genui.a2ui.A2uiAction): Boolean = chat.send(action)
    fun stop() = chat.stop()
    fun selectVersion(messageId: String, index: Int) = chat.selectVersion(messageId, index)
    fun restoreCheckpoint(messageId: String) = chat.restoreCheckpoint(messageId)
    fun removeQueued(id: String) = chat.removeQueued(id)
    fun sendQueuedNow(id: String) = chat.sendQueuedNow(id)
    fun regenerate() = chat.regenerate()
    fun dismissError() = chat.dismissError()

    fun newChat() {
        runtime.newSession()
        chat.load(emptyList())
        _conversationId.value = newId()
    }

    fun open(id: String) {
        val conversation = repository.get(id) ?: return
        _conversationId.value = id
        runtime.newSession()
        chat.load(conversation.messages)
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            if (id == _conversationId.value) newChat()
        }
    }

    suspend fun listModels(profile: ProviderProfile): List<String> = when {
        profile.kind == dev.ai.elements.core.config.ProviderKind.A2A -> listOf(runtime.a2aAgent(profile.baseUrl).card().name())
        // The ChatGPT backend has no public model list; offer the plan's current models.
        profile.oauth == OAuthProvider.CHATGPT -> { providers.tokenSource(profile).fresh(); ChatGptModels }
        profile.usesTokens -> profile.listModels(providers.tokenSource(profile).fresh().accessToken)
        else -> profile.listModels(providers.apiKey(profile.id))
    }

    val signIn = SignInController(app, providers, viewModelScope)

    // --- Capabilities: MCP servers, skills, agents ------------------------------------------

    /** Last connection check per MCP server (null while checking). */
    private val _mcpStatus = MutableStateFlow<Map<String, McpServerStatus?>>(emptyMap())
    val mcpStatus: StateFlow<Map<String, McpServerStatus?>> = _mcpStatus.asStateFlow()

    fun checkMcp(server: McpServerConfig) {
        _mcpStatus.update { it + (server.id to null) }
        viewModelScope.launch {
            val client = mcpServers.client(server)
            val status = try {
                val tools = client.listTools()
                McpServerStatus.Connected(tools, client.serverInfo, client.protocolVersion)
            } catch (e: McpAuthRequiredException) {
                McpServerStatus.NeedsSignIn(e)
            } catch (e: Exception) {
                McpServerStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
            _mcpStatus.update { it + (server.id to status) }
        }
    }

    fun signInMcp(activity: android.app.Activity, server: McpServerConfig) {
        val challenge = (_mcpStatus.value[server.id] as? McpServerStatus.NeedsSignIn)?.error
        signIn.signInMcp(activity, mcpServers, server, challenge) { checkMcp(server) }
    }

    fun removeMcp(id: String) {
        mcpServers.remove(id)
        _mcpStatus.update { it - id }
    }

    /** Install a skill `.zip`; returns its name, or throws with why it is not a valid skill. */
    suspend fun installSkill(uri: android.net.Uri): String = skills.install(uri).name

    fun uninstallSkill(name: String) = viewModelScope.launch { skills.uninstall(name) }

    /** Name and description from a remote agent's A2A card. */
    suspend fun agentCard(url: String) = runtime.a2aAgent(url).card()

    private suspend fun persist(state: ChatState) {
        val id = _conversationId.value
        val existing = repository.get(id)
        if (existing?.messages == state.messages) return
        repository.save(
            Conversation(
                id = id,
                title = existing?.title ?: ConversationRepository.titleFor(state.messages),
                updatedAt = System.currentTimeMillis(),
                providerId = providers.selected.id,
                messages = state.messages,
            ),
        )
    }

    private fun newId() = UUID.randomUUID().toString()
}
