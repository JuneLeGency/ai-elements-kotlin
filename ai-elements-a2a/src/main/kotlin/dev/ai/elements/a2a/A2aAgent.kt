package dev.ai.elements.a2a

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.a2aproject.sdk.client.Client
import org.a2aproject.sdk.client.ClientEvent
import org.a2aproject.sdk.client.config.ClientConfig
import org.a2aproject.sdk.client.http.A2ACardResolver
import org.a2aproject.sdk.client.http.A2AHttpClient
import org.a2aproject.sdk.client.http.android.AndroidA2AHttpClient
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext
import org.a2aproject.sdk.spec.AgentCard
import org.a2aproject.sdk.spec.CancelTaskParams
import org.a2aproject.sdk.spec.Message
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * One remote [A2A](https://a2a-protocol.org) agent, reached through the
 * official A2A Java SDK (JSON-RPC binding, A2A 1.0) and its Android HTTP
 * client.
 *
 * @param agentUrl the agent's base URL (its card is read from
 *   `/.well-known/agent-card.json`) or the card's URL.
 * @param headers sent with every request, e.g. `Authorization`.
 */
class A2aAgent(
    val agentUrl: String,
    private val headers: Map<String, String> = emptyMap(),
    private val httpClient: A2AHttpClient = AndroidA2AHttpClient(),
) {
    private val lock = Mutex()
    private var card: AgentCard? = null
    private var client: Client? = null

    /** The agent card (fetched once). */
    suspend fun card(): AgentCard = lock.withLock { card ?: fetchCard().also { card = it } }

    private suspend fun fetchCard(): AgentCard = withContext(Dispatchers.IO) {
        val builder = A2ACardResolver.builder().httpClient(httpClient)
        if (agentUrl.endsWith(".json")) {
            val path = agentUrl.substringAfter("://").substringAfter('/', "")
            builder.baseUrl(agentUrl.removeSuffix(path).trimEnd('/')).agentCardPath("/$path")
        } else {
            builder.baseUrl(agentUrl.trimEnd('/'))
        }
        if (headers.isNotEmpty()) builder.authHeaders(headers)
        builder.build().agentCard
    }

    private suspend fun client(): Client {
        val card = card()
        return lock.withLock {
            client ?: Client.builder(card)
                .withTransport(JSONRPCTransport::class.java, JSONRPCTransportConfigBuilder().httpClient(httpClient))
                .clientConfig(
                    ClientConfig.builder()
                        .setStreaming(true)
                        .setAcceptedOutputModes(listOf("text/plain", "text/markdown", "application/json", "image/png", "image/jpeg"))
                        .build(),
                )
                .build()
                .also { client = it }
        }
    }

    /**
     * Send [message] and stream what the agent answers: a `Message`, or a
     * `Task` followed by its status and artifact updates. The flow completes
     * when the agent's stream ends; cancelling it stops reading.
     */
    fun send(message: Message): Flow<ClientEvent> = callbackFlow {
        val client = client()
        val streaming = card().capabilities().streaming()
        val context = ClientCallContext(emptyMap(), headers)
        client.sendMessage(
            message,
            listOf(BiConsumer<ClientEvent, AgentCard> { event, _ -> trySend(event) }),
            // The SDK signals the end of a stream with a null error.
            Consumer<Throwable> { error: Throwable? -> if (error == null) close() else close(error) },
            context,
        )
        // A blocking (non-streaming) send has delivered its single event by now.
        if (!streaming) close()
        awaitClose()
    }.flowOn(Dispatchers.IO)

    /** Ask the agent to cancel [taskId]. */
    suspend fun cancel(taskId: String) {
        val client = client()
        withContext(Dispatchers.IO) { runCatching { client.cancelTask(CancelTaskParams(taskId), ClientCallContext(emptyMap(), headers)) } }
    }
}
