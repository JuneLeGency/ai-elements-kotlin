package dev.ai.elements.core.protocol.agui

import com.reidsync.kxjsonpatch.JsonPatch
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
import kotlinx.serialization.json.JsonElement
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

    /** Forgets [threadId]'s events, e.g. when its conversation is deleted. */
    suspend fun delete(threadId: String)

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

        override suspend fun delete(threadId: String) = lock.withLock { threads.remove(threadId); Unit }
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

        override suspend fun delete(threadId: String) = lock.withLock { withContext(Dispatchers.IO) { file(threadId).delete(); Unit } }

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
         * The events compacted as AG-UI's reference client does (`compactEvents` in `@ag-ui/client`,
         * AG-UI serialization §Compaction): each text message's and tool call's deltas become one
         * `…_CONTENT` / `TOOL_CALL_ARGS` event, events that arrived mid-stream follow the stream they
         * interrupted, and each run's state events become one `STATE_SNAPSHOT` from the last snapshot
         * on (deltas with no snapshot before them stay as they are). Collapsed events keep the
         * metadata of what they replace (merged key by key) and the time of the last one. Like the
         * reference, it reorders (a run's state moves to its end, mid-stream events after their
         * stream), so it suits archiving finished runs; keep the log as recorded for event-level replay.
         */
        fun compact(events: List<JsonObject>): List<JsonObject> {
            class Stream(var start: JsonObject? = null, var end: JsonObject? = null) {
                val deltas = mutableListOf<JsonObject>()
                val others = mutableListOf<JsonObject>()
                var postStartMetadata: JsonObject? = null
            }
            val out = mutableListOf<JsonObject>()
            val texts = linkedMapOf<String, Stream>()
            val calls = linkedMapOf<String, Stream>()
            var state = mutableListOf<JsonObject>()

            fun flush(idKey: String, id: String, deltaType: String, stream: Stream) {
                stream.start?.let { out += it }
                if (stream.deltas.isNotEmpty()) out += JsonObject(buildMap {
                    put("type", JsonPrimitive(deltaType))
                    put(idKey, JsonPrimitive(id))
                    put("delta", JsonPrimitive(stream.deltas.joinToString("") { it.str("delta").orEmpty() }))
                    stream.deltas.last()["timestamp"]?.let { put("timestamp", it) }
                    stream.postStartMetadata?.let { put("metadata", it) }
                })
                stream.end?.let { out += it }
                out += stream.others
            }

            fun flushState() {
                if (state.isEmpty()) return
                val last = state.indexOfLast { it.str("type") == "STATE_SNAPSHOT" }
                if (last < 0) { out += state; state = mutableListOf(); return }
                var doc: JsonElement = JsonObject(emptyMap())
                state.drop(last).forEach { e ->
                    doc = if (e.str("type") == "STATE_SNAPSHOT") e["snapshot"] ?: JsonObject(emptyMap())
                    else runCatching { JsonPatch.apply(e["delta"] ?: JsonArray(emptyList()), doc) }.getOrDefault(doc)
                }
                val metadata = state.fold(null as JsonObject?) { m, e -> mergeMetadata(m, e["metadata"] as? JsonObject) }
                out += JsonObject(buildMap {
                    put("type", JsonPrimitive("STATE_SNAPSHOT"))
                    put("snapshot", doc)
                    state.last()["timestamp"]?.let { put("timestamp", it) }
                    metadata?.let { put("metadata", it) }
                })
                state = mutableListOf()
            }

            fun start(streams: MutableMap<String, Stream>, id: String, event: JsonObject) {
                val stream = streams.getOrPut(id) { Stream() }
                if (stream.deltas.isEmpty()) {
                    // A start replayed before its deltas: both starts' metadata apply.
                    val merged = mergeMetadata(stream.start?.get("metadata") as? JsonObject, event["metadata"] as? JsonObject)
                    stream.start = if (merged == null) event else JsonObject(event + ("metadata" to merged))
                } else {
                    // Replayed after deltas: its fields win, its metadata rides the collapsed delta.
                    val carried = stream.start?.get("metadata")
                    stream.start = JsonObject(event - "metadata" + (carried?.let { mapOf("metadata" to it) } ?: emptyMap()))
                    stream.postStartMetadata = mergeMetadata(stream.postStartMetadata, event["metadata"] as? JsonObject)
                }
            }

            fun delta(streams: MutableMap<String, Stream>, id: String, event: JsonObject) {
                val stream = streams.getOrPut(id) { Stream() }
                stream.deltas += event
                stream.postStartMetadata = mergeMetadata(stream.postStartMetadata, event["metadata"] as? JsonObject)
            }

            events.forEach { event ->
                val messageId = event.str("messageId").orEmpty()
                val toolCallId = event.str("toolCallId").orEmpty()
                when (event.str("type")) {
                    "TEXT_MESSAGE_START" -> start(texts, messageId, event)
                    "TEXT_MESSAGE_CONTENT" -> delta(texts, messageId, event)
                    "TEXT_MESSAGE_END" -> texts.getOrPut(messageId) { Stream() }.let { it.end = event; flush("messageId", messageId, "TEXT_MESSAGE_CONTENT", it); texts.remove(messageId) }
                    "TOOL_CALL_START" -> start(calls, toolCallId, event)
                    "TOOL_CALL_ARGS" -> delta(calls, toolCallId, event)
                    "TOOL_CALL_END" -> calls.getOrPut(toolCallId) { Stream() }.let { it.end = event; flush("toolCallId", toolCallId, "TOOL_CALL_ARGS", it); calls.remove(toolCallId) }
                    "RUN_STARTED", "RUN_FINISHED", "RUN_ERROR" -> { flushState(); out += event }
                    "STATE_SNAPSHOT", "STATE_DELTA" -> state += event
                    else -> {
                        val open = texts.values.firstOrNull { it.start != null && it.end == null } ?: calls.values.firstOrNull { it.start != null && it.end == null }
                        if (open != null) open.others += event else out += event
                    }
                }
            }
            texts.forEach { (id, stream) -> flush("messageId", id, "TEXT_MESSAGE_CONTENT", stream) }
            calls.forEach { (id, stream) -> flush("toolCallId", id, "TOOL_CALL_ARGS", stream) }
            flushState()
            return out
        }

        /** AG-UI `mergeMetadata`: the incoming keys replace the existing ones. */
        private fun mergeMetadata(existing: JsonObject?, incoming: JsonObject?): JsonObject? = when {
            incoming == null -> existing
            existing == null -> incoming
            else -> JsonObject(existing + incoming)
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
