package dev.ai.elements.demo

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.mcp.McpServerConfig
import dev.ai.elements.demo.data.CapabilitySettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * MCP Apps end to end: the offline agent calls `show_notes_board` on the reference MCP server
 * (official Python SDK), whose view — built on the official `@modelcontextprotocol/ext-apps` SDK —
 * runs in the sandbox and talks to the host. Needs the server (`cd server && uv run uvicorn main:app
 * --port 8788`) and network access for the SDK's CDN.
 */
@RunWith(AndroidJUnit4::class)
class McpAppsTest {
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

    private val probe = McpAppProbe(compose) { scenario!! }
    private fun view(expression: String) = probe.view(expression)
    private fun awaitView(expression: String, expected: String, timeoutMs: Long = 60_000) = probe.await(expression, expected, timeoutMs)

    @Test
    fun notesBoard_rendersCallsToolsAndTalksToTheChat() {
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

        compose.onNodeWithTag("prompt-input").performClick().performTextInput("Show my board with notes__show_notes_board")
        compose.onNodeWithTag("send-button").performClick()

        // The view connects (official SDK from its declared CDN) and gets the tool's result.
        // (The server keeps notes in memory: count what is there already.)
        compose.waitUntil(60_000) { view("d.getElementById('title').textContent").matches(Regex("\"Notes \\(\\d+\\)\"")) }
        val before = Regex("\\d+").find(view("d.getElementById('title').textContent"))!!.value.toInt()
        val title = "Milk ${System.currentTimeMillis()}"

        // Saving a note is a write: the host asks the user first.
        view(
            "(d.getElementById('note-title').value = '$title', d.getElementById('note-content').value = '2 litres', " +
                "d.getElementById('save').click(), 'ok')",
        )
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithTag("mcp-app-allow").assertExists() }.isSuccess }
        compose.onNodeWithTag("mcp-app-allow").performClick()
        // The board refreshes through `board_notes`, a tool only the app may call.
        awaitView("d.getElementById('title').textContent", "Notes (${before + 1})")
        awaitView("[...d.querySelectorAll('#notes li b')].some((b) => b.textContent === '$title') ? 'listed' : ''", "listed")

        // The Content-Security-Policy blocks origins the view did not declare.
        view("(d.defaultView.fetch('https://example.com/').then(() => { d.body.dataset.fetch = 'allowed'; }, () => { d.body.dataset.fetch = 'blocked'; }), 'ok')")
        awaitView("d.body.dataset.fetch || ''", "blocked", 15_000)

        // `ui/message` sends a user turn, carrying the view's model context (`ui/update-model-context`).
        view("(d.getElementById('ask').click(), 'ok')")
        compose.waitUntil(30_000) {
            runCatching { compose.onNodeWithTag("conversation").performScrollToNode(hasText("Summarize the notes on my board")) }.isSuccess
        }
        // The turn is saved with its context part (the model reads it before the user's words).
        val saved = File(app.filesDir, "conversations.json")
        compose.waitUntil(60_000) { saved.exists() && saved.readText().contains("model-context") }
        val stored = saved.readText()
        assertTrue(stored, stored.contains("The notes board shows ${before + 1} note(s)") && stored.contains(title))
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("tool-notes__show_notes_board"))
    }
}
