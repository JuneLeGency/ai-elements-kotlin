package dev.ai.elements.ui.chat

import androidx.compose.runtime.Immutable
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.ReasoningPart
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import kotlinx.serialization.json.JsonObject

/**
 * Where the agent's run stands, for surfaces outside the chat (notifications, widgets, a watch):
 * derived from the conversation's model alone, whatever protocol produced it.
 *
 * @property tool the step running (or waiting for approval).
 * @property steps the reply's tool calls so far.
 * @property plan the plan the agent keeps (a `plan` data part, or `plan` in shared state), if any.
 * @property reply the reply's text so far.
 */
@Immutable
data class AgentProgress(
    val phase: Phase,
    val tool: ToolPart? = null,
    val steps: Int = 0,
    val plan: List<WorkflowStep> = emptyList(),
    val reply: String = "",
    val error: String? = null,
) {
    enum class Phase { THINKING, WORKING, WRITING, NEEDS_APPROVAL, NEEDS_INPUT, DONE, FAILED }

    /** The run is going on (or waiting on the user). */
    val isActive: Boolean get() = phase != Phase.DONE && phase != Phase.FAILED

    val planDone: Int get() = plan.count { it.status == StepStatus.COMPLETE }

    companion object {
        /** The progress of the conversation's latest turn, or null before the first reply. */
        fun of(state: ChatState): AgentProgress? {
            val reply = state.messages.lastOrNull()?.takeIf { it.role == Role.ASSISTANT }
            if (reply == null && !state.isBusy) return null
            val tools = reply?.parts.orEmpty().filterIsInstance<ToolPart>()
            val waiting = tools.lastOrNull { it.state == ToolState.APPROVAL_REQUESTED }
            val last = reply?.parts?.lastOrNull()
            val phase = when {
                state.status == ChatStatus.ERROR -> Phase.FAILED
                state.inputRequests.isNotEmpty() -> Phase.NEEDS_INPUT
                waiting != null -> Phase.NEEDS_APPROVAL
                !state.isBusy -> Phase.DONE
                last is ToolPart && last.isStreaming -> Phase.WORKING
                last is TextPart && last.isStreaming -> Phase.WRITING
                last is ReasoningPart || reply == null -> Phase.THINKING
                else -> Phase.WORKING
            }
            return AgentProgress(
                phase = phase,
                tool = waiting ?: tools.lastOrNull(),
                steps = tools.size,
                plan = reply?.let(::planOf).orEmpty(),
                reply = reply?.parts.orEmpty().filterIsInstance<TextPart>().joinToString("\n\n") { it.text }.trim(),
                error = state.error,
            )
        }

        private fun planOf(message: Message): List<WorkflowStep> {
            val data = message.parts.filterIsInstance<DataPart>().lastOrNull { it.name == "plan" || it.name == DataPart.STATE } ?: return emptyList()
            val json = data.data as? JsonObject ?: return emptyList()
            val plan = if (data.name == DataPart.STATE) json["plan"] as? JsonObject ?: return emptyList() else json
            return plan.steps("steps")
        }
    }
}
