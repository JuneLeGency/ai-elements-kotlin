package dev.ai.elements.core.agent

import dev.ai.elements.core.ChatBackend
import dev.ai.elements.core.ChatEvent
import dev.ai.elements.core.ToolApprover
import dev.ai.elements.core.finishStreaming
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.reduce
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/**
 * One delegate of [SubAgents]: a named agent with its own instructions, model
 * and tools, reached through [backend]. Any [ChatBackend] works — an on-device
 * loop, an AG-UI or AI SDK server, or a remote A2A agent.
 *
 * @param backend builds the delegate's backend for one delegation, given the
 *   approver of the enclosing chat (its tools ask the same user).
 */
class SubAgent(
    val name: String,
    val description: String,
    val backend: (ToolApprover) -> ChatBackend,
) {
    init {
        require(NAME.matches(name)) { "Sub-agent name must match ${NAME.pattern}: $name" }
    }

    private companion object {
        val NAME = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}

/**
 * Lets the agent delegate self-contained tasks to named [agents] — the same
 * contract as Pydantic AI Harness `SubAgents`, so on-device and server agents
 * behave alike and UIs render both the same way:
 *
 * - one tool, `delegate_task(agent_name, task)`;
 * - the roster is a static instruction (cache-stable);
 * - each delegation runs in a fresh context and returns the delegate's final
 *   answer to the parent.
 *
 * While a delegation runs, its reply streams to the UI through
 * [ToolCallContext.subagent] and is rendered by the `Subagent` element.
 */
class SubAgents(
    val agents: List<SubAgent>,
    val toolName: String = DELEGATE_TASK,
    private val clock: () -> Long = System::currentTimeMillis,
) : Capability {

    init {
        val duplicate = agents.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "Duplicate sub-agent names: $duplicate" }
    }

    private val byName = agents.associateBy { it.name }

    override val instructions: String?
        get() = if (agents.isEmpty()) null else
            "You can delegate self-contained tasks to these sub-agents using the `$toolName` tool. " +
                "Each runs in its own fresh context and does not see this conversation, so pass everything it needs.\n\n" +
                "Available sub-agents:\n" + agents.joinToString("\n") { "- ${it.name}: ${it.description}" }

    override suspend fun tools(): List<AgentTool> = if (agents.isEmpty()) emptyList() else listOf(tool)

    private val tool = object : AgentTool {
        override val name = toolName
        override val description = "Delegate a self-contained task to a named sub-agent and return its result.\n\n" +
            "The sub-agent runs in its own fresh context and does not see this conversation, so `task` must contain everything it needs."
        override val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("agent_name") {
                    put("type", "string")
                    put("description", "Name of the sub-agent to run. Must be one of the agents listed in the instructions.")
                    put("enum", JsonArray(agents.map { JsonPrimitive(it.name) }))
                }
                putJsonObject("task") {
                    put("type", "string")
                    put("description", "The complete, self-contained instruction for the sub-agent.")
                }
            }
            put("required", JsonArray(listOf(JsonPrimitive("agent_name"), JsonPrimitive("task"))))
        }

        override fun titleFor(arguments: JsonObject): String? = arguments.string("agent_name")

        override suspend fun execute(arguments: JsonObject): String {
            val agentName = arguments.string("agent_name").orEmpty()
            val agent = byName[agentName]
                ?: throw IllegalArgumentException("Unknown sub-agent '$agentName'. Available sub-agents: ${byName.keys.sorted().joinToString()}.")
            val task = arguments.string("task")?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("missing task")
            return delegate(agent, task)
        }
    }

    private suspend fun delegate(agent: SubAgent, task: String): String {
        val context = ToolCallContext.current()
        val prompt = Message(UUID.randomUUID().toString(), Role.USER, listOf(TextPart("task", task)), clock())
        var reply = Message(UUID.randomUUID().toString(), Role.ASSISTANT, createdAt = clock())
        var error: String? = null
        agent.backend(context?.approver ?: dev.ai.elements.core.ToolApprover.AlwaysApprove).stream(listOf(prompt)).collect { event ->
            if (event is ChatEvent.Error) error = event.message
            reply = reply.reduce(event, clock())
            context?.subagent(reply)
        }
        reply = reply.finishStreaming(clock())
        context?.subagent(reply)
        error?.let { throw IllegalStateException("Sub-agent '${agent.name}' failed: $it") }
        // Only the final answer goes back to the parent, keeping its context small.
        return reply.parts.filterIsInstance<TextPart>().lastOrNull { it.text.isNotBlank() }?.text
            ?: "Sub-agent '${agent.name}' finished without an answer."
    }

    companion object {
        /** The Pydantic AI Harness delegate tool name; UIs render calls to it as a `Subagent`. */
        const val DELEGATE_TASK = "delegate_task"
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
