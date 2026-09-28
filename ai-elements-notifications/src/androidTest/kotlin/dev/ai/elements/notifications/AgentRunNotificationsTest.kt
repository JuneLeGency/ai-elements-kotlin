package dev.ai.elements.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.core.chat.ChatState
import dev.ai.elements.core.model.ChatStatus
import dev.ai.elements.core.model.DataPart
import dev.ai.elements.core.model.Message
import dev.ai.elements.core.model.Role
import dev.ai.elements.core.model.TextPart
import dev.ai.elements.core.model.ToolCategory
import dev.ai.elements.core.model.ToolPart
import dev.ai.elements.core.model.ToolState
import dev.ai.elements.ui.chat.AgentProgress
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The run's notifications as built (posting needs the user's permission; building does not). */
@RunWith(AndroidJUnit4::class)
class AgentRunNotificationsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = AgentRunNotifications(context).also { it.ensureChannel() }
    private val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE)
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private val user = Message("u", Role.USER, listOf(TextPart("t", "Fix the build")))
    private val plan = DataPart("p", "plan", Json.parseToJsonElement("""{"steps":[{"label":"Read","status":"complete"},{"label":"Fix","status":"active"},{"label":"Test"}]}"""))

    @Test fun running_isALiveUpdateWithThePlanAndTheStep() {
        val tool = ToolPart("c1", "run_command", ToolState.INPUT_AVAILABLE, """{"command":"./gradlew test"}""", title = "./gradlew test", category = ToolCategory.EXECUTE)
        val state = ChatState(messages = listOf(user, Message("a", Role.ASSISTANT, listOf(plan, tool))), status = ChatStatus.STREAMING)
        val n = notifications.running(AgentProgress.of(state)!!, "Fix the build", open, open)
        assertEquals("Fix the build", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(s(dev.ai.elements.ui.R.string.ai_activity_execute) + " · ./gradlew test", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Fix", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf(s(R.string.ai_notif_stop)), n.actions.map { it.title.toString() })
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 Live Update: ProgressStyle with a segment per plan step, promoted, a chip.
            assertEquals(Notification.ProgressStyle::class.java.name, n.extras.getString(Notification.EXTRA_TEMPLATE))
            assertEquals(1, n.extras.getInt(Notification.EXTRA_PROGRESS))
            assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
            assertEquals("1/3", n.shortCriticalText)
            assertTrue("promotable", n.hasPromotableCharacteristics())
        }
    }

    @Test fun waitingForApproval_saysSoAndOpensTheChat() {
        val tool = ToolPart("c2", "save_note", ToolState.APPROVAL_REQUESTED, "{}", title = "Save note")
        val state = ChatState(messages = listOf(user, Message("a", Role.ASSISTANT, listOf(tool))), status = ChatStatus.STREAMING)
        val n = notifications.running(AgentProgress.of(state)!!, "Notes", open, open)
        assertEquals(s(R.string.ai_notif_approval, "Save note"), n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        // No approve button: the user sees the call in the chat before deciding.
        assertEquals(listOf(s(R.string.ai_notif_review), s(R.string.ai_notif_stop)), n.actions.map { it.title.toString() })
        if (Build.VERSION.SDK_INT >= 36) assertEquals(s(R.string.ai_notif_chip_waiting), n.shortCriticalText)
    }

    @Test fun finished_showsTheReply() {
        val state = ChatState(messages = listOf(user, Message("a", Role.ASSISTANT, listOf(TextPart("x", "All tests pass now.")))))
        val n = notifications.finished(AgentProgress.of(state)!!, "Fix the build", open)
        assertEquals("All tests pass now.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT == 0)
    }
}
