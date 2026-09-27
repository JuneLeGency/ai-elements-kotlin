package dev.ai.elements.demo

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app as a pure client of remote agents, end to end against the reference server
 * (`server/main.py`: Pydantic AI + Harness, AG-UI 1.0, AI SDK 6, A2A 1.0). The server's
 * offline scripted model picks a capability by keyword, so no model key is needed.
 * Skips when the server is not reachable (override with `-e agentServer <url>`).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CapabilitiesFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = InstrumentationRegistry.getArguments().getString("agentServer") ?: "http://10.0.2.2:8788"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun checkServer() = assumeTrue("Agent server not reachable at $server", reachable("$server/health"))

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launchWith(kind: ProviderKind) {
        resetDemoApp(ProviderProfile("e2e-${kind.name.lowercase()}", "E2E ${kind.label}", kind, server, ""))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun send(prompt: String) {
        compose.onNodeWithTag("prompt-input").performClick().performTextInput(prompt)
        compose.onNodeWithTag("send-button").performClick()
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag(tag))

    @Test
    fun agUi_subAgentDelegation_rendersAsSubagent() {
        launchWith(ProviderKind.AG_UI)
        send("please delegate this")
        compose.awaitTurnEnd(60_000)
        scrollTo("subagent-researcher")
    }

    @Test
    fun agUi_interrupt_approvalInTheUi_resumesTheRun() {
        launchWith(ProviderKind.AG_UI)
        send("save a note")
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("approve").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("approve").performClick()
        compose.awaitTurnEnd(60_000)
        scrollTo("tool-save_note")
        compose.onNodeWithTag("conversation").performScrollToNode(hasText("Saved note", substring = true))
    }

    @Test
    fun aiSdk_skillLoad_andPlan() {
        launchWith(ProviderKind.AGENT_SERVER)
        send("use a skill")
        compose.awaitTurnEnd(60_000)
        scrollTo("tool-load_capability")
        send("make a plan")
        compose.waitUntil(60_000) { runCatching { scrollTo("data-plan") }.isSuccess }
    }

    @Test
    fun a2a_remoteAgent_streamsItsAnswer() {
        launchWith(ProviderKind.A2A)
        send("What is AG-UI?")
        compose.awaitTurnEnd(60_000)
        compose.onNodeWithTag("conversation").performScrollToNode(hasText("Agent run complete", substring = true))
        scrollTo("data-task")
    }

    private fun reachable(url: String) = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 2_000
            readTimeout = 2_000
            responseCode == 200
        }
    }.getOrDefault(false)
}
