package dev.ai.elements.demo.data

import android.content.Context
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val providerId: String = "",
    val messages: List<Message> = emptyList(),
)

/** All conversations in one JSON file; small enough for a demo, atomic via temp-file rename. */
class ConversationRepository(context: Context) {
    private val file = File(context.filesDir, "conversations.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Conversation.serializer())
    private val writeLock = Mutex()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val loaded = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
        _conversations.value = loaded.sortedByDescending { it.updatedAt }
    }

    fun get(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    suspend fun save(conversation: Conversation) {
        _conversations.update { list ->
            (listOf(conversation) + list.filterNot { it.id == conversation.id }).sortedByDescending { it.updatedAt }
        }
        persist()
    }

    suspend fun delete(id: String) {
        _conversations.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(serializer, _conversations.value))
            tmp.renameTo(file)
        }
    }

    companion object {
        fun titleFor(messages: List<Message>): String =
            messages.firstOrNull { it.role == Role.USER }?.text?.lineSequence()?.firstOrNull()?.take(60)?.trim()
                ?.ifBlank { null } ?: "New chat"
    }
}
