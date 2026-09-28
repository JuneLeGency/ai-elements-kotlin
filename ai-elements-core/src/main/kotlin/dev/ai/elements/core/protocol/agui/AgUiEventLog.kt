package dev.ai.elements.core.protocol.agui

import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.http.BackendJson
import dev.ai.elements.core.http.str
import dev.ai.elements.core.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * A thread's AG-UI events, as AG-UI [serialization](https://docs.ag-ui.com/concepts/serialization)
 * specifies: an append-only log of the events exactly as they were streamed, per `threadId`,
 * runs linked by `RUN_STARTED.parentRunId`, stored as a JSON array of events. [AgUiBackend]
 * appends every run to it; [replay] plays runs back through the same event parser.
 *
 * Events without a `timestamp` get the time they arrived (the optional `BaseEvent.timestamp`,
 * milliseconds since the epoch), so a replay keeps the run's pace.
 */
interface AgUiEventLog {
    suspend fun append(threadId: String, events: List<JsonObject>)

    suspend fun load(threadId: String): List<JsonObject>

    /**
     * The recorded run of [message] (the runs named in its metadata, see [runsOf]) played back
     * through the event parser, or null when this log has no runs for it. For `Chat(replay = …)`.
     */
    fun replayOf(message: Message, speed: Float = 1f): Flow<ChatEvent>? {
        val (thread, runs) = runsOf(message)?.takeIf { it.second.isNotEmpty() } ?: return null
        return flow { emitAll(replay(eventsOf(load(thread), runs), speed)) }
    }

    /** Kept in memory for the life of the process. */
    class InMemory : AgUiEventLog {
        private val threads = mutableMapOf<String, List<JsonObject>>()
        private val lock = Mutex()

        override suspend fun append(threadId: String, events: List<JsonObject>) = lock.withLock { threads[threadId] = threads[threadId].orEmpty() + events }

        override suspend fun load(threadId: String): List<JsonObject> = lock.withLock { threads[threadId].orEmpty() }
    }

    /** One JSON file per thread in [directory] (`<threadId>.json`, a JSON array of events). */
    class Files(private val directory: File) : AgUiEventLog {
        private val lock = Mutex()

        override suspend fun append(threadId: String, events: List<JsonObject>) = lock.withLock {
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                val file = file(threadId)
                file.writeText(JsonArray(read(file) + events).toString())
            }
        }

        override suspend fun load(threadId: String): List<JsonObject> = lock.withLock { withContext(Dispatchers.IO) { read(file(threadId)) } }

        private fun file(threadId: String) = File(directory, threadId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

        private fun read(file: File): List<JsonObject> =
            if (!file.exists()) emptyList() else runCatching { BackendJson.parseToJsonElement(file.readText()).jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())
    }

    companion object {
        /** [Message.metadata] key holding `{threadId, runIds}`: the runs that produced the reply. */
        const val METADATA_KEY = "agui"

        /** The thread and runs [message] came from, when an [AgUiBackend] with a log produced it. */
        fun runsOf(message: Message): Pair<String, List<String>>? {
            val meta = message.metadata?.get(METADATA_KEY) as? JsonObject ?: return null
            val thread = (meta["threadId"] as? JsonPrimitive)?.contentOrNull ?: return null
            val runs = (meta["runIds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            return thread to runs
        }

        /** The events of [runIds] in [events], in log order. */
        fun eventsOf(events: List<JsonObject>, runIds: Collection<String>): List<JsonObject> {
            var inRun = false
            return events.filter { event ->
                if (event.str("type") == "RUN_STARTED") inRun = event.str("runId") in runIds
                inRun
            }
        }

        /**
         * Plays [events] (one reply's runs, see [eventsOf]) back through the AG-UI event parser,
         * pausing between events as long as the run did ([speed] 2 is twice as fast; gaps are
         * capped at [maxGapMs] so waiting for a model does not stall the replay).
         */
        fun replay(events: List<JsonObject>, speed: Float = 1f, maxGapMs: Long = 1_500): Flow<ChatEvent> = flow {
            val parser = AgUiParser()
            var last: Long? = null
            events.forEach { event ->
                val at = (event["timestamp"] as? JsonPrimitive)?.longOrNull
                if (at != null && last != null) {
                    val gap = ((at - last!!).coerceIn(0, maxGapMs) / speed.coerceAtLeast(0.01f)).toLong()
                    if (gap > 0) delay(gap)
                }
                if (at != null) last = at
                val runStarted = event.str("type") == "RUN_STARTED"
                if (runStarted) {
                    parser.startRun()
                    inputEvents(event).forEach { emit(it) }
                }
                parser.parse(event).forEach { emit(it) }
            }
            emit(ChatEvent.Finish)
        }

        /**
         * What the client added to the run (`RUN_STARTED.input.messages`): frontend tool results,
         * which never appear in the server's events.
         */
        private fun inputEvents(runStarted: JsonObject): List<ChatEvent> {
            val messages = ((runStarted["input"] as? JsonObject)?.get("messages") as? JsonArray).orEmpty()
            return messages.mapNotNull { element ->
                val message = element as? JsonObject ?: return@mapNotNull null
                val id = message.str("toolCallId")?.takeIf { message.str("role") == "tool" } ?: return@mapNotNull null
                message.str("error")?.let { ChatEvent.ToolError(id, it) } ?: ChatEvent.ToolOutput(id, message.str("content").orEmpty())
            }
        }
    }
}

/** [event] with its arrival time as `timestamp` when the producer sent none. */
internal fun stamped(event: JsonObject, now: Long): JsonObject =
    if ((event["timestamp"] as? JsonPrimitive)?.longOrNull != null) event else JsonObject(event + ("timestamp" to JsonPrimitive(now)))
