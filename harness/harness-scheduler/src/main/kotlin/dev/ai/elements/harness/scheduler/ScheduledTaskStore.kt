package dev.ai.elements.harness.scheduler

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A prompt the agent runs later; [everyMinutes] null runs it once. Times are ISO 8601 instants. */
@Serializable
data class ScheduledTask(
    val id: String,
    val title: String,
    val prompt: String,
    val firstRunAt: String,
    val everyMinutes: Int? = null,
    val lastRunAt: String? = null,
    val lastResult: String? = null,
    val lastError: String? = null,
)

/** Scheduled tasks in SharedPreferences, shared by [Scheduler] and [ScheduledAgentWorker]. */
internal class ScheduledTaskStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("ai_elements_scheduled_tasks", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ScheduledTask.serializer())

    private val _tasks = MutableStateFlow(
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty(),
    )
    val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()

    fun get(id: String) = _tasks.value.firstOrNull { it.id == id }

    fun put(task: ScheduledTask) = save { list -> list.filter { it.id != task.id } + task }

    fun remove(id: String): Boolean {
        val had = get(id) != null
        save { list -> list.filter { it.id != id } }
        return had
    }

    fun update(id: String, transform: (ScheduledTask) -> ScheduledTask) = save { list -> list.map { if (it.id == id) transform(it) else it } }

    @Synchronized
    private fun save(transform: (List<ScheduledTask>) -> List<ScheduledTask>) {
        _tasks.update(transform)
        prefs.edit().putString(KEY, json.encodeToString(serializer, _tasks.value)).apply()
    }

    companion object {
        private const val KEY = "tasks"
        @Volatile private var instance: ScheduledTaskStore? = null

        fun get(context: Context): ScheduledTaskStore =
            instance ?: synchronized(this) { instance ?: ScheduledTaskStore(context.applicationContext).also { instance = it } }
    }
}
