package dev.ai.elements.harness.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.ai.elements.core.agent.AgentTool
import dev.ai.elements.core.agent.Capability
import dev.ai.elements.core.chat.ChatBackend
import dev.ai.elements.core.chat.ToolApprover
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Where scheduled runs get their agent: implement it on your `Application`.
 * Return the same backend your chat uses (e.g. `harness.backend(approver)`), so
 * a scheduled task sees the user's current provider, tools and skills.
 */
fun interface ScheduledAgentHost {
    fun scheduledBackend(approver: ToolApprover): ChatBackend
}

/**
 * Scheduled and recurring agent runs: the agent can `schedule_task` a prompt for
 * later (once, or every N minutes), `list_scheduled_tasks` and
 * `cancel_scheduled_task`. Runs happen in the background on WorkManager
 * ([ScheduledAgentWorker]), survive reboots, and post their answer as a
 * notification (if the app may post notifications).
 *
 * Background runs have nobody to approve tools, so tools that require approval are
 * denied there. Scheduling itself requires the user's approval. The app's
 * `Application` must implement [ScheduledAgentHost].
 */
class Scheduler(context: Context) : Capability {
    private val context = context.applicationContext
    private val store = ScheduledTaskStore.get(this.context)
    private val work get() = WorkManager.getInstance(context)

    /** All scheduled tasks with their last result, for a settings screen. */
    val tasks: StateFlow<List<ScheduledTask>> = store.tasks

    override val instructions: String
        get() = "You can schedule work for later with `schedule_task`: the prompt runs as a new background " +
            "conversation at that time (tools that need approval are unavailable there) and its answer is shown " +
            "as a notification. Write the prompt so it stands alone. The device time zone is ${ZoneId.systemDefault().id}."

    override suspend fun tools(): List<AgentTool> = listOf(
        tool(
            "schedule_task",
            "Run a prompt later in the background, once or repeatedly; the answer is shown as a notification.",
            "title" to schema("string", "Short name of the task."),
            "prompt" to schema("string", "The complete, self-contained instruction to run."),
            "at" to schema("string", "ISO 8601 time of the (first) run, e.g. 2026-09-28T08:00:00+08:00."),
            "in_minutes" to schema("integer", "Alternatively, minutes from now."),
            "every_minutes" to schema("integer", "Repeat interval in minutes (at least 15); omit to run once."),
            approval = true,
            required = listOf("title", "prompt"),
        ) { a ->
            val first = a.s("at")?.let(::parseTime) ?: a.i("in_minutes")?.let { Instant.now().plus(Duration.ofMinutes(it.toLong())) }
            val task = schedule(a.s("title")!!, a.s("prompt")!!, first ?: Instant.now(), a.i("every_minutes"))
            "Scheduled \"${task.title}\" (id ${task.id}) for ${task.firstRunAt}" + (task.everyMinutes?.let { ", every $it minutes." } ?: ".")
        },
        tool("list_scheduled_tasks", "List scheduled tasks with their next run and last result.") { list() },
        tool("cancel_scheduled_task", "Cancel a scheduled task.", "id" to schema("string", "The task id."), approval = true, required = listOf("id")) { a ->
            if (cancel(a.s("id")!!)) "Cancelled." else error("No scheduled task ${a.s("id")}.")
        },
    )

    /** Schedule [prompt] at [firstRunAt], repeating every [everyMinutes] (≥ 15) when given. */
    fun schedule(title: String, prompt: String, firstRunAt: Instant, everyMinutes: Int? = null): ScheduledTask {
        require(prompt.isNotBlank()) { "The prompt is empty." }
        require(everyMinutes == null || everyMinutes >= MIN_INTERVAL_MINUTES) { "Repeating tasks run at most every $MIN_INTERVAL_MINUTES minutes." }
        val task = ScheduledTask(UUID.randomUUID().toString().take(8), title, prompt, firstRunAt.toString(), everyMinutes)
        val delay = Duration.between(Instant.now(), firstRunAt).toMillis().coerceAtLeast(0)
        val input = Data.Builder().putString(ScheduledAgentWorker.KEY_TASK, task.id).build()
        if (everyMinutes == null) {
            val request = OneTimeWorkRequestBuilder<ScheduledAgentWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(input).addTag(TAG).build()
            work.enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.REPLACE, request)
        } else {
            val request = PeriodicWorkRequestBuilder<ScheduledAgentWorker>(everyMinutes.toLong(), TimeUnit.MINUTES)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(input).addTag(TAG).build()
            work.enqueueUniquePeriodicWork(workName(task.id), ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        store.put(task)
        return task
    }

    /** Cancel the task [id]; false when there is none. */
    fun cancel(id: String): Boolean {
        work.cancelUniqueWork(workName(id))
        return store.remove(id)
    }

    private fun list(): String = buildJsonArray {
        store.tasks.value.forEach { t ->
            addJsonObject {
                put("id", t.id)
                put("title", t.title)
                put("prompt", t.prompt)
                put("first_run", t.firstRunAt)
                t.everyMinutes?.let { put("every_minutes", it) }
                t.lastRunAt?.let { put("last_run", it) }
                t.lastResult?.let { put("last_result", it.take(500)) }
                t.lastError?.let { put("last_error", it) }
            }
        }
    }.toString()

    companion object {
        const val TAG = "ai-elements-scheduled-agent"
        const val MIN_INTERVAL_MINUTES = 15
        internal fun workName(id: String) = "$TAG-$id"

        /** ISO 8601 with or without an offset (then the device time zone applies). */
        internal fun parseTime(text: String): Instant = try {
            OffsetDateTime.parse(text).toInstant()
        } catch (_: DateTimeParseException) {
            LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()
        }

        private fun schema(type: String, description: String) = buildJsonObject { put("type", type); put("description", description) }
        private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.i(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

        private fun tool(name: String, description: String, vararg params: Pair<String, JsonObject>, approval: Boolean = false, required: List<String> = emptyList(), run: suspend (JsonObject) -> String) = object : AgentTool {
            override val name = name
            override val description = description
            override val parameters = buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(params.toMap()))
                put("required", JsonArray(required.map(::JsonPrimitive)))
            }
            override val requiresApproval = approval
            override suspend fun execute(arguments: JsonObject) = run(arguments)
        }
    }
}
