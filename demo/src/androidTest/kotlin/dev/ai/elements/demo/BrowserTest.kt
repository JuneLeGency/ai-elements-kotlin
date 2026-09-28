package dev.ai.elements.demo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.agent.runTool
import dev.ai.elements.core.agent.runToolWithContent
import dev.ai.elements.harness.browser.WebBrowser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** The in-app browser (off-screen WebView) filling and submitting a real form on the reference server. */
@RunWith(AndroidJUnit4::class)
class BrowserTest {
    /**
     * The app in front, as when a user watches the agent browse: MIUI freezes background processes
     * (cgroup freezer), which would stop the off-screen WebView mid-test.
     */
    @get:org.junit.Rule
    val app = androidx.test.ext.junit.rules.ActivityScenarioRule(MainActivity::class.java)

    private val server = InstrumentationRegistry.getArguments().getString("agentServer") ?: "http://10.0.2.2:8788"

    @Test
    fun navigateSnapshotTypeSelectSubmit() = runBlocking<Unit> {
        assumeTrue(runCatching { (URL("$server/health").openConnection() as HttpURLConnection).responseCode == 200 }.getOrDefault(false))
        val browser = WebBrowser(ApplicationProvider.getApplicationContext())
        val tools = browser.tools().associateBy { it.name }
        suspend fun call(name: String, args: JsonObject = JsonObject(emptyMap())) = tools.getValue(name).execute(args)

        val page = call("navigate", buildJsonObject { put("url", "$server/browser-test") })
        assertTrue(page, page.startsWith("URL: $server/browser-test\nTitle: Order form") && page.contains("Order a coffee"))

        val snapshot = call("snapshot")
        assertTrue(snapshot, snapshot.contains("- textbox \"Your name\" [ref=") && snapshot.contains("- button \"Order\" [ref="))
        val nameRef = Regex("textbox \"Your name\" \\[ref=(e\\d+)]").find(snapshot)!!.groupValues[1]

        call("type_text", buildJsonObject { put("selector", "aria-ref=$nameRef"); put("text", "Ann") })
        call("select_option", buildJsonObject { put("selector", "select[name=size]"); put("values", JsonArray(listOf(JsonPrimitive("Large")))) })
        val done = call("press_key", buildJsonObject { put("key", "Enter"); put("selector", "aria-ref=$nameRef") })
        assertTrue(done, done.contains("Thanks Ann!") && done.contains("large coffee"))

        val back = call("go_back")
        assertTrue(back, back.startsWith("Went back. URL: $server/browser-test"))
        val about = call("click", buildJsonObject { put("selector", "a") })
        assertTrue(about, about.contains("Open since 2026"))
        runCatching { call("navigate", buildJsonObject { put("url", "file:///etc/hosts") }) }.exceptionOrNull().let { assertTrue(it is IllegalArgumentException) }
        browser.close()
    }

    /** Page-changing calls attach a screenshot of the viewport, and it shows the page (not a blank frame). */
    @Test
    fun navigate_attachesAScreenshotOfThePage() = runBlocking<Unit> {
        val browser = WebBrowser(ApplicationProvider.getApplicationContext())
        val events = kotlinx.coroutines.flow.flow<dev.ai.elements.core.chat.ChatEvent> {
            runTool(browser.tools(), dev.ai.elements.core.chat.ToolApprover.AlwaysApprove, "c1", "navigate", buildJsonObject { put("url", "$server/browser-test") }.toString())
        }.toList()
        val shot = events.filterIsInstance<dev.ai.elements.core.chat.ChatEvent.File>().single()
        assertTrue(shot.mediaType == "image/jpeg" && shot.url.startsWith("data:image/jpeg;base64,"))
        val bytes = android.util.Base64.decode(shot.url.substringAfter("base64,"), android.util.Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val colors = (0 until bitmap.height step 16).flatMap { y -> (0 until bitmap.width step 16).map { x -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("screenshot looks blank (${colors.size} colours)", colors.size > 3)
    }

    /** `screenshot` returns the page to the model as a PNG (Harness contract); the chat shows it too. */
    @Test
    fun screenshot_returnsThePageToTheModel() = runBlocking<Unit> {
        assumeTrue(runCatching { (URL("$server/health").openConnection() as HttpURLConnection).responseCode == 200 }.getOrDefault(false))
        val browser = WebBrowser(ApplicationProvider.getApplicationContext())
        val tools = browser.tools()
        lateinit var result: dev.ai.elements.core.agent.ToolResult
        val events = kotlinx.coroutines.flow.flow<dev.ai.elements.core.chat.ChatEvent> {
            runTool(tools, dev.ai.elements.core.chat.ToolApprover.AlwaysApprove, "c1", "navigate", buildJsonObject { put("url", "$server/browser-test") }.toString())
            result = runToolWithContent(tools, dev.ai.elements.core.chat.ToolApprover.AlwaysApprove, "c2", "screenshot", "{}")
        }.toList()
        assertTrue(result.text, result.text == "Screenshot captured. URL: $server/browser-test")
        val png = result.content.single()
        assertTrue(png.mediaType == "image/png")
        val bytes = android.util.Base64.decode(png.url.substringAfter("base64,"), android.util.Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        // The desktop viewport at CSS pixels (Playwright's default): image coordinates are page coordinates.
        assertTrue("${bitmap.width}x${bitmap.height}", bitmap.width == 1280 && bitmap.height == 720)
        // Every pixel: a desktop page is mostly margin, and a sparse grid can miss all of its text.
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        assertTrue("screenshot looks blank", pixels.toSet().size > 3)
        // Shown with the call as well.
        assertTrue(events.any { it is dev.ai.elements.core.chat.ChatEvent.File && it.id.startsWith("c2-") })
        lateinit var full: dev.ai.elements.core.agent.ToolResult
        kotlinx.coroutines.flow.flow<dev.ai.elements.core.chat.ChatEvent> {
            full = runToolWithContent(tools, dev.ai.elements.core.chat.ToolApprover.AlwaysApprove, "c3", "screenshot", """{"full_page":true}""")
        }.toList()
        assertTrue(full.text, full.text.startsWith("Screenshot captured.") && full.content.size == 1)
        browser.close()
    }

    /** A click that follows a link returns once the new page has loaded (as Playwright auto-waits). */
    @Test
    fun click_waitsForTheNavigationItStarts() = runBlocking<Unit> {
        assumeTrue(runCatching { (URL("$server/health").openConnection() as HttpURLConnection).responseCode == 200 }.getOrDefault(false))
        val browser = WebBrowser(ApplicationProvider.getApplicationContext())
        val tools = browser.tools().associateBy { it.name }
        tools.getValue("navigate").execute(buildJsonObject { put("url", "$server/browser-test") })
        val clicked = tools.getValue("click").execute(buildJsonObject { put("selector", "a") })
        assertTrue(clicked, clicked.contains("URL: $server/browser-test/about") && clicked.contains("Open since 2026"))
        browser.close()
    }

    /** The steps view's screenshot outlines the element an action targeted. */
    @Test
    fun actionScreenshot_outlinesTheTarget() = runBlocking<Unit> {
        assumeTrue(runCatching { (URL("$server/health").openConnection() as HttpURLConnection).responseCode == 200 }.getOrDefault(false))
        val browser = WebBrowser(ApplicationProvider.getApplicationContext())
        val tools = browser.tools()
        val events = kotlinx.coroutines.flow.flow<dev.ai.elements.core.chat.ChatEvent> {
            runTool(tools, dev.ai.elements.core.chat.ToolApprover.AlwaysApprove, "c1", "navigate", buildJsonObject { put("url", "$server/browser-test") }.toString())
            runTool(tools, dev.ai.elements.core.chat.ToolApprover.AlwaysApprove, "c2", "type_text", """{"selector":"input[name=name]","text":"Ann"}""")
        }.toList()
        fun orange(id: String): Int {
            val file = events.filterIsInstance<dev.ai.elements.core.chat.ChatEvent.File>().single { it.id.startsWith(id) }
            val bytes = android.util.Base64.decode(file.url.substringAfter("base64,"), android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            return (0 until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x ->
                val c = bitmap.getPixel(x, y)
                android.graphics.Color.red(c) > 220 && android.graphics.Color.green(c) in 60..120 && android.graphics.Color.blue(c) < 80
            } }
        }
        assertTrue("the navigation has no outline", orange("c1-") == 0)
        assertTrue("the typed-into field is outlined", orange("c2-") > 100)
        browser.close()
    }
}
