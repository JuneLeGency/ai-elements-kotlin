package dev.ai.elements.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallStatus
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.model.ApprovalAnswers
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/**
 * An [AcpAgent] as a chat provider: each conversation is one ACP session, each turn one
 * `session/prompt`.
 *
 * The session id is kept in the reply's [Message.metadata] under `acp`; the next turn continues
 * that session (the agent keeps the history), so only the new user message is sent. A
 * conversation whose session is gone and cannot be loaded starts a new one with the earlier turns
 * as context.
 *
 * Mapping (ACP `session/update`): `agent_message_chunk` → text (images → files, resource links →
 * sources), `agent_thought_chunk` → reasoning, `tool_call` / `tool_call_update` → tool calls
 * (`title` as the title, `kind` as the name, `content` — text, diffs, terminals — as the output,
 * `rawInput` as the input), `plan` → the `plan` data part. `session/request_permission` asks
 * [approver]: approve picks the agent's allow-once option, deny its reject-once option (ACP
 * answers carry an option id only, so the UI offers no reason or edits). Stopping the turn sends
 * `session/cancel`.
 */
class AcpBackend(
    private val agent: AcpAgent,
    private val approver: ToolApprover = ToolApprover.AlwaysApprove,
) : ChatBackend {

    override fun stream(history: List<Message>): Flow<ChatEvent> = channelFlow {
        val user = history.lastOrNull { it.role == Role.USER } ?: return@channelFlow
        val previous = history.lastOrNull { it.role == Role.ASSISTANT }?.metadata?.get(METADATA_KEY) as? JsonObject
        val previousId = (previous?.get("sessionId") as? JsonPrimitive)?.contentOrNull
        val session = try {
            agent.session(previousId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            agent.reset()
            throw e
        }
        val sessionId = session.sessionId.value
        send(ChatEvent.Metadata(buildJsonObject { putJsonObject(METADATA_KEY) { put("sessionId", sessionId) } }))

        val info = agent.info()
        val earlier = if (sessionId != previousId && history.count { it.role == Role.USER } > 1) transcript(history.dropLast(1)) else null
        val content = promptContent(user, images = info.capabilities.promptCapabilities.image, context = earlier)

        val mapper = Mapper()
        agent.turns[sessionId] = AcpTurn { toolCall, options -> permission(mapper, toolCall, options) }
        try {
            session.prompt(content).collect { event ->
                when (event) {
                    is Event.SessionUpdateEvent -> mapper.map(event.update).forEach { send(it) }
                    is Event.PromptResponseEvent -> mapper.finish(event.response).forEach { send(it) }
                }
            }
        } catch (e: CancellationException) {
            // Stopped by the user: tell the agent (ACP `session/cancel`), then stop.
            withContext(NonCancellable) { runCatching { session.cancel() } }
            throw e
        } catch (e: Throwable) {
            agent.reset()
            throw e
        } finally {
            agent.turns.remove(sessionId)
        }
        send(ChatEvent.Finish)
    }

    private suspend fun SendChannel<ChatEvent>.permission(
        mapper: Mapper,
        toolCall: SessionUpdate.ToolCallUpdate,
        options: List<PermissionOption>,
    ): RequestPermissionResponse {
        val id = toolCall.toolCallId.value
        mapper.map(toolCall).forEach { send(it) }
        send(ChatEvent.ToolApprovalRequest(id, ApprovalAnswers.YesNo))
        val decision = approver.decide(id)
        mapper.decided(id, decision.approved)
        send(if (decision.approved) ChatEvent.ToolApproved(id) else ChatEvent.ToolDenied(id, decision.reason))
        val choice = if (decision.approved) {
            options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ONCE } ?: options.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ALWAYS }
        } else {
            options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ONCE } ?: options.firstOrNull { it.kind == PermissionOptionKind.REJECT_ALWAYS }
        }
        return RequestPermissionResponse(choice?.let { RequestPermissionOutcome.Selected(it.optionId) } ?: RequestPermissionOutcome.Cancelled)
    }

    /** Stateful `session/update` → [ChatEvent] mapping for one turn. */
    internal class Mapper {
        private val turn = UUID.randomUUID().toString().take(8)
        private var segment = 0
        private var text: String? = null
        private var reasoning: String? = null
        private val tools = mutableMapOf<String, Tool>()
        private val denied = mutableSetOf<String>()

        private class Tool(var title: String, val name: String, var input: String, var output: String? = null)

        /** `rawInput` as JSON text; agents may send the arguments as an encoded JSON string. */
        private fun inputText(raw: JsonElement): String? = when {
            raw is JsonNull -> null
            raw is JsonPrimitive && raw.isString -> raw.content
            else -> raw.toString()
        }

        fun map(update: SessionUpdate): List<ChatEvent> = when (update) {
            is SessionUpdate.AgentMessageChunk -> message(update.content)
            is SessionUpdate.AgentThoughtChunk -> {
                val t = update.content as? ContentBlock.Text
                if (t == null) emptyList() else endText() + ChatEvent.ReasoningDelta(reasoning ?: "reasoning-$turn-${segment++}".also { reasoning = it }, t.text)
            }
            is SessionUpdate.ToolCall -> endAll() + tool(update.toolCallId.value, update.title, update.rawInput, update.status, update.content, update.rawOutput)
            is SessionUpdate.ToolCallUpdate -> endAll() + tool(update.toolCallId.value, update.title, update.rawInput, update.status, update.content, update.rawOutput)
            is SessionUpdate.PlanUpdate -> listOf(ChatEvent.Data("acp-plan-$turn", "plan", plan(update.entries)))
            else -> emptyList()
        }

        fun decided(id: String, approved: Boolean) {
            if (!approved) denied += id
        }

        @OptIn(UnstableApi::class)
        fun finish(response: PromptResponse): List<ChatEvent> = buildList {
            addAll(endAll())
            response.usage?.let { add(ChatEvent.Usage(it.inputTokens.toInt(), it.outputTokens.toInt())) }
            when (response.stopReason) {
                StopReason.REFUSAL -> add(ChatEvent.Error("The agent refused to continue."))
                StopReason.MAX_TOKENS -> add(ChatEvent.Error("The agent reached its token limit."))
                StopReason.MAX_TURN_REQUESTS -> add(ChatEvent.Error("The agent reached its request limit for this turn."))
                else -> Unit
            }
        }

        private fun message(block: ContentBlock): List<ChatEvent> = when (block) {
            is ContentBlock.Text -> endReasoning() + ChatEvent.TextDelta(text ?: "text-$turn-${segment++}".also { text = it }, block.text)
            is ContentBlock.Image -> endAll() + ChatEvent.File("file-$turn-${segment++}", block.mimeType, block.uri ?: "data:${block.mimeType};base64,${block.data}")
            is ContentBlock.ResourceLink -> listOf(ChatEvent.SourceUrl("source-$turn-${segment++}", block.uri, block.title ?: block.name))
            else -> emptyList()
        }

        // ACP tool calls have a title ("Reading main.kt") but no tool name: the first title names
        // the call, later ones retitle it.
        private fun tool(
            id: String,
            title: String?,
            rawInput: JsonElement?,
            status: ToolCallStatus?,
            content: List<ToolCallContent>?,
            rawOutput: JsonElement?,
        ): List<ChatEvent> = buildList {
            val known = tools[id]
            val tool = known ?: Tool(title ?: "tool", title ?: "tool", "{}").also { tools[id] = it }
            val input = rawInput?.let(::inputText)
            val changed = known == null || (title != null && title != tool.title) || (input != null && input != tool.input)
            title?.let { tool.title = it }
            input?.let { tool.input = it }
            if (changed) add(ChatEvent.ToolInputAvailable(id, tool.name, tool.input, title = tool.title))
            val output = content?.takeIf { it.isNotEmpty() }?.let(::render)
                ?: rawOutput?.takeIf { it !is JsonNull }?.let { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
            if (id in denied) return@buildList
            when (status) {
                ToolCallStatus.COMPLETED -> add(ChatEvent.ToolOutput(id, output ?: tool.output ?: ""))
                ToolCallStatus.FAILED -> add(ChatEvent.ToolError(id, output ?: tool.output ?: "Failed"))
                else -> if (output != null && output != tool.output) add(ChatEvent.ToolOutput(id, output, preliminary = true))
            }
            output?.let { tool.output = it }
        }

        private fun endText(): List<ChatEvent> = listOfNotNull(text?.let { ChatEvent.TextEnd(it) }).also { text = null }
        private fun endReasoning(): List<ChatEvent> = listOfNotNull(reasoning?.let { ChatEvent.ReasoningEnd(it) }).also { reasoning = null }
        private fun endAll(): List<ChatEvent> = endReasoning() + endText()
    }

    companion object {
        /** [Message.metadata] key holding `{sessionId}`. */
        const val METADATA_KEY = "acp"

        /** Tool call content as text: text blocks, unified diffs, terminal references. */
        internal fun render(content: List<ToolCallContent>): String = content.joinToString("\n\n") { item ->
            when (item) {
                is ToolCallContent.Content -> when (val block = item.content) {
                    is ContentBlock.Text -> block.text
                    is ContentBlock.ResourceLink -> block.uri
                    else -> ""
                }
                is ToolCallContent.Diff -> unifiedDiff(item.path, item.oldText, item.newText)
                is ToolCallContent.Terminal -> "terminal ${item.terminalId}"
            }
        }.trim()

        /** The ACP plan as the `plan` data part the Plan element renders. */
        internal fun plan(entries: List<PlanEntry>): JsonObject = buildJsonObject {
            val done = entries.count { it.status == PlanEntryStatus.COMPLETED }
            put("title", "Plan")
            put("description", "$done/${entries.size}")
            put("streaming", done < entries.size)
            putJsonArray("steps") {
                entries.forEach { entry ->
                    addJsonObject {
                        put("label", entry.content)
                        put("status", when (entry.status) {
                            PlanEntryStatus.COMPLETED -> "complete"
                            PlanEntryStatus.IN_PROGRESS -> "active"
                            PlanEntryStatus.PENDING -> "pending"
                        })
                    }
                }
            }
        }

        /** The user message as ACP content: text, and images when the agent accepts them. */
        internal fun promptContent(user: Message, images: Boolean, context: String? = null): List<ContentBlock> = buildList {
            context?.let { add(ContentBlock.Text(it)) }
            user.parts.forEach { part ->
                when (part) {
                    is TextPart -> if (part.text.isNotBlank()) add(ContentBlock.Text(part.text))
                    is FilePart -> {
                        val data = part.base64Data
                        if (images && part.isImage && data != null) add(ContentBlock.Image(data, part.mediaType))
                        else if (!part.url.startsWith("data:")) add(ContentBlock.ResourceLink(part.filename ?: part.url, part.url, mimeType = part.mediaType))
                    }
                    else -> Unit
                }
            }
        }

        /** Earlier turns for an agent that starts a new session mid-conversation. */
        internal fun transcript(history: List<Message>): String = buildString {
            append("Conversation so far:\n")
            history.filter { it.role == Role.USER || it.role == Role.ASSISTANT }.forEach { message ->
                val text = message.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }.trim()
                if (text.isNotEmpty()) append("\n").append(if (message.role == Role.USER) "User: " else "Assistant: ").append(text).append('\n')
            }
        }

        /** A line-based unified diff of [old] → [new] (whole file as one hunk). */
        internal fun unifiedDiff(path: String, old: String?, new: String): String {
            val a = old?.lines().orEmpty()
            val b = new.lines()
            val lines = if (a.size * b.size > 4_000_000) a.map { "-$it" } + b.map { "+$it" } else lcsDiff(a, b)
            return buildString {
                append("--- ").append(if (old == null) "/dev/null" else path).append('\n')
                append("+++ ").append(path).append('\n')
                append("@@ -1,").append(a.size).append(" +1,").append(b.size).append(" @@\n")
                lines.joinTo(this, "\n")
            }
        }

        private fun lcsDiff(a: List<String>, b: List<String>): List<String> {
            val n = a.size
            val m = b.size
            val lcs = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
            val out = ArrayList<String>(n + m)
            var i = 0
            var j = 0
            while (i < n && j < m) {
                when {
                    a[i] == b[j] -> { out += " ${a[i]}"; i++; j++ }
                    lcs[i + 1][j] >= lcs[i][j + 1] -> out += "-${a[i++]}"
                    else -> out += "+${b[j++]}"
                }
            }
            while (i < n) out += "-${a[i++]}"
            while (j < m) out += "+${b[j++]}"
            return out
        }
    }
}
