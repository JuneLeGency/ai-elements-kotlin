package dev.ai.elements.demo

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.harness.ModelBinding
import dev.ai.elements.koog.KoogBackend
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.URI

/**
 * This provider driven by JetBrains Koog (`ai-elements-koog` with Koog's own clients) instead of
 * the built-in loop; null for kinds without a Koog client here (the built-in loop is used then).
 */
fun ProviderProfile.koogModel(apiKey: String): ModelBinding? {
    // Koog's HTTP layer on Ktor + OkHttp, passed explicitly (no ServiceLoader lookup on Android).
    val http = KtorKoogHttpClient.Factory(HttpClient(OkHttp))
    val capabilities = listOf(LLMCapability.Tools, LLMCapability.Completion, LLMCapability.Temperature)
    val (provider: LLMProvider, client: LLMClient, llm: LLModel) = when (kind) {
        ProviderKind.OPENAI, ProviderKind.OPENAI_RESPONSES -> {
            // Koog joins a host and a path: https://openrouter.ai/api/v1 → https://openrouter.ai + api/v1/chat/completions.
            val uri = URI(baseUrl.trimEnd('/'))
            val root = "${uri.scheme}://${uri.authority}"
            val path = uri.path.trim('/').let { if (it.isEmpty()) "v1" else it }
            val llm = LLModel(LLMProvider.OpenAI, model, capabilities + LLMCapability.OpenAIEndpoint.Completions)
            Triple(LLMProvider.OpenAI, OpenAILLMClient(apiKey, OpenAIClientSettings(baseUrl = root, chatCompletionsPath = "$path/chat/completions"), http), llm)
        }
        ProviderKind.ANTHROPIC -> {
            val llm = LLModel(LLMProvider.Anthropic, model, capabilities)
            // Koog maps models to API ids; any id the user configured is passed through.
            Triple(LLMProvider.Anthropic, AnthropicLLMClient(apiKey, AnthropicClientSettings(modelVersionsMap = mapOf(llm to model), baseUrl = baseUrl.trimEnd('/')), http), llm)
        }
        ProviderKind.OLLAMA -> Triple(LLMProvider.Ollama, OllamaClient(http, baseUrl.trimEnd('/')), LLModel(LLMProvider.Ollama, model, capabilities))
        else -> return null
    }
    val executor = MultiLLMPromptExecutor(mapOf(provider to client))
    return ModelBinding { tools, instructions, approver ->
        KoogBackend(executor, llm, tools, listOf(systemPrompt, instructions).filter { it.isNotBlank() }.joinToString("\n\n"), approver)
    }
}
