package dev.ai.elements.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.ai.elements.ui.chat.AgentProgress
import dev.ai.elements.ui.chat.StepStatus
import dev.ai.elements.ui.chat.activityLabel

/**
 * The agent's run as notifications. While it runs: one ongoing notification that follows it, as an
 * Android 16 Live Update (`ProgressStyle`, promoted to the status bar chip and the lock screen,
 * `shortCriticalText` in the chip) and a plain progress notification on earlier versions. When it
 * ends: a "reply ready" (or "stopped") notification. Every text comes from [AgentProgress].
 *
 * Approvals are not answered from the notification: it says the agent is waiting and opens the chat,
 * where the user sees what the call would do.
 *
 * This artifact declares `POST_NOTIFICATIONS` (which the app asks for at runtime) and
 * `POST_PROMOTED_NOTIFICATIONS`. An app that keeps the run going in the background posts [running]
 * from a foreground service.
 */
class AgentRunNotifications(
    private val context: Context,
    val channelId: String = CHANNEL,
    @param:DrawableRes private val smallIcon: Int = R.drawable.ai_elements_ic_agent,
) {
    /** Creates the channel (once); call before posting. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channelId, context.getString(R.string.ai_notif_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.ai_notif_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** The ongoing notification of a run in progress. */
    fun running(progress: AgentProgress, title: String, contentIntent: PendingIntent?, stopIntent: PendingIntent? = null, startedAtMs: Long = System.currentTimeMillis()): Notification {
        val res = context.resources
        val text = when (progress.phase) {
            AgentProgress.Phase.THINKING -> res.getString(R.string.ai_notif_thinking)
            AgentProgress.Phase.WRITING -> res.getString(R.string.ai_notif_writing)
            AgentProgress.Phase.NEEDS_APPROVAL -> res.getString(R.string.ai_notif_approval, progress.tool?.displayName.orEmpty())
            AgentProgress.Phase.NEEDS_INPUT -> res.getString(R.string.ai_notif_input)
            else -> progress.tool?.activityLabel(res) ?: res.getString(R.string.ai_notif_thinking)
        }
        val waiting = progress.phase == AgentProgress.Phase.NEEDS_APPROVAL || progress.phase == AgentProgress.Phase.NEEDS_INPUT
        val chip = when {
            waiting -> res.getString(R.string.ai_notif_chip_waiting)
            progress.plan.isNotEmpty() -> "${progress.planDone}/${progress.plan.size}"
            progress.steps > 0 -> res.getString(R.string.ai_notif_chip_step, progress.steps)
            else -> null
        }
        // The plan as segments, one per step (Live Update progress); otherwise indeterminate.
        val style = NotificationCompat.ProgressStyle()
        if (progress.plan.isEmpty()) {
            style.setProgressIndeterminate(true)
        } else {
            progress.plan.forEach { _ -> style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(1)) }
            style.setProgress(progress.planDone)
            style.setStyledByProgress(true)
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(progress.plan.firstOrNull { it.status == StepStatus.ACTIVE }?.label)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(!waiting)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setRequestPromotedOngoing(true)
            .apply { chip?.let { setShortCriticalText(it) } }
            .setWhen(startedAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(contentIntent)
            .apply {
                if (waiting && contentIntent != null) addAction(0, res.getString(R.string.ai_notif_review), contentIntent)
                if (stopIntent != null) addAction(0, res.getString(R.string.ai_notif_stop), stopIntent)
            }
            .build()
    }

    /** The notification of a finished run: the reply's start, or why it stopped. */
    fun finished(progress: AgentProgress, title: String, contentIntent: PendingIntent?): Notification {
        val res = context.resources
        val failed = progress.phase == AgentProgress.Phase.FAILED
        val text = if (failed) progress.error ?: res.getString(R.string.ai_notif_failed) else progress.reply.take(MAX_PREVIEW).ifBlank { res.getString(R.string.ai_notif_done) }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(if (failed) res.getString(R.string.ai_notif_failed) else title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(if (failed) title else res.getString(R.string.ai_notif_done))
            .setAutoCancel(true)
            .setCategory(if (failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)
            .build()
    }

    /** Posts [notification] as [id] if the app may post notifications; returns whether it did. */
    @android.annotation.SuppressLint("MissingPermission") // checked just before
    fun post(id: Int, notification: Notification): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        manager.notify(id, notification)
        return true
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    companion object {
        const val CHANNEL = "ai-elements-agent-runs"
        private const val MAX_PREVIEW = 600
    }
}
