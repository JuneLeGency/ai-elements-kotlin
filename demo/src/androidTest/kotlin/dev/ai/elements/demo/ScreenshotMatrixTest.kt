package dev.ai.elements.demo

import android.app.LocaleManager
import android.app.UiModeManager
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
 * The UI review matrix of GOAL.md (W4): the same conversation (offline provider: Markdown,
 * a tool call, a plan, Mermaid, code) in light and dark, in every shipped language. Screenshots
 * go to the app's external files dir (`screens/<size>-<theme>-<lang>.png`); run it once per
 * screen size (e.g. `adb shell wm size 2560x1600` for a tablet). Opt-in: `-e screenshots true`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotMatrixTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as DemoApplication

    @Test
    fun themesAndLanguages() {
        assumeTrue("Pass -e screenshots true", InstrumentationRegistry.getArguments().getString("screenshots") == "true")
        val size = InstrumentationRegistry.getArguments().getString("size") ?: "phone"
        val out = File(app.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val uiMode = app.getSystemService(UiModeManager::class.java)
        val locales = app.getSystemService(LocaleManager::class.java)
        try {
            for ((theme, mode) in listOf("light" to UiModeManager.MODE_NIGHT_NO, "dark" to UiModeManager.MODE_NIGHT_YES)) {
                for (lang in listOf("en", "zh-CN", "zh-TW", "ja")) {
                    val file = File(out, "$size-$theme-$lang.png")
                    if (file.exists()) continue // resumable: a rerun fills in what is missing
                    uiMode.setApplicationNightMode(mode)
                    locales.applicationLocales = LocaleList.forLanguageTags(lang)
                    resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        compose.onNodeWithTag("prompt-input").performClick().performTextInput("Show me a plan, a tool call and a diagram")
                        compose.onNodeWithTag("send-button").performClick()
                        compose.awaitTurnEnd(60_000)
                        scenario.onActivity { WindowCompat.getInsetsController(it.window, it.window.decorView).hide(WindowInsetsCompat.Type.ime()) }
                        // The test clock only moves when told: run the streaming fade-ins to the end, then give
                        // WebView diagrams real time to draw.
                        compose.mainClock.advanceTimeBy(5_000)
                        compose.waitForIdle()
                        Thread.sleep(4_000)
                        val shot = instrumentation.uiAutomation.takeScreenshot()
                        file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    }
                }
            }
        } finally {
            uiMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
            locales.applicationLocales = LocaleList.getEmptyLocaleList()
        }
    }
}
