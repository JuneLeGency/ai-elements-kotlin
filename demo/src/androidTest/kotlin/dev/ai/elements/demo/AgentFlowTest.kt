package dev.ai.elements.demo

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import dev.ai.elements.core.config.ProviderStore
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end chat flows through the real UI. Each test seeds the active
 * provider, launches the app, sends a prompt and waits for the agent run.
 *
 * Live tests use the host through the emulator alias and skip when it is not
 * reachable. Override with instrumentation args, e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.ollama=http://10.0.2.2:11435`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AgentFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val args = InstrumentationRegistry.getArguments()
    private val ollama = args.getString("ollama") ?: "http://10.0.2.2:11434"
    private val agentServer = args.getString("agentServer") ?: "http://10.0.2.2:8788"
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launchWith(profile: ProviderProfile) {
        context.getSharedPreferences("ai_elements_providers", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "conversations.json").delete()
        ProviderStore(context).apply {
            upsert(profile)
            select(profile.id)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun send(prompt: String) {
        compose.onNodeWithTag("prompt-input").performClick().performTextInput(prompt)
        compose.onNodeWithTag("send-button").performClick()
    }

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onNodeWithTag("conversation").performScrollToNode(matcher)
    }

    private fun awaitTurnEnd(timeoutMs: Long) {
        compose.waitUntilExactlyOneExists(hasTestTag("regenerate"), timeoutMs)
        compose.onNodeWithTag("chat-error").assertDoesNotExist()
    }

    @Test
    fun offlineAgent_runsToolAndRendersMarkdownAndMermaid() {
        launchWith(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        send("What is 6 * 7?")
        awaitTurnEnd(20_000)
        // Replies are virtualized: bring each slice into view before asserting on it.
        scrollTo(hasTestTag("tool-calculate"))
        compose.onNodeWithText("Done").assertExists()
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
        compose.onNodeWithText("Done").assertExists()

        send("Copy that again to the clipboard")
        awaitConfirmation()
        compose.onNodeWithTag("deny").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("regenerate").fetchSemanticsNodes().size == 1 }
        scrollTo(hasText("Denied"))
    }

    @Test
    fun realModel_ollamaNative_runsOnDeviceAgentLoop() {
        assumeTrue("Ollama not reachable at $ollama", reachable("$ollama/api/tags"))
        launchWith(ProviderProfile("live-ollama", "Live Ollama", ProviderKind.OLLAMA, ollama, "qwen3:4b"))
        send("Use the calculate tool to compute 1234 * 5678.")
        awaitTurnEnd(240_000)
        scrollTo(hasTestTag("tool-calculate"))
        scrollTo(hasText("7,006,652", substring = true) or hasText("7006652", substring = true))
    }

    @Test
    fun realModel_agentServerAgUi_runsServerSideAgent() {
        assumeTrue("agent server not reachable at $agentServer", reachable("$agentServer/health"))
        launchWith(ProviderProfile("live-agui", "Live AG-UI", ProviderKind.AG_UI, agentServer))
        send("Use the calculate tool to compute 1234 * 5678.")
        awaitTurnEnd(240_000)
        scrollTo(hasTestTag("tool-calculate"))
    }

    /** The Confirmation sits inside the clickable tool card, whose semantics merge it. */
    private fun awaitConfirmation() = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag("confirmation", useUnmergedTree = true).fetchSemanticsNodes().size == 1
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
