package dev.ai.elements.core.protocol

import dev.ai.elements.core.agent.SubAgents
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.skills.Skills
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The agent-server tool conventions this library follows (Pydantic AI Harness, AGENTS.md §1.4)
 * translated into [ToolKind], for tool calls that arrive over a protocol (AI SDK, AG-UI) rather
 * than from an on-device [dev.ai.elements.core.agent.AgentTool], which declares its kind itself:
 *
 * - `delegate_task(agent_name, task)` → [ToolKind.Delegation]
 * - `load_capability(id)` → [ToolKind.Skill]
 *
 * This is the only place those names are interpreted; UI elements look at [ToolKind] alone.
 */
object ToolConventions {
    fun kindOf(name: String, input: String): ToolKind? {
        val args = runCatching { Json.parseToJsonElement(input).jsonObject }.getOrNull()
        return when (name) {
            SubAgents.DELEGATE_TASK -> ToolKind.Delegation(args?.string("agent_name"), args?.string("task"))
            Skills.LOAD_CAPABILITY -> ToolKind.Skill(args?.string("id"))
            else -> null
        }
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}

/** This event with its [ChatEvent.ToolInputAvailable.kind] filled in from [ToolConventions] when it has none. */
internal fun ChatEvent.ToolInputAvailable.withConventions(): ChatEvent.ToolInputAvailable =
    if (kind != null) this else copy(kind = ToolConventions.kindOf(name, input))
