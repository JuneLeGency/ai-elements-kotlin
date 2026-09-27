package dev.ai.elements.acp

import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.chat.reduce
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Against the reference server's ACP agent (Pydantic AI Harness, scripted model):
 *
 *     ./gradlew :ai-elements-acp:testDebugUnitTest --tests '*LiveAcpTest*' \
 *         -PliveAcpCommand="uv run --directory server python acp_agent.py" \
 *         -PliveAcp=ws://localhost:8788/acp
 */
class LiveAcpTest {

    @Test fun stdio_twoTurnsInOneSessionWithAnApproval() = runBlocking {
        val command = System.getProperty("live.acp.command")
        assumeTrue("set -PliveAcpCommand", command != null)
        conversation(AcpAgent.process(command!!.split(" "), cwd = System.getProperty("java.io.tmpdir")))
    }

    @Test fun webSocket_twoTurnsInOneSessionWithAnApproval() = runBlocking {
        val url = System.getProperty("live.acp")
        assumeTrue("set -PliveAcp", url != null)
        conversation(AcpAgent.webSocket(url!!, cwd = "/tmp"))
    }

    private suspend fun conversation(agent: AcpAgent) = agent.use {
        assertEquals("AI Elements agent", agent.displayName())
        val approvals = mutableListOf<String>()
        val backend = AcpBackend(agent, ToolApprover { id -> approvals += id; true })
        var history = listOf(user("u1", "please plan"))
        val first = turn(backend, history)
        assertTrue(first.parts.any { it is DataPart && it.name == "plan" })
        assertEquals("write_plan", first.parts.filterIsInstance<ToolPart>().single().name)

        history = history + first + user("u2", "please note")
        val second = turn(backend, history)
        val note = second.parts.filterIsInstance<ToolPart>().single()
        assertEquals(listOf(note.id), approvals)
        assertEquals(ToolState.OUTPUT_AVAILABLE, note.state)
        // Same ACP session: the agent kept the history.
        assertEquals(first.metadata!!["acp"], second.metadata!!["acp"])
    }

    private suspend fun turn(backend: AcpBackend, history: List<Message>): Message = withTimeout(60_000) {
        val events = backend.stream(history).toList()
        assertTrue(events.last() is ChatEvent.Finish)
        events.fold(Message("a${history.size}", Role.ASSISTANT)) { m, e -> m.reduce(e, 0) }
    }

    private fun user(id: String, text: String) = Message(id, Role.USER, listOf(TextPart("$id-t", text)))
}
