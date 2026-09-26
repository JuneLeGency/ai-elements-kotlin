package dev.ai.elements.demo

import dev.ai.elements.a2a.A2aAgent
import dev.ai.elements.a2a.A2aBackend
import dev.ai.elements.a2a.asSubAgent
import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.BuiltinTools
import dev.ai.elements.core.agent.Capability
import dev.ai.elements.core.agent.StaticCapability
import dev.ai.elements.core.config.McpServerStore
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.core.config.ProviderStore
import dev.ai.elements.core.skills.Skills
import dev.ai.elements.demo.data.AgentsStore
import dev.ai.elements.demo.data.SkillsRepository
import dev.ai.elements.harness.AgentHarness
import dev.ai.elements.harness.LocalSubAgent
import dev.ai.elements.harness.ModelBinding
import dev.ai.elements.harness.model
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The demo's glue between settings and the library: which provider, which
 * capabilities are switched on. The agent itself is an [AgentHarness]
 * (`harness-core`); an A2A provider is talked to directly (`ai-elements-a2a`).
 */
class AgentRuntime(
    private val providers: ProviderStore,
    private val mcp: McpServerStore,
    private val agents: AgentsStore,
    private val skills: SkillsRepository,
    private val appTools: List<AgentTool>,
) {
    private val a2aAgents = mutableMapOf<String, A2aAgent>()

    fun a2aAgent(url: String): A2aAgent = synchronized(a2aAgents) { a2aAgents.getOrPut(url.trimEnd('/')) { A2aAgent(url.trimEnd('/')) } }

    private val harness = AgentHarness(
        model = { tools, instructions, approver -> modelOf(providers.selected).backend(tools, instructions, approver) },
        capabilities = ::capabilities,
        localSubAgents = {
            agents.settings.value.subAgents.filter { it.enabled }.map { def ->
                val profile = def.providerId?.let { id -> providers.profiles.value.firstOrNull { it.id == id } }
                LocalSubAgent(def.name, def.description, def.instructions, profile?.let(::modelOf))
            }
        },
        remoteSubAgents = ::remoteAgents,
    )

    /** The backend of the chat's next turn. */
    fun backend(approver: ToolApprover): ChatBackend {
        val profile = providers.selected
        return if (profile.kind == ProviderKind.A2A) A2aBackend(a2aAgent(profile.baseUrl)) else harness.backend(approver)
    }

    private fun modelOf(profile: ProviderProfile): ModelBinding =
        if (profile.kind == ProviderKind.A2A) ModelBinding { _, _, _ -> A2aBackend(a2aAgent(profile.baseUrl)) }
        else if (profile.usesTokens) profile.model(tokens = providers.tokenSource(profile))
        else profile.model(apiKey = providers.apiKey(profile.id))

    private fun capabilities(): List<Capability> {
        val settings = agents.settings.value
        return buildList {
            if (settings.builtinTools) add(StaticCapability(tools = BuiltinTools + appTools))
            if (settings.skillsEnabled) {
                val enabled = skills.skills.value.map { it.skill }.filter { it.name !in settings.disabledSkills }
                if (enabled.isNotEmpty()) add(Skills(enabled))
            }
            if (settings.mcpEnabled && mcp.servers.value.any { it.enabled }) add(mcp.toolset())
        }
    }

    /** Enabled remote A2A agents, described by their cards; unreachable ones are skipped. */
    private suspend fun remoteAgents() = coroutineScope {
        agents.settings.value.remoteAgents.filter { it.enabled }.map { def ->
            async { withTimeoutOrNull(5_000) { runCatching { a2aAgent(def.url).let { it.asSubAgent(it.card()) } }.getOrNull() } }
        }.awaitAll().filterNotNull()
    }
}
