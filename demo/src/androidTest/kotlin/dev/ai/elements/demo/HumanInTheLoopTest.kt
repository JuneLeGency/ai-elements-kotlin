package dev.ai.elements.demo

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.mcp.McpServerConfig
import dev.ai.elements.demo.data.CapabilitySettings
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Human in the loop on the device: an MCP tool that changes data asks for approval — the user
 * edits its arguments first — and then the server (official `mcp` SDK) asks the user for details
 * mid-call (elicitation), answered in a form built from its schema.
 */
@RunWith(AndroidJUnit4::class)
class HumanInTheLoopTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val server = InstrumentationRegistry.getArguments().getString("agentServer") ?: "http://10.0.2.2:8788"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun checkServer() = assumeTrue("Agent server not reachable at $server", runCatching {
        (URL("$server/health").openConnection() as HttpURLConnection).run { connectTimeout = 3_000; responseCode == 200 }
    }.getOrDefault(false))

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollToText(text: String) = compose.waitUntil(60_000) {
        runCatching { compose.onNodeWithTag("conversation").performScrollToNode(hasText(text, substring = true)) }.isSuccess
    }

    @Test
    fun mcpTool_editedApproval_thenElicitationForm() {
        val app = resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        app.agents.update {
            CapabilitySettings(
                workspaceFiles = false, sandboxShell = false, memory = false, planning = false, webBrowser = false,
                deviceTools = false, speech = false, scheduledTasks = false, skillsEnabled = false, mcpEnabled = true,
                subAgents = emptyList(), remoteAgents = emptyList(),
            )
        }
        app.mcpServers.upsert(McpServerConfig("notes", "Notes", "$server/mcp"))
        scenario = ActivityScenario.launch(MainActivity::class.java)

        compose.onNodeWithTag("prompt-input").performClick().performTextInput("""Book it: notes__book_table {"restaurant": "Sora"}""")
        compose.onNodeWithTag("send-button").performClick()

        // A write: the user changes the restaurant, then approves.
        waitForTag("edit-and-approve")
        compose.onNodeWithTag("edit-and-approve", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("edit-arguments").performTextReplacement("""{"restaurant": "Hoshi"}""")
        scenario!!.hideKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("edit-and-approve-send", useUnmergedTree = true).performClick()

        // The server asks for the details (MCP elicitation) in a form built from its schema.
        waitForTag("input-request")
        scrollToText("Booking a table at Hoshi")
        compose.onNodeWithTag("input-field-party_size").performTextInput("4")
        compose.onNodeWithTag("input-field-time").performTextInput("19:30")
        scenario!!.hideKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("conversation").performScrollToNode(hasText("outdoor"))
        compose.onNodeWithTag("input-option-seating-outdoor").performClick()
        compose.onNodeWithTag("input-submit").performClick()

        scrollToText("Booked a table for 4 at Hoshi, 19:30, outdoor.")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("input-request").fetchSemanticsNodes().isEmpty() }
    }

    /**
     * Pydantic AI Harness `ask_user_question` from the agent server: the app advertises it as an
     * AG-UI frontend tool, shows the questions, and the run continues with the answers.
     */
    @Test
    fun agUi_agentAsksAQuestion_answeredInTheApp() {
        resetDemoApp(ProviderProfile("e2e-agui", "E2E AG-UI", ProviderKind.AG_UI, server, ""))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("prompt-input").performClick().performTextInput("please ask me")
        compose.onNodeWithTag("send-button").performClick()
        scenario!!.hideKeyboard()
        waitForTag("input-request")
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("input-option-Database-Postgres"))
        compose.onNodeWithTag("input-option-Database-Postgres").performClick()
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("input-option-Features-Search"))
        compose.onNodeWithTag("input-option-Features-Search").performClick()
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("input-submit"))
        compose.onNodeWithTag("input-submit").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("input-request").fetchSemanticsNodes().isEmpty() }
        scrollToText("Agent run complete")
    }
}
