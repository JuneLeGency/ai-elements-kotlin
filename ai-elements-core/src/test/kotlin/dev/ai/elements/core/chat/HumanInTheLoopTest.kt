package dev.ai.elements.core.chat

import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The human in the loop beyond yes / no: questions to the user, reasons and edited arguments. */
@OptIn(ExperimentalCoroutinesApi::class)
class HumanInTheLoopTest {
    private val schema = buildJsonObject { put("type", "object") }

    /** A backend that asks the user once and writes the answer as its reply. */
    private fun asking(request: InputRequest) = { human: ToolApprover ->
        ChatBackend {
            flow {
                val answer = human.input(request)
                emit(ChatEvent.TextDelta("t", answer.toString()))
                emit(ChatEvent.Finish)
            }
        }
    }

    @Test
    fun inputRequest_isShownUntilAnswered() = runTest(StandardTestDispatcher()) {
        val request = InputRequest("q1", "Where to?", schema, source = "Trips")
        val chat = ChatController(asking(request), backgroundScope)
        chat.send("plan a trip")
        runCurrent()
        assertEquals(listOf(request), chat.state.value.inputRequests)
        assertTrue(chat.state.value.isBusy)

        val answer = buildJsonObject { put("destination", "Kyoto") }
        chat.respondToInput("q1", InputResponse.Accept(answer))
        runCurrent()
        assertTrue(chat.state.value.inputRequests.isEmpty())
        assertEquals(ChatStatus.READY, chat.state.value.status)
        assertEquals(InputResponse.Accept(answer).toString(), chat.state.value.messages.last().text)
    }

    @Test
    fun stop_cancelsTheQuestion() = runTest(StandardTestDispatcher()) {
        val chat = ChatController(asking(InputRequest("q1", "Where to?", schema)), backgroundScope)
        chat.send("plan a trip")
        runCurrent()
        chat.stop()
        runCurrent()
        assertTrue(chat.state.value.inputRequests.isEmpty())
    }

    @Test
    fun hostsThatCannotAsk_cancel() = runBlocking {
        assertEquals(InputResponse.Cancel, ToolApprover.AlwaysApprove.input(InputRequest("q", "?")))
        assertEquals(ToolDecision(true), ToolApprover.AlwaysApprove.decide("call"))
    }

    private class NoteTool : AgentTool {
        var received: JsonObject? = null
        override val name = "save_note"
        override val description = "Save a note"
        override val parameters = buildJsonObject { put("type", "object") }
        override val requiresApproval = true
        override suspend fun execute(arguments: JsonObject) = "saved ${arguments["title"]!!.jsonPrimitive.content}".also { received = arguments }
    }

    private fun decider(decision: ToolDecision) = object : ToolApprover {
        override suspend fun approve(toolCallId: String) = decision.approved
        override suspend fun decide(toolCallId: String) = decision
    }

    @Test
    fun editedArguments_replaceTheProposedOnes() = runBlocking {
        val tool = NoteTool()
        val edited = buildJsonObject { put("title", "Groceries") }
        var result = ""
        val events = flow { result = runTool(listOf(tool), decider(ToolDecision(true, editedInput = edited)), "c1", "save_note", """{"title":"Milk"}""") }.toList()
        assertEquals(edited, tool.received)
        assertEquals("saved Groceries", result)
        // The card shows what actually ran.
        val inputs = events.filterIsInstance<ChatEvent.ToolInputAvailable>().map { it.input }
        assertEquals(edited.toString(), inputs.last())
        var part = ToolPart("c1", "save_note")
        events.forEach { e -> part = dev.ai.elements.core.model.Message("m", dev.ai.elements.core.model.Role.ASSISTANT, listOf(part)).reduce(e, 0).parts.single() as ToolPart }
        assertEquals(ToolState.OUTPUT_AVAILABLE, part.state)
    }

    @Test
    fun denialReason_reachesTheModelAndTheCard() = runBlocking {
        val tool = NoteTool()
        var result = ""
        val events = flow { result = runTool(listOf(tool), decider(ToolDecision(false, reason = "Not that note")), "c1", "save_note", """{"title":"Milk"}""") }.toList()
        assertEquals(null, tool.received)
        assertTrue(result, result.endsWith("Reason: Not that note"))
        assertEquals("Not that note", events.filterIsInstance<ChatEvent.ToolDenied>().single().reason)
    }

    /** A backend that asks approval for two calls of the same tool, then one of another. */
    private val twoNotesAndAList = { human: ToolApprover ->
        ChatBackend {
            flow {
                listOf("c1" to "save_note", "c2" to "save_note", "c3" to "delete_list").forEach { (id, name) ->
                    emit(ChatEvent.ToolInputAvailable(id, name, "{}"))
                    emit(ChatEvent.ToolApprovalRequest(id))
                    val decision = human.decide(id)
                    emit(if (decision.approved) ChatEvent.ToolApproved(id) else ChatEvent.ToolDenied(id))
                }
                emit(ChatEvent.Finish)
            }
        }
    }

    @Test
    fun alwaysAllow_skipsTheSameToolForTheConversation() = runTest(StandardTestDispatcher()) {
        val chat = ChatController(twoNotesAndAList, backgroundScope)
        chat.send("go")
        runCurrent()
        chat.respondToApproval("c1", ToolDecision(true, remember = true))
        runCurrent()
        // c2 (same tool) was approved without asking; c3 (another tool) still asks.
        fun state(id: String) = chat.state.value.messages.last().parts.filterIsInstance<ToolPart>().single { it.id == id }.state
        assertEquals(ToolState.INPUT_AVAILABLE, state("c2"))
        assertEquals(ToolState.APPROVAL_REQUESTED, state("c3"))
        chat.respondToApproval("c3", true)
        runCurrent()

        // A new conversation forgets it.
        chat.load(emptyList())
        chat.send("again")
        runCurrent()
        assertEquals(ToolState.APPROVAL_REQUESTED, state("c1"))
    }

    @Test
    fun toolFiles_followTheCall() = runBlocking {
        val tool = object : AgentTool {
            override val name = "browse"
            override val description = "Open a page"
            override val parameters = buildJsonObject { put("type", "object") }
            override suspend fun execute(arguments: JsonObject): String {
                dev.ai.elements.core.agent.ToolCallContext.current()!!.file("image/png", "data:image/png;base64,AAAA")
                return "Opened"
            }
        }
        val events = flow { runTool(listOf(tool), ToolApprover.AlwaysApprove, "c1", "browse", "{}") }.toList()
        val file = events.filterIsInstance<ChatEvent.File>().single()
        assertEquals("c1-file-0" to "image/png", file.id to file.mediaType)
        assertTrue(events.indexOf(file) < events.indexOfFirst { it is ChatEvent.ToolOutput && !it.preliminary })
    }
}
