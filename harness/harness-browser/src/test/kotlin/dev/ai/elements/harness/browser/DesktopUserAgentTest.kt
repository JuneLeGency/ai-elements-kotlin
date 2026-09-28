package dev.ai.elements.harness.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopUserAgentTest {
    /** Chrome's "Desktop site" user agent, with the WebView's Chrome major version. */
    @Test fun desktopUserAgent_keepsTheChromeVersion() {
        val webView = "Mozilla/5.0 (Linux; Android 15; 24018RPACC Build/AQ3A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.207 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
            WebBrowser.desktopUserAgent(webView),
        )
    }
}
