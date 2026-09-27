package dev.ai.elements.core.config

import android.content.Context
import dev.ai.elements.core.auth.OAuthTokens
import dev.ai.elements.core.auth.TokenStore
import dev.ai.elements.core.mcp.McpAuth
import dev.ai.elements.core.mcp.McpClient
import dev.ai.elements.core.mcp.McpOAuthRegistration
import dev.ai.elements.core.mcp.McpOAuthTokens
import dev.ai.elements.core.mcp.McpServerConfig
import dev.ai.elements.core.mcp.McpToolset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Saved remote MCP servers. Configs live in SharedPreferences; bearer tokens,
 * OAuth registrations and tokens are encrypted in [secrets]. Clients are
 * cached per server so the negotiated protocol era and session are reused.
 *
 * @param clientCapabilities declared by every client, e.g. [dev.ai.elements.core.mcp.McpApps.CLIENT_CAPABILITIES]
 *   when the app renders MCP Apps (`ai-elements-mcp-apps`).
 */
class McpServerStore(
    context: Context,
    private val secrets: SecretStore = SecretStore(context),
    private val clientCapabilities: JsonObject = JsonObject(emptyMap()),
) {
    private val prefs = context.getSharedPreferences("ai_elements_mcp", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(McpServerConfig.serializer())

    private val _servers = MutableStateFlow(load())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val clients = mutableMapOf<String, Pair<McpServerConfig, McpClient>>()

    fun upsert(server: McpServerConfig) {
        _servers.update { list -> if (list.any { it.id == server.id }) list.map { if (it.id == server.id) server else it } else list + server }
        synchronized(clients) { clients.remove(server.id) }
        save()
    }

    fun remove(id: String) {
        _servers.update { list -> list.filterNot { it.id == id } }
        listOf(BEARER, REGISTRATION, TOKENS).forEach { secrets.put(it + id, "") }
        synchronized(clients) { clients.remove(id) }
        save()
    }

    fun bearerToken(id: String): String = secrets.get(BEARER + id)
    fun setBearerToken(id: String, token: String) { secrets.put(BEARER + id, token.trim()); synchronized(clients) { clients.remove(id) } }

    /** OAuth client registration from [dev.ai.elements.core.mcp.McpOAuth.discover]. */
    fun registration(id: String): McpOAuthRegistration? =
        secrets.get(REGISTRATION + id).takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString(McpOAuthRegistration.serializer(), it) }.getOrNull() }

    fun setRegistration(id: String, registration: McpOAuthRegistration?) {
        secrets.put(REGISTRATION + id, registration?.let { json.encodeToString(McpOAuthRegistration.serializer(), it) }.orEmpty())
        synchronized(clients) { clients.remove(id) }
    }

    fun tokenStore(id: String): TokenStore = object : TokenStore {
        override fun load(): OAuthTokens? =
            secrets.get(TOKENS + id).takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString(OAuthTokens.serializer(), it) }.getOrNull() }

        override fun save(tokens: OAuthTokens?) = secrets.put(TOKENS + id, tokens?.let { json.encodeToString(OAuthTokens.serializer(), it) }.orEmpty())
    }

    /** The client for [server], with its configured credentials. */
    fun client(server: McpServerConfig): McpClient = synchronized(clients) {
        clients[server.id]?.takeIf { it.first == server }?.second ?: McpClient(server.url, auth = auth(server), capabilities = clientCapabilities).also { clients[server.id] = server to it }
    }

    /** The client of the saved server [id] (e.g. for an MCP App of one of its tools), or null. */
    fun client(id: String): McpClient? = _servers.value.firstOrNull { it.id == id }?.let(::client)

    /** The enabled servers as a capability for a turn. */
    fun toolset(): McpToolset = McpToolset(_servers.value.filter { it.enabled }, ::client)

    private fun auth(server: McpServerConfig): McpAuth? = when (server.auth) {
        McpServerConfig.Auth.NONE -> null
        McpServerConfig.Auth.BEARER -> McpAuth.bearer(bearerToken(server.id))
        McpServerConfig.Auth.OAUTH -> registration(server.id)?.let { McpOAuthTokens(it, tokenStore(server.id)) }
    }

    private fun load(): List<McpServerConfig> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    private fun save() = prefs.edit().putString(KEY, json.encodeToString(listSerializer, _servers.value)).apply()

    private companion object {
        const val KEY = "servers"
        const val BEARER = "mcp.bearer."
        const val REGISTRATION = "mcp.registration."
        const val TOKENS = "mcp.tokens."
    }
}
