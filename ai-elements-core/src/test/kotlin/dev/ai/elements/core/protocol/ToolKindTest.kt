package dev.ai.elements.core.protocol

import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.reduce
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tool calls reach the UI with a protocol-independent [ToolKind], whatever produced them. */
class ToolKindTest {
    @Test
    fun harnessConventions_mapToKinds() {
        assertEquals(ToolKind.Delegation("researcher", "Find X"), ToolConventions.kindOf("delegate_task", """{"agent_name":"researcher","task":"Find X"}"""))
        assertEquals(ToolKind.Skill("pdf"), ToolConventions.kindOf("load_capability", """{"id":"pdf"}"""))
        assertNull(ToolConventions.kindOf("get_weather", "{}"))
        assertEquals(ToolKind.Delegation(null, null), ToolConventions.kindOf("delegate_task", "not json"))
    }

    @Test
    fun explicitKind_winsOverConventions() {
        val explicit = ChatEvent.ToolInputAvailable("c", "delegate_task", "{}", kind = ToolKind.Function)
        assertEquals(ToolKind.Function, explicit.withConventions().kind)
    }

    @Test
    fun reducer_keepsKindAndSource() {
        val message = Message("m", Role.ASSISTANT)
            .reduce(ChatEvent.ToolInputStart("c", "notes__save"), 0)
            .reduce(ChatEvent.ToolInputAvailable("c", "notes__save", "{}", title = "Save note", source = "Notes"), 0)
        val part = message.parts.single() as ToolPart
        assertEquals("Save note", part.title)
        assertEquals("Notes", part.source)
        assertEquals(ToolKind.Function, part.kind)
    }

    @Test
    fun aiSdkStream_delegateTask_becomesADelegation() {
        val events = UiMessageStreamBackend.parseChunks(
            Json.parseToJsonElement("""{"type":"tool-input-available","toolCallId":"c1","toolName":"delegate_task","input":{"agent_name":"writer","task":"Draft"}}""").jsonObject,
        )
        assertEquals(ToolKind.Delegation("writer", "Draft"), events.filterIsInstance<ChatEvent.ToolInputAvailable>().single().kind)
    }
}
