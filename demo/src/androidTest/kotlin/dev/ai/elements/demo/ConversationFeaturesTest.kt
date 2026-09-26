package dev.ai.elements.demo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.ai.elements.ui.R as UiR
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.config.ProviderStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Queue, Branch, Checkpoint, data parts and the agent-run Canvas, driven
 * through the real UI with the offline agent. Screenshots of each state are
 * written to the app's external files dir (`screens/`) for review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ConversationFeaturesTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** UI text in the device's language (tests also run on non-English devices). */
    private fun s(id: Int) = context.getString(id)

    @Before
    fun launch() {
        context.getSharedPreferences("ai_elements_providers", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "conversations.json").delete()
        ProviderStore(context).select(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK }.id)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        scenario?.close()
    }

    // Like a user: tap the composer, then type (the app keeps untouched fields from grabbing focus).
    private fun type(prompt: String) = compose.onNodeWithTag("prompt-input").performClick().performTextInput(prompt)

    /** Headless emulators run in hardware-keyboard mode and keep the IME up; hide it. */
    private fun hideKeyboard() {
        scenario?.onActivity { activity ->
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
    }

    private fun awaitIdleTurn() = compose.waitUntil(30_000) {
        compose.onAllNodesWithTag("regenerate").fetchSemanticsNodes().size == 1 &&
            compose.onAllNodesWithTag("stop-button").fetchSemanticsNodes().isEmpty()
    }

    private fun screenshot(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun queue_branch_checkpoint_dataPlan_runGraph() {
        // Queue: a prompt typed while the agent streams waits, then goes out.
        // The test clock is paused so interactions don't wait for the stream to go idle.
        type("Hello there")
        compose.onNodeWithTag("send-button").performClick()
        compose.mainClock.autoAdvance = false
        var waited = 0
        while (compose.onAllNodesWithTag("stop-button").fetchSemanticsNodes().isEmpty()) {
            check(waited < 5_000) { "reply never started streaming" }
            Thread.sleep(50)
            waited += 50
            compose.mainClock.advanceTimeByFrame()
        }
        type("What is 6 * 7")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("queue-button").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithTag("queue-item").fetchSemanticsNodes().let { check(it.size == 1) { "queued: ${it.size}" } }
        screenshot("01-queue")
        compose.mainClock.autoAdvance = true
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("queue").fetchSemanticsNodes().isEmpty() }
        awaitIdleTurn()
        hideKeyboard()
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("tool-calculate"))

        // Data part: the offline agent streams a live `data-plan`, just above the tool call. Replies
        // are virtualized, so bring it into the viewport (how far depends on the screen) first.
        repeat(12) {
            if (compose.onAllNodesWithTag("data-plan", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return@repeat
            compose.onNodeWithTag("conversation").performTouchInput { swipeDown(startY = centerY - 150f, endY = centerY + 150f) }
            compose.waitForIdle()
        }
        compose.onAllNodesWithTag("data-plan", useUnmergedTree = true).onFirst().assertExists()

        // Branch: regenerating keeps the first answer as version 1 of 2.
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("regenerate"))
        compose.onNodeWithTag("regenerate").performClick()
        awaitIdleTurn()
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("branch-selector"))
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithTag("branch-previous").performClick()
        compose.onNodeWithText("1 / 2").assertExists()
        screenshot("02-branch")

        // Canvas: the agent run as a node graph.
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("regenerate"))
        // Several replies each have a run-graph action; click the one on screen.
        val viewport = compose.onNodeWithTag("conversation").fetchSemanticsNode().boundsInRoot
        val visible = compose.onAllNodesWithTag("run-graph").fetchSemanticsNodes()
            .indexOfFirst { it.boundsInRoot.top >= viewport.top && it.boundsInRoot.bottom <= viewport.bottom }
        check(visible >= 0) { "no run-graph action on screen" }
        compose.onAllNodesWithTag("run-graph")[visible].performSemanticsAction(SemanticsActions.OnClick)
        runCatching { compose.waitUntilAtLeastOneExists(hasTestTag("workflow-canvas"), 5_000) }.onFailure {
            val shot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
            File(dir, "fail-run-graph.png").outputStream().use { out -> shot.compress(Bitmap.CompressFormat.PNG, 100, out) }
            throw it
        }
        compose.onNode(isDialog()).assertExists()
        compose.waitUntilAtLeastOneExists(hasText(s(UiR.string.ai_graph_prompt)), 5_000)
        screenshot("03-run-graph", dialog = true)
        compose.onNodeWithContentDescription(s(UiR.string.ai_close)).performClick()

        // Checkpoint: rewind to the first turn.
        compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("checkpoint-restore"))
        compose.onNodeWithTag("checkpoint-restore").performClick()
        compose.onNodeWithTag("checkpoint-confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("What is 6 * 7").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("regenerate").assertExists()
        screenshot("04-after-checkpoint")
    }
}
