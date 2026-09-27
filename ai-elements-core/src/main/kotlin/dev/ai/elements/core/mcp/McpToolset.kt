package dev.ai.elements.core.mcp

import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import dev.ai.elements.core.agent.ToolCallContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.ai.elements.core.model.DataPart

/** When an MCP tool asks the user before running. */
@Serializable
enum class McpApproval {
    /** Every call. */
    ALWAYS,

    /** Unless the server marks the tool `readOnlyHint` (the default: hints are untrusted, so writes ask). */
    UNLESS_READ_ONLY,

    /** Never (only for servers you trust completely). */
    NEVER,
}

/** How an app configures one remote MCP server. Secrets (tokens) are stored separately. */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val auth: Auth = Auth.NONE,
    val approval: McpApproval = McpApproval.UNLESS_READ_ONLY,
    /** Tool names the user switched off. */
    val disabledTools: Set<String> = emptySet(),
) {
    @Serializable
    enum class Auth { NONE, BEARER, OAUTH }

    /** Prefix of this server's tools: its id reduced to `[a-z0-9_]`. */
    val toolPrefix: String get() = id.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').take(20).ifEmpty { "mcp" }
}

/**
 * One MCP tool as an [AgentTool]. The model sees `<server>__<tool>` (unique
 * across servers, within the 64-character limit), the UI the tool's title and
 * server name. Progress notifications stream as preliminary output; results
 * the server flags `isError` become tool errors.
 */
class McpAgentTool(
    private val client: McpClient,
    val tool: McpTool,
    val server: McpServerConfig,
) : AgentTool {
    override val name: String = qualifiedName(server.toolPrefix, tool.name)
    override val description: String = tool.description ?: tool.displayName
    override val parameters: JsonObject = tool.inputSchema.let {
        if (it["type"] == null) JsonObject(it + ("type" to JsonPrimitive("object"))) else it
    }
    override val title: String = tool.displayName
    override val source: String = server.name
    override val requiresApproval: Boolean = when (server.approval) {
        McpApproval.ALWAYS -> true
        McpApproval.NEVER -> false
        McpApproval.UNLESS_READ_ONLY -> tool.annotations?.readOnlyHint != true
    }

    override suspend fun execute(arguments: JsonObject): String {
        val context = ToolCallContext.current()
        // MCP Apps: the view shows with the input while the tool runs, then gets its result.
        val app = tool.uiResourceUri?.let { uri -> context?.let { McpAppPart(it, uri, arguments) } }
        app?.publish(null)
        val result = client.callTool(tool, arguments) { p ->
            val percent = p.fraction?.let { " ${(it * 100).toInt()}%" }.orEmpty()
            context?.progress(listOfNotNull(p.message, percent.trim().ifEmpty { null }).joinToString(" ").ifEmpty { "…" })
        }
        app?.publish(result)
        val text = result.toText()
        if (result.isError) throw McpException(text.ifBlank { "${tool.name} failed" })
        return text
    }

    /** The [DataPart.MCP_APP] part of one call. */
    private inner class McpAppPart(private val context: ToolCallContext, private val resourceUri: String, private val input: JsonObject) {
        suspend fun publish(result: McpToolResult?) = context.data(
            "mcp-app-${context.toolCallId}",
            DataPart.MCP_APP,
            buildJsonObject {
                put("server", server.id)
                put("serverName", server.name)
                put("resourceUri", resourceUri)
                put("tool", tool.toJson())
                put("toolCallId", context.toolCallId)
                put("input", input)
                result?.raw?.let { put("result", it) }
            },
        )
    }

    companion object {
        fun qualifiedName(prefix: String, tool: String): String {
            val clean = tool.replace(Regex("[^A-Za-z0-9_-]"), "_")
            return "${prefix}__$clean".take(64)
        }
    }
}

/** What happened when a server's tools were listed for a turn. */
sealed interface McpServerStatus {
    data class Connected(val tools: List<McpTool>, val server: McpImplementation?, val protocolVersion: String?) : McpServerStatus
    data class NeedsSignIn(val error: McpAuthRequiredException) : McpServerStatus
    data class Failed(val message: String) : McpServerStatus
}

/**
 * The enabled MCP [servers] as a [Capability]: their tools, listed in
 * parallel with a [timeoutMs] per server. A server that fails is skipped (see
 * [status]) instead of failing the turn. Tool lists are cached for
 * [cacheMs]; [invalidate] forces a refresh.
 *
 * @param clientFor the client of a server (reuse instances: they cache the
 *   protocol era and session).
 */
class McpToolset(
    private val servers: List<McpServerConfig>,
    private val clientFor: (McpServerConfig) -> McpClient,
    private val timeoutMs: Long = 15_000,
    private val cacheMs: Long = 5 * 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) : Capability {
    private val cache = mutableMapOf<String, Pair<Long, List<McpTool>>>()
    private val _status = mutableMapOf<String, McpServerStatus>()

    /** Last listing result per server id. */
    val status: Map<String, McpServerStatus> get() = synchronized(_status) { _status.toMap() }

    fun invalidate() = synchronized(cache) { cache.clear() }

    override suspend fun tools(): List<AgentTool> = coroutineScope {
        servers.filter { it.enabled }.map { server ->
            async {
                val client = clientFor(server)
                val tools = runCatching { listCached(server, client) }
                    .onFailure { e -> record(server, if (e is McpAuthRequiredException) McpServerStatus.NeedsSignIn(e) else McpServerStatus.Failed(e.message ?: e::class.simpleName.orEmpty())) }
                    .getOrNull().orEmpty()
                // MCP Apps: tools only an app may call (`visibility: ["app"]`) are not the model's.
                tools.filter { it.name !in server.disabledTools && McpTool.VISIBLE_TO_MODEL in it.visibility }
                    .map { McpAgentTool(client, it, server) }
            }
        }.awaitAll().flatten()
    }

    private suspend fun listCached(server: McpServerConfig, client: McpClient): List<McpTool> {
        synchronized(cache) { cache[server.id] }?.let { (at, tools) -> if (now() - at < cacheMs) return tools }
        val tools = withTimeoutOrNull(timeoutMs) { client.listTools() }
            ?: throw McpException("${server.name} did not answer within ${timeoutMs / 1000}s")
        synchronized(cache) { cache[server.id] = now() to tools }
        record(server, McpServerStatus.Connected(tools, client.serverInfo, client.protocolVersion))
        return tools
    }

    private fun record(server: McpServerConfig, status: McpServerStatus) = synchronized(_status) { _status[server.id] = status }
}
