package dev.ai.elements.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ai.elements.core.config.Credential
import dev.ai.elements.core.config.GatewayConfigStore
import dev.ai.elements.core.config.GatewayProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-service UI test.
 *
 * Drives the full UI against a **live** OpenAI-compatible gateway (the local
 * CLIProxyAPI on the host, reached from the emulator via `10.0.2.2`). Flow:
 *
 *   1. Open Settings, select "OpenAI-compatible".
 *   2. Fill base URL + API key + model (from [BuildConfig] gradle properties).
 *   3. Return to Chat, type a prompt, hit send.
 *   4. Assert the assistant message streams text back.
 *
 * The test **skips** (does not fail) when no API key is configured, so it is safe
 * to run in CI / on a clean checkout. To run for real:
 *
 *   ./gradlew :demo:connectedDebugAndroidTest \
 *       -PrealGatewayBaseUrl="http://10.0.2.2:9090/v1" \
 *       -PrealGatewayApiKey="<sk-...>" \
 *       -PrealGatewayModel="claude-sonnet-5"
 */
@RunWith(AndroidJUnit4::class)
class RealGatewayUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val prompt = "Reply with exactly: pong"

    /**
     * Pre-seed the [GatewayConfigStore] directly (same process + prefs as the app)
     * so we don't depend on the emulator's IME to type into the Settings fields.
     * This keeps the test focused on the Chat send→stream flow, which is what we
     * actually want to assert against a live gateway.
     */
    @Before
    fun seedGatewayConfig() {
        val apiKey = BuildConfig.REAL_GATEWAY_API_KEY
        assumeTrue("No REAL_GATEWAY_API_KEY set — skipping real-service UI test", apiKey.isNotBlank())
        val ctx = composeRule.activity.applicationContext
        val store = GatewayConfigStore(ctx)
        store.update {
            it.copy(
                provider = GatewayProvider.OPENAI_COMPATIBLE,
                baseUrl = BuildConfig.REAL_GATEWAY_BASE_URL,
                credential = Credential.ApiKey(),
                model = BuildConfig.REAL_GATEWAY_MODEL,
            )
        }
        store.writeApiKey(apiKey)
    }

    @Test
    fun realGatewayStreamsAssistantReply() {
        composeRule.runOnIdle { }

        // The app boots directly onto the Chat screen (no Settings detour needed
        // because the store is pre-seeded in @Before).

        // Type the prompt and send. (No explicit focus click — performTextInput
        // handles focus; an extra click can race the IME and drop the text.)
        composeRule.onNodeWithTag("prompt_input").performTextInput(prompt)
        composeRule.waitForIdle()
        // DIAGNOSTIC: dump what the input node actually holds right now.
        val inputNode = composeRule.onNodeWithTag("prompt_input").fetchSemanticsNode()
        val inputText = runCatching {
            inputNode.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        }.getOrDefault("<no-editable-text>")
        android.util.Log.i("AIElems", "prompt_input editableText='$inputText'")
        composeRule.onNodeWithTag("prompt_send").performClick()
        composeRule.waitForIdle()
        // DIAGNOSTIC: did the user message appear? (Proves onSend fired.)
        val userMsgPresent = runCatching {
            composeRule.onNodeWithText(prompt).fetchSemanticsNode()
            true
        }.getOrDefault(false)
        android.util.Log.i("AIElems", "after send-click: userMessagePresent=$userMsgPresent")

        // 6. The user message should be visible immediately.
        composeRule.onNodeWithText(prompt).assertIsDisplayed()

        // Assistant text is tagged on the TextShimmer nodes (`assistant_text`).
        fun assistantTexts(): List<String> = composeRule
            .onAllNodes(androidx.compose.ui.test.hasTestTag("assistant_text"))
            .fetchSemanticsNodes()
            .flatMap { node ->
                runCatching {
                    node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                        .map { it.text }
                }.getOrDefault(emptyList())
            }

        // 7. Wait for the assistant to stream a non-empty reply (up to 90s live).
        val deadline = System.currentTimeMillis() + 90_000
        runBlocking {
            while (System.currentTimeMillis() < deadline) {
                composeRule.waitForIdle()
                if (assistantTexts().any { it.isNotBlank() }) break
                delay(500)
            }
        }
        composeRule.waitForIdle()
        val texts = assistantTexts().filter { it.isNotBlank() }
        assertTrue("Assistant bubble never streamed a reply", texts.isNotEmpty())

        // 8. The reply must be real (not just the echoed prompt).
        assertTrue(
            "Assistant reply was empty / just the prompt",
            texts.any { it.trim() != prompt.trim() },
        )
    }
}
