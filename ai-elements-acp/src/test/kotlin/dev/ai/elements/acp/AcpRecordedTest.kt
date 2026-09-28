package dev.ai.elements.acp

import com.agentclientprotocol.rpc.JsonRpcMessage
import com.agentclientprotocol.rpc.JsonRpcNotification
import com.agentclientprotocol.rpc.JsonRpcRequest
import com.agentclientprotocol.rpc.JsonRpcResponse
import com.agentclientprotocol.rpc.RequestId
import com.agentclientprotocol.rpc.decodeJsonRpcMessage
import com.agentclientprotocol.transport.BaseTransport
import com.agentclientprotocol.transport.Transport
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.chat.ToolDecision
import dev.ai.elements.core.chat.reduce
import dev.ai.elements.core.model.ApprovalAnswers
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Agent Client Protocol sessions recorded from the Pydantic AI Harness ACP adapter (driven by the
 * official ACP Python SDK client, `server/record_fixtures.py … acp`), replayed to the official ACP
 * Kotlin SDK client under [AcpBackend].
 */
class AcpRecordedTest {

    @Test fun plan_becomesThePlanPartAndAToolCall() = runBlocking {
        val (events, _) = replay("plan")
        val message = fold(events)
        val tool = message.parts.filterIsInstance<ToolPart>().single()
        assertEquals("write_plan", tool.name)
        assertEquals(ToolState.OUTPUT_AVAILABLE, tool.state)
        assertTrue(tool.input.contains("Read the question"))
        val plan = message.parts.filterIsInstance<DataPart>().single { it.name == "plan" }.data as JsonObject
        val steps = plan["steps"]!!.jsonArray.map { it.jsonObject["status"]!!.jsonPrimitive.content }
        assertEquals(listOf("complete", "active", "pending"), steps)
        assertTrue(message.parts.filterIsInstance<TextPart>().joinToString("") { it.text }.isNotBlank())
        assertEquals(100, message.usage?.inputTokens)
        val sessionId = ((message.metadata!!["acp"] as JsonObject)["sessionId"])!!.jsonPrimitive.content
        assertTrue(sessionId.isNotBlank())
    }

    @Test fun approval_approvedPicksAllowOnceAndRunsTheTool() = runBlocking {
        val asked = mutableListOf<String>()
        val (events, sent) = replay("note", approver = object : ToolApprover {
            override suspend fun approve(toolCallId: String) = true.also { asked += toolCallId }
        })
        val id = asked.single()
        assertTrue(events.contains(ChatEvent.ToolApprovalRequest(id, ApprovalAnswers.YesNo)))
        assertTrue(events.contains(ChatEvent.ToolApproved(id)))
        val tool = fold(events).parts.filterIsInstance<ToolPart>().single()
        assertEquals("save_note", tool.name)
        assertEquals(ToolState.OUTPUT_AVAILABLE, tool.state)
        assertTrue(tool.output!!.startsWith("Saved note"))
        assertEquals("allow_once", selectedOption(sent))
    }

    @Test fun approval_rememberedPicksAllowAlways() = runBlocking {
        val (_, sent) = replay("note", approver = object : ToolApprover {
            override suspend fun approve(toolCallId: String) = true
            override suspend fun decide(toolCallId: String) = ToolDecision(true, remember = true)
        })
        assertEquals("allow_always", selectedOption(sent))
    }

    @Test fun approval_deniedPicksRejectOnceAndStaysDenied() = runBlocking {
        val (events, sent) = replay("note-denied", approver = object : ToolApprover {
            override suspend fun approve(toolCallId: String) = false
            override suspend fun decide(toolCallId: String) = ToolDecision(false, reason = "Not now")
        })
        val tool = fold(events).parts.filterIsInstance<ToolPart>().single()
        assertEquals(ToolState.OUTPUT_DENIED, tool.state)
        assertEquals("Not now", tool.errorText)
        assertFalse(events.any { it is ChatEvent.ToolError })
        assertEquals("reject_once", selectedOption(sent))
    }

    @Test fun kindAndLocations_becomeCategoryAndLocation() = runBlocking {
        val (events, _) = replay("read")
        val tool = fold(events).parts.filterIsInstance<ToolPart>().single()
        assertEquals(ToolCategory.READ, tool.category)
        assertEquals("/tmp/README.md", tool.location)
        assertEquals(ToolState.OUTPUT_AVAILABLE, tool.state)
    }

    /** `session/load` replays the session; each reply's turn is found by its metadata and mapped as when it streamed. */
    @Test fun replay_loadsTheSessionAndPlaysTheReplysTurn() = runBlocking {
        val lines = javaClass.getResource("/acp/load.jsonl")!!.readText().lines().filter { it.isNotBlank() }
        val sessionId = lines.firstNotNullOf { Regex("\"sessionId\": \"([0-9a-f]+)\"").find(it)?.groupValues?.get(1) }
        fun reply(turn: Int) = Message("a$turn", Role.ASSISTANT, metadata = buildJsonObject {
            putJsonObject(AcpBackend.METADATA_KEY) { put("sessionId", sessionId); put("turn", turn) }
        })
        suspend fun replay(turn: Int): Message {
            val agent = AcpAgent("recorded", cwd = "/tmp") { ReplayTransport(lines) }
            val events = AcpBackend(agent).replayOf(reply(turn), speed = 1_000f)!!.toList()
            agent.close()
            return fold(events)
        }
        val first = replay(0)
        val read = first.parts.filterIsInstance<ToolPart>().single()
        assertEquals("read_file", read.name)
        assertEquals(ToolCategory.READ, read.category)
        assertEquals(ToolState.OUTPUT_AVAILABLE, read.state)
        assertTrue(first.parts.filterIsInstance<TextPart>().joinToString("") { it.text }.isNotBlank())
        assertEquals("get_current_time", replay(1).parts.filterIsInstance<ToolPart>().single().name)
        assertEquals(null, AcpBackend(AcpAgent("x", cwd = "/tmp") { ReplayTransport(lines) }).replayOf(Message("m", Role.ASSISTANT)))
    }

    @Test fun diff_isAUnifiedDiff() {
        val diff = AcpBackend.unifiedDiff("/a.kt", "one\ntwo\nthree", "one\n2\nthree")
        assertEquals("--- /a.kt\n+++ /a.kt\n@@ -1,3 +1,3 @@\n one\n-two\n+2\n three", diff)
        assertTrue(AcpBackend.unifiedDiff("/new.kt", null, "x").startsWith("--- /dev/null\n+++ /new.kt"))
    }

    @Test fun newSessionMidConversation_sendsEarlierTurnsAsContext() {
        val history = listOf(
            Message("u1", Role.USER, listOf(TextPart("t1", "hi"))),
            Message("a1", Role.ASSISTANT, listOf(TextPart("t2", "hello"))),
        )
        assertEquals("Conversation so far:\n\nUser: hi\n\nAssistant: hello\n", AcpBackend.transcript(history))
    }

    private suspend fun replay(name: String, approver: ToolApprover = ToolApprover.AlwaysApprove): Pair<List<ChatEvent>, List<JsonRpcMessage>> {
        val transport = ReplayTransport(javaClass.getResource("/acp/$name.jsonl")!!.readText().lines().filter { it.isNotBlank() })
        val agent = AcpAgent("recorded", cwd = "/tmp") { transport }
        val user = Message("u1", Role.USER, listOf(TextPart("t1", "please $name")))
        val events = AcpBackend(agent, approver).stream(listOf(user)).toList()
        agent.close()
        return events to transport.sent
    }

    private fun fold(events: List<ChatEvent>): Message =
        events.fold(Message("a1", Role.ASSISTANT, emptyList())) { m, e -> m.reduce(e, 0) }

    private fun selectedOption(sent: List<JsonRpcMessage>): String =
        sent.filterIsInstance<JsonRpcResponse>().single().result!!.jsonObject["outcome"]!!.jsonObject["optionId"]!!.jsonPrimitive.content

    /**
     * Plays the agent's side of a recording: each client request is answered by the next recorded
     * response (with the client's id); notifications and agent requests before it are sent first,
     * and an agent request waits for the client's answer.
     */
    private class ReplayTransport(recording: List<String>) : BaseTransport() {
        private val script = ArrayDeque(recording.map(::decodeJsonRpcMessage))
        private val executor = Executors.newSingleThreadExecutor()
        private var pending: RequestId? = null
        val sent = mutableListOf<JsonRpcMessage>()

        override fun start() {
            _state.value = Transport.State.STARTED
        }

        override fun send(message: JsonRpcMessage) {
            sent += message
            when (message) {
                is JsonRpcRequest -> { pending = message.id; executor.execute(::play) }
                is JsonRpcResponse -> executor.execute(::play) // answered an agent request: go on
                is JsonRpcNotification -> Unit
            }
        }

        private fun play() {
            while (script.isNotEmpty()) {
                when (val next = script.removeFirst()) {
                    is JsonRpcResponse -> { fireMessage(next.copy(id = pending!!)); return }
                    is JsonRpcRequest -> { fireMessage(next); return }
                    is JsonRpcNotification -> fireMessage(next)
                }
            }
        }

        override fun close() {
            _state.value = Transport.State.CLOSED
            executor.shutdown()
            fireClose()
        }
    }
}

