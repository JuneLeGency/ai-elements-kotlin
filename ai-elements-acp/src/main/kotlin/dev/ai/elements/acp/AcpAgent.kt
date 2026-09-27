package dev.ai.elements.acp

import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ClientCapabilities
import com.agentclientprotocol.model.FileSystemCapability
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.ReadTextFileResponse
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.WriteTextFileResponse
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.agentclientprotocol.transport.Transport
import com.agentclientprotocol.transport.WebSocketTransport
import dev.ai.elements.core.http.DefaultHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * A connection to an [Agent Client Protocol](https://agentclientprotocol.com) agent — a coding
 * agent such as Claude Code, Codex or Gemini CLI, or any agent built on an ACP SDK (e.g. Pydantic
 * AI Harness `run_acp_stdio`). This app is the ACP *client*: it creates sessions, sends prompts,
 * renders `session/update`s and answers `session/request_permission` (and, when [files] is given,
 * `fs/read_text_file` / `fs/write_text_file`).
 *
 * The connection opens on first use, is shared by every conversation ([AcpBackend]) using this
 * agent, and reopens after a failure. Sessions are kept per conversation; a conversation whose
 * session is gone is resumed with `session/load` when the agent supports it.
 *
 * Transports: [process] (stdio, the transport the ACP spec defines) and [webSocket] (the ACP
 * Kotlin SDK's WebSocket transport, one JSON-RPC message per text frame, for agents on another
 * machine — the spec's remote transport is still a draft).
 *
 * @param name shown in the UI until the agent introduces itself in `initialize`.
 * @param cwd the session's working directory on the agent's machine (absolute, ACP `session/new`).
 * @param files the client file system offered to the agent; null offers none.
 * @param connect opens a transport; called again after the connection failed.
 */
class AcpAgent(
    val name: String,
    private val cwd: String = "/",
    private val files: AcpFileSystem? = null,
    private val connect: suspend (scope: CoroutineScope) -> Transport,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var connection: Connection? = null

    /** The turn in progress per session, which permission requests are routed to. */
    internal val turns = ConcurrentHashMap<String, AcpTurn>()

    private class Connection(val scope: CoroutineScope, val client: Client, val info: AgentInfo) {
        val sessions = mutableMapOf<String, ClientSession>()
    }

    /** What the agent said about itself in `initialize` (connects if needed). */
    suspend fun info(): AgentInfo = connection().info

    /** The agent's display name: its `agentInfo` title or name, else [name]. */
    suspend fun displayName(): String = info().implementation?.let { it.title ?: it.name } ?: name

    /** The session [id] if it is still open (or can be loaded), else a new one. */
    internal suspend fun session(id: String?): ClientSession = lock.withLock {
        val conn = connectionLocked()
        id?.let { conn.sessions[it] }?.let { return it }
        val parameters = SessionCreationParameters(cwd, mcpServers = emptyList())
        val session = if (id != null && conn.info.capabilities.loadSession) {
            runCatching { conn.client.loadSession(SessionId(id), parameters, ::operations) }.getOrNull()
        } else null
        (session ?: conn.client.newSession(parameters, ::operations)).also { conn.sessions[it.sessionId.value] = it }
    }

    /** Forget the connection after a failure; the next turn reconnects. */
    internal suspend fun reset() = lock.withLock { connection?.scope?.cancel(); connection = null }

    override fun close() {
        connection?.scope?.cancel()
        connection = null
        scope.cancel()
    }

    private suspend fun connection(): Connection = lock.withLock { connectionLocked() }

    private suspend fun connectionLocked(): Connection {
        connection?.let { return it }
        val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job]))
        try {
            val protocol = Protocol(child, connect(child))
            val client = Client(protocol)
            protocol.start()
            val info = client.initialize(
                ClientInfo(
                    capabilities = ClientCapabilities(
                        fs = files?.let { FileSystemCapability(readTextFile = true, writeTextFile = !it.readOnly) },
                        terminal = false,
                    ),
                    implementation = Implementation("ai-elements-kotlin", BuildConfig.VERSION, "AI Elements"),
                ),
            )
            return Connection(child, client, info).also { connection = it }
        } catch (e: Throwable) {
            child.cancel()
            throw e
        }
    }

    private fun operations(sessionId: SessionId, @Suppress("UNUSED_PARAMETER") response: Any?): ClientSessionOperations =
        object : ClientSessionOperations {
            override suspend fun requestPermissions(
                toolCall: SessionUpdate.ToolCallUpdate,
                permissions: List<PermissionOption>,
                _meta: JsonElement?,
            ): RequestPermissionResponse =
                turns[sessionId.value]?.permission(toolCall, permissions)
                    // No turn is listening (e.g. it was stopped): the prompt turn is cancelled.
                    ?: RequestPermissionResponse(RequestPermissionOutcome.Cancelled)

            // Updates outside a prompt turn (e.g. `session/load` replaying history) are not shown:
            // the conversation already holds them.
            override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) = Unit

            override suspend fun fsReadTextFile(path: String, line: UInt?, limit: UInt?, _meta: JsonElement?): ReadTextFileResponse =
                ReadTextFileResponse(requireNotNull(files) { "No file system" }.read(path, line?.toInt(), limit?.toInt()))

            override suspend fun fsWriteTextFile(path: String, content: String, _meta: JsonElement?): WriteTextFileResponse {
                val fs = requireNotNull(files) { "No file system" }
                check(!fs.readOnly) { "The file system is read-only" }
                fs.write(path, content)
                return WriteTextFileResponse()
            }
        }

    companion object {
        /**
         * An agent reached over WebSocket at [url] (`ws://` or `wss://`), with the ACP Kotlin SDK's
         * WebSocket transport. [client] carries TLS, proxies and timeouts.
         */
        fun webSocket(
            url: String,
            name: String = url,
            cwd: String = "/",
            files: AcpFileSystem? = null,
            client: OkHttpClient = DefaultHttpClient,
        ): AcpAgent {
            val http = HttpClient(OkHttp) {
                engine { preconfigured = client }
                install(WebSockets)
            }
            return AcpAgent(name, cwd, files) { scope ->
                val session = http.webSocketSession(url)
                WebSocketTransport(parentScope = scope, wss = session)
            }
        }

        /**
         * An agent started as a subprocess speaking ACP over stdio (the spec's transport), e.g.
         * `listOf("npx", "@zed-industries/claude-code-acp")` on a desktop JVM or in a sandbox.
         */
        fun process(
            command: List<String>,
            name: String = command.first(),
            cwd: String = "/",
            files: AcpFileSystem? = null,
            environment: Map<String, String> = emptyMap(),
        ): AcpAgent = AcpAgent(name, cwd, files) { scope ->
            val process = ProcessBuilder(command).apply { environment().putAll(environment) }.start()
            // Drain stderr (agents log there) so a full pipe never blocks the agent.
            scope.launch(Dispatchers.IO) { process.errorStream.bufferedReader().forEachLine { } }
            val writer = process.outputStream.bufferedWriter()
            StdioTransport(
                parentScope = scope,
                ioDispatcher = Dispatchers.IO,
                input = process.inputStream.bufferedReader().lineSequence().asFlow()
                    .flowOn(Dispatchers.IO)
                    .onCompletion { process.destroy() },
                output = { line ->
                    writer.write(line)
                    writer.newLine()
                    writer.flush()
                },
                name = "acp:${command.first()}",
            )
        }
    }
}

/**
 * The client file system an [AcpAgent] offers (`fs/read_text_file`, `fs/write_text_file`), e.g.
 * a workspace or a folder the user shared. Paths are absolute, as the agent sends them.
 */
interface AcpFileSystem {
    /** Only reading is offered when true. */
    val readOnly: Boolean get() = true

    /** The file's text, from 1-based [line] and at most [limit] lines when given. */
    suspend fun read(path: String, line: Int?, limit: Int?): String

    suspend fun write(path: String, content: String): Unit = error("read-only")
}

/** A prompt turn in progress; see [AcpBackend]. */
internal fun interface AcpTurn {
    suspend fun permission(toolCall: SessionUpdate.ToolCallUpdate, options: List<PermissionOption>): RequestPermissionResponse
}
