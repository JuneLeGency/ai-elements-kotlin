package dev.ai.elements.genui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.a2ui.A2uiState
import dev.ai.elements.genui.a2ui.A2uiSurfaceView
import dev.ai.elements.ui.theme.AiElementsTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A2UI surfaces rendered natively from the official Basic Catalog examples. */
@RunWith(AndroidJUnit4::class)
class A2uiRenderTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun example(name: String): A2uiState {
        val text = instrumentation.context.assets.open("a2ui/$name").bufferedReader().readText()
        return A2uiState().also { state -> (Json.parseToJsonElement(text).jsonObject["messages"] as JsonArray).forEach { state.process(it) } }
    }

    @Test
    fun everyOfficialExample_rendersWithoutErrors_andIsScreenshotted() {
        val names = instrumentation.context.assets.list("a2ui")!!.sorted()
        val out = File(instrumentation.targetContext.getExternalFilesDir(null), "a2ui").apply { mkdirs() }
        var current by mutableStateOf(example(names.first()))
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Surface {
                    Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        current.surfaces.forEach { A2uiSurfaceView(it) }
                    }
                }
            }
        }
        names.forEach { name ->
            current = example(name)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(2_000)
            Thread.sleep(1_500) // real time for images to download (the test clock does not wait for I/O)
            compose.waitForIdle()
            val surfaceId = current.surfaces.first().id
            compose.onNodeWithTag("a2ui-surface-$surfaceId").assertExists()
            instrumentation.uiAutomation.takeScreenshot()?.let { shot ->
                File(out, name.removeSuffix(".json") + ".png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 80, it) }
            }
        }
    }

    @Test
    fun loginForm_twoWayBindingChecksAndAction() {
        val state = example("09_login-form.json")
        val actions = mutableListOf<A2uiAction>()
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Surface { A2uiSurfaceView(state.surfaces.single(), onAction = { actions += it }) }
            }
        }
        val surface = state.surfaces.single()
        val button = compose.onNodeWithText("Sign in", useUnmergedTree = true)
        // Empty email / password fail their checks: the action is disabled.
        compose.onNodeWithTag("a2ui-login_btn").assertIsNotEnabled()
        compose.onNodeWithTag("a2ui-email_field").performTextReplacement("jane@example.com")
        compose.onNodeWithTag("a2ui-password_field").performTextReplacement("hunter22")
        assertEquals("jane@example.com", surface.read("/email")!!.jsonPrimitive.content) // two-way binding
        compose.onNodeWithTag("a2ui-login_btn").assertIsEnabled().performClick()
        val action = actions.single()
        assertEquals("gallery-login-form", action.surfaceId)
        assertTrue(action.context.toString(), action.context.toString().contains("jane@example.com") || action.dataModel.toString().contains("jane@example.com"))
        button.assertExists()
    }
}
