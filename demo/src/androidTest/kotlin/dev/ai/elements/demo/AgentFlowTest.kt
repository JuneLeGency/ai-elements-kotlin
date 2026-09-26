package dev.ai.elements.demo

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end chat flows through the real UI: pick a provider, send a prompt,
 * and wait for the agent's tool call, Markdown answer and Mermaid diagram.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AgentFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun selectProvider(supportingText: String) {
        compose.onNodeWithTag("new-chat").performClick()
        compose.onNodeWithTag("provider-button").performClick()
        compose.onNodeWithText(supportingText, substring = true).performClick()
        compose.waitForIdle()
    }

    private fun send(prompt: String) {
        compose.onNodeWithTag("prompt-input").performTextInput(prompt)
        compose.onNodeWithTag("send-button").performClick()
    }

    @Test
    fun offlineAgent_runsToolAndRendersMarkdownAndMermaid() {
        selectProvider("Offline demo · mock-agent")
        send("What is 6 * 7?")

        compose.waitUntilExactlyOneExists(hasTestTag("tool-calculate"), 10_000)
        // The turn is over once the regenerate action appears under the reply.
        compose.waitUntilExactlyOneExists(hasTestTag("regenerate"), 20_000)
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithTag("mermaid", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Offline agent demo").assertExists()
    }

    @Test
    fun agentServer_streamsServerSideToolCall() {
        assumeTrue("agent server not reachable on 10.0.2.2:8788", serverUp("http://10.0.2.2:8788/health"))
        selectProvider("Agent server (UI Message Stream) · ")
        send("What time is it in Tokyo?")

        compose.waitUntilExactlyOneExists(hasTestTag("tool-get_current_time"), 15_000)
        compose.waitUntilExactlyOneExists(hasTestTag("regenerate"), 30_000)
        compose.onNodeWithTag("chat-error").assertDoesNotExist()
        compose.onNodeWithTag("assistant-message").assertExists()
    }

    private fun serverUp(url: String): Boolean = runCatching {
        var ok = false
        val thread = Thread {
            ok = runCatching {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 2000
                    readTimeout = 2000
                    responseCode == 200
                }
            }.getOrDefault(false)
        }
        thread.start()
        thread.join(5000)
        ok
    }.getOrDefault(false)
}
