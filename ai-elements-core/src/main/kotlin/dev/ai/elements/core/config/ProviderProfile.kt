package dev.ai.elements.core.config

import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.auth.OAuthProvider
import dev.ai.elements.core.auth.TokenSource
import dev.ai.elements.core.protocol.agui.AgUiBackend
import dev.ai.elements.core.provider.anthropic.AnthropicBackend
import dev.ai.elements.core.http.DefaultHttpClient
import dev.ai.elements.core.provider.gemini.GeminiBackend
import dev.ai.elements.core.provider.mock.MockAgentBackend
import dev.ai.elements.core.auth.OAuthBackend
import dev.ai.elements.core.provider.ollama.OllamaBackend
import dev.ai.elements.core.provider.openai.OpenAiChatBackend
import dev.ai.elements.core.provider.openai.OpenAiResponsesBackend
import dev.ai.elements.core.protocol.aisdk.UiMessageStreamBackend
import dev.ai.elements.core.http.getJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/** The wire protocol a provider speaks; decides which [ChatBackend] is built. */
@Serializable
enum class ProviderKind(val label: String, val needsKey: Boolean, val serverSideAgent: Boolean = false) {
    MOCK("Offline demo", needsKey = false),
    AGENT_SERVER("AI SDK stream (v5 UI / v4 Data)", needsKey = false, serverSideAgent = true),
    AG_UI("AG-UI", needsKey = false, serverSideAgent = true),
    OPENAI("OpenAI Chat Completions", needsKey = true),
    OPENAI_RESPONSES("OpenAI Responses", needsKey = true),
    ANTHROPIC("Anthropic Messages", needsKey = true),
    GEMINI("Gemini", needsKey = true),
    OLLAMA("Ollama native", needsKey = false),

    /** A remote A2A agent; built by `ai-elements-a2a` (`A2aBackend`), not by [ProviderProfile.createBackend]. */
    A2A("A2A agent", needsKey = false, serverSideAgent = true),

    /**
     * An Agent Client Protocol agent (a coding agent) at a `ws://` / `wss://` URL; built by
     * `ai-elements-acp` (`AcpBackend`), not by [ProviderProfile.createBackend].
     */
    ACP("ACP agent", needsKey = false, serverSideAgent = true),
}

/**
 * One configured agent provider. The API key is not part of the profile — it
 * lives encrypted in [SecretStore], keyed by [id].
 *
 * @property useTools expose on-device tools to client-side agent loops (servers
 *   that run the agent themselves bring their own tools).
 */
@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String = "",
    val model: String = "",
    val useTools: Boolean = true,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val builtIn: Boolean = false,
    /** Signed in with OAuth instead of an API key (see [OAuthProvider]). */
    val oauth: OAuthProvider? = null,
) {
    /** Signed in with tokens (OpenRouter's sign-in yields a plain API key instead). */
    val usesTokens: Boolean get() = oauth != null && oauth != OAuthProvider.OPENROUTER

    /**
     * The backend for a signed-in profile: every turn takes a fresh access
     * token from [tokens] (refreshing as needed) and adds the provider's
     * account headers.
     */
    fun createOAuthBackend(
        tokens: TokenSource,
        tools: List<AgentTool> = BuiltinTools,
        approver: ToolApprover = ToolApprover.AlwaysApprove,
        instructions: String = "",
    ): ChatBackend {
        val provider = requireNotNull(oauth) { "$id is not an OAuth profile" }
        return OAuthBackend(tokens) { t -> createBackend(t.accessToken, tools, approver, provider.apiHeaders(t), instructions) }
    }

    /**
     * The backend for this profile.
     *
     * @param tools on-device tools: run by on-device loops, advertised to AG-UI servers as
     *   frontend tools, and executed for AI SDK servers that hand a call to the client.
     * @param instructions extra guidance (e.g. [dev.ai.elements.core.agent.Capability]
     *   instructions): appended to the system prompt of on-device loops and sent to AG-UI
     *   servers as `context`.
     */
    fun createBackend(
        apiKey: String,
        tools: List<AgentTool> = BuiltinTools,
        approver: ToolApprover = ToolApprover.AlwaysApprove,
        headers: Map<String, String> = emptyMap(),
        instructions: String = "",
    ): ChatBackend {
        val agentTools = if (useTools) tools else emptyList()
        val base = baseUrl.trimEnd('/')
        val system = listOf(systemPrompt, instructions).filter { it.isNotBlank() }.joinToString("\n\n")
        return when (kind) {
            ProviderKind.MOCK -> MockAgentBackend(tools, approver)
            ProviderKind.AGENT_SERVER -> UiMessageStreamBackend("$base/api/chat", model, apiKey, agentTools, approver)
            ProviderKind.AG_UI -> AgUiBackend(
                "$base/api/agui", apiKey, agentTools, approver,
                context = listOfNotNull(instructions.takeIf { it.isNotBlank() }?.let { "Client capabilities" to it }),
            )
            ProviderKind.OPENAI -> OpenAiChatBackend(base, model, apiKey, system, agentTools, approver, extraHeaders = headers)
            ProviderKind.OPENAI_RESPONSES -> OpenAiResponsesBackend(base, model, apiKey, system, agentTools, approver, extraHeaders = headers)
            ProviderKind.ANTHROPIC -> AnthropicBackend(base, model, apiKey, system, agentTools, approver)
            ProviderKind.GEMINI -> GeminiBackend(base, model, apiKey, system, agentTools, approver)
            ProviderKind.OLLAMA -> OllamaBackend(base, model, system, agentTools, approver)
            ProviderKind.A2A -> throw UnsupportedOperationException("A2A agents need the ai-elements-a2a module: A2aBackend(A2aAgent(\"$baseUrl\"))")
            ProviderKind.ACP -> throw UnsupportedOperationException("ACP agents need the ai-elements-acp module: AcpBackend(AcpAgent.webSocket(\"$baseUrl\"))")
        }
    }

    /** Ask the provider which models it serves (for the model picker). */
    suspend fun listModels(apiKey: String, client: OkHttpClient = DefaultHttpClient): List<String> {
        fun ids(key: String, json: kotlinx.serialization.json.JsonObject) =
            json[key]?.jsonArray?.mapNotNull {
                it.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            }.orEmpty()
        val base = baseUrl.trimEnd('/')
        return when (kind) {
            ProviderKind.MOCK -> listOf("mock-agent")
            ProviderKind.AGENT_SERVER, ProviderKind.AG_UI -> {
                val health = client.getJson("$base/health")
                listOfNotNull(health["default_model"]?.jsonPrimitive?.contentOrNull, "demo").distinct()
            }
            ProviderKind.OPENAI, ProviderKind.OPENAI_RESPONSES -> ids("data", client.getJson("$base/models", bearer(apiKey)))
            ProviderKind.OLLAMA -> ids("models", client.getJson("$base/api/tags"))
            ProviderKind.GEMINI -> ids("models", client.getJson("$base/v1beta/models", mapOf("x-goog-api-key" to apiKey)))
                .map { it.removePrefix("models/") }
            ProviderKind.ANTHROPIC -> ids(
                "data",
                client.getJson("$base/v1/models", mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")),
            )
            ProviderKind.A2A -> listOfNotNull(client.getJson("$base/.well-known/agent-card.json")["name"]?.jsonPrimitive?.contentOrNull)
            // The agent names itself in ACP `initialize`, which needs ai-elements-acp.
            ProviderKind.ACP -> emptyList()
        }.sorted()
    }

    private fun bearer(key: String) = mapOf("Authorization" to if (key.isBlank()) "" else "Bearer $key")

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant in an Android app that renders GitHub-flavoured Markdown " +
                "and Mermaid diagrams (```mermaid fenced blocks). Use a Mermaid diagram when explaining " +
                "flows, architectures or sequences. Use the available tools when they help. " +
                "Answer in the user's language."

        /**
         * Presets. `10.0.2.2` is the Android emulator's alias for the host
         * machine; on a physical device use the host's LAN address.
         */
        val Presets: List<ProviderProfile> = listOf(
            ProviderProfile("mock", "Offline demo", ProviderKind.MOCK, model = "mock-agent", builtIn = true),
            ProviderProfile("agent-server", "PydanticAI · AI SDK stream", ProviderKind.AGENT_SERVER, "http://10.0.2.2:8788", "", builtIn = true),
            ProviderProfile("agent-server-agui", "PydanticAI · AG-UI", ProviderKind.AG_UI, "http://10.0.2.2:8788", "", builtIn = true),
            ProviderProfile("ollama", "Ollama", ProviderKind.OLLAMA, "http://10.0.2.2:11434", "qwen3:4b", builtIn = true),
            ProviderProfile("cliproxy", "CLIProxyAPI", ProviderKind.OPENAI, "http://10.0.2.2:8317/v1", "gpt-5", builtIn = true),
            ProviderProfile("openai", "OpenAI", ProviderKind.OPENAI_RESPONSES, "https://api.openai.com/v1", "gpt-5", builtIn = true),
            ProviderProfile("anthropic", "Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-5", builtIn = true),
            ProviderProfile("gemini", "Google Gemini", ProviderKind.GEMINI, "https://generativelanguage.googleapis.com", "gemini-2.5-flash", builtIn = true),
            ProviderProfile("openrouter", "OpenRouter", ProviderKind.OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-5", builtIn = true),
            ProviderProfile("a2a-research", "Research agent · A2A", ProviderKind.A2A, "http://10.0.2.2:8788", "", builtIn = true),
            ProviderProfile("acp-agent", "PydanticAI · ACP", ProviderKind.ACP, "ws://10.0.2.2:8788/acp", "", builtIn = true),
        )
    }
}
