package dev.ai.elements.core.chat

import dev.ai.elements.core.agent.CalculatorTool
import dev.ai.elements.core.provider.mock.MockAgentBackend
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.ai.elements.core.chat.reduce
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.chat.ChatBackendException
import dev.ai.elements.core.chat.ChatBackend

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
        assertTrue(assistant.parts[0] is dev.ai.elements.core.model.DataPart)
        assertTrue(assistant.parts[1] is ReasoningPart)
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
        assertTrue("a send while busy is queued", chat.send("again"))
        assertEquals(listOf("again"), chat.state.value.queue.map { it.text })

        chat.stop()
        runCurrent()
        val state = chat.state.value
        assertEquals(ChatStatus.READY, state.status)
        assertNull(state.error)
        assertEquals("so far", state.messages.last().text)
        assertFalse(state.messages.last().isStreaming)
        assertTrue("stopping pauses the queue", state.queuePaused)
        assertEquals(1, state.queue.size)
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

    @Test
    fun queue_sendsNextPromptWhenTurnFinishes() = runTest(StandardTestDispatcher()) {
        val prompts = mutableListOf<String>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val chat = controller(ChatBackend { history ->
            flow {
                val prompt = history.last().text
                prompts += prompt
                if (prompt == "first") gate.await()
                emit(ChatEvent.TextDelta("t", "re: $prompt"))
            }
        })
        chat.send("first")
        runCurrent()
        chat.send("second")
        chat.send("third")
        chat.removeQueued(chat.state.value.queue.last().id)
        assertEquals(listOf("second"), chat.state.value.queue.map { it.text })

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "second"), prompts)
        assertTrue(chat.state.value.queue.isEmpty())
        assertEquals(listOf("first", "re: first", "second", "re: second"), chat.state.value.messages.map { it.text })
    }

    @Test
    fun regenerate_keepsVersions_andSelectVersionSwitches() = runTest(StandardTestDispatcher()) {
        var n = 0
        val chat = controller { flow { emit(ChatEvent.TextDelta("t", "answer ${n++}")) } }
        chat.send("q")
        runCurrent()
        testScheduler.advanceTimeBy(10); chat.regenerate(); runCurrent()
        testScheduler.advanceTimeBy(10); chat.regenerate(); runCurrent()

        val reply = chat.state.value.messages.last()
        assertEquals("answer 2", reply.text)
        assertEquals(listOf("answer 0", "answer 1", "answer 2"), reply.versions.map { it.text })
        assertEquals(2, reply.versionIndex)

        chat.selectVersion(reply.id, 0)
        val first = chat.state.value.messages.last()
        assertEquals("answer 0", first.text)
        assertEquals(0, first.versionIndex)
        assertEquals(3, first.versions.size)
        assertEquals(2, chat.state.value.messages.size)
    }

    @Test
    fun failedRegenerate_keepsPreviousAnswer() = runTest(StandardTestDispatcher()) {
        var fail = false
        val chat = controller {
            flow {
                if (fail) throw ChatBackendException("boom")
                emit(ChatEvent.TextDelta("t", "good"))
            }
        }
        chat.send("q"); runCurrent()
        fail = true
        testScheduler.advanceTimeBy(10); chat.regenerate(); runCurrent()
        assertEquals(ChatStatus.ERROR, chat.state.value.status)
        assertEquals("good", chat.state.value.messages.last().text)
    }

    @Test
    fun checkpoint_rewindsConversation() = runTest(StandardTestDispatcher()) {
        val chat = controller { h -> flow { emit(ChatEvent.TextDelta("t", "re: ${h.last().text}")) } }
        chat.send("one"); runCurrent()
        chat.send("two"); runCurrent()
        val firstReply = chat.state.value.messages[1]
        chat.restoreCheckpoint(firstReply.id)
        assertEquals(listOf("one", "re: one"), chat.state.value.messages.map { it.text })
    }

    @Test
    fun dataParts_withSameIdAreReplaced() {
        val data1 = kotlinx.serialization.json.JsonPrimitive(1)
        val data2 = kotlinx.serialization.json.JsonPrimitive(2)
        val m = Message("x", Role.ASSISTANT)
            .reduce(ChatEvent.Data("p", "plan", data1), 0)
            .reduce(ChatEvent.Data("p", "plan", data2), 0)
        assertEquals(data2, (m.parts.single() as dev.ai.elements.core.model.DataPart).data)
    }

    @Test
    fun mockExtractsBalancedExpressions() {
        assertEquals("(1234 * 5678) / 9", MockAgentBackend.extractExpression("Calculate (1234 * 5678) / 9 and show the steps"))
        assertEquals("6 * 7", MockAgentBackend.extractExpression("what is 6 * 7?"))
        assertEquals(null, MockAgentBackend.extractExpression("What time is it in Tokyo?"))
    }

    @Test
    fun deltas_areCoalescedPerFrame_andNothingIsLost() = runTest(StandardTestDispatcher()) {
        val chat = ChatController(
            backend = {
                ChatBackend {
                    flow {
                    repeat(300) { i ->
                        emit(ChatEvent.TextDelta("t", "$i "))
                        if (i % 30 == 29) kotlinx.coroutines.delay(10) // ~10 tokens per ms
                    }
                    }
                }
            },
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            publishIntervalMs = 32,
        )
        val emissions = mutableListOf<ChatState>()
        backgroundScope.launch { chat.state.collect { emissions += it } }
        runCurrent()
        chat.send("go")
        testScheduler.advanceTimeBy(1_000)
        runCurrent()
        val streamed = emissions.count { it.status == ChatStatus.STREAMING }
        assertTrue("300 deltas should publish only a few frames, got $streamed", streamed in 1..10)
        assertEquals((0 until 300).joinToString(" ") + " ", chat.state.value.messages.last().text)
    }

    @Test
    fun stop_keepsUnpublishedDeltas() = runTest(StandardTestDispatcher()) {
        val chat = ChatController(
            backend = {
                ChatBackend { flow {
                    emit(ChatEvent.TextDelta("t", "a"))
                    emit(ChatEvent.TextDelta("t", "b"))
                    emit(ChatEvent.TextDelta("t", "c"))
                    awaitCancellation()
                } }
            },
            scope = backgroundScope,
            publishIntervalMs = 10_000,
        )
        chat.send("go")
        runCurrent()
        assertEquals("a", chat.state.value.messages.last().text)
        chat.stop()
        assertEquals("abc", chat.state.value.messages.last().text)
    }
}
