package dev.ai.elements.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.chat.AgentComputerScaffold
import dev.ai.elements.ui.chat.Conversation
import dev.ai.elements.ui.chat.rememberAgentComputerState
import dev.ai.elements.ui.theme.AiElementsTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Screenshots of the agent's computer for review, from real outputs: the page the reference server
 * rendered for its `browse` tool, a Rich progress bar and `git log` in colour, a diff and a file.
 * Opt-in (`-e screenshots true`); files go to `screens/computer-<size>-<n>.png` in the app's external
 * files dir. Run once at tablet size and once at phone size (`adb shell wm size`).
 */
@RunWith(AndroidJUnit4::class)
class AgentComputerScreenshots {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context

    private fun asset(name: String) = context.assets.open(name).use { it.readBytes() }

    private fun run(streaming: Boolean): Message {
        val page = "data:image/png;base64," + Base64.encodeToString(asset("page.png"), Base64.NO_WRAP)
        val terminal = String(asset("rich-progress.txt")) + String(asset("git-log.txt"))
        return Message(
            "a1", Role.ASSISTANT,
            listOf(
                TextPart("t0", "I'll check the pricing page, then update the README."),
                ToolPart("s1", "navigate", ToolState.OUTPUT_AVAILABLE, """{"url":"https://example.com/pricing"}""", output = "Pricing — Free, Pro, Team", title = "https://example.com/pricing", category = ToolCategory.FETCH, location = "https://example.com/pricing"),
                FilePart("f1", "image/png", page),
                ToolPart("s2", "run_command", ToolState.OUTPUT_AVAILABLE, """{"command":"./sync.sh && git log --oneline -3"}""", output = terminal, title = "./sync.sh && git log --oneline -3", category = ToolCategory.EXECUTE),
                ToolPart("s3", "read_file", ToolState.OUTPUT_AVAILABLE, """{"path":"README.md"}""", output = "     1\t# Demo workspace\n     2\t\n     3\tPrices: Free, Pro.", category = ToolCategory.READ, location = "README.md"),
                ToolPart(
                    "s4", "edit_file", if (streaming) ToolState.INPUT_AVAILABLE else ToolState.OUTPUT_AVAILABLE, """{"path":"README.md"}""",
                    output = "--- README.md\n+++ README.md\n@@ -3 +3 @@\n-Prices: Free, Pro.\n+Prices: Free, Pro, Team.",
                    category = ToolCategory.EDIT, location = "README.md",
                ),
            ) + if (streaming) emptyList() else listOf(TextPart("t1", "Updated the README with the Team plan.")),
        )
    }

    @Test fun screens() {
        assumeTrue("Pass -e screenshots true", InstrumentationRegistry.getArguments().getString("screenshots") == "true")
        val size = InstrumentationRegistry.getArguments().getString("size") ?: "phone"
        val out = File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val messages = listOf(Message("u1", Role.USER, listOf(TextPart("u", "Add the Team plan to the README"))), run(streaming = true))
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                val computer = rememberAgentComputerState()
                AgentComputerScaffold(computer, messages, Modifier.safeDrawingPadding()) { Conversation(ChatState(messages = messages)) }
            }
        }
        fun shot(n: Int) {
            compose.mainClock.advanceTimeBy(3_000)
            compose.waitForIdle()
            Thread.sleep(1_500)
            File(out, "computer-$size-$n.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        shot(1) // the live card under the reply
        compose.onNodeWithTag("agent-computer-card").performClick()
        shot(2) // following the live step: the diff
        // Step back with "previous" (short panels have no step chips).
        compose.onNodeWithTag("run-previous").performClick()
        shot(3) // the file read
        compose.onNodeWithTag("run-previous").performClick()
        shot(4) // the terminal step
        compose.onNodeWithTag("run-previous").performClick()
        shot(5) // the browser step
    }
}
