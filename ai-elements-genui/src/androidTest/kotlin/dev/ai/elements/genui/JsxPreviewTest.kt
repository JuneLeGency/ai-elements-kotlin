package dev.ai.elements.genui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.jsx.JsxPreview
import dev.ai.elements.genui.jsx.jsxCodeBlocks
import dev.ai.elements.ui.chat.AiElementsRenderers
import dev.ai.elements.ui.chat.LocalAiElementsRenderers
import dev.ai.elements.ui.markdown.MarkdownContent
import dev.ai.elements.ui.theme.AiElementsTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class JsxPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val jsx = """
        <Card>
          <h2>Trip to {city}</h2>
          <p>Leaving <b>{date}</b> · {nights} nights</p>
          <input name="note" placeholder="Note for the hotel" />
          <div className="flex">
            <Button variant="primary" onClick={book}>Book</Button>
            <Button onClick={later}>Later</Button>
          </div>
        </Card>
    """.trimIndent()

    private fun shot(name: String) {
        val out = File(instrumentation.targetContext.getExternalFilesDir(null), "jsx").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { b -> File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) } }
    }

    @Test
    fun streamingJsx_rendersProgressively_keepsInput_andDispatches() {
        val bindings = Json.parseToJsonElement("""{"city":"Kyoto","date":"May 3","nights":4,"note":""}""").jsonObject
        var source by mutableStateOf("")
        val actions = mutableListOf<A2uiAction>()
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Surface { JsxPreview(source, Modifier.padding(12.dp), bindings = bindings, onAction = { actions += it }) }
            }
        }
        // Stream it in chunks: every prefix must render.
        jsx.chunked(17).runningReduce { a, b -> a + b }.forEach { chunk ->
            source = chunk
            compose.waitForIdle()
        }
        compose.onNodeWithText("Trip to Kyoto", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Leaving May 3 · 4 nights", useUnmergedTree = true, substring = true).assertExists()
        // Type, then let more JSX arrive: the input survives the update.
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("Late check-in")
        source = jsx.replace("</Card>", "<small>Prices include taxes</small></Card>")
        compose.waitForIdle()
        compose.onNodeWithText("Late check-in", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Prices include taxes", useUnmergedTree = true).assertExists()
        shot("preview")
        compose.onNodeWithText("Book", useUnmergedTree = true).performClick()
        assertEquals("book", actions.single().name)
        assertTrue(actions.single().dataModel.toString().contains("Late check-in"))
    }

    @Test
    fun jsxFencesInChatMarkdown_renderAsPreviews() {
        val markdown = "Here is the card:\n\n```jsx\n<Card><h3>Hello from JSX</h3><Button variant=\"primary\">OK</Button></Card>\n```\n"
        compose.setContent {
            AiElementsTheme(dynamicColor = false) {
                Surface {
                    CompositionLocalProvider(LocalAiElementsRenderers provides AiElementsRenderers(codeBlocks = mapOf("jsx" to jsxCodeBlocks()))) {
                        Column(Modifier.padding(12.dp)) { MarkdownContent(markdown) }
                    }
                }
            }
        }
        compose.onNodeWithText("Hello from JSX", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("jsx-preview", useUnmergedTree = true).assertExists()
        shot("fence")
        compose.onNodeWithText("Code", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("jsx-preview", useUnmergedTree = true).assertDoesNotExist()
    }
}
