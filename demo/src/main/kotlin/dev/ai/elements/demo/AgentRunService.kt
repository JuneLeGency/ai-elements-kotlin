package dev.ai.elements.demo

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.notifications.AgentRunNotifications
import dev.ai.elements.ui.chat.AgentProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** The chat's run as the notification follows it; the chat updates it, the service shows it. */
class AgentRunTracker {
    data class Run(val state: ChatState, val title: String, val startedAtMs: Long)

    private val _run = MutableStateFlow<Run?>(null)
    val run: StateFlow<Run?> = _run.asStateFlow()

    /** Stops the chat's run (the notification's Stop action). */
    @Volatile var stop: (() -> Unit)? = null

    fun update(state: ChatState, title: String) {
        val started = _run.value?.takeIf { it.state.isBusy }?.startedAtMs ?: System.currentTimeMillis()
        _run.value = Run(state, title, started)
    }
}

/**
 * Keeps the agent's run going while the app is in the background (a `dataSync` foreground service,
 * as Android requires for work the user started and expects to finish), with the run's Live Update
 * notification. Started when a run starts; stops itself when it ends, leaving a "reply ready"
 * notification if the user is elsewhere.
 */
class AgentRunService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tracker get() = (application as DemoApplication).agentRuns
    private lateinit var notifications: AgentRunNotifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = AgentRunNotifications(this).also { it.ensureChannel() }
        // A foreground service must show its notification right away.
        val run = tracker.run.value
        val progress = run?.let { AgentProgress.of(it.state) } ?: AgentProgress(AgentProgress.Phase.THINKING)
        ServiceCompat.startForeground(
            this, RUNNING_ID,
            notifications.running(progress, run?.title ?: getString(R.string.app_name), openApp(), stopIntent()),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        scope.launch {
            tracker.run.filterNotNull().collect { run ->
                val progress = AgentProgress.of(run.state) ?: return@collect
                if (progress.isActive && run.state.isBusy || progress.phase == AgentProgress.Phase.NEEDS_APPROVAL || progress.phase == AgentProgress.Phase.NEEDS_INPUT) {
                    notifications.post(RUNNING_ID, notifications.running(progress, run.title, openApp(), stopIntent(), run.startedAtMs))
                } else {
                    ServiceCompat.stopForeground(this@AgentRunService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    // The user is elsewhere: tell them the reply is ready (or why the run stopped).
                    if (!inForeground()) notifications.post(DONE_ID, notifications.finished(progress, run.title, openApp()))
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) tracker.stop?.invoke()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this, 1, Intent(this, AgentRunService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun inForeground(): Boolean = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        .importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    companion object {
        const val RUNNING_ID = 4101
        const val DONE_ID = 4102
        private const val ACTION_STOP = "dev.ai.elements.demo.action.STOP_RUN"

        /** Starts the service for a run the user just started (the app is in front). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, AgentRunService::class.java)) }
        }
    }
}
