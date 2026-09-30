package dev.ai.elements.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The demo app; its Compose test tags are resource ids (`testTagsAsResourceId`). */
const val PACKAGE = "dev.ai.elements.demo"

/** The offline agent answers this with 24 streamed sections (Markdown, links, inline code). */
const val LONG_PROMPT = "Write a long answer"

private const val TIMEOUT = 10_000L

private fun MacrobenchmarkScope.find(res: String): UiObject2 =
    device.wait(Until.findObject(By.res(res)), TIMEOUT) ?: run {
        // What was on screen instead (kept after the test app is uninstalled).
        device.executeShellCommand("screencap -p /data/local/tmp/benchmark-missing-$res.png")
        device.executeShellCommand("uiautomator dump /data/local/tmp/benchmark-missing-$res.xml")
        error("'$res' is not on screen (in front: ${device.currentPackageName})")
    }

/**
 * The first message asks for the notification permission (the run's Live Update), in the system's
 * or the vendor's dialog. Back dismisses it without answering, and the app does not ask again.
 */
fun MacrobenchmarkScope.dismissPermissionDialog() {
    device.waitForIdle()
    if (device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 1_500) && device.currentPackageName == PACKAGE) return
    device.pressBack()
    device.waitForIdle()
}

fun MacrobenchmarkScope.send(text: String) {
    find("prompt-input").apply { click(); setText(text) }
    find("send-button").click()
    dismissPermissionDialog()
    hideKeyboard()
}

/** Back closes the keyboard (the reader's position: the whole conversation on screen). */
fun MacrobenchmarkScope.hideKeyboard() {
    if (device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")) {
        device.pressBack()
        device.waitForIdle()
    }
}

/** Lets a fling or a transition finish (waitForIdle can wait out its whole timeout meanwhile). */
private fun settle() = android.os.SystemClock.sleep(700)

/** Waits for the reply to finish: the stop button is gone and the reply's actions are shown. */
fun MacrobenchmarkScope.awaitReply(timeoutMs: Long = 120_000) {
    device.wait(Until.hasObject(By.res("stop-button")), 5_000)
    check(device.wait(Until.gone(By.res("stop-button")), timeoutMs)) { "the reply did not finish in $timeoutMs ms" }
    device.wait(Until.hasObject(By.res("regenerate")), TIMEOUT)
    device.waitForIdle()
}

/** Selects the offline agent (no network, no key), whatever the device's demo was set to. */
fun MacrobenchmarkScope.useOfflineAgent() {
    find("provider-button").click()
    val option = device.wait(Until.findObject(By.res("provider-mock")), TIMEOUT)
    if (option != null) option.click() else device.pressBack()
    device.wait(Until.gone(By.res("provider-mock")), 3_000)
    device.waitForIdle()
}

/**
 * The app opens on a new chat; opens the long conversation, making it first if there is none.
 * Leaves it on screen, at its end.
 */
fun MacrobenchmarkScope.openLongConversation() {
    useOfflineAgent()
    // Earlier conversations with this title may have ended in an error: open each until one is
    // the long answer (it opens at its end, "Section 24").
    for (index in 0 until 8) {
        openHistory()
        val rows = device.findObjects(By.res("history-row")).filter { it.hasObject(By.text(LONG_PROMPT)) }
        if (index >= rows.size) {
            device.pressBack()
            break
        }
        rows[index].click()
        device.waitForIdle()
        if (device.wait(Until.hasObject(By.textContains("Section 24")), 3_000)) return
    }
    newChat()
    send(LONG_PROMPT)
    awaitReply()
}

fun MacrobenchmarkScope.newChat() {
    device.findObject(By.res("new-chat"))?.takeIf { it.isEnabled }?.click()
    device.wait(Until.hasObject(By.res("prompt-input")), TIMEOUT)
}

/** Two conversations in the history, the long answer on screen. */
fun MacrobenchmarkScope.ensureTwoConversations() {
    openLongConversation()
    openHistory()
    val rows = device.findObjects(By.res("history-row")).size
    device.pressBack()
    device.waitForIdle()
    if (rows >= 2) return
    newChat()
    send("What is 6 * 7?")
    awaitReply()
    openLongConversation()
}

/** Flings the conversation to its start and back, the way a reader skims a long answer. */
fun MacrobenchmarkScope.scrollConversation() {
    val list = find("conversation")
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) {
        list.fling(Direction.UP)
        settle()
    }
    repeat(2) {
        list.fling(Direction.DOWN)
        settle()
    }
}

/** Phones: the history is a drawer behind the menu button; tablets show it beside the chat. */
fun MacrobenchmarkScope.openHistory() {
    hideKeyboard()
    repeat(2) {
        val menu = device.wait(Until.findObject(By.res("history-open")), 3_000) ?: return // a pane: already shown
        menu.click()
        // A tap while the drawer still slides in stops it instead of reaching the row.
        if (device.wait(Until.hasObject(By.res("history-row")), 3_000)) return settle()
    }
}

/** Opens the history and another conversation: the drawer, then a long chat laid out at its end. */
fun MacrobenchmarkScope.switchConversation() {
    openHistory()
    val other = device.wait(Until.findObject(By.res("history-row").selected(false)), TIMEOUT)
    checkNotNull(other) { "needs two conversations" }.click()
    settle()
}
