package dev.ai.elements.demo

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.harness.browser.WebBrowser
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
}
