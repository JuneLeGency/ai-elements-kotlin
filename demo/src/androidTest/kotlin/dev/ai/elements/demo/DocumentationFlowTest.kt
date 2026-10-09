package dev.ai.elements.demo

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in real demo flow, recorded externally; the offline agent executes an actual approved tool. */
class DocumentationFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun inputPlanningApprovalExecutionAndCompletion() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("documentationFlow") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val language = args.getString("language") ?: "en"
        val app = resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        val locales = app.getSystemService(LocaleManager::class.java)
        val previous = locales.applicationLocales
        if (language == "zh-CN") locales.applicationLocales = LocaleList.forLanguageTags(language)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitForIdle()
            Thread.sleep(4_000)
            val prompt = if (language == "zh-CN") "把问候语复制到剪贴板" else "Copy a greeting to the clipboard"
            compose.onNodeWithTag("prompt-input").performClick().performTextInput(prompt)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("send-button").fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(800)
            compose.onNodeWithTag("send-button").performClick()
            scenario.hideKeyboard()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("approve").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("approve"))
            Thread.sleep(2_500)
            compose.onNodeWithTag("approve").performClick()
            compose.awaitTurnEnd(30_000)
            compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("tool-copy_to_clipboard"))
            Thread.sleep(2_000)
            compose.onNodeWithTag("conversation").performScrollToNode(hasTestTag("data-plan"))
            Thread.sleep(2_000)
            Thread.sleep(2_000)
        } finally {
            scenario.close()
            locales.applicationLocales = previous
        }
    }
}
