package dev.ai.elements.harness.planning

import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import dev.ai.elements.core.agent.ToolCallContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/** Status of a plan step (Pydantic AI Harness `TaskStatus`, without subtasks). */
enum class TaskStatus(val wire: String, val icon: String) {
    PENDING("pending", "[ ]"), IN_PROGRESS("in_progress", "[~]"), COMPLETED("completed", "[x]"), CANCELLED("cancelled", "[-]");

    companion object {
        fun parse(value: String?): TaskStatus = entries.firstOrNull { it.wire == value }
            ?: throw IllegalArgumentException("Invalid status '$value'. Use one of: ${entries.joinToString { it.wire }}.")
    }
}

/** One step: [content] in imperative form, [activeForm] shown while it runs. */
data class PlanItem(val id: String, val content: String, val status: TaskStatus = TaskStatus.PENDING, val activeForm: String = "")

/** Where the plan lives; [InMemoryPlanStore] keeps it for the harness' lifetime. */
interface PlanStore {
    suspend fun get(): List<PlanItem>
    suspend fun set(items: List<PlanItem>)
}

class InMemoryPlanStore : PlanStore {
    @Volatile private var items: List<PlanItem> = emptyList()
    override suspend fun get() = items
    override suspend fun set(items: List<PlanItem>) { this.items = items }
}

/**
 * A model-owned task list with the tools, texts and reminder of Pydantic AI
 * Harness `Planning`: `write_plan` replaces the whole plan, `read_plan` shows
 * ids, `add_task`, `update_task_status`, `update_task_statuses` (validated as a
 * batch) and `remove_task` edit it. The current plan is appended to each turn
 * as a `<plan-reminder>` ([context]), and every change is published to the UI
 * as a `data-plan` part for the `Plan` element.
 */
class Planning(private val store: PlanStore = InMemoryPlanStore(), private val title: String = "Plan") : Capability {
    private val lock = Mutex()

    override val instructions: String = WRITE_PLAN_GUIDANCE + " " + GRANULAR_GUIDANCE

    override suspend fun context(): String? {
        val items = store.get()
        if (items.isEmpty()) return null
        return "<plan-reminder>\nYour current plan (keep it updated with the planning tools):\n\n${render(items)}\n</plan-reminder>"
    }

    override suspend fun tools(): List<AgentTool> = listOf(
        tool("write_plan", WRITE_PLAN, mapOf("items" to buildJsonObject {
            put("type", "array")
            put("description", "The complete ordered list of plan steps.")
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("id") { put("type", "string"); put("description", "Stable id; omit for new steps.") }
                    putJsonObject("content") { put("type", "string"); put("description", "Imperative description of the step.") }
                    putJsonObject("status") { put("type", "string"); put("enum", JsonArray(TaskStatus.entries.map { JsonPrimitive(it.wire) })) }
                    putJsonObject("active_form") { put("type", "string"); put("description", "Present-continuous label shown while the step runs.") }
                }
                put("required", JsonArray(listOf(JsonPrimitive("content"))))
            }
        }), listOf("items")) { args -> writePlan((args["items"] as? JsonArray).orEmpty().map { it.jsonObject }) },
        tool("read_plan", READ_PLAN, emptyMap(), emptyList()) { readPlan() },
        tool("add_task", ADD_TASK, mapOf("content" to str("The step description in imperative form."), "active_form" to str("Optional present-continuous label, e.g. \"Fix bug\" -> \"Fixing bug\".")), listOf("content")) { args ->
            change { items ->
                val item = PlanItem(newId(), args.s("content")!!, activeForm = args.s("active_form").orEmpty())
                (items + item) to "Added step '${item.content}' with id: ${item.id}"
            }
        },
        tool("update_task_status", UPDATE_TASK_STATUS, mapOf("task_id" to str("Id of the step to update."), "status" to statusSchema()), listOf("task_id", "status")) { args ->
            val status = TaskStatus.parse(args.s("status"))
            change { items ->
                val id = args.s("task_id")
                val item = items.firstOrNull { it.id == id } ?: return@change items to "Step with id '$id' not found."
                items.map { if (it.id == id) it.copy(status = status) else it } to "Updated step '${item.content}' status to '${status.wire}'."
            }
        },
        tool("update_task_statuses", UPDATE_TASK_STATUSES, mapOf("updates" to buildJsonObject {
            put("type", "array")
            put("description", "The `{task_id, status}` entries to apply.")
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("task_id") { put("type", "string") }; put("status", statusSchema()) }
                put("required", JsonArray(listOf(JsonPrimitive("task_id"), JsonPrimitive("status"))))
            }
        }), listOf("updates")) { args ->
            val updates = (args["updates"] as? JsonArray).orEmpty().map { it.jsonObject }
            change { items ->
                if (updates.isEmpty()) return@change items to "No updates provided."
                val errors = mutableListOf<String>()
                var projected = items
                val applied = mutableListOf<PlanItem>()
                updates.forEach { u ->
                    val id = u.s("task_id")
                    val status = runCatching { TaskStatus.parse(u.s("status")) }.getOrElse { errors += it.message.orEmpty(); return@forEach }
                    val item = projected.firstOrNull { it.id == id } ?: run { errors += "Step with id '$id' not found."; return@forEach }
                    projected = projected.map { if (it.id == id) it.copy(status = status) else it }
                    applied += item.copy(status = status)
                }
                if (errors.isNotEmpty()) items to "No changes applied. Errors:\n" + errors.joinToString("\n") { "- $it" }
                else projected to "Updated ${applied.size} step(s):\n" + applied.joinToString("\n") { "- [${it.id}] ${it.content} -> ${it.status.wire}" }
            }
        },
        tool("remove_task", REMOVE_TASK, mapOf("task_id" to str("Id of the step to remove.")), listOf("task_id")) { args ->
            change { items ->
                val id = args.s("task_id")
                val item = items.firstOrNull { it.id == id } ?: return@change items to "Step with id '$id' not found."
                items.filterNot { it.id == id } to "Removed step '${item.content}' (id: $id)."
            }
        },
    )

    private suspend fun writePlan(raw: List<JsonObject>): String = change { _ ->
        val items = raw.map { o ->
            PlanItem(o.s("id")?.takeIf { it.isNotBlank() } ?: newId(), o.s("content").orEmpty(), TaskStatus.parse(o.s("status") ?: "pending"), o.s("active_form").orEmpty())
        }
        val duplicates = items.groupBy { it.id }.filterValues { it.size > 1 }.keys.sorted()
        if (duplicates.isNotEmpty()) return@change null to "Plan not updated: Duplicate step ids: ${duplicates.joinToString()}. Every step needs a unique id."
        val note = if (items.count { it.status == TaskStatus.IN_PROGRESS } > 1) "\n\nNote: keep only one step in_progress at a time." else ""
        items to "Plan updated: ${items.size} step(s).\n\n${render(items)}$note"
    }

    private suspend fun readPlan(): String {
        val items = store.get()
        if (items.isEmpty()) return "No plan yet. Use write_plan to create one."
        val lines = listOf("Current plan:") + items.mapIndexed { i, it -> "${i + 1}. ${it.status.icon} [${it.id}] ${it.content}" }
        return lines.joinToString("\n") + "\n\n" + summary(items)
    }

    /** Apply a change (a null list means "refused, keep the plan"), then publish the plan to the UI. */
    private suspend fun change(block: (List<PlanItem>) -> Pair<List<PlanItem>?, String>): String {
        val (items, message) = lock.withLock {
            val (next, message) = block(store.get())
            if (next != null) store.set(next)
            next to message
        }
        if (items != null) ToolCallContext.current()?.data(PLAN_PART, "plan", planData(items))
        return message
    }

    /** The AI Elements `data-plan` shape: `{title, description, streaming, steps: [{label, status}]}`. */
    fun planData(items: List<PlanItem>) = buildJsonObject {
        val done = items.count { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED }
        put("title", title)
        put("description", "$done/${items.size} steps done")
        put("streaming", done < items.size)
        putJsonArray("steps") {
            items.forEach { item ->
                addJsonObject {
                    put("label", if (item.status == TaskStatus.IN_PROGRESS && item.activeForm.isNotBlank()) item.activeForm else item.content)
                    put("status", when (item.status) { TaskStatus.PENDING -> "pending"; TaskStatus.IN_PROGRESS -> "active"; else -> "complete" })
                }
            }
        }
    }

    private fun tool(name: String, description: String, properties: Map<String, JsonObject>, required: List<String>, run: suspend (JsonObject) -> String) = object : AgentTool {
        override val name = name
        override val description = description
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("required", JsonArray(required.map(::JsonPrimitive)))
        }
        override suspend fun execute(arguments: JsonObject) = run(arguments)
    }

    companion object {
        /** Id of the plan data part (one live plan per reply). */
        const val PLAN_PART = "plan"

        // Pydantic AI Harness texts, verbatim.
        const val WRITE_PLAN_GUIDANCE = "You have a planning tool, `write_plan`. For multi-step work, call it first to lay out the steps, then keep it current: mark exactly one step `in_progress`, and mark a step `completed` as soon as it is fully done. Pass the full plan every time you call `write_plan`."
        const val GRANULAR_GUIDANCE = "Use `add_task` to append a single step, `update_task_status`/`update_task_statuses` to move steps between statuses, and `read_plan` to see step ids before a granular edit."
        const val WRITE_PLAN = "Create or replace the entire plan. Pass the whole ordered list every time -- including steps that are unchanged, completed, or cancelled -- so there are no indices to track. Keep exactly one step `in_progress`. Call this first for multi-step work, then again as you start and finish steps."
        const val READ_PLAN = "Read the current plan: each step's id, content, and status, plus a progress summary. Use it before granular edits (the ids come from here) and to check what is left."
        const val ADD_TASK = "Append one new `pending` step without replacing the plan. Prefer this over write_plan when you only need to add a single step."
        const val UPDATE_TASK_STATUS = "Update one step's status by id. Set `in_progress` when you START a step and `completed` when it is fully done -- never mark work complete while tests fail or the implementation is partial."
        const val UPDATE_TASK_STATUSES = "Update several steps' statuses in one call -- ideal for handing off from a finished step to the next one. The whole batch is validated first: if any entry is invalid nothing is applied and the errors are returned. Entries apply in order, so when a batch both completes a prerequisite and starts its dependent, list the prerequisite's completion first."
        const val REMOVE_TASK = "Permanently delete a step by id -- use it for steps that are no longer relevant or were created in error. To mark work done, use update_task_status instead."
        private const val ALL_DONE = "All steps are completed. Do NOT call read_plan again -- respond to the user with a summary instead."

        fun render(items: List<PlanItem>): String {
            if (items.isEmpty()) return "No plan yet."
            val done = items.count { it.status == TaskStatus.COMPLETED }
            return (items.mapIndexed { i, it -> "${i + 1}. ${it.status.icon} ${it.content}" } + "($done/${items.size} completed)").joinToString("\n")
        }

        private fun summary(items: List<PlanItem>): String {
            val count = { s: TaskStatus -> items.count { it.status == s } }
            val parts = listOf("${count(TaskStatus.COMPLETED)} completed", "${count(TaskStatus.IN_PROGRESS)} in progress", "${count(TaskStatus.PENDING)} pending") +
                listOfNotNull(count(TaskStatus.CANCELLED).takeIf { it > 0 }?.let { "$it cancelled" })
            val active = count(TaskStatus.PENDING) + count(TaskStatus.IN_PROGRESS)
            return "Summary: ${parts.joinToString(", ")}" + if (active == 0 && count(TaskStatus.COMPLETED) > 0) "\n\n$ALL_DONE" else ""
        }

        private fun newId() = UUID.randomUUID().toString().replace("-", "").take(8)
        private fun str(description: String) = buildJsonObject { put("type", "string"); put("description", description) }
        private fun statusSchema() = buildJsonObject { put("type", "string"); put("enum", JsonArray(TaskStatus.entries.map { JsonPrimitive(it.wire) })) }
        private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
