package dev.ai.elements.a2a

import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.model.DataPart as ChatDataPart
import dev.ai.elements.core.model.Message as ChatMessage
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart as ChatTextPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.a2aproject.sdk.client.MessageEvent
import org.a2aproject.sdk.spec.DataPart
import org.a2aproject.sdk.spec.Message
import org.a2aproject.sdk.spec.TextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The A2UI A2A extension (v1.0): `application/a2ui+json` data parts, both ways. */
class A2uiA2aTest {
    private val messages = """[{"version":"v1.0","createSurface":{"surfaceId":"example_surface","catalogId":"https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"}},{"version":"v1.0","updateComponents":{"surfaceId":"example_surface","components":[{"id":"root","component":"Text","text":"Hello!"}]}}]"""

    @Test
    fun agentDataPart_withTheA2uiMimeType_becomesTheNeutralPart() {
        val data = com.google.gson.Gson().fromJson(messages, Any::class.java)
        val message = Message.builder().role(Message.Role.ROLE_AGENT).messageId("m1")
            .parts(listOf(TextPart("Here you go"), DataPart(data, mapOf<String, Any>("mimeType" to "application/a2ui+json")), DataPart(mapOf("other" to 1))))
            .build()
        val events = A2aBackend.Mapper("agent").map(MessageEvent(message))
        val parts = events.filterIsInstance<ChatEvent.Data>()
        assertEquals(ChatDataPart.A2UI, parts[0].name)
        assertEquals(Json.parseToJsonElement(messages), parts[0].data)
        assertTrue(parts[1].name != ChatDataPart.A2UI) // other data parts keep their own name
    }

    @Test
    fun userAction_goesOutAsAnA2uiDataPart() {
        val action = Json.parseToJsonElement("""{"version":"v1.0","action":{"name":"submit_form","surfaceId":"contact_form_1","sourceComponentId":"submit_button","timestamp":"2026-01-15T12:00:00Z","context":{"email":"user@example.com"}}}""").jsonObject
        val user = ChatMessage("u", Role.USER, listOf(ChatTextPart("t", "Submit the form."), ChatDataPart("d", ChatDataPart.A2UI, JsonArray(listOf(action)))))
        val parts = A2aBackend.outgoingParts(user)
        assertEquals("Submit the form.", (parts[0] as TextPart).text())
        val data = parts[1] as DataPart
        assertEquals("application/a2ui+json", data.metadata()!!["mimeType"])
        assertEquals(JsonArray(listOf(action)), Json.parseToJsonElement(com.google.gson.Gson().toJson(data.data())))
    }
}
