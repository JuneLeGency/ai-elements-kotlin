package dev.ai.elements.core

import dev.ai.elements.core.agent.CalculatorTool
import dev.ai.elements.core.backend.MockAgentBackend
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.SourcePart
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatControllerTest {

    private var ids = 0
    private fun TestScope.controller(backend: ChatBackend) =
        ChatController({ backend }, backgroundScope, clock = { testScheduler.currentTime }, newId = { "id${ids++}" })

    @Test
    fun mockAgent_producesEveryPartType() = runTest(StandardTestDispatcher()) {
        val chat = controller(MockAgentBackend(chunkDelayMs = 0))
        assertTrue(chat.send("what is 6 * 7"))
        runCurrent()

        val state = chat.state.value
        assertEquals(ChatStatus.READY, state.status)
        val assistant = state.messages.last()
        assertEquals(Role.ASSISTANT, assistant.role)
        assertFalse(assistant.isStreaming)
        val tool = assistant.parts.filterIsInstance<ToolPart>().single()
        assertEquals("calculate", tool.name)
        assertEquals(ToolState.OUTPUT_AVAILABLE, tool.state)
        assertEquals("42", tool.output)
        assertTrue(assistant.parts.first() is ReasoningPart)
        assertEquals(3, assistant.parts.count { it is SourcePart })
        assertTrue(assistant.text.contains("```mermaid"))
    }

    @Test
    fun partsKeepStreamOrder_acrossSteps() {
        val events = listOf(
            ChatEvent.TextDelta("a", "Let me check. "),
            ChatEvent.TextEnd("a"),
            ChatEvent.ToolInputAvailable("c", "calculate", "{}"),
            ChatEvent.ToolOutput("c", "1"),
            ChatEvent.TextDelta("b", "Done"),
            ChatEvent.TextDelta("b", "!"),
        )
        val msg = events.fold(Message("x", Role.ASSISTANT)) { m, e -> m.reduce(e, 0) }
        assertEquals(listOf("a", "c", "b"), msg.parts.map { it.id })
        assertEquals("Done!", (msg.parts[2] as TextPart).text)
        assertTrue(msg.parts[2].isStreaming)
        assertFalse(msg.parts[0].isStreaming)
    }

    @Test
    fun reasoningDuration_isMeasured() {
        val m = Message("x", Role.ASSISTANT)
            .reduce(ChatEvent.ReasoningDelta("r", "think"), now = 1_000)
            .reduce(ChatEvent.ReasoningEnd("r"), now = 3_500)
        assertEquals(2_500L, (m.parts.single() as ReasoningPart).durationMs)
    }

    @Test
    fun backendFailure_setsErrorAndKeepsPartialReply() = runTest(StandardTestDispatcher()) {
        val chat = controller {
            flow {
                emit(ChatEvent.TextDelta("t", "partial"))
                throw ChatBackendException("HTTP 500: boom")
            }
        }
        chat.send("hi")
        runCurrent()
        val state = chat.state.value
        assertEquals(ChatStatus.ERROR, state.status)
        assertEquals("HTTP 500: boom", state.error)
        assertEquals("partial", state.messages.last().text)
        assertFalse(state.messages.last().isStreaming)

        chat.dismissError()
        assertEquals(ChatStatus.READY, chat.state.value.status)
    }

    @Test
    fun stop_cancelsAndFinalizes() = runTest(StandardTestDispatcher()) {
        val chat = controller {
            flow {
                emit(ChatEvent.TextDelta("t", "so far"))
                awaitCancellation()
            }
        }
        chat.send("hi")
        runCurrent()
        assertEquals(ChatStatus.STREAMING, chat.state.value.status)
        assertFalse("second send while busy is rejected", chat.send("again"))

        chat.stop()
        runCurrent()
        val state = chat.state.value
        assertEquals(ChatStatus.READY, state.status)
        assertNull(state.error)
        assertEquals("so far", state.messages.last().text)
        assertFalse(state.messages.last().isStreaming)
    }

    @Test
    fun regenerate_replacesLastReply() = runTest(StandardTestDispatcher()) {
        var n = 0
        val chat = controller { flow { emit(ChatEvent.TextDelta("t", "reply ${n++}")) } }
        chat.send("hi")
        runCurrent()
        chat.regenerate()
        runCurrent()
        val messages = chat.state.value.messages
        assertEquals(2, messages.size)
        assertEquals("reply 1", messages.last().text)
    }

    @Test
    fun calculator_handlesPrecedenceAndErrors() {
        assertEquals(14.0, CalculatorTool.evaluate("2 + 3 * 4"), 0.0)
        assertEquals(-512.0, CalculatorTool.evaluate("-2^3^2"), 0.0)
        assertEquals(8.4, CalculatorTool.evaluate("(3 + 4) * 12 / 10"), 1e-9)
        assertTrue(runCatching { CalculatorTool.evaluate("2 +") }.isFailure)
        assertTrue(runCatching { CalculatorTool.evaluate("os.system()") }.isFailure)
    }
}
