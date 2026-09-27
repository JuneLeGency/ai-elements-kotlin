package dev.ai.elements.demo

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reads and drives an MCP App view in the chat: scripts run in its sandbox page, whose inner frame is the view. */
class McpAppProbe(private val compose: ComposeTestRule, private val scenario: () -> ActivityScenario<MainActivity>) {

    /** Runs [script] in the sandbox page and returns the JSON result. */
    fun js(script: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        scenario().onActivity { activity ->
            val webView = activity.window.decorView.findWebView()
            if (webView == null) latch.countDown() else webView.evaluateJavascript(script) { result = it; latch.countDown() }
        }
        latch.await(10, TimeUnit.SECONDS)
        return result
    }

    private fun View.findWebView(): WebView? = when {
        this is WebView && tag?.toString()?.startsWith("mcp-app-webview") == true -> this
        this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
        else -> null
    }

    /** The view's item, scrolled into sight (its WebView is attached only while shown). */
    fun show() = runCatching {
        compose.onNodeWithTag("conversation").performScrollToNode(
            SemanticsMatcher("an MCP App") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("mcp-app-call") == true },
        )
    }

    /** [expression] evaluated with `d`, the view's document (null before it loaded). */
    fun view(expression: String) = show().let { js("(() => { const d = document.querySelector('iframe')?.contentDocument; return d ? ($expression) : null; })()") }

    fun await(expression: String, expected: String, timeoutMs: Long = 60_000) {
        val met = runCatching { compose.waitUntil(timeoutMs) { view(expression) == "\"$expected\"" } }.isSuccess
        // On failure, say what the view shows (its value and status line).
        if (!met) assertEquals("view status: ${view("d.getElementById('status').textContent")}", "\"$expected\"", view(expression))
    }
}
