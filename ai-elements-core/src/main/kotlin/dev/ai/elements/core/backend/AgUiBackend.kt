package dev.ai.elements.core.backend

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.finishStreaming
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.core.reduce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.agui.core.types.ActivityDeltaEvent
import com.agui.core.types.ActivitySnapshotEvent
import com.agui.core.types.AgUiJson
import com.agui.core.types.BaseEvent
import com.agui.core.types.ReasoningEndEvent
import com.agui.core.types.ReasoningMessageChunkEvent
import com.agui.core.types.ReasoningMessageContentEvent
import com.agui.core.types.ReasoningMessageEndEvent
import com.agui.core.types.ReasoningMessageStartEvent
import com.agui.core.types.ReasoningStartEvent
import com.agui.core.types.RunErrorEvent
import com.agui.core.types.RunFinishedEvent
import com.agui.core.types.RunFinishedInterruptOutcome
import com.agui.core.types.StateDeltaEvent
import com.agui.core.types.StateSnapshotEvent
import com.agui.core.types.StepFinishedEvent
import com.agui.core.types.StepStartedEvent
import com.agui.core.types.TextMessageChunkEvent
import com.agui.core.types.TextMessageContentEvent
import com.agui.core.types.TextMessageEndEvent
import com.agui.core.types.TextMessageStartEvent
import com.agui.core.types.ThinkingEndEvent
import com.agui.core.types.ThinkingStartEvent
import com.agui.core.types.ThinkingTextMessageContentEvent
import com.agui.core.types.ThinkingTextMessageEndEvent
import com.agui.core.types.ThinkingTextMessageStartEvent
import com.agui.core.types.ToolCallArgsEvent
import com.agui.core.types.ToolCallChunkEvent
import com.agui.core.types.ToolCallEndEvent
import com.agui.core.types.ToolCallResultEvent
import com.agui.core.types.ToolCallStartEvent
import com.reidsync.kxjsonpatch.JsonPatch
import okhttp3.OkHttpClient
import java.util.UUID

/**
 * The [AG-UI](https://docs.ag-ui.com) protocol (1.x): `POST` a
 * `RunAgentInput`, receive the run's events over SSE. Served by Pydantic AI,
 * LangGraph, CrewAI, Mastra, and the bundled `server/main.py` at `/api/agui`.
 *
 * The agent runs server-side; this backend implements the client half of the
 * protocol:
 *
 * - **Frontend tools**: [tools] are advertised in `RunAgentInput.tools`. When
 *   the agent calls one, the run finishes with the call pending; the tool runs
 *   here (after [approver] if it needs approval) and a follow-up run carries
 *   the `tool` message.
 * - **Interrupts** (human in the loop): a run finishing with
 *   `outcome: interrupt` for a tool call asks [approver], then resumes with
 *   `RunAgentInput.resume`.
 * - **Subagents**: `SUBAGENT_STARTED` and the events carrying its
 *   `subagentRunId` are folded into the delegating tool call's nested run.
 * - **Shared state**: `STATE_SNAPSHOT` / `STATE_DELTA` (JSON Patch) become a
 *   `state` data part, sent back as `RunAgentInput.state` on the next turn;
 *   `ACTIVITY_*` become data parts named by their `activityType`; steps a
 *   chain-of-thought part.
 * - **Context**: [context] entries (e.g. capability instructions) go into
 *   `RunAgentInput.context`.
 */
class AgUiBackend(
    private val endpoint: String,
    private val apiKey: String = "",
    private val tools: List<AgentTool> = emptyList(),
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
    private val context: List<Pair<String, String>> = emptyList(),
    private val threadId: String? = null,
    private val client: OkHttpClient = DefaultHttpClient,
    private val maxRuns: Int = 8,
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = flow {
        val thread = threadId ?: history.firstOrNull()?.id ?: UUID.randomUUID().toString()
        val messages = history.flatMap { it.toAgUi() }.toMutableList()
        var state: JsonElement = history.lastState() ?: JsonObject(emptyMap())
        var resume: List<JsonObject>? = null
        val parser = AgUiParser()
        val headers = mapOf("Authorization" to if (apiKey.isBlank()) "" else "Bearer $apiKey")

        repeat(maxRuns) {
            parser.startRun()
            val body = buildJsonObject {
                put("threadId", thread)
                put("runId", UUID.randomUUID().toString())
                put("protocolVersion", PROTOCOL_VERSION)
                put("state", state)
                put("messages", JsonArray(messages))
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        }
                    }
                }
                putJsonArray("context") {
                    context.forEach { (description, value) -> addJsonObject { put("description", description); put("value", value) } }
                }
                putJsonObject("forwardedProps") {}
                resume?.let { put("resume", JsonArray(it)) }
            }
            client.sse(jsonPost(endpoint, body, headers)).collect { sse ->
                val event = runCatching { BackendJson.parseToJsonElement(sse.data).jsonObject }.getOrNull() ?: return@collect
                parser.parse(event).forEach { emit(it) }
            }
            if (parser.failed) return@flow
            parser.state?.let { state = it }
            messages += parser.transcript()
            resume = null

            val interrupts = parser.interrupts
            if (interrupts.isNotEmpty()) {
                resume = interrupts.map { interrupt -> resolve(interrupt) }
                return@repeat
            }
            val pending = parser.pendingCalls(tools.map { it.name }.toSet())
            if (pending.isNotEmpty()) {
                pending.forEach { call ->
                    val result = runTool(tools, approver, call.id, call.name, call.args.toString())
                    messages += buildJsonObject {
                        put("id", "result-${call.id}")
                        put("role", "tool")
                        put("toolCallId", call.id)
                        put("content", result)
                    }
                }
                return@repeat
            }
            emit(ChatEvent.Finish)
            return@flow
        }
        emit(ChatEvent.Error("Agent stopped after $maxRuns runs"))
    }

    /** Answer one interrupt: tool-call approvals go to [approver]; others are cancelled (not supported). */
    private suspend fun FlowCollector<ChatEvent>.resolve(interrupt: AgUiParser.Interrupt): JsonObject {
        val toolCallId = interrupt.toolCallId
        if (toolCallId == null) {
            emit(ChatEvent.Error(interrupt.message ?: "The agent asked for input this app cannot provide (${interrupt.reason})."))
            return buildJsonObject { put("interruptId", interrupt.id); put("status", "cancelled") }
        }
        emit(ChatEvent.ToolApprovalRequest(toolCallId))
        val approved = approver.approve(toolCallId)
        emit(if (approved) ChatEvent.ToolApproved(toolCallId) else ChatEvent.ToolDenied(toolCallId))
        return buildJsonObject {
            put("interruptId", interrupt.id)
            put("status", "resolved")
            putJsonObject("payload") {
                put("approved", approved)
                if (!approved) put("reason", "The user denied this tool call.")
            }
        }
    }

    companion object {
        const val PROTOCOL_VERSION = "1.0.0"

        /** Name of the data part holding the agent's shared state (`STATE_SNAPSHOT`). */
        const val STATE_PART = "state"
    }
}

/** The latest shared AG-UI state recorded in the conversation. */
private fun List<Message>.lastState(): JsonElement? =
    asReversed().firstNotNullOfOrNull { m -> m.parts.lastOrNull { it is DataPart && it.name == AgUiBackend.STATE_PART }?.let { (it as DataPart).data } }

/** One chat message as AG-UI messages: user content, assistant text + tool calls, then tool results. */
internal fun Message.toAgUi(): List<JsonObject> = when (role) {
    Role.USER -> if (!hasContent) emptyList() else listOf(buildJsonObject {
        put("id", id)
        put("role", "user")
        if (images.isEmpty()) put("content", text) else putJsonArray("content") {
            if (text.isNotBlank()) addJsonObject { put("type", "text"); put("text", text) }
            images.forEach { image ->
                addJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        val data = image.base64Data
                        if (data != null) { put("type", "data"); put("value", data) } else { put("type", "url"); put("value", image.url) }
                        put("mimeType", image.mediaType)
                    }
                }
            }
        }
    })
    Role.ASSISTANT -> {
        val calls = parts.filterIsInstance<ToolPart>().filter { it.state != ToolState.INPUT_STREAMING }
        val content = parts.filterIsInstance<TextPart>().joinToString("\n\n") { it.text }
        if (content.isBlank() && calls.isEmpty()) emptyList() else buildList {
            add(buildJsonObject {
                put("id", id)
                put("role", "assistant")
                if (content.isNotBlank()) put("content", content)
                if (calls.isNotEmpty()) putJsonArray("toolCalls") {
                    calls.forEach { call ->
                        addJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.input.ifBlank { "{}" })
                            }
                        }
                    }
                }
            })
            calls.forEach { call ->
                val result = when (call.state) {
                    ToolState.OUTPUT_AVAILABLE -> call.output
                    ToolState.OUTPUT_ERROR -> call.errorText?.let { "Error: $it" }
                    ToolState.OUTPUT_DENIED -> DENIED_RESULT
                    else -> null
                } ?: return@forEach
                add(buildJsonObject {
                    put("id", "result-${call.id}")
                    put("role", "tool")
                    put("toolCallId", call.id)
                    put("content", result)
                    if (call.state == ToolState.OUTPUT_ERROR) put("error", call.errorText.orEmpty())
                })
            }
        }
    }
}

/**
 * Stateful AG-UI event → [ChatEvent] mapping. One parser spans every run of
 * a turn (follow-up runs after frontend tools or interrupts), so subagent
 * runs, steps and state carry over; [startRun] resets what is per run.
 */
internal class AgUiParser(private val nested: Boolean = false) {
    data class Interrupt(val id: String, val reason: String, val toolCallId: String?, val message: String?)
    data class Call(val id: String, var name: String, val args: StringBuilder = StringBuilder(), var parentMessageId: String? = null)

    private val calls = linkedMapOf<String, Call>()
    private val results = mutableSetOf<String>()
    private var lastTextId = "text"
    private var lastReasoningId = "reasoning"

    /** Transcript of the current run, replayed as `messages` in follow-up runs. */
    private val transcript = mutableListOf<Pair<String, JsonObject>>()
    private val texts = linkedMapOf<String, StringBuilder>()

    var state: JsonElement? = null
        private set
    var interrupts: List<Interrupt> = emptyList()
        private set
    private var pendingIds: List<String> = emptyList()
    var failed = false
        private set

    private val steps = mutableListOf<Pair<String, Boolean>>()
    private val activities = mutableMapOf<String, JsonElement>()
    private val subagents = linkedMapOf<String, Subagent>()

    private class Subagent(val toolCallId: String, val name: String, val synthetic: Boolean, val parser: AgUiParser, var message: Message)

    fun startRun() {
        calls.clear(); results.clear(); transcript.clear(); texts.clear()
        interrupts = emptyList(); pendingIds = emptyList()
    }

    /** Frontend tool calls of this run still waiting for a result from the client. */
    fun pendingCalls(local: Set<String>): List<Call> =
        calls.values.filter { it.id !in results && it.name in local && (pendingIds.isEmpty() || it.id in pendingIds) }

    fun transcript(): List<JsonObject> = transcript.map { (id, message) ->
        val text = texts[id]?.toString()
        val toolCalls = calls.values.filter { it.parentMessageId == id }
        if (message["role"]?.let { (it as JsonPrimitive).content } != "assistant") message
        else buildJsonObject {
            put("id", id)
            put("role", "assistant")
            if (!text.isNullOrEmpty()) put("content", text)
            if (toolCalls.isNotEmpty()) put("toolCalls", buildJsonArray {
                toolCalls.forEach { c ->
                    addJsonObject {
                        put("id", c.id)
                        put("type", "function")
                        putJsonObject("function") { put("name", c.name); put("arguments", c.args.toString().ifBlank { "{}" }) }
                    }
                }
            })
        }
    }

    /** Whether [subagentRunId] is one of this parser's subagents (at any depth). */
    fun owns(subagentRunId: String): Boolean = subagentRunId in subagents || subagents.values.any { it.parser.owns(subagentRunId) }

    fun parse(event: JsonObject): List<ChatEvent> {
        val type = event.str("type")
        // Route subagent traffic to the subagent's own parser.
        if (type == "SUBAGENT_STARTED") event.str("parentSubagentRunId")?.let { parent -> return route(parent, event, "parentSubagentRunId") }
        event.str("subagentRunId")?.takeIf { type !in SUBAGENT_LIFECYCLE }?.let { return route(it, event, "subagentRunId") }

        when (type) {
            "SUBAGENT_STARTED" -> return startSubagent(event)
            "SUBAGENT_FINISHED", "SUBAGENT_ERROR" -> {
                val id = event.str("subagentRunId").orEmpty()
                return subagents[id]?.let { finishSubagent(it, event, type == "SUBAGENT_ERROR") } ?: route(id, event, strip = null)
            }
            // AG-UI 1.x tool results may be content parts; kotlin-core 0.4 only models strings.
            "TOOL_CALL_RESULT" -> if (event["content"] is JsonArray) return toolResult(event.str("toolCallId").orEmpty(), event.str("messageId"), event["content"].asText())
        }
        // Everything else is decoded into the official AG-UI Kotlin SDK types.
        val typed = runCatching { AgUiJson.decodeFromJsonElement(BaseEvent.serializer(), event) }.getOrNull() ?: return emptyList()
        return when (typed) {
            is TextMessageStartEvent -> { lastTextId = typed.messageId; assistant(lastTextId); emptyList() }
            is TextMessageContentEvent -> text(typed.messageId, typed.delta)
            is TextMessageChunkEvent -> text(typed.messageId ?: lastTextId, typed.delta.orEmpty())
            is TextMessageEndEvent -> listOf(ChatEvent.TextEnd(typed.messageId))
            is ReasoningStartEvent -> reasoningStart(typed.messageId)
            is ReasoningMessageStartEvent -> reasoningStart(typed.messageId)
            is ThinkingStartEvent, is ThinkingTextMessageStartEvent -> reasoningStart(null)
            is ReasoningMessageContentEvent -> listOf(ChatEvent.ReasoningDelta(typed.messageId, typed.delta))
            is ReasoningMessageChunkEvent -> listOf(ChatEvent.ReasoningDelta(typed.messageId ?: lastReasoningId, typed.delta.orEmpty()))
            is ThinkingTextMessageContentEvent -> listOf(ChatEvent.ReasoningDelta(lastReasoningId, typed.delta))
            is ReasoningMessageEndEvent -> listOf(ChatEvent.ReasoningEnd(typed.messageId))
            is ReasoningEndEvent -> listOf(ChatEvent.ReasoningEnd(typed.messageId))
            is ThinkingTextMessageEndEvent, is ThinkingEndEvent -> listOf(ChatEvent.ReasoningEnd(lastReasoningId))
            is ToolCallStartEvent -> toolStart(typed.toolCallId, typed.toolCallName, typed.parentMessageId)
            is ToolCallArgsEvent -> toolArgs(typed.toolCallId, null, typed.delta)
            is ToolCallChunkEvent -> {
                val id = typed.toolCallId ?: calls.keys.lastOrNull() ?: return emptyList()
                val start = if (id !in calls && typed.toolCallName != null) toolStart(id, typed.toolCallName!!, typed.parentMessageId) else emptyList()
                start + toolArgs(id, typed.toolCallName, typed.delta.orEmpty())
            }
            is ToolCallEndEvent -> {
                val call = calls[typed.toolCallId]
                listOf(ChatEvent.ToolInputAvailable(typed.toolCallId, call?.name.orEmpty(), call?.args?.toString().orEmpty().ifBlank { "{}" }))
            }
            is ToolCallResultEvent -> toolResult(typed.toolCallId, typed.messageId, typed.content)
            is StateSnapshotEvent -> {
                state = typed.snapshot
                listOf(ChatEvent.Data(AgUiBackend.STATE_PART, AgUiBackend.STATE_PART, typed.snapshot))
            }
            is StateDeltaEvent -> {
                state = runCatching { JsonPatch.apply(typed.delta, state ?: JsonObject(emptyMap())) }.getOrElse { return emptyList() }
                listOf(ChatEvent.Data(AgUiBackend.STATE_PART, AgUiBackend.STATE_PART, state!!))
            }
            is ActivitySnapshotEvent -> {
                activities[typed.messageId] = typed.content
                listOf(ChatEvent.Data(typed.messageId, typed.activityType, typed.content))
            }
            is ActivityDeltaEvent -> {
                val content = runCatching { JsonPatch.apply(typed.patch, activities[typed.messageId] ?: JsonObject(emptyMap())) }.getOrElse { return emptyList() }
                activities[typed.messageId] = content
                listOf(ChatEvent.Data(typed.messageId, typed.activityType, content))
            }
            is StepStartedEvent -> { steps += (typed.stepName to false); listOf(stepsEvent()) }
            is StepFinishedEvent -> {
                val i = steps.indexOfLast { it.first == typed.stepName && !it.second }
                if (i >= 0) steps[i] = typed.stepName to true
                listOf(stepsEvent())
            }
            is RunErrorEvent -> { failed = true; usage(event) + ChatEvent.Error(typed.message) }
            is RunFinishedEvent -> {
                when (val outcome = typed.outcome) {
                    is RunFinishedInterruptOutcome -> interrupts = outcome.interrupts.map { Interrupt(it.id, it.reason, it.toolCallId, it.message) }
                    else -> Unit
                }
                // Not yet in kotlin-core: RUN_FINISHED.outcome.pendingToolCallIds and RUN_FINISHED.usage.
                pendingIds = ((event["outcome"] as? JsonObject)?.get("pendingToolCallIds") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                usage(event)
            }
            else -> emptyList()
        }
    }

    private fun text(messageId: String, delta: String): List<ChatEvent> {
        lastTextId = messageId
        assistant(messageId)
        texts.getOrPut(messageId) { StringBuilder() }.append(delta)
        return if (delta.isEmpty()) emptyList() else listOf(ChatEvent.TextDelta(messageId, delta))
    }

    private fun reasoningStart(messageId: String?): List<ChatEvent> {
        lastReasoningId = messageId ?: "reasoning-${UUID.randomUUID()}"
        return emptyList()
    }

    private fun toolStart(id: String, name: String, parentMessageId: String?): List<ChatEvent> {
        val parent = parentMessageId ?: lastTextId
        assistant(parent)
        calls[id] = Call(id, name, parentMessageId = parent)
        return listOf(ChatEvent.ToolInputStart(id, name))
    }

    private fun toolArgs(id: String, name: String?, delta: String): List<ChatEvent> {
        calls.getOrPut(id) { Call(id, name.orEmpty(), parentMessageId = lastTextId) }.args.append(delta)
        return if (delta.isEmpty()) emptyList() else listOf(ChatEvent.ToolInputDelta(id, delta))
    }

    private fun toolResult(toolCallId: String, messageId: String?, content: String): List<ChatEvent> {
        results += toolCallId
        val id = messageId ?: "result-$toolCallId"
        transcript += id to buildJsonObject {
            put("id", id)
            put("role", "tool")
            put("toolCallId", toolCallId)
            put("content", content)
        }
        val sub = subagents.values.firstOrNull { it.toolCallId == toolCallId && !it.synthetic }
        return listOfNotNull(
            ChatEvent.ToolOutput(toolCallId, content),
            sub?.let { ChatEvent.SubagentUpdate(toolCallId, it.message.finishStreaming(System.currentTimeMillis())) },
        )
    }

    private fun assistant(id: String) {
        if (nested || transcript.any { it.first == id }) return
        transcript += id to buildJsonObject { put("id", id); put("role", "assistant") }
    }

    private fun startSubagent(event: JsonObject): List<ChatEvent> {
        val runId = event.str("subagentRunId") ?: return emptyList()
        val name = event.str("name") ?: "agent"
        val parentCall = event.str("parentToolCallId")
        val toolCallId = parentCall ?: runId
        val sub = Subagent(toolCallId, name, synthetic = parentCall == null, AgUiParser(nested = true), Message(runId, Role.ASSISTANT))
        subagents[runId] = sub
        return buildList {
            if (sub.synthetic) {
                // No tool call delegated it: show the subagent as its own delegation.
                val input = buildJsonObject { put("agent_name", name); event.str("description")?.let { put("task", it) } }.toString()
                add(ChatEvent.ToolInputStart(toolCallId, DELEGATE_TOOL, name))
                add(ChatEvent.ToolInputAvailable(toolCallId, DELEGATE_TOOL, input, name))
            }
            add(ChatEvent.SubagentUpdate(toolCallId, sub.message))
        }
    }

    private fun finishSubagent(sub: Subagent, event: JsonObject, error: Boolean): List<ChatEvent> {
        sub.message = sub.message.finishStreaming(System.currentTimeMillis())
        return buildList {
            add(ChatEvent.SubagentUpdate(sub.toolCallId, sub.message))
            if (sub.synthetic) {
                if (error) add(ChatEvent.ToolError(sub.toolCallId, event.str("message") ?: "${sub.name} failed"))
                else add(ChatEvent.ToolOutput(sub.toolCallId, event["result"]?.asText()?.ifEmpty { null } ?: sub.message.text))
            }
        }
    }

    /** Hand an event to the subagent that owns [runId]; its output updates the delegating tool call. */
    private fun route(runId: String, event: JsonObject, strip: String?): List<ChatEvent> {
        val sub = subagents[runId] ?: subagents.values.firstOrNull { it.parser.owns(runId) } ?: return emptyList()
        // For the subagent itself the event is top-level; deeper ones keep their attribution.
        val inner = if (subagents[runId] != null && strip != null) JsonObject(event - strip) else event
        val events = sub.parser.parse(inner)
        if (events.isEmpty()) return emptyList()
        events.forEach { if (it !is ChatEvent.Error) sub.message = sub.message.reduce(it, System.currentTimeMillis()) }
        return listOf(ChatEvent.SubagentUpdate(sub.toolCallId, sub.message))
    }

    private fun stepsEvent() = ChatEvent.Data(
        "steps",
        "chain-of-thought",
        buildJsonObject {
            put("title", "Steps")
            putJsonArray("steps") {
                steps.forEach { (name, done) -> addJsonObject { put("label", name); put("status", if (done) "complete" else "active") } }
            }
        },
    )

    private fun usage(event: JsonObject): List<ChatEvent> {
        val usage = (event["usage"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return emptyList()
        if (usage.isEmpty()) return emptyList()
        return listOf(ChatEvent.Usage(usage.sumOf { it.int("inputTokens") ?: 0 }, usage.sumOf { it.int("outputTokens") ?: 0 }))
    }

    private companion object {
        val SUBAGENT_LIFECYCLE = setOf("SUBAGENT_STARTED", "SUBAGENT_FINISHED", "SUBAGENT_ERROR")

        /** The Pydantic AI Harness delegate tool; a subagent without a parent tool call is shown as one. */
        const val DELEGATE_TOOL = "delegate_task"

        fun JsonElement?.asText(): String = when (this) {
            null -> ""
            is JsonPrimitive -> contentOrNull.orEmpty()
            // 1.x tool results may be content parts: keep the text.
            is JsonArray -> mapNotNull { (it as? JsonObject)?.str("text") }.joinToString("\n").ifEmpty { toString() }
            else -> toString()
        }
    }
}
