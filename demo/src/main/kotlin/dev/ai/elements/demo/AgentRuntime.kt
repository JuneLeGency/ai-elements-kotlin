package dev.ai.elements.demo

import dev.ai.elements.a2a.A2aAgent
import dev.ai.elements.a2a.A2aBackend
import dev.ai.elements.a2a.asSubAgent
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ToolApprover
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
import dev.ai.elements.harness.filesystem.FileSystem
import dev.ai.elements.harness.memory.FileMemoryStore
import dev.ai.elements.harness.memory.Memory
import dev.ai.elements.harness.planning.Planning
import dev.ai.elements.harness.sandbox.AlpineSandbox
import dev.ai.elements.harness.shell.Shell
import dev.ai.elements.harness.browser.WebBrowser
import dev.ai.elements.harness.device.DeviceTools
import dev.ai.elements.harness.scheduler.Scheduler
import dev.ai.elements.harness.speech.Speech
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
    context: android.content.Context,
    private val providers: ProviderStore,
    private val mcp: McpServerStore,
    private val agents: AgentsStore,
    private val skills: SkillsRepository,
    private val appTools: List<AgentTool>,
) {
    private val a2aAgents = mutableMapOf<String, A2aAgent>()

    /** The agent's workspace: shared by the file tools and the Linux sandbox (mounted at /workspace). */
    val workspace = java.io.File(context.filesDir, "workspace").apply { mkdirs() }
    val sandbox = AlpineSandbox(context, workspace)
    private val shell = Shell(sandbox, defaultTimeoutSeconds = 120.0)
    private val fileSystem = FileSystem(workspace)
    private val browser = WebBrowser(context)
    private val device = DeviceTools(context, PermissionBroker)
    private val speech = Speech(context)
    val scheduler = Scheduler(context)
    private val memory = Memory(FileMemoryStore(java.io.File(context.filesDir, "memory")))

    /** One plan per conversation. */
    @Volatile private var planning = Planning()

    /** A new conversation starts with an empty plan. */
    fun newSession() { planning = Planning() }

    fun a2aAgent(url: String): A2aAgent = synchronized(a2aAgents) { a2aAgents.getOrPut(url.trimEnd('/')) { A2aAgent(url.trimEnd('/')) } }

    /**
     * For agent servers (AI SDK / AG-UI): only device-side capabilities travel, as client /
     * frontend tools. The server's own harness brings its sub-agents and skills; sending ours
     * too would duplicate them (and collide on `delegate_task` / `load_capability`).
     */
    private val deviceToolsOnly = AgentHarness(
        model = { tools, instructions, approver -> modelOf(providers.selected).backend(tools, instructions, approver) },
        capabilities = { capabilities(serverSide = true) },
    )

    private val harness = AgentHarness(
        model = { tools, instructions, approver -> modelOf(providers.selected).backend(tools, instructions, approver) },
        capabilities = { capabilities(serverSide = false) },
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
        return when {
            profile.kind == ProviderKind.A2A -> A2aBackend(a2aAgent(profile.baseUrl))
            profile.kind.serverSideAgent -> deviceToolsOnly.backend(approver)
            else -> harness.backend(approver)
        }
    }

    private fun modelOf(profile: ProviderProfile): ModelBinding =
        if (profile.kind == ProviderKind.A2A) ModelBinding { _, _, _ -> A2aBackend(a2aAgent(profile.baseUrl)) }
        else if (profile.usesTokens) profile.model(tokens = providers.tokenSource(profile))
        else profile.model(apiKey = providers.apiKey(profile.id))

    /** [serverSide]: only what the device alone can do (app tools, MCP); servers have their own clock, calculator and skills. */
    private fun capabilities(serverSide: Boolean): List<Capability> {
        val settings = agents.settings.value
        return buildList {
            if (settings.builtinTools) add(StaticCapability(tools = if (serverSide) appTools else BuiltinTools + appTools))
            if (!serverSide && settings.skillsEnabled) {
                val enabled = skills.skills.value.map { it.skill }.filter { it.name !in settings.disabledSkills }
                if (enabled.isNotEmpty()) add(Skills(enabled))
            }
            if (settings.mcpEnabled && mcp.servers.value.any { it.enabled }) add(mcp.toolset())
            if (!serverSide) {
                if (settings.workspaceFiles) add(fileSystem)
                if (settings.sandboxShell) add(shell)
                if (settings.memory) add(memory)
                if (settings.planning) add(planning)
                if (settings.webBrowser) add(browser)
                if (settings.deviceTools) add(device)
                if (settings.speech) add(speech)
                if (settings.scheduledTasks) add(scheduler)
            }
        }
    }

    /** Enabled remote A2A agents, described by their cards; unreachable ones are skipped. */
    private suspend fun remoteAgents() = coroutineScope {
        agents.settings.value.remoteAgents.filter { it.enabled }.map { def ->
            async { withTimeoutOrNull(5_000) { runCatching { a2aAgent(def.url).let { it.asSubAgent(it.card()) } }.getOrNull() } }
        }.awaitAll().filterNotNull()
    }
}
