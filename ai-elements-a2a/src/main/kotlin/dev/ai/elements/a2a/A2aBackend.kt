package dev.ai.elements.a2a

import com.google.gson.Gson
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.agent.SubAgent
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.a2aproject.sdk.client.ClientEvent
import org.a2aproject.sdk.client.MessageEvent
import org.a2aproject.sdk.client.TaskEvent
import org.a2aproject.sdk.client.TaskUpdateEvent
import org.a2aproject.sdk.spec.AgentCard
import org.a2aproject.sdk.spec.Artifact
import org.a2aproject.sdk.spec.DataPart
import org.a2aproject.sdk.spec.FileWithBytes
import org.a2aproject.sdk.spec.FileWithUri
import org.a2aproject.sdk.spec.Message
import org.a2aproject.sdk.spec.Part
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent
import org.a2aproject.sdk.spec.TaskState
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent
import org.a2aproject.sdk.spec.TextPart
import java.util.UUID
import dev.ai.elements.core.model.Message as ChatMessage

/**
 * A remote A2A agent as a chat provider.
 *
 * Each turn sends the last user message with the conversation's A2A
 * `contextId`; when the previous reply left the task `input-required` (or
 * `auth-required`), the answer continues that task. Both ids are kept in the
 * reply's [ChatMessage.metadata] under `a2a`.
 *
 * Mapping to the UI: artifacts and the agent's final message become text,
 * files and data parts; progress messages while the task is `working` show as
 * a `data-task` checklist; `failed` / `rejected` / `canceled` end the turn
 * with an error.
 */
class A2aBackend(private val agent: A2aAgent) : ChatBackend {

    override fun stream(history: List<ChatMessage>): Flow<ChatEvent> = flow {
        val card = agent.card()
        val user = history.lastOrNull { it.role == Role.USER } ?: return@flow
        val previous = history.lastOrNull { it.role == Role.ASSISTANT }?.metadata?.get(METADATA_KEY) as? JsonObject
        val state = previous?.string("state")?.let { runCatching { TaskState.valueOf(it) }.getOrNull() }
        val parts = buildList<Part<*>> {
            user.parts.filterIsInstance<FilePart>().forEach { file ->
                val data = file.base64Data
                add(org.a2aproject.sdk.spec.FilePart(
                    if (data != null) FileWithBytes(file.mediaType, file.filename ?: "attachment", data)
                    else FileWithUri(file.mediaType, file.filename ?: "attachment", file.url),
                ))
            }
            if (user.text.isNotBlank()) add(TextPart(user.text))
        }
        val message = Message.builder()
            .role(Message.Role.ROLE_USER)
            .messageId(UUID.randomUUID().toString())
            .parts(parts)
            .apply {
                previous?.string("contextId")?.let(::contextId)
                if (state?.isInterrupted() == true) previous.string("taskId")?.let(::taskId)
            }
            .build()
        val mapper = Mapper(card.name())
        agent.send(message).collect { event -> mapper.map(event).forEach { emit(it) } }
        mapper.finish().forEach { emit(it) }
    }

    /** Stateful A2A event → [ChatEvent] mapping for one turn. */
    internal class Mapper(private val agentName: String) {
        private var taskId: String? = null
        private var contextId: String? = null
        private var state: TaskState? = null
        private val progress = mutableListOf<String>()
        private val generations = mutableMapOf<String, Int>()

        fun map(event: ClientEvent): List<ChatEvent> = when (event) {
            is MessageEvent -> {
                contextId = event.message.contextId() ?: contextId
                messageEvents(event.message, "message-${event.message.messageId()}") + metadata()
            }
            is TaskEvent -> {
                val task = event.task
                taskId = task.id()
                contextId = task.contextId()
                task.artifacts().orEmpty().flatMap { artifactEvents(it, append = false) } +
                    status(task.status().state(), task.status().message()) + metadata()
            }
            is TaskUpdateEvent -> {
                taskId = event.task.id()
                contextId = event.task.contextId()
                when (val update = event.updateEvent) {
                    is TaskStatusUpdateEvent -> status(update.status().state(), update.status().message()) + metadata()
                    is TaskArtifactUpdateEvent -> artifactEvents(update.artifact(), update.append() == true)
                    else -> emptyList()
                }
            }
        }

        fun finish(): List<ChatEvent> = buildList {
            if (progress.isNotEmpty()) add(progressEvent(done = state?.isFinal() == true || state?.isInterrupted() == true))
            add(ChatEvent.Finish)
        }

        private fun status(newState: TaskState, message: Message?): List<ChatEvent> {
            state = newState
            val text = message?.text()?.takeIf { it.isNotBlank() }
            return when (newState) {
                TaskState.TASK_STATE_FAILED, TaskState.TASK_STATE_REJECTED ->
                    listOf(ChatEvent.Error(text ?: "$agentName ${newState.label()} the task"))
                TaskState.TASK_STATE_CANCELED -> listOf(ChatEvent.Error("$agentName canceled the task"))
                // Progress notes while working; the answer (or question) when done / interrupted.
                TaskState.TASK_STATE_WORKING, TaskState.TASK_STATE_SUBMITTED ->
                    if (text != null && progress.lastOrNull() != text) { progress += text; listOf(progressEvent(done = false)) } else emptyList()
                else -> message?.let { messageEvents(it, "status-${it.messageId()}") }.orEmpty()
            }
        }

        private fun messageEvents(message: Message, idPrefix: String): List<ChatEvent> =
            message.parts().flatMapIndexed { i, part ->
                val id = "$idPrefix-$i"
                when (part) {
                    is TextPart -> listOf(ChatEvent.TextDelta(id, part.text()), ChatEvent.TextEnd(id))
                    else -> listOfNotNull(partEvent(id, part, name = "a2a-data"))
                }
            }

        private fun artifactEvents(artifact: Artifact, append: Boolean): List<ChatEvent> {
            val key = artifact.artifactId()
            // A non-append update replaces the artifact: start fresh parts.
            val generation = if (append) generations.getOrPut(key) { 0 }
            else (generations[key]?.plus(1) ?: 0).also { generations[key] = it }
            return artifact.parts().mapIndexedNotNull { i, part ->
                val id = "artifact-$key-$generation-$i"
                if (part is TextPart) ChatEvent.TextDelta(id, part.text()) else partEvent(id, part, artifact.name() ?: "a2a-data")
            }
        }

        private fun partEvent(id: String, part: Part<*>, name: String): ChatEvent? = when (part) {
            is org.a2aproject.sdk.spec.FilePart -> when (val file = part.file()) {
                is FileWithUri -> ChatEvent.File(id, file.mimeType() ?: DEFAULT_MIME, file.uri())
                is FileWithBytes -> ChatEvent.File(id, file.mimeType() ?: DEFAULT_MIME, "data:${file.mimeType() ?: DEFAULT_MIME};base64,${file.bytes()}")
                else -> null
            }
            is DataPart -> ChatEvent.Data(id, name, part.data().toJson())
            else -> null
        }

        private fun progressEvent(done: Boolean) = ChatEvent.Data(
            "a2a-progress",
            "task",
            buildJsonObject {
                put("title", agentName)
                putJsonArray("items") {
                    progress.forEachIndexed { i, text ->
                        addJsonObject {
                            put("label", text)
                            put("status", if (done || i < progress.lastIndex) "complete" else "active")
                        }
                    }
                }
            },
        )

        private fun metadata() = ChatEvent.Metadata(
            buildJsonObject {
                putJsonObject(METADATA_KEY) {
                    contextId?.let { put("contextId", it) }
                    taskId?.let { put("taskId", it) }
                    state?.let { put("state", it.name) }
                }
            },
        )
    }

    companion object {
        /** Key of the A2A ids in [ChatMessage.metadata]. */
        const val METADATA_KEY = "a2a"
        private const val DEFAULT_MIME = "application/octet-stream"
        private val gson = Gson()

        private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun Message.text() = parts().filterIsInstance<TextPart>().joinToString("\n") { it.text() }
        private fun TaskState.label() = name.removePrefix("TASK_STATE_").lowercase()
        private fun Any?.toJson(): JsonElement = if (this == null) JsonNull else Json.parseToJsonElement(gson.toJson(this))
    }
}

/**
 * This remote agent as a [SubAgent] for [dev.ai.elements.core.agent.SubAgents]:
 * the orchestrator delegates to it with `delegate_task`, and its reply
 * streams into the UI like any sub-agent's.
 *
 * @param name how the orchestrator refers to it; defaults to the card name.
 */
fun A2aAgent.asSubAgent(card: AgentCard, name: String = subAgentName(card.name())): SubAgent {
    val skills = card.skills().joinToString("; ") { it.name() + if (!it.description().isNullOrBlank()) ": ${it.description()}" else "" }
    val description = listOfNotNull(card.description(), skills.takeIf { it.isNotEmpty() }?.let { "Skills: $it" })
        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { card.name() }
    return SubAgent(name, description) { A2aBackend(this) }
}

/** An agent name as a delegate name (`^[A-Za-z0-9_-]{1,64}$`). */
fun subAgentName(name: String): String =
    name.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(64).ifEmpty { "agent" }
