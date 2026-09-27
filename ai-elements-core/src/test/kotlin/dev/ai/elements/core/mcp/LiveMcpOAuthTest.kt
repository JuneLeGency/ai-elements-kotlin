package dev.ai.elements.core.mcp

import dev.ai.elements.core.auth.LoopbackRedirect
import dev.ai.elements.core.auth.OAuthClient
import dev.ai.elements.core.auth.OAuthTokens
import dev.ai.elements.core.auth.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * MCP authorization end to end against the official `mcp` SDK's OAuth server
 * (`server/mcp_auth_server.py`): 401 challenge → RFC 9728 protected resource metadata →
 * RFC 8414 authorization server metadata → RFC 7591 dynamic registration → authorization code
 * with PKCE S256 and the RFC 8707 resource → token → authorized tool call → refresh.
 * Opt-in: `-PliveMcpOAuth=http://127.0.0.1:8791/mcp`
 */
class LiveMcpOAuthTest {
    private val server: String? = System.getProperty("live.mcpOAuth")

    @Test
    fun signIn_discoversRegistersAuthorizesAndCallsTools() = runBlocking<Unit> {
        assumeTrue(server != null)
        // 1. Unauthenticated: the server challenges with its resource metadata.
        val challenge = runCatching { McpClient(server!!).listTools() }.exceptionOrNull()
        assertTrue("expected an auth challenge, got $challenge", challenge is McpAuthRequiredException)
        challenge as McpAuthRequiredException
        assertNotNull(challenge.resourceMetadataUrl)

        // 2. Discovery + dynamic client registration.
        val registration = McpOAuth().discover(server!!, challenge.resourceMetadataUrl, challenge.scope, LoopbackRedirect(port = 53682))
        assertEquals("$server", registration.resource)
        assertEquals("notes", registration.scope)
        assertTrue(registration.clientId.isNotBlank())

        // 3. Authorization code + PKCE. The test server approves at once, so read the code from the redirect.
        val oauth = OAuthClient()
        val flow = registration.flow()
        val attempt = oauth.begin(flow)
        val redirect = OkHttpClient.Builder().followRedirects(false).build()
            .newCall(Request.Builder().url(attempt.url).build()).execute().use { it.header("Location") }
        assertNotNull("authorize did not redirect", redirect)
        val callback = redirect!!.toHttpUrl()
        assertEquals(attempt.state, callback.queryParameter("state"))
        val tokens = oauth.exchange(flow, attempt, callback.queryParameter("code")!!)

        // 4. Authorized calls through McpOAuthTokens, then a refresh.
        val store = object : TokenStore {
            var saved: OAuthTokens? = tokens
            override fun load() = saved
            override fun save(tokens: OAuthTokens?) { saved = tokens }
        }
        val auth = McpOAuthTokens(registration, store)
        val client = McpClient(server!!, auth = auth)
        val whoami = client.listTools().single { it.name == "whoami" }
        assertTrue(client.callTool(whoami, kotlinx.serialization.json.JsonObject(emptyMap())).toText().startsWith("Signed in"))
        val before = store.saved!!.accessToken
        assertTrue(auth.refresh())
        assertTrue(store.saved!!.accessToken != before)
        assertTrue(client.callTool(whoami, kotlinx.serialization.json.JsonObject(emptyMap())).toText().startsWith("Signed in"))
    }
}
