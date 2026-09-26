package dev.ai.elements.core.auth

import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in, real ChatGPT (Codex) round trip with an existing Codex CLI login:
 *
 *     ./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveCodexTest*' -PliveCodexAuth=/path/to/auth.json
 *
 * Reads the access token from that `auth.json` in memory only, never prints
 * it, and **never refreshes**: ChatGPT rotates refresh tokens, so refreshing
 * here would sign that Codex CLI out. If the access token is expired the test
 * is skipped — run `codex` once to renew it.
 */
class LiveCodexTest {
    private val authPath = System.getProperty("live.codexAuth").orEmpty()
    private val model = System.getProperty("live.codexModel").orEmpty().ifBlank { OAuthProvider.CHATGPT.defaultModel }

    private class ReadOnlyStore(private val tokens: OAuthTokens) : TokenStore {
        override fun load() = tokens
        override fun save(tokens: OAuthTokens?) = error("the live test must not refresh or persist tokens")
    }

    private fun codexCliTokens(file: File): OAuthTokens {
        val t = Json.parseToJsonElement(file.readText()).jsonObject["tokens"]!!.jsonObject
        val access = t.string("access_token")!!
        val exp = jwtClaims(access)?.get("exp")?.jsonPrimitive?.longOrNull
        val id = t.string("id_token")
        return OAuthTokens(
            accessToken = access,
            idToken = id,
            expiresAtMs = exp?.let { it * 1000 },
            accountId = t.string("account_id") ?: (jwtClaims(id)?.get("https://api.openai.com/auth") as? kotlinx.serialization.json.JsonObject)?.string("chatgpt_account_id"),
        )
    }

    @Test fun chatgptSubscription_streamsAReply() = runBlocking {
        assumeTrue("set -PliveCodexAuth=/path/to/auth.json", authPath.isNotBlank())
        val tokens = codexCliTokens(File(authPath))
        assumeTrue("access token expired — run `codex` once to renew it", !tokens.expiresSoon(slackMs = 60_000))
        val profile = ProviderProfile(
            id = "live-chatgpt", name = "ChatGPT", kind = OAuthProvider.CHATGPT.kind,
            baseUrl = OAuthProvider.CHATGPT.baseUrl, model = model, useTools = false, oauth = OAuthProvider.CHATGPT,
        )
        val backend = profile.createOAuthBackend(TokenSource(OAuthProvider.CHATGPT, ReadOnlyStore(tokens)))
        val prompt = Message("u1", Role.USER, listOf(TextPart("t1", "Reply with exactly: pong")))
        val events = backend.stream(listOf(prompt)).toList()
        val text = events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }
        println("LiveCodex model=$model events=${events.size} text=${text.take(80)}")
        assertTrue("expected a reply, got events=${events.map { it::class.simpleName }}", text.contains("pong", ignoreCase = true))
    }

    @Test fun chatgptSubscription_runsOnDeviceToolLoop() = runBlocking {
        assumeTrue("set -PliveCodexAuth=/path/to/auth.json", authPath.isNotBlank())
        val tokens = codexCliTokens(File(authPath))
        assumeTrue("access token expired — run `codex` once to renew it", !tokens.expiresSoon(slackMs = 60_000))
        val profile = ProviderProfile(
            id = "live-chatgpt-tools", name = "ChatGPT", kind = OAuthProvider.CHATGPT.kind,
            baseUrl = OAuthProvider.CHATGPT.baseUrl, model = model, useTools = true, oauth = OAuthProvider.CHATGPT,
        )
        val backend = profile.createOAuthBackend(TokenSource(OAuthProvider.CHATGPT, ReadOnlyStore(tokens)))
        val prompt = Message("u1", Role.USER, listOf(TextPart("t1", "Use the calculate tool to compute 1234 * 5678, then state the result.")))
        val events = backend.stream(listOf(prompt)).toList()
        val outputs = events.filterIsInstance<ChatEvent.ToolOutput>()
        val text = events.filterIsInstance<ChatEvent.TextDelta>().joinToString("") { it.delta }
        println("LiveCodex tools: calls=${outputs.size} output=${outputs.firstOrNull()?.output} text=${text.take(100)}")
        assertTrue("expected the calculate tool to run", outputs.any { it.output.contains("7006652") || it.output.contains("7,006,652") })
        assertTrue("expected the result in the reply: $text", text.contains("7006652") || text.contains("7,006,652"))
    }
}
