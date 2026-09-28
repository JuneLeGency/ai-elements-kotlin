package dev.ai.elements.demo

import android.content.Context
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.ui.R as UiR
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end chat flows through the real UI. Each test seeds the active
 * provider, launches the app, sends a prompt and waits for the agent run.
 *
 * Live tests use the host through the emulator alias and skip when it is not
 * reachable. Real-model Ollama tests (built-in loop and Koog) are opt-in:
 * `-Pandroid.testInstrumentationRunnerArguments.ollama=http://10.0.2.2:11434`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AgentFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val args = InstrumentationRegistry.getArguments()
    /** Opt-in: pass `ollama` (e.g. http://10.0.2.2:11434) to run the real-model tests; small local models can be very slow. */
    private val ollama = args.getString("ollama").orEmpty()
    private val agentServer = args.getString("agentServer") ?: "http://10.0.2.2:8788"
    private var scenario: ActivityScenario<MainActivity>? = null

    /** UI text in the device's language (tests also run on non-English devices). */
    private fun s(id: Int) = context.getString(id)

    private fun launchWith(profile: ProviderProfile) {
        resetDemoApp(profile)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun send(prompt: String) {
        compose.onNodeWithTag("prompt-input").performClick().performTextInput(prompt)
        compose.onNodeWithTag("send-button").performClick()
        scenario?.hideKeyboard() // a tablet's keyboard would move the cards the test taps next
        compose.waitForIdle()
    }

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onNodeWithTag("conversation").performScrollToNode(matcher)
    }

    private fun awaitTurnEnd(timeoutMs: Long) = compose.awaitTurnEnd(timeoutMs)

    /** The offline agent's scripted browser run: each step on the agent's computer, with screenshots. */
    @Test
    fun offlineAgent_browsesAndShowsTheAgentComputer() {
        assumeTrue("needs the internet", runCatching { (java.net.URL("https://example.com").openConnection() as java.net.HttpURLConnection).responseCode == 200 }.getOrDefault(false))
        launchWith(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        send(s(R.string.sugg_browse_prompt))
        awaitTurnEnd(120_000)
        scrollTo(hasTestTag("agent-computer-card"))
        compose.onNodeWithTag("agent-computer-card").performClick()
        compose.onNodeWithTag("agent-computer").assertExists()
        // navigate, snapshot, click, screenshot.
        val counter = compose.onNodeWithTag("run-step-counter").fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        val notOpened = compose.onAllNodesWithText("did not open", substring = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }
        val noLink = compose.onAllNodesWithText("I'll follow it", substring = true).fetchSemanticsNodes().isEmpty()
        assertTrue("steps: $counter; not opened: $notOpened; no link found: $noLink", counter.contains("4"))
        compose.onNodeWithTag("run-screen").assertExists()
    }

    @Test
    fun offlineAgent_runsToolAndRendersMarkdownAndMermaid() {
        launchWith(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        send("What is 6 * 7?")
        awaitTurnEnd(20_000)
        // Replies are virtualized: bring each slice into view before asserting on it.
        scrollTo(hasTestTag("tool-calculate"))
        compose.onNodeWithText(s(UiR.string.ai_tool_done)).assertExists()
        scrollTo(hasTestTag("mermaid"))
        scrollTo(hasTestTag("context-usage"))
    }

    @Test
    fun toolApproval_approveRunsTool_denyDoesNot() {
        launchWith(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        send("Copy this to my clipboard")
        awaitConfirmation()
        compose.onNodeWithTag("approve").performClick()
        awaitTurnEnd(20_000)
        scrollTo(hasTestTag("tool-copy_to_clipboard"))
        compose.onNodeWithText(s(UiR.string.ai_tool_done)).assertExists()

        send("Copy that again to the clipboard")
        awaitConfirmation()
        compose.onNodeWithTag("deny").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("regenerate").fetchSemanticsNodes().size == 1 }
        scrollTo(hasText(s(UiR.string.ai_tool_denied)))
    }

    @Test
    fun realModel_ollamaNative_runsOnDeviceAgentLoop() {
        assumeTrue("Pass -e ollama <url> to run real-model tests", ollama.isNotEmpty())
        assumeTrue("Ollama not reachable at $ollama", reachable("$ollama/api/tags"))
        assumeTrue("qwen3:4b is not pulled on $ollama", fetch("$ollama/api/tags").contains("\"qwen3:4b\""))
        resetDemoApp(ProviderProfile("live-ollama", "Live Ollama", ProviderKind.OLLAMA, ollama, "qwen3:4b")).leanAgent()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        send("Use the calculate tool to compute 1234 * 5678.")
        awaitTurnEnd(240_000)
        scrollTo(hasTestTag("tool-calculate"))
        scrollTo(hasText("7,006,652", substring = true) or hasText("7006652", substring = true))
    }

    /** The same turn with JetBrains Koog driving the in-app agent (ai-elements-koog). */
    @Test
    fun realModel_ollama_onKoogRuntime() {
        assumeTrue("Pass -e ollama <url> to run real-model tests", ollama.isNotEmpty())
        assumeTrue("Ollama not reachable at $ollama", reachable("$ollama/api/tags"))
        assumeTrue("qwen3:4b is not pulled on $ollama", fetch("$ollama/api/tags").contains("\"qwen3:4b\""))
        resetDemoApp(ProviderProfile("live-ollama-koog", "Live Ollama (Koog)", ProviderKind.OLLAMA, ollama, "qwen3:4b"))
            .leanAgent(koog = true)
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            send("Use the calculate tool to compute 1234 * 5678.")
            awaitTurnEnd(240_000)
            scrollTo(hasTestTag("tool-calculate"))
            scrollTo(hasText("7,006,652", substring = true) or hasText("7006652", substring = true))
        } finally {
            resetDemoApp()
        }
    }

    @Test
    fun realModel_agentServerAgUi_runsServerSideAgent() {
        assumeTrue("agent server not reachable at $agentServer", reachable("$agentServer/health"))
        launchWith(ProviderProfile("live-agui", "Live AG-UI", ProviderKind.AG_UI, agentServer))
        send("Use the calculate tool to compute 1234 * 5678.")
        awaitTurnEnd(240_000)
        // A real model calls `calculate`; the server's offline scripted model calls the clock.
        scrollTo(hasTestTag("tool-calculate") or hasTestTag("tool-get_current_time"))
    }

    /** The Confirmation sits inside the clickable tool card, whose semantics merge it. */
    private fun awaitConfirmation() = compose.waitUntil(20_000) {
        compose.onAllNodesWithTag("confirmation", useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }

    private fun fetch(url: String): String {
        var body = ""
        Thread { body = runCatching { URL(url).readText() }.getOrDefault("") }.apply { start(); join(5_000) }
        return body
    }

    private fun reachable(url: String): Boolean {
        var ok = false
        val thread = Thread {
            ok = runCatching {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 2000
                    readTimeout = 3000
                    responseCode == 200
                }
            }.getOrDefault(false)
        }
        thread.start()
        thread.join(6000)
        return ok
    }
}
