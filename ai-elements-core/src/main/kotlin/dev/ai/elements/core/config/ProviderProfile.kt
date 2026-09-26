package dev.ai.elements.core.config

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.backend.AnthropicBackend
import dev.ai.elements.core.backend.DefaultHttpClient
import dev.ai.elements.core.backend.MockAgentBackend
import dev.ai.elements.core.backend.OpenAiChatBackend
import dev.ai.elements.core.backend.UiMessageStreamBackend
import dev.ai.elements.core.backend.getJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/** The wire protocol a provider speaks; decides which [ChatBackend] is built. */
@Serializable
enum class ProviderKind(val label: String, val needsKey: Boolean) {
    MOCK("Offline demo", needsKey = false),
    AGENT_SERVER("Agent server (UI Message Stream)", needsKey = false),
    OPENAI("OpenAI-compatible", needsKey = true),
    ANTHROPIC("Anthropic Messages", needsKey = true),
}

/**
 * One configured agent provider. The API key is not part of the profile — it
 * lives encrypted in [SecretStore], keyed by [id].
 *
 * @property useTools expose the on-device [BuiltinTools] (OpenAI / Anthropic only;
 *   the agent server brings its own tools).
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
) {
    fun createBackend(apiKey: String, tools: List<AgentTool> = BuiltinTools): ChatBackend {
        val agentTools = if (useTools) tools else emptyList()
        return when (kind) {
            ProviderKind.MOCK -> MockAgentBackend(tools)
            ProviderKind.AGENT_SERVER -> UiMessageStreamBackend(baseUrl.trimEnd('/') + "/api/chat", model, apiKey)
            ProviderKind.OPENAI -> OpenAiChatBackend(baseUrl, model, apiKey, systemPrompt, agentTools)
            ProviderKind.ANTHROPIC -> AnthropicBackend(baseUrl, model, apiKey, systemPrompt, agentTools)
        }
    }

    /** Ask the provider which models it serves (for the model picker). */
    suspend fun listModels(apiKey: String, client: OkHttpClient = DefaultHttpClient): List<String> {
        fun ids(key: String, json: kotlinx.serialization.json.JsonObject) =
            json[key]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.orEmpty()
        val base = baseUrl.trimEnd('/')
        return when (kind) {
            ProviderKind.MOCK -> listOf("mock-agent")
            ProviderKind.AGENT_SERVER -> {
                val health = client.getJson("$base/health")
                listOfNotNull(health["default_model"]?.jsonPrimitive?.contentOrNull, "demo").distinct()
            }
            ProviderKind.OPENAI -> ids("data", client.getJson("$base/models", bearer(apiKey)))
            ProviderKind.ANTHROPIC -> ids(
                "data",
                client.getJson("$base/v1/models", mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")),
            )
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
            ProviderProfile("agent-server", "PydanticAI agent server", ProviderKind.AGENT_SERVER, "http://10.0.2.2:8788", "", builtIn = true),
            ProviderProfile("cliproxy", "CLIProxyAPI", ProviderKind.OPENAI, "http://10.0.2.2:8317/v1", "gpt-5", builtIn = true),
            ProviderProfile("ollama", "Ollama", ProviderKind.OPENAI, "http://10.0.2.2:11434/v1", "qwen3", builtIn = true),
            ProviderProfile("openai", "OpenAI", ProviderKind.OPENAI, "https://api.openai.com/v1", "gpt-5", builtIn = true),
            ProviderProfile("anthropic", "Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-5", builtIn = true),
            ProviderProfile("gemini", "Google Gemini", ProviderKind.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", builtIn = true),
            ProviderProfile("openrouter", "OpenRouter", ProviderKind.OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-5", builtIn = true),
        )
    }
}
