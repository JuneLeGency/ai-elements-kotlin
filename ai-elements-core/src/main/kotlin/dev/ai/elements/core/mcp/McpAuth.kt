package dev.ai.elements.core.mcp

import dev.ai.elements.core.auth.AuthorizationCodeFlow
import dev.ai.elements.core.auth.LoopbackRedirect
import dev.ai.elements.core.auth.OAuthClient
import dev.ai.elements.core.auth.OAuthException
import dev.ai.elements.core.auth.OAuthTokens
import dev.ai.elements.core.auth.TokenStore
import dev.ai.elements.core.http.DefaultHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Supplies bearer tokens to an [McpClient]. */
interface McpAuth {
    /** The token to send, or null for none. */
    suspend fun token(): String?

    /** Called after a 401; return true when a new token is available and the request should be retried once. */
    suspend fun refresh(): Boolean = false

    companion object {
        /** A fixed bearer token (a personal access token or API key). */
        fun bearer(token: String): McpAuth = object : McpAuth {
            override suspend fun token() = token.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * What [McpOAuth.discover] learned about an MCP server's authorization and
 * the client it registered — persist it with the tokens.
 */
@Serializable
data class McpOAuthRegistration(
    val resource: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val clientSecret: String? = null,
    val scope: String? = null,
    val redirectUri: String,
) {
    /** The authorization-code + PKCE flow for [OAuthClient.begin], with the RFC 8707 `resource` indicator. */
    fun flow(): AuthorizationCodeFlow {
        val redirect = redirectUri.toHttpUrl()
        return AuthorizationCodeFlow(
            authorizeUrl = authorizationEndpoint,
            tokenUrl = tokenEndpoint,
            clientId = clientId,
            clientSecret = clientSecret,
            redirect = LoopbackRedirect(redirect.host, redirect.port, redirect.encodedPath),
            scopes = scope,
            extraAuthorizeParams = mapOf("resource" to resource),
            extraTokenParams = mapOf("resource" to resource),
        )
    }
}

/**
 * MCP authorization (OAuth 2.1) discovery and client registration, per the
 * MCP authorization spec:
 *
 * 1. Protected Resource Metadata (RFC 9728) from the 401 challenge's
 *    `resource_metadata`, else the well-known URIs of the server URL;
 * 2. Authorization Server Metadata (RFC 8414, then OpenID Connect discovery);
 *    PKCE `S256` support is required;
 * 3. Dynamic Client Registration (RFC 7591) as a public client with a
 *    loopback redirect (RFC 8252), unless a client id is supplied.
 *
 * Then run [McpOAuthRegistration.flow] with [OAuthClient] and keep the tokens
 * in a [TokenStore] behind [McpOAuthTokens].
 */
class McpOAuth(private val http: OkHttpClient = DefaultHttpClient) {

    suspend fun discover(
        serverUrl: String,
        resourceMetadataUrl: String? = null,
        scope: String? = null,
        redirect: LoopbackRedirect,
        clientName: String = "AI Elements",
        clientId: String? = null,
    ): McpOAuthRegistration {
        val server = serverUrl.toHttpUrl()
        val prm = (listOfNotNull(resourceMetadataUrl) + wellKnown(server, "oauth-protected-resource"))
            .firstNotNullOfOrNull { getJson(it) }
            ?: throw OAuthException("$serverUrl does not publish OAuth protected resource metadata")
        val issuer = (prm["authorization_servers"] as? JsonArray)?.firstOrNull()?.string
            ?: throw OAuthException("$serverUrl lists no authorization server")
        val asMeta = authorizationServerMetadata(issuer.toHttpUrl())
            ?: throw OAuthException("No authorization server metadata at $issuer")
        val methods = (asMeta["code_challenge_methods_supported"] as? JsonArray)?.mapNotNull { it.string }
        if (methods == null || "S256" !in methods) throw OAuthException("$issuer does not support PKCE (S256); refusing to sign in")
        val authorize = asMeta.str("authorization_endpoint") ?: throw OAuthException("$issuer has no authorization endpoint")
        val token = asMeta.str("token_endpoint") ?: throw OAuthException("$issuer has no token endpoint")
        val resource = prm.str("resource") ?: canonicalResource(server)
        val scopes = scope ?: (prm["scopes_supported"] as? JsonArray)?.mapNotNull { it.string }?.joinToString(" ")?.ifBlank { null }

        val (id, secret) = if (clientId != null) clientId to null else register(asMeta, redirect, clientName)
        return McpOAuthRegistration(resource, authorize, token, id, secret, scopes, redirect.uri)
    }

    private suspend fun register(asMeta: JsonObject, redirect: LoopbackRedirect, clientName: String): Pair<String, String?> {
        val endpoint = asMeta.str("registration_endpoint")
            ?: throw OAuthException("The authorization server does not support dynamic client registration; configure a client id")
        val body = buildJsonObject {
            put("client_name", clientName)
            put("redirect_uris", JsonArray(listOf(JsonPrimitive(redirect.uri))))
            put("grant_types", JsonArray(listOf(JsonPrimitive("authorization_code"), JsonPrimitive("refresh_token"))))
            put("response_types", JsonArray(listOf(JsonPrimitive("code"))))
            put("token_endpoint_auth_method", "none")
        }
        val json = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(endpoint).post(body.toString().toRequestBody("application/json".toMediaType())).build())
                .execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw OAuthException("Client registration failed: HTTP ${response.code} ${text.take(200)}")
                    Json.parseToJsonElement(text).jsonObject
                }
        }
        return (json.str("client_id") ?: throw OAuthException("Client registration returned no client_id")) to json.str("client_secret")
    }

    private suspend fun authorizationServerMetadata(issuer: HttpUrl): JsonObject? {
        val path = issuer.encodedPath.trimEnd('/')
        val root = issuer.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/')
        val candidates = if (path.isEmpty()) listOf(
            "$root/.well-known/oauth-authorization-server",
            "$root/.well-known/openid-configuration",
        ) else listOf(
            "$root/.well-known/oauth-authorization-server$path",
            "$root/.well-known/openid-configuration$path",
            "$root$path/.well-known/openid-configuration",
        )
        return candidates.firstNotNullOfOrNull { getJson(it) }
    }

    private fun wellKnown(server: HttpUrl, name: String): List<String> {
        val root = server.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/')
        val path = server.encodedPath.trimEnd('/')
        return listOfNotNull("$root/.well-known/$name$path".takeIf { path.isNotEmpty() }, "$root/.well-known/$name")
    }

    private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
                if (!response.isSuccessful) null else Json.parseToJsonElement(response.body!!.string()).jsonObject
            }
        }.getOrNull()
    }

    companion object {
        /** RFC 8707 canonical URI of the MCP server: scheme and host lowercase, no fragment, no trailing slash. */
        fun canonicalResource(server: HttpUrl): String =
            server.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
    }
}

/**
 * [McpAuth] over OAuth tokens kept in [store]: refreshes shortly before
 * expiry and after a 401, single-flight, sending the `resource` indicator.
 */
class McpOAuthTokens(
    private val registration: McpOAuthRegistration,
    private val store: TokenStore,
    private val client: OAuthClient = OAuthClient(),
    private val now: () -> Long = System::currentTimeMillis,
) : McpAuth {
    private val mutex = Mutex()

    val signedIn: Boolean get() = store.load() != null

    override suspend fun token(): String? = mutex.withLock {
        val current = store.load() ?: return@withLock null
        if (!current.expiresSoon(now()) || current.refreshToken == null) return@withLock current.accessToken
        refreshLocked(current)?.accessToken ?: current.accessToken
    }

    override suspend fun refresh(): Boolean = mutex.withLock {
        val current = store.load() ?: return@withLock false
        refreshLocked(current) != null
    }

    private suspend fun refreshLocked(current: OAuthTokens): OAuthTokens? {
        if (current.refreshToken == null) return null
        return runCatching {
            client.refresh(registration.tokenEndpoint, registration.clientId, registration.clientSecret, current, mapOf("resource" to registration.resource))
        }.getOrNull()?.also(store::save)
    }
}

