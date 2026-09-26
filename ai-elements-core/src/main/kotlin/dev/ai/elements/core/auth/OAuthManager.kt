package dev.ai.elements.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Parameters describing one OAuth authorization-code + PKCE provider.
 *
 * Keeping these as plain values (not a per-vendor subclass) is what makes the
 * framework generic: to support a new provider, construct an [OAuthManager] with
 * its [authUrl] / [tokenUrl] / [clientId] / [callbackPort] / [scopes] and the
 * right [tokenResponseFormat].
 */
data class OAuthSpec(
    val instanceId: String,
    val providerLabel: String,
    val authUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val clientSecret: String = "",
    val callbackPort: Int = 8465,
    val fallbackPorts: List<Int> = emptyList(),
    val redirectPath: String = "/callback",
    val scopes: String = "",
    val extraAuthParams: Map<String, String> = emptyMap(),
    /** How the token endpoint expects the request + how it responds. */
    val tokenResponseFormat: TokenResponseFormat = TokenResponseFormat.JSON,
) {
    val redirectUri: String get() = "http://localhost:$callbackPort$redirectPath"
}

/** Whether the token exchange is `application/x-www-form-urlencoded` or JSON. */
enum class TokenResponseFormat { FORM, JSON }

/**
 * A generic OAuth authorization-code + PKCE flow.
 *
 * Flow ([startLogin]):
 * 1. Build a [Pkce] triple.
 * 2. Bind the loopback [OAuthCallbackServer] (so it's listening before we open a browser).
 * 3. Open the authorization URL (browser / custom tab) via [openBrowser].
 * 4. Await the redirect; verify `state`.
 * 5. Exchange `code` (+ `code_verifier`) at [OAuthSpec.tokenUrl] for a token.
 * 6. Persist it to [tokenStore].
 *
 * The class is transport-agnostic enough to unit-test: the browser opener and
 * the token HTTP client are injectable.
 */
class OAuthManager(
    val spec: OAuthSpec,
    private val tokenStore: OAuthTokenStore,
    private val openBrowser: (Uri) -> Unit,
    private val client: OkHttpClient = DEFAULT_CLIENT,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** True when a token is stored for this instance. */
    fun isAuthenticated(): Boolean = tokenStore.isAuthenticated(spec.instanceId)

    suspend fun validAccessToken(): String? = tokenStore.validAccessToken(spec.instanceId)

    fun logout() = tokenStore.logout(spec.instanceId)

    /** Run the full login flow on the main thread (launches browser, suspends). */
    suspend fun startLogin(): OAuthToken = withContext(Dispatchers.Main) {
        val pkce = Pkce.generate()
        val authUri = buildAuthorizationUri(pkce)

        val server = OAuthCallbackServer(
            port = spec.callbackPort,
            fallbackPorts = spec.fallbackPorts,
            redirectPath = spec.redirectPath,
        )
        val redirect = server.await(expectedState = pkce.state) {
            openBrowser(authUri)
        }

        exchangeAndSave(redirect.code, pkce.verifier)
    }

    private fun buildAuthorizationUri(pkce: Pkce): Uri =
        Uri.parse(spec.authUrl).buildUpon()
            .appendQueryParameter("client_id", spec.clientId)
            .appendQueryParameter("redirect_uri", spec.redirectUri)
            .appendQueryParameter("response_type", "code")
            .apply { if (spec.scopes.isNotEmpty()) appendQueryParameter("scope", spec.scopes) }
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .also { b -> spec.extraAuthParams.forEach { (k, v) -> b.appendQueryParameter(k, v) } }
            .build()

    private suspend fun exchangeAndSave(code: String, codeVerifier: String): OAuthToken =
        withContext(Dispatchers.IO) {
            val body = when (spec.tokenResponseFormat) {
                TokenResponseFormat.FORM -> FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("client_id", spec.clientId)
                    .also { if (spec.clientSecret.isNotEmpty()) it.add("client_secret", spec.clientSecret) }
                    .add("redirect_uri", spec.redirectUri)
                    .add("code_verifier", codeVerifier)
                    .build()
                TokenResponseFormat.JSON ->
                    """{"grant_type":"authorization_code","code":"$code","client_id":"${spec.clientId}","redirect_uri":"${spec.redirectUri}","code_verifier":"$codeVerifier"}"""
                        .toRequestBody(JSON_MEDIA)
            }
            val request = okhttp3.Request.Builder().url(spec.tokenUrl).post(body).build()
            val responseText = client.newCall(request).execute().use { resp ->
                val t = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw OAuthException("token exchange failed: HTTP ${resp.code} $t")
                t
            }
            val token = parseToken(responseText)
            tokenStore.save(spec.instanceId, token)
            token
        }

    private fun parseToken(raw: String): OAuthToken {
        val o = json.parseToJsonElement(raw).jsonObject
        val accessToken = o["access_token"]?.jsonPrimitive?.content
            ?: throw OAuthException("token response missing access_token")
        val expiresIn = o["expires_in"]?.jsonPrimitive?.content?.toLongOrNull()
        val refresh = o["refresh_token"]?.jsonPrimitive?.content
        val email = (o["email"] ?: o["account_email"])?.jsonPrimitive?.content
        return OAuthToken(
            accessToken = accessToken,
            refreshToken = refresh,
            expiresAtMillis = expiresIn?.let { System.currentTimeMillis() + it * 1000L } ?: 0L,
            accountEmail = email,
        )
    }

    /** Refresh the stored token and persist the new one. */
    suspend fun refreshToken(): OAuthToken {
        val current = tokenStore.load(spec.instanceId)
            ?: throw OAuthException("no stored token to refresh")
        val body = when (spec.tokenResponseFormat) {
            TokenResponseFormat.FORM -> FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", current.refreshToken ?: throw OAuthException("no refresh_token stored"))
                .add("client_id", spec.clientId)
                .also { if (spec.clientSecret.isNotEmpty()) it.add("client_secret", spec.clientSecret) }
                .build()
            TokenResponseFormat.JSON ->
                """{"grant_type":"refresh_token","refresh_token":"${current.refreshToken}","client_id":"${spec.clientId}"}"""
                    .toRequestBody(JSON_MEDIA)
        }
        val request = okhttp3.Request.Builder().url(spec.tokenUrl).post(body).build()
        val responseText = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                val t = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw OAuthException("refresh failed: HTTP ${resp.code} $t")
                t
            }
        }
        val token = parseToken(responseText)
        tokenStore.save(spec.instanceId, token)
        return token
    }

    companion object {
        val DEFAULT_CLIENT = OkHttpClient.Builder().build()
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}

/** Any OAuth flow failure (auth URL, token exchange, refresh). */
class OAuthException(message: String) : RuntimeException(message)
