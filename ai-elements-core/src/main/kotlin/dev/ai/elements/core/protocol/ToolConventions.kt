package dev.ai.elements.core.protocol

import dev.ai.elements.core.agent.SubAgents
import dev.ai.elements.core.chat.ChatEvent
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolKind
import dev.ai.elements.core.skills.Skills
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The agent-server tool conventions this library follows (Pydantic AI Harness, AGENTS.md §1.4)
 * translated into the model, for tool calls that arrive over a protocol (AI SDK, AG-UI) rather
 * than from an on-device [dev.ai.elements.core.agent.AgentTool], which declares them itself:
 *
 * - `delegate_task(agent_name, task)` → [ToolKind.Delegation]
 * - `load_capability(id)` → [ToolKind.Skill]
 * - the Harness `FileSystem` and `Shell` tools → a [ToolCategory] and location, exactly as the
 *   Harness ACP adapter presents them (`pydantic_ai_harness.experimental.acp` `default_coding_presenter`):
 *   `read_file` / `file_info` read, `edit_file` / `write_file` edit, `list_directory` /
 *   `search_files` / `find_files` search, `run_command` / `start_command` / `check_command` /
 *   `stop_command` execute, `create_directory` other; `path` is the location.
 *
 * This is the only place those names are interpreted; UI elements look at the model alone.
 */
object ToolConventions {
    fun kindOf(name: String, input: String): ToolKind? {
        val args = parse(input)
        return when (name) {
            SubAgents.DELEGATE_TASK -> ToolKind.Delegation(args?.string("agent_name"), args?.string("task"))
            Skills.LOAD_CAPABILITY -> ToolKind.Skill(args?.string("id"))
            else -> null
        }
    }

    /** The [ToolCategory] of a Harness file-system or shell call, or null for any other tool. */
    fun categoryOf(name: String, arguments: JsonObject?): ToolCategory? = when (name) {
        "read_file", "file_info" -> ToolCategory.READ
        "edit_file", "write_file" -> ToolCategory.EDIT
        "list_directory", "search_files", "find_files" -> ToolCategory.SEARCH
        "run_command", "start_command", "check_command", "stop_command" -> ToolCategory.EXECUTE
        "create_directory" -> ToolCategory.OTHER
        else -> null
    }

    /** The path a Harness file-system call acts on (its ACP `ToolCallLocation`), or null. */
    fun locationOf(name: String, arguments: JsonObject?): String? =
        if (categoryOf(name, arguments).let { it != null && it != ToolCategory.EXECUTE }) arguments?.string("path")?.takeIf { it.isNotEmpty() } else null

    internal fun parse(input: String): JsonObject? = runCatching { Json.parseToJsonElement(input).jsonObject }.getOrNull()

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}

/** This event with what it leaves out (kind, category, location) filled in from [ToolConventions]. */
internal fun ChatEvent.ToolInputAvailable.withConventions(): ChatEvent.ToolInputAvailable {
    if (kind != null && category != null) return this
    val args = ToolConventions.parse(input)
    return copy(
        kind = kind ?: ToolConventions.kindOf(name, input),
        category = category ?: ToolConventions.categoryOf(name, args),
        location = location ?: ToolConventions.locationOf(name, args),
    )
}
