package dev.ai.elements.harness.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ai.elements.core.chat.ToolApprover
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.harness.runHeadless
import kotlinx.coroutines.CancellationException
import java.time.Instant

/**
 * Runs one [ScheduledTask] with the app's agent ([ScheduledAgentHost]) and posts
 * the answer as a notification. Tools that need approval are denied (nobody is there
 * to approve). A failed run is recorded on the task and not retried, so a broken
 * prompt cannot loop.
 */
class ScheduledAgentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = ScheduledTaskStore.get(applicationContext)
        val task = inputData.getString(KEY_TASK)?.let(store::get) ?: return Result.success()
        val host = applicationContext as? ScheduledAgentHost
            ?: return fail(store, task, "The Application does not implement ScheduledAgentHost.")
        return try {
            val reply = runHeadless(host.scheduledBackend(ToolApprover { false }), task.prompt)
            val text = reply.parts.filterIsInstance<TextPart>().joinToString("") { it.text }.trim().ifEmpty { "(no answer)" }
            store.update(task.id) { it.copy(lastRunAt = Instant.now().toString(), lastResult = text.take(MAX_RESULT), lastError = null) }
            notify(task, text)
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fail(store, task, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(store: ScheduledTaskStore, task: ScheduledTask, message: String): Result {
        store.update(task.id) { it.copy(lastRunAt = Instant.now().toString(), lastError = message) }
        notify(task, "Failed: $message")
        return Result.failure()
    }

    private fun notify(task: ScheduledTask, text: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Scheduled agent tasks", NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, task.id.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val icon = context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        val notification = android.app.Notification.Builder(context, CHANNEL).setSmallIcon(icon).setContentTitle(task.title)
            .setContentText(text).setStyle(android.app.Notification.BigTextStyle().bigText(text.take(MAX_RESULT)))
            .setContentIntent(open).setAutoCancel(true).build()
        manager.notify(task.id.hashCode(), notification)
    }

    companion object {
        const val KEY_TASK = "task_id"
        private const val CHANNEL = "ai_elements_scheduled_tasks"
        private const val MAX_RESULT = 4_000
    }
}
