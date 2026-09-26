package dev.ai.elements.core.auth

import com.sun.net.httpserver.HttpServer
import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatBackendException
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.backend.OAuthBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class OAuthTest {
    private val http = OkHttpClient()
    private var server: HttpServer? = null

    /** A tiny authorization server; handlers get the form body and return (status, json). */
    private fun serve(routes: Map<String, (Map<String, String>) -> Pair<Int, String>>): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        routes.forEach { (path, handler) ->
            s.createContext(path) { ex ->
                val form = ex.requestBody.readBytes().decodeToString().split('&').filter { it.contains('=') }
                    .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                val (status, body) = handler(form)
                val bytes = body.toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    @After fun stop() { server?.stop(0) }

    private fun jwt(claims: String) =
        "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray()) + ".sig"

    private fun freePort() = ServerSocket(0).use { it.localPort }

    @Test fun pkce_matchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val v = Pkce.verifier()
        assertTrue(v.length in 43..128 && v.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test fun authorizeUrl_standardAndOpenRouter() {
        val chatgpt = OAuthClient(http).begin(OAuthProvider.ChatGptFlow)
        val u = chatgpt.url.toHttpUrl()
        assertEquals("code", u.queryParameter("response_type"))
        assertEquals("http://localhost:1455/auth/callback", u.queryParameter("redirect_uri"))
        assertEquals("S256", u.queryParameter("code_challenge_method"))
        assertEquals(chatgpt.state, u.queryParameter("state"))
        assertEquals("codex_cli_rs", u.queryParameter("originator"))

        val openRouter = OAuthClient(http).begin(OAuthProvider.OpenRouterFlow).url.toHttpUrl()
        assertEquals("http://localhost:3000/callback", openRouter.queryParameter("callback_url"))
        assertNull(openRouter.queryParameter("client_id"))
        assertNull(openRouter.queryParameter("state"))

        val xai = OAuthClient(http).begin(OAuthProvider.XaiFlow).url.toHttpUrl()
        assertTrue(xai.queryParameter("nonce")!!.isNotBlank())
        assertEquals("http://127.0.0.1:56121/callback", xai.queryParameter("redirect_uri"))
    }

    @Test fun loopback_ignoresOtherPaths_checksState_returnsCode() = runBlocking {
        val redirect = LoopbackRedirect(host = "127.0.0.1", port = freePort(), path = "/cb")
        LoopbackReceiver.open(redirect).use { receiver ->
            val code = async(Dispatchers.IO) { receiver.awaitCode("s1", timeoutMs = 10_000) }
            fun get(path: String) = http.newCall(Request.Builder().url("http://127.0.0.1:${redirect.port}$path").build()).execute().use { it.code }
            assertEquals(404, get("/favicon.ico"))
            assertEquals(200, get("/cb?code=abc%2F1&state=s1"))
            assertEquals("abc/1", code.await())
        }
        LoopbackReceiver.open(redirect).use { receiver ->
            val code = async(Dispatchers.IO) { runCatching { receiver.awaitCode("s1", timeoutMs = 10_000) } }
            http.newCall(Request.Builder().url("http://127.0.0.1:${redirect.port}/cb?code=x&state=forged").build()).execute().close()
            assertTrue(code.await().exceptionOrNull() is OAuthException)
        }
    }

    @Test fun exchange_readsIdTokenClaims_refreshKeepsUnrotatedRefreshToken() = runBlocking {
        val seen = CopyOnWriteArrayList<Map<String, String>>()
        val id = jwt("""{"email":"a@b.c","https://api.openai.com/auth":{"chatgpt_account_id":"acct_1"}}""")
        val base = serve(mapOf("/token" to { form ->
            seen += form
            if (form["grant_type"] == "authorization_code") {
                200 to """{"access_token":"at1","refresh_token":"rt1","id_token":"$id","expires_in":3600}"""
            } else {
                200 to """{"access_token":"at2","expires_in":3600}"""
            }
        }))
        val flow = OAuthProvider.ChatGptFlow.copy(tokenUrl = "$base/token")
        val client = OAuthClient(http)
        val attempt = client.begin(flow)
        val t1 = client.exchange(flow, attempt, "the-code")
        assertEquals("acct_1", t1.accountId)
        assertEquals("a@b.c", t1.email)
        assertEquals("the-code", seen[0]["code"])
        assertEquals("http://localhost:1455/auth/callback", seen[0]["redirect_uri"])
        assertEquals(Pkce.challenge(seen[0]["code_verifier"]!!), attempt.url.toHttpUrl().queryParameter("code_challenge"))

        val t2 = client.refresh("$base/token", flow.clientId, null, t1)
        assertEquals("at2", t2.accessToken)
        assertEquals("rt1", t2.refreshToken)
        assertEquals("acct_1", t2.accountId)
        assertTrue(!t1.toString().contains("at1") && !t1.toString().contains("rt1"))
    }

    @Test fun deviceFlow_pendingSlowDownThenTokens() = runBlocking {
        val polls = AtomicInteger()
        val base = serve(mapOf(
            "/device" to { _ -> 200 to """{"device_code":"dc","user_code":"ABCD-1234","verification_uri":"https://x/verify","expires_in":60,"interval":1}""" },
            "/token" to { form ->
                assertEquals("urn:ietf:params:oauth:grant-type:device_code", form["grant_type"])
                when (polls.incrementAndGet()) {
                    1 -> 400 to """{"error":"authorization_pending"}"""
                    else -> 200 to """{"access_token":"at","refresh_token":"rt","expires_in":60}"""
                }
            },
        ))
        val flow = DeviceCodeFlow("$base/device", "$base/token", "client")
        val client = OAuthClient(http)
        val device = client.startDevice(flow)
        assertEquals("ABCD-1234", device.userCode)
        assertEquals("at", client.pollDevice(flow, device.copy(intervalSeconds = 1)).accessToken)
        assertEquals(2, polls.get())

        val denied = serve(mapOf("/token" to { _ -> 400 to """{"error":"access_denied"}""" }))
        try {
            client.pollDevice(flow.copy(tokenUrl = "$denied/token"), device.copy(intervalSeconds = 1))
            fail("expected denial")
        } catch (e: OAuthException) {
            assertTrue(e.message!!.contains("declined"))
        }
    }

    private class MemoryStore(var tokens: OAuthTokens?) : TokenStore {
        override fun load() = tokens
        override fun save(tokens: OAuthTokens?) { this.tokens = tokens }
    }

    @Test fun tokenSource_refreshesOnceForConcurrentCallers() = runBlocking {
        val refreshes = AtomicInteger()
        val base = serve(mapOf("/token" to { _ ->
            refreshes.incrementAndGet()
            Thread.sleep(200)
            200 to """{"access_token":"fresh","refresh_token":"rt2","expires_in":3600}"""
        }))
        val store = MemoryStore(OAuthTokens("stale", "rt1", expiresAtMs = 0))
        val source = TokenSource(OAuthProvider.XAI, store, OAuthClient(http), refreshUrlOverride = "$base/token")
        val results = (1..5).map { async { source.fresh().accessToken } }.awaitAll()
        assertEquals(listOf("fresh"), results.distinct())
        assertEquals(1, refreshes.get())
        assertEquals("rt2", store.tokens!!.refreshToken)
    }

    @Test fun oauthBackend_refreshesAndRetriesOnce_on401() = runBlocking {
        val base = serve(mapOf("/token" to { _ -> 200 to """{"access_token":"good","expires_in":3600}""" }))
        val store = MemoryStore(OAuthTokens("revoked", "rt", expiresAtMs = Long.MAX_VALUE))
        val source = TokenSource(OAuthProvider.XAI, store, OAuthClient(http), refreshUrlOverride = "$base/token")
        val used = mutableListOf<String>()
        val backend = OAuthBackend(source) { t ->
            used += t.accessToken
            ChatBackend { _ ->
                flow {
                    if (t.accessToken == "revoked") throw ChatBackendException("HTTP 401", statusCode = 401)
                    emit(ChatEvent.TextDelta("t", "hi"))
                }
            }
        }
        val events = backend.stream(emptyList()).toList()
        assertEquals(listOf("revoked", "good"), used)
        assertEquals(1, events.size)
    }
}
