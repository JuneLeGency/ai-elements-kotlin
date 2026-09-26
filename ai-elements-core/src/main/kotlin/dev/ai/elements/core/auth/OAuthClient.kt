package dev.ai.elements.core.auth

import dev.ai.elements.core.backend.DefaultHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The network half of OAuth: build authorize URLs, exchange and refresh
 * tokens, run the device flow. No Android dependencies, so it is unit-tested
 * on the JVM against a local server. Tokens and codes never appear in
 * exception messages.
 */
class OAuthClient(private val http: OkHttpClient = DefaultHttpClient) {

    /** One authorization-code attempt: open [url] in the browser, then [exchange]. */
    class Attempt internal constructor(val url: String, internal val verifier: String, internal val challenge: String, val state: String?)

    fun begin(flow: AuthorizationCodeFlow): Attempt {
        val verifier = Pkce.verifier()
        val challenge = Pkce.challenge(verifier)
        val state = if (flow.usesState) Pkce.randomToken() else null
        val url = flow.authorizeUrl.toHttpUrl().newBuilder().apply {
            if (flow.clientId.isNotEmpty()) {
                addQueryParameter("response_type", "code")
                addQueryParameter("client_id", flow.clientId)
            }
            addQueryParameter(flow.redirectParam, flow.redirect.uri)
            flow.scopes?.let { addQueryParameter("scope", it) }
            state?.let { addQueryParameter("state", it) }
            addQueryParameter("code_challenge", challenge)
            addQueryParameter("code_challenge_method", "S256")
            if (flow.nonce) addQueryParameter("nonce", Pkce.randomToken())
            flow.extraAuthorizeParams.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build().toString()
        return Attempt(url, verifier, challenge, state)
    }

    suspend fun exchange(flow: AuthorizationCodeFlow, attempt: Attempt, code: String): OAuthTokens = tokenRequest(
        flow.tokenUrl,
        buildMap {
            put("grant_type", "authorization_code")
            put("code", code)
            put("redirect_uri", flow.redirect.uri)
            put("client_id", flow.clientId)
            put("code_verifier", attempt.verifier)
            flow.clientSecret?.let { put("client_secret", it) }
            if (flow.echoChallengeOnExchange) {
                put("code_challenge", attempt.challenge)
                put("code_challenge_method", "S256")
            }
            putAll(flow.extraTokenParams)
        },
        previous = null,
    )

    /** Exchanges the refresh token; keeps the old refresh token when the server doesn't rotate it. */
    suspend fun refresh(
        tokenUrl: String,
        clientId: String,
        clientSecret: String?,
        tokens: OAuthTokens,
        extraParams: Map<String, String> = emptyMap(),
    ): OAuthTokens {
        val refresh = tokens.refreshToken ?: throw OAuthException("Signed out: no refresh token. Sign in again.")
        return tokenRequest(
            tokenUrl,
            buildMap {
                put("grant_type", "refresh_token")
                put("refresh_token", refresh)
                put("client_id", clientId)
                clientSecret?.let { put("client_secret", it) }
                putAll(extraParams)
            },
            previous = tokens,
        )
    }

    suspend fun startDevice(flow: DeviceCodeFlow): DeviceAuthorization {
        val json = post(flow.deviceAuthorizationUrl, form(buildMap {
            put("client_id", flow.clientId)
            flow.scopes?.let { put("scope", it) }
        }))
        val o = json.second ?: throw OAuthException("Device sign-in failed (HTTP ${json.first}).")
        if (json.first !in 200..299) throw OAuthException(o.errorMessage() ?: "Device sign-in failed (HTTP ${json.first}).")
        return DeviceAuthorization(
            deviceCode = o.string("device_code") ?: throw OAuthException("Device sign-in: no device_code."),
            userCode = o.string("user_code") ?: throw OAuthException("Device sign-in: no user_code."),
            verificationUri = o.string("verification_uri") ?: o.string("verification_url")
                ?: throw OAuthException("Device sign-in: no verification URI."),
            verificationUriComplete = o.string("verification_uri_complete") ?: o.string("verification_url_complete"),
            expiresInSeconds = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 900,
            intervalSeconds = o["interval"]?.jsonPrimitive?.longOrNull ?: 5,
        )
    }

    /**
     * Polls until the user approves (RFC 8628 §3.5): waits on
     * `authorization_pending`, backs off 5s on `slow_down`, fails on
     * `access_denied` / `expired_token` or when the code expires.
     */
    suspend fun pollDevice(flow: DeviceCodeFlow, device: DeviceAuthorization): OAuthTokens {
        var interval = device.intervalSeconds.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + device.expiresInSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000)
            val (status, json) = post(flow.tokenUrl, form(mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "device_code" to device.deviceCode,
                "client_id" to flow.clientId,
            )))
            if (status in 200..299 && json != null && json.string("access_token") != null) return json.toTokens(previous = null)
            when (json?.string("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "access_denied" -> throw OAuthException("Sign-in was declined.")
                "expired_token" -> throw OAuthException("The code expired. Start again.")
                else -> throw OAuthException(json?.errorMessage() ?: "Device sign-in failed (HTTP $status).")
            }
        }
        throw OAuthException("The code expired. Start again.")
    }

    /**
     * OpenRouter's PKCE flow for apps: the result is an API key owned by the
     * user (not a token), so it is stored like any other API key.
     * See https://openrouter.ai/docs/use-cases/oauth-pkce.
     */
    suspend fun openRouterKey(keysUrl: String, code: String, attempt: Attempt): String {
        val body = buildJsonObject {
            put("code", code)
            put("code_verifier", attempt.verifier)
            put("code_challenge_method", "S256")
        }.toString().toRequestBody(JSON)
        val (status, json) = post(keysUrl, body)
        if (status !in 200..299) throw OAuthException(json?.errorMessage() ?: "OpenRouter sign-in failed (HTTP $status).")
        return json?.string("key") ?: throw OAuthException("OpenRouter returned no key.")
    }

    private suspend fun tokenRequest(url: String, params: Map<String, String>, previous: OAuthTokens?): OAuthTokens {
        val (status, json) = post(url, form(params))
        if (status !in 200..299 || json == null || json.string("access_token") == null) {
            throw OAuthException(json?.errorMessage() ?: "Token request failed (HTTP $status).")
        }
        return json.toTokens(previous)
    }

    private suspend fun post(url: String, body: RequestBody): Pair<Int, JsonObject?> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            response.code to runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        }
    }

    private fun form(params: Map<String, String>): RequestBody =
        FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

private fun JsonObject.errorMessage(): String? {
    val error = this["error"]
    val nested = (error as? JsonObject)?.let { it.string("message") ?: it.string("code") }
    return string("error_description") ?: nested ?: string("error") ?: string("message")
}

/** Maps a token response; claims from the ID token fill in the account id and email. */
internal fun JsonObject.toTokens(previous: OAuthTokens?): OAuthTokens {
    val idToken = string("id_token") ?: previous?.idToken
    val claims = jwtClaims(idToken)
    val openAiAuth = claims?.get("https://api.openai.com/auth") as? JsonObject
    return OAuthTokens(
        accessToken = string("access_token")!!,
        refreshToken = string("refresh_token") ?: previous?.refreshToken,
        expiresAtMs = this["expires_in"]?.jsonPrimitive?.longOrNull?.let { System.currentTimeMillis() + it * 1000 },
        idToken = idToken,
        accountId = openAiAuth?.string("chatgpt_account_id") ?: previous?.accountId,
        email = claims?.string("email") ?: previous?.email,
    )
}
