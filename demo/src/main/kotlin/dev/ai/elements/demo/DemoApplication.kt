package dev.ai.elements.demo

import dev.ai.elements.core.mcp.McpApps
import android.app.Application
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.config.McpServerStore
import dev.ai.elements.core.config.ProviderStore
import dev.ai.elements.demo.data.AgentsStore
import dev.ai.elements.demo.data.SkillsRepository
import dev.ai.elements.demo.tools.ClipboardTool
import dev.ai.elements.harness.scheduler.ScheduledAgentHost

/**
 * Owns the agent so it outlives the UI: the chat and scheduled background runs
 * (`harness-scheduler`) use the same providers, capabilities and settings.
 */
class DemoApplication : Application(), ScheduledAgentHost {
    val providers by lazy { ProviderStore(this) }
    val mcpServers by lazy { McpServerStore(this, providers.secrets, clientCapabilities = McpApps.CLIENT_CAPABILITIES) }
    val agents by lazy { AgentsStore(this) }
    val skills by lazy { SkillsRepository(this) }
    val runtime by lazy { AgentRuntime(this, providers, mcpServers, agents, skills, appTools = listOf(ClipboardTool(this))) }

    override fun scheduledBackend(approver: ToolApprover): ChatBackend = runtime.backend(approver)
}
