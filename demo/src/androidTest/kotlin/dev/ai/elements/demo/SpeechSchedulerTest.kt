package dev.ai.elements.demo

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.ai.elements.harness.scheduler.Scheduler
import dev.ai.elements.harness.speech.Speech
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** harness-speech on the device's TTS engine; harness-scheduler end to end on the real WorkManager with the offline provider. */
@RunWith(AndroidJUnit4::class)
class SpeechSchedulerTest {
    private val app = ApplicationProvider.getApplicationContext<DemoApplication>()
    private val scheduler = Scheduler(app)
    private var previousProvider: String? = null

    @After
    fun restore() {
        scheduler.tasks.value.forEach { scheduler.cancel(it.id) }
        previousProvider?.let(app.providers::select)
    }

    @Test
    fun speak_waitsUntilSpoken_andStopInterrupts() = runBlocking<Unit> {
        val speech = Speech(app)
        try {
            val tools = speech.tools().associateBy { it.name }
            val result = runCatching { tools.getValue("speak").execute(buildJsonObject { put("text", "Hello from the agent."); put("rate", 1.5) }) }
            assumeTrue("No TTS engine on this device: ${result.exceptionOrNull()?.message}", result.exceptionOrNull()?.message?.contains("No text-to-speech engine") != true)
            assertEquals("Spoke 21 characters.", result.getOrThrow())
            assertEquals("Stopped.", tools.getValue("stop_speaking").execute(buildJsonObject {}))
        } finally {
            speech.close()
        }
    }

    @Test
    fun scheduledTask_runsInTheBackground_withTheAppsAgent() = runBlocking<Unit> {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        previousProvider = app.providers.selectedId.value
        app.providers.select("mock")

        val tools = scheduler.tools().associateBy { it.name }
        assertTrue(tools.getValue("schedule_task").requiresApproval)
        val scheduled = tools.getValue("schedule_task").execute(buildJsonObject {
            put("title", "Morning brief")
            put("prompt", "Say good morning.")
            put("in_minutes", 0)
        })
        val id = scheduled.substringAfter("(id ").substringBefore(")")

        // WorkManager runs it (no delay); the worker records the answer on the task.
        val done = withTimeout(60_000) { scheduler.tasks.first { tasks -> tasks.any { it.id == id && it.lastRunAt != null } } }.single { it.id == id }
        assertNull(done.lastError)
        assertTrue(done.lastResult.orEmpty().isNotBlank())
        val info = withTimeout(10_000) {
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow("${Scheduler.TAG}-$id").first { it.singleOrNull()?.state?.isFinished == true }.single()
        }
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)

        val listed = Json.parseToJsonElement(tools.getValue("list_scheduled_tasks").execute(buildJsonObject {})).jsonArray
        assertEquals(1, listed.size)
        tools.getValue("cancel_scheduled_task").execute(buildJsonObject { put("id", id) })
        assertTrue(scheduler.tasks.value.none { it.id == id })
    }

    @Test
    fun repeatingTasks_needAtLeast15Minutes() {
        val error = runCatching { scheduler.schedule("x", "y", Instant.now(), everyMinutes = 5) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
