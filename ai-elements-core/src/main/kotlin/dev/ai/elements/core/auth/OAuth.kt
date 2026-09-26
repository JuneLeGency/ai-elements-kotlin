package dev.ai.elements.core.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/**
 * Tokens from an OAuth sign-in. Persist them encrypted (the demo uses
 * `SecretStore`); never log them.
 *
 * @property expiresAtMs wall-clock expiry of [accessToken], if the server said.
 * @property accountId provider account the API needs alongside the token
 *   (e.g. ChatGPT's workspace id), taken from the ID token.
 */
@Serializable
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMs: Long? = null,
    val idToken: String? = null,
    val accountId: String? = null,
    val email: String? = null,
) {
    /** True when the access token is expired or about to be (default 5 minutes of slack). */
    fun expiresSoon(nowMs: Long = System.currentTimeMillis(), slackMs: Long = 5 * 60_000L): Boolean =
        expiresAtMs != null && nowMs >= expiresAtMs - slackMs

    override fun toString() = "OAuthTokens(email=$email, expiresAtMs=$expiresAtMs, refresh=${refreshToken != null})"
}

/** Base64url without padding (RFC 4648 §5); Kotlin's codec works on every API level (java.util.Base64 needs 26). */
private val Base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** PKCE (RFC 7636) with the S256 method. */
object Pkce {
    private val random = SecureRandom()

    /** A 43–128 character verifier from 64 random bytes. */
    fun verifier(): String = randomToken(64)

    fun challenge(verifier: String): String =
        Base64Url.encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** Opaque URL-safe random value, for `state` and `nonce`. */
    fun randomToken(bytes: Int = 32): String = Base64Url.encode(ByteArray(bytes).also(random::nextBytes))
}

/** Reads a JWT's claims without verifying it (only for display / routing, never for trust). */
fun jwtClaims(jwt: String?): JsonObject? = runCatching {
    val payload = jwt!!.split('.')[1]
    Json.parseToJsonElement(Base64Url.decode(payload).decodeToString()).jsonObject
}.getOrNull()

internal fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

/** How the browser hands the authorization code back to the app. */
data class LoopbackRedirect(val host: String = "localhost", val port: Int, val path: String = "/callback") {
    val uri: String get() = "http://$host:$port$path"
}

/** A way to sign in: [AuthorizationCodeFlow] (browser) or [DeviceCodeFlow] (code on another screen). */
sealed interface SignInFlow

/**
 * The authorization-code grant with PKCE (RFC 6749 §4.1 + RFC 7636) through
 * a loopback redirect (RFC 8252 §7.3): the app listens on
 * [LoopbackRedirect.port] while the user signs in in the browser.
 */
data class AuthorizationCodeFlow(
    val authorizeUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val redirect: LoopbackRedirect,
    val scopes: String? = null,
    val clientSecret: String? = null,
    /** Extra query parameters some servers require on the authorize URL. */
    val extraAuthorizeParams: Map<String, String> = emptyMap(),
    /** Send a `nonce` (OpenID Connect servers that require one). */
    val nonce: Boolean = false,
    /** Also send the `code_challenge` pair on exchange (a few servers insist). */
    val echoChallengeOnExchange: Boolean = false,
    /** Name of the redirect parameter (OpenRouter calls it `callback_url`). */
    val redirectParam: String = "redirect_uri",
    /** Whether the server round-trips `state` (OpenRouter does not). */
    val usesState: Boolean = true,
) : SignInFlow

/** The device authorization grant (RFC 8628): show a code, the user approves it elsewhere. */
data class DeviceCodeFlow(
    val deviceAuthorizationUrl: String,
    val tokenUrl: String,
    val clientId: String,
    val scopes: String? = null,
) : SignInFlow

/** What the user needs to approve a device-flow sign-in. */
data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
) {
    /** Open this: it has the code pre-filled when the server offers that. */
    val openUrl: String get() = verificationUriComplete ?: verificationUri
}

/** A sign-in that failed in a way the user should see (denied, expired, wrong state…). */
class OAuthException(message: String) : Exception(message)
