package dev.ai.elements.core.provider

import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.provider.mock.MockAgentBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The offline agent's scripted browser run, against stand-ins for the browser's tools. */
class MockBrowseTest {
    private val calls = mutableListOf<Pair<String, JsonObject>>()

    private fun tool(name: String, result: String) = object : AgentTool {
        override val name = name
        override val description = name
        override val parameters = buildJsonObject { put("type", JsonPrimitive("object")) }
        override suspend fun execute(arguments: JsonObject) = result.also { calls += name to arguments }
    }

    private val browser = listOf(
        tool("navigate", "URL: https://example.com/\nTitle: Example Domain"),
        tool("snapshot", "- document \"Example Domain\"\n- heading \"Example Domain\" [ref=e1] [level=1]\n- link \"Learn more\" [ref=e2]"),
        tool("click", "Clicked 'aria-ref=e2'."),
        tool("screenshot", "Screenshot captured. URL: https://www.iana.org/help/example-domains"),
    )

    private fun run(prompt: String) = runBlocking {
        MockAgentBackend(browser, chunkDelayMs = 0).stream(listOf(Message("u", Role.USER, listOf(TextPart("t", prompt))))).toList()
    }

    @Test fun browse_opensReadsFollowsTheLinkAndLooks() {
        // The demo's prompts: the URL ends where the prose starts, whatever the language.
        listOf(
            "用浏览器打开 https://example.com，点进其中的链接并截图",
            "ブラウザで https://example.com を開き、リンクをたどってスクリーンショットを撮って",
            "Use the browser: open https://example.com, follow its link and take a screenshot",
        ).forEach { prompt ->
            calls.clear()
            val events = run(prompt)
            assertEquals(prompt, listOf("navigate", "snapshot", "click", "screenshot"), calls.map { it.first })
            assertEquals(prompt, "https://example.com", (calls[0].second["url"] as JsonPrimitive).content)
            assertEquals("aria-ref=e2", (calls[2].second["selector"] as JsonPrimitive).content)
            assertTrue(events.last() is ChatEvent.Finish)
        }
    }

    @Test fun jsxPrompt_answersWithAJsxFence() {
        val text = run("生成一个酒店预订界面（JSX）").filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }
        assertTrue(text, text.contains("```jsx\n<Card>") && text.contains("<select name=\"room\"") && text.contains("onClick={book}"))
        assertTrue(calls.isEmpty())
    }
}
