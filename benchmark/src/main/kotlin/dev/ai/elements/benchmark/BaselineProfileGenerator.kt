package dev.ai.elements.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The demo's Baseline Profile: the code of startup and of the chat's hot paths (streaming,
 * scrolling, the history, switching conversations) is compiled ahead of time at install, so
 * the first run is as smooth as later ones. `./gradlew :demo:generateReleaseBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        ensureTwoConversations()
        scrollConversation()
        switchConversation()
        newChat()
        send(LONG_PROMPT)
        awaitReply()
    }
}
