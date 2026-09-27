package dev.ai.elements.core.agent

import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.http.BackendJson
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `ask_user_question` behaves as Pydantic AI Harness `AskUser` does. */
class AskUserTest {
    private val call = BackendJson.parseToJsonElement(
        """{"questions":[
            {"header":"Database","question":"Which database should the app use?","options":[
                {"label":"SQLite","description":"Local, no server"},{"label":"Postgres"}]},
            {"header":"Features","question":"What should it include?","multi_select":true,"options":[
                {"label":"Auth"},{"label":"Payments"},{"label":"Search"}]}
        ]}""",
    ).jsonObject

    private suspend fun ask(answer: (InputRequest) -> InputResponse): Pair<InputRequest?, String> {
        var asked: InputRequest? = null
        val approver = object : ToolApprover {
            override suspend fun approve(toolCallId: String) = true
            override suspend fun input(request: InputRequest) = answer(request).also { asked = request }
        }
        var result = ""
        flow<ChatEvent> { result = runTool(AskUser().tools(), approver, "call-1", AskUser.TOOL_NAME, call.toString()) }.toList()
        return asked to result
    }

    @Test fun contract_matchesHarness() = runBlocking {
        val tool = AskUser().tools().single()
        assertEquals("ask_user_question", tool.name)
        assertEquals(listOf("questions"), tool.parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(AskUser().instructions.startsWith("When the task is ambiguous"))
    }

    @Test fun question_isAStandardJsonSchemaForm() = runBlocking {
        val (request, _) = ask { InputResponse.Cancel }
        val schema = request!!.schema!!
        assertEquals("call-1", request.id)
        val database = schema["properties"]!!.jsonObject["Database"]!!.jsonObject
        assertEquals("string", database["type"]!!.jsonPrimitive.content)
        val choices = database["anyOf"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("SQLite", "Postgres"), choices.mapNotNull { it["const"]?.jsonPrimitive?.content })
        assertEquals("Local, no server", choices.first()["description"]!!.jsonPrimitive.content)
        assertEquals(buildJsonObject { put("type", "string") }, choices.last())   // the user's own answer
        val features = schema["properties"]!!.jsonObject["Features"]!!.jsonObject
        assertEquals("array", features["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("Database", "Features"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun answers_areKeyedByHeader() = runBlocking {
        val (_, result) = ask {
            InputResponse.Accept(buildJsonObject {
                put("Database", "Postgres")
                putJsonArray("Features") { add("Search"); add("Auth") }
            })
        }
        assertEquals("""{"Database":["Postgres"],"Features":["Search","Auth"]}""", result)
    }

    @Test fun ownAnswer_isAOneItemList() = runBlocking {
        val (_, result) = ask {
            InputResponse.Accept(buildJsonObject {
                put("Database", "DuckDB, it's for analytics")
                putJsonArray("Features") { add("Auth") }
            })
        }
        assertEquals("""{"Database":["DuckDB, it's for analytics"],"Features":["Auth"]}""", result)
    }

    @Test fun declined_tellsTheModel() = runBlocking {
        assertEquals(AskUser.DECLINED, ask { InputResponse.Decline }.second)
        assertEquals(AskUser.DECLINED, ask { InputResponse.Cancel }.second)
    }

    @Test fun invalidCalls_goBackToTheModel() {
        fun invalid(json: String) = runCatching { askUserQuestions(BackendJson.parseToJsonElement(json).jsonObject) }.isFailure
        assertTrue(invalid("""{"questions":[]}"""))
        assertTrue(invalid("""{"questions":[{"header":"A","question":"?","options":[{"label":"only one"}]}]}"""))
        assertTrue(invalid("""{"questions":[{"header":"A","question":"?","options":[{"label":"x"},{"label":"x"}]}]}"""))
        assertTrue(invalid("""{"questions":[{"header":"A","question":"?","options":[{"label":"x"},{"label":"y"}]},{"header":"A","question":"?","options":[{"label":"x"},{"label":"y"}]}]}"""))
        assertTrue(invalid("""{"questions":[{"header":"A","question":"?","options":[{"label":"x"},{"label":"y"}],"extra":1}]}"""))
        assertTrue(invalid("""{"questions":[{"header":"A\u001b[2J","question":"?","options":[{"label":"x"},{"label":"y"}]}]}"""))
    }

}
