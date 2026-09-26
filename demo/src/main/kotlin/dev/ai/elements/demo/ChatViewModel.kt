package dev.ai.elements.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ai.elements.core.ChatController
import dev.ai.elements.core.ChatState
import dev.ai.elements.core.config.McpServerStore
import dev.ai.elements.core.auth.OAuthProvider
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.config.ProviderStore
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.demo.auth.SignInController
import dev.ai.elements.demo.data.AgentsStore
import dev.ai.elements.demo.data.AppSettings
import dev.ai.elements.demo.data.SkillsRepository
import dev.ai.elements.demo.data.Conversation
import dev.ai.elements.demo.data.ConversationRepository
import dev.ai.elements.demo.tools.ClipboardTool
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
    val providers = ProviderStore(app)
    val settings = AppSettings(app)
    private val repository = ConversationRepository(app)
    val conversations: StateFlow<List<Conversation>> = repository.conversations

    val mcpServers = McpServerStore(app, providers.secrets)
    val agents = AgentsStore(app)
    val skills = SkillsRepository(app)

    /** Each turn: the selected provider with the enabled skills, MCP servers and sub-agents. */
    val runtime = AgentRuntime(providers, mcpServers, agents, skills, appTools = listOf(ClipboardTool(app)))

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

    fun send(text: String, attachments: List<FilePart> = emptyList()): Boolean = chat.send(text, attachments)
    fun respondToApproval(toolCallId: String, approved: Boolean) = chat.respondToApproval(toolCallId, approved)
    fun stop() = chat.stop()
    fun selectVersion(messageId: String, index: Int) = chat.selectVersion(messageId, index)
    fun restoreCheckpoint(messageId: String) = chat.restoreCheckpoint(messageId)
    fun removeQueued(id: String) = chat.removeQueued(id)
    fun sendQueuedNow(id: String) = chat.sendQueuedNow(id)
    fun regenerate() = chat.regenerate()
    fun dismissError() = chat.dismissError()

    fun newChat() {
        chat.load(emptyList())
        _conversationId.value = newId()
    }

    fun open(id: String) {
        val conversation = repository.get(id) ?: return
        _conversationId.value = id
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
