package dev.ai.elements.demo

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.ai.elements.ui.R as UiR
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.config.ProviderStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Streaming + scroll control, with a long streamed answer from the offline agent:
 *
 * A. at the bottom, the view follows the stream;
 * B. once the user scrolls up — even a little, inside the reply that is still
 *    growing — what they are reading does not move;
 * C. "Scroll to latest" appears, and tapping it resumes following;
 * D. accessibility / programmatic scrolls also stop following.
 *
 * The test clock is driven manually so the UI is observed mid-stream.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StreamingScrollTest {

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
        compose.mainClock.autoAdvance = true
        scenario?.close()
    }

    /** Let real time pass (the stream runs on real time) while rendering frames. */
    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            Thread.sleep(16)
            compose.mainClock.advanceTimeByFrame()
        }
    }

    private fun density() = context.resources.displayMetrics.density

    private fun streaming() = compose.onAllNodesWithTag("stop-button").fetchSemanticsNodes().isNotEmpty()

    private fun viewport(): Rect = compose.onNodeWithTag("conversation").getBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun lastAssistantBottom(): Float =
        compose.onAllNodesWithTag("assistant-row").fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom } / density()

    private fun assertFollowing(label: String, slackDp: Float = 48f) {
        val gap = viewport().bottom - lastAssistantBottom()
        assertTrue("$label: reply bottom should sit at the viewport bottom (gap=${gap}dp)", gap in -1f..slackDp)
    }

    /** Send a long prompt and return once the reply overflows the screen, with the clock paused. */
    private fun startLongStream() {
        compose.onNodeWithTag("prompt-input").performClick().performTextInput("Write a long answer")
        compose.onNodeWithTag("send-button").performClick()
        compose.mainClock.autoAdvance = false
        var waited = 0L
        while (!(streaming() && compose.onAllNodesWithText("Section 3", substring = true).fetchSemanticsNodes().isNotEmpty())) {
            pump(200)
            waited += 200
            check(waited < 60_000) { "stream never got long" }
        }
    }

    @Test
    fun followsBottom_holdsStillWhenScrolledUp_resumesOnJump() {
        startLongStream()

        // A. Following.
        repeat(5) {
            pump(300)
            assertFollowing("A")
        }

        // B. Scroll up a little — inside the reply that is growing — and keep streaming.
        compose.onNodeWithTag("conversation").performTouchInput {
            swipeDown(startY = centerY - 125f, endY = centerY + 125f, durationMillis = 400)
        }
        pump(600)
        assertTrue("still streaming after scroll-up", streaming())
        val view = viewport()
        val anchor = compose.onAllNodesWithText("Section ", substring = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top / density() > view.top && it.boundsInRoot.bottom / density() < view.bottom }
            .maxByOrNull { it.boundsInRoot.top }
        checkNotNull(anchor) { "no section visible after scroll" }
        val anchorText = anchor.config[SemanticsProperties.Text].joinToString("")
        repeat(6) {
            pump(300)
            val now = compose.onAllNodesWithText(anchorText).fetchSemanticsNodes().single().boundsInRoot.top
            assertEquals("B: '$anchorText' moved while reading (streaming=${streaming()})", anchor.boundsInRoot.top, now, 2f)
        }

        // C. Jump to latest, then it follows again.
        compose.onNodeWithContentDescription(s(UiR.string.ai_scroll_to_latest)).performClick()
        pump(800)
        if (streaming()) assertFollowing("C")
        compose.mainClock.autoAdvance = true
        compose.waitUntilExactlyOneExists(hasTestTag("regenerate"), 60_000)
        assertFollowing("C (end)", slackDp = 140f)
    }

    @Test
    fun accessibilityScrollDuringStream_isNotFoughtBack() {
        startLongStream()
        // D. A non-touch scroll (TalkBack, keyboard, app code) must stop following too.
        compose.onNodeWithTag("conversation").performScrollToIndex(0)
        repeat(8) {
            pump(250)
            assertTrue(
                "D: pulled back to the bottom after an accessibility scroll (streaming=${streaming()})",
                compose.onAllNodesWithTag("user-message").fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }
}
