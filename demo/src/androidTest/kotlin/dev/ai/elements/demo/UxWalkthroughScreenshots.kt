package dev.ai.elements.demo

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The main user journey, captured screen by screen for a UX review (opt-in: `-e ux true`; files go
 * to `ux/<size>-<n>-<name>.png` in the app's external files dir): first launch, history, provider
 * picker, a reply, an approval, settings and components.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UxWalkthroughScreenshots {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val size = InstrumentationRegistry.getArguments().getString("size") ?: "phone"

    @Test fun walkthrough() {
        assumeTrue("Pass -e ux true", InstrumentationRegistry.getArguments().getString("ux") == "true")
        val out = File(instrumentation.targetContext.getExternalFilesDir(null), "ux").apply { mkdirs() }
        var n = 0
        fun shot(name: String) {
            compose.waitForIdle()
            Thread.sleep(1_200)
            File(out, "$size-${++n}-$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        val app = resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        fun hideKeyboard() = scenario.onActivity { it.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(it.window.decorView.windowToken, 0) }
        // A real Back key: it reaches the topmost window (a sheet or menu), as the user's would.
        fun back() { instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); compose.waitForIdle(); Thread.sleep(500) }
        fun send(text: String) {
            compose.onNodeWithTag("prompt-input").performClick().performTextInput(text)
            compose.onNodeWithTag("send-button").performClick()
            hideKeyboard()
        }
        shot("first-launch")
        // History: a drawer on phones, a pane on tablets.
        compose.onAllNodes(hasContentDescription(app.getString(R.string.conversations))).fetchSemanticsNodes().firstOrNull()?.let {
            compose.onNode(hasContentDescription(app.getString(R.string.conversations))).performClick()
            shot("history")
            back()
        }
        compose.onNodeWithTag("provider-button").performClick()
        shot("provider-menu")
        back()
        send(app.getString(R.string.sugg_agent_loop_prompt))
        compose.awaitTurnEnd(60_000)
        shot("reply")
        send(app.getString(R.string.sugg_clipboard_prompt))
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("approve"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        shot("approval")
        compose.onNodeWithTag("approval-more").performClick()
        shot("approval-more")
        back()
        compose.onNodeWithTag("approve", useUnmergedTree = true).performClick()
        compose.awaitTurnEnd(30_000)
        // Settings and Components: in the drawer on phones, on the navigation rail on tablets.
        fun open(rail: String, drawer: String) {
            if (compose.onAllNodes(hasTestTag(rail)).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag(rail).performClick()
            else {
                compose.onNode(hasContentDescription(app.getString(R.string.conversations))).performClick()
                compose.waitForIdle()
                compose.onNodeWithTag(drawer).performClick()
            }
        }
        compose.onAllNodes(hasContentDescription(app.getString(R.string.conversations))).fetchSemanticsNodes().firstOrNull()?.let {
            compose.onNode(hasContentDescription(app.getString(R.string.conversations))).performClick()
            shot("history-with-chat")
            back()
        }
        open("nav-settings", "drawer-settings")
        shot("settings")
        // Rail destinations are top level (Back would leave the app); the drawer's are pushed.
        if (compose.onAllNodes(hasTestTag("nav-components")).fetchSemanticsNodes().isEmpty()) back()
        open("nav-components", "drawer-components")
        shot("components")
        scenario.close()
    }
}
