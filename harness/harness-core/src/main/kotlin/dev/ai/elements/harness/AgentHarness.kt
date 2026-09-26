package dev.ai.elements.harness

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import dev.ai.elements.core.agent.SubAgent
import dev.ai.elements.core.agent.SubAgents
import dev.ai.elements.core.agent.collectTools
import dev.ai.elements.core.auth.TokenSource
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.deferredBackend

/**
 * How to reach a model: builds the backend of one agent run from its tools
 * and instructions. [ProviderProfile.model] adapts any configured provider.
 */
fun interface ModelBinding {
    fun backend(tools: List<AgentTool>, instructions: String, approver: ToolApprover): ChatBackend
}

/** This provider as a [ModelBinding] (API key, or signed-in [tokens]); [systemPrompt] overrides the profile's. */
fun ProviderProfile.model(apiKey: String = "", tokens: TokenSource? = null, systemPrompt: String? = null): ModelBinding {
    val profile = systemPrompt?.let { copy(systemPrompt = it) } ?: this
    return ModelBinding { tools, instructions, approver ->
        if (tokens != null) profile.createOAuthBackend(tokens, tools, approver, instructions)
        else profile.createBackend(apiKey, tools, approver, instructions = instructions)
    }
}

/** A sub-agent the harness runs itself: its own [instructions] (and optionally its own [model]) with the harness' capabilities. */
data class LocalSubAgent(
    val name: String,
    val description: String,
    val instructions: String,
    /** Null runs it on the harness' model. */
    val model: ModelBinding? = null,
)

/**
 * An in-app agent: a [model] plus [capabilities] (tools, skills, MCP servers…),
 * served as a [ChatBackend] for [dev.ai.elements.core.ChatController] — the
 * Kotlin counterpart of a Pydantic AI agent with Harness capabilities.
 *
 * Every turn re-reads the providers (they are `suspend` so they can reflect
 * the user's current settings and do I/O such as listing MCP tools). Sub-agents
 * are exposed through [SubAgents] (`delegate_task`): [localSubAgents] run on this
 * harness with its capabilities, [remoteSubAgents] are ready-made (e.g. A2A agents
 * from `ai-elements-a2a`). Delegation is limited to [maxDepth] levels, counting
 * the top-level run, like Harness' `max_depth`.
 *
 * ```kotlin
 * val harness = AgentHarness(
 *     model = profile.model(apiKey),
 *     capabilities = { listOf(Skills.from(library), mcpServers.toolset()) },
 *     localSubAgents = { listOf(LocalSubAgent("researcher", "Researches topics", "Be thorough.")) },
 * )
 * val chat = ChatController(backend = harness::backend, scope = viewModelScope)
 * ```
 */
class AgentHarness(
    private val model: ModelBinding,
    private val capabilities: suspend () -> List<Capability> = { emptyList() },
    private val localSubAgents: suspend () -> List<LocalSubAgent> = { emptyList() },
    private val remoteSubAgents: suspend () -> List<SubAgent> = { emptyList() },
    private val maxDepth: Int = 2,
) {
    init {
        require(maxDepth >= 1) { "maxDepth counts the top-level run, so it must be at least 1" }
    }

    /** The backend of the next turn; pass it as `ChatController(backend = harness::backend)`. */
    fun backend(approver: ToolApprover): ChatBackend = deferredBackend { run(model, "", approver, depth = 1) }

    private suspend fun run(model: ModelBinding, ownInstructions: String, approver: ToolApprover, depth: Int): ChatBackend {
        val all = capabilities() + listOfNotNull(if (depth < maxDepth) subAgents(depth) else null)
        val instructions = (listOf(ownInstructions) + all.mapNotNull { it.instructions }).filter { it.isNotBlank() }.joinToString("\n\n")
        return model.backend(collectTools(all), instructions, approver)
    }

    private suspend fun subAgents(depth: Int): SubAgents? {
        val local = localSubAgents().map { def ->
            SubAgent(def.name, def.description) { approver -> deferredBackend { run(def.model ?: model, def.instructions, approver, depth + 1) } }
        }
        val all = (local + remoteSubAgents()).distinctBy { it.name }
        return if (all.isEmpty()) null else SubAgents(all)
    }
}
