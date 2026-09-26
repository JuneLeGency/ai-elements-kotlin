package dev.ai.elements.core.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in (`-PliveOAuth=true`): checks each provider's real authorization
 * server accepts our client configuration — client id, redirect URI, scopes,
 * PKCE parameters — up to the point where a person has to sign in. No
 * credentials are used.
 */
class LiveOAuthEndpointsTest {
    private val enabled = System.getProperty("live.oauth") == "true"
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    private data class Landing(val status: Int, val url: String, val botWall: Boolean)

    /** Follows redirects by hand so an OAuth error redirect is visible. */
    private fun open(url: String): Landing {
        var current = url
        repeat(6) {
            http.newCall(Request.Builder().url(current).header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AI-Elements-Test").build()).execute().use { r ->
                val location = r.header("Location")
                if (location == null) {
                    val body = r.body?.string().orEmpty()
                    return Landing(r.code, current, r.code == 403 && ("challenge-platform" in body || "Just a moment" in body))
                }
                current = r.request.url.resolve(location).toString()
            }
        }
        return Landing(0, current, false)
    }

    private fun check(name: String, flow: AuthorizationCodeFlow) {
        val url = OAuthClient(http).begin(flow).url
        val (status, landed, botWall) = open(url)
        println("[$name] status=$status botWall=$botWall landed=${landed.substringBefore('?').take(120)}")
        // A browser-check page (e.g. Cloudflare) says nothing about our parameters: inconclusive, not a pass.
        assumeFalse("$name answered a browser check to a non-browser client", botWall)
        val lower = landed.lowercase()
        assertFalse("$name rejected the request: $landed", lower.contains("error=invalid") || lower.contains("unauthorized_client") || lower.contains("redirect_uri_mismatch"))
        assertTrue("$name reachable (status $status)", status in 200..399)
    }

    @Test fun chatgptAuthorizeAccepted() { assumeTrue(enabled); check("chatgpt", OAuthProvider.ChatGptFlow) }

    @Test fun xaiAuthorizeAccepted() { assumeTrue(enabled); check("xai", OAuthProvider.XaiFlow) }

    @Test fun openRouterAuthorizeAccepted() { assumeTrue(enabled); check("openrouter", OAuthProvider.OpenRouterFlow) }

    @Test fun kimiDeviceCodeIssued() = runBlocking {
        assumeTrue(enabled)
        val device = OAuthClient(http).startDevice(OAuthProvider.KimiFlow)
        println("[kimi] user code issued (${device.userCode.length} chars), verify at ${device.verificationUri}, expires in ${device.expiresInSeconds}s")
        assertTrue(device.userCode.isNotBlank() && device.verificationUri.startsWith("https://"))
    }
}
