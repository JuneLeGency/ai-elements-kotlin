package dev.ai.elements.core.agent

/**
 * A composable bundle of what an agent can do: [instructions] added to the
 * system prompt and [tools] it may call — the Kotlin counterpart of a
 * Pydantic AI capability. [SubAgents], [Skills] and MCP servers
 * ([dev.ai.elements.core.mcp.McpToolset]) are capabilities; so is any app
 * feature that pairs tools with guidance.
 *
 * Instructions should be static for a given configuration: they sit at the
 * front of every request, so changing them per turn defeats prompt caching.
 */
interface Capability {
    /** Guidance for the model, or null. */
    val instructions: String? get() = null

    /** Tools the capability contributes. */
    suspend fun tools(): List<AgentTool> = emptyList()
}

/** A capability made of fixed [tools] and [instructions]. */
class StaticCapability(override val instructions: String? = null, private val tools: List<AgentTool> = emptyList()) : Capability {
    override suspend fun tools(): List<AgentTool> = tools
}

/** [base] followed by every capability's instructions, separated by blank lines. */
fun composeInstructions(base: String, capabilities: List<Capability>): String =
    (listOf(base) + capabilities.mapNotNull { it.instructions }).filter { it.isNotBlank() }.joinToString("\n\n")

/** All tools of [capabilities]; the first tool of a given name wins. */
suspend fun collectTools(capabilities: List<Capability>): List<AgentTool> =
    capabilities.flatMap { it.tools() }.distinctBy { it.name }
