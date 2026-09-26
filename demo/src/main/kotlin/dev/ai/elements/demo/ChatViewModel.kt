package dev.ai.elements.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ai.elements.core.ChatController
import dev.ai.elements.core.ChatState
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.config.ProviderStore
import dev.ai.elements.demo.data.AppSettings
import dev.ai.elements.demo.data.Conversation
import dev.ai.elements.demo.data.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * App-wide state: providers, conversation history and the active chat.
 *
 * The [ChatController] resolves the backend from the selected provider on each
 * turn, so switching provider or model mid-conversation just works.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val providers = ProviderStore(app)
    val settings = AppSettings(app)
    private val repository = ConversationRepository(app)
    val conversations: StateFlow<List<Conversation>> = repository.conversations

    private val chat = ChatController(
        backend = { providers.selected.let { it.createBackend(providers.apiKey(it.id)) } },
        scope = viewModelScope,
    )
    val chatState: StateFlow<ChatState> = chat.state

    private val _conversationId = MutableStateFlow(newId())
    val conversationId: StateFlow<String> = _conversationId.asStateFlow()

    init {
        viewModelScope.launch {
            repository.load()
            // Persist every settled turn (not each streamed delta).
            chat.state.collect { state ->
                if (!state.isBusy && state.messages.isNotEmpty()) persist(state)
            }
        }
    }

    fun send(text: String): Boolean = chat.send(text)
    fun stop() = chat.stop()
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

    suspend fun listModels(profile: ProviderProfile): List<String> =
        profile.listModels(providers.apiKey(profile.id))

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
