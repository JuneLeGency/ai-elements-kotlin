package dev.ai.elements.demo

import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The history: one tap on an earlier conversation opens it (a drawer on phones, a pane on tablets). */
@RunWith(AndroidJUnit4::class)
class HistoryTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun send(text: String) {
        compose.onNodeWithTag("prompt-input").performClick().performTextInput(text)
        compose.onNodeWithTag("send-button").performClick()
        scenario?.hideKeyboard()
        compose.awaitTurnEnd(60_000)
    }

    @Test
    fun oneTapOpensAnEarlierConversation() {
        val app = resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        scenario = ActivityScenario.launch(MainActivity::class.java)
        send(FIRST)
        compose.onNodeWithTag("new-chat").performClick()
        send("What is 6 * 7?")

        // Phones: the history is behind the menu button; tablets show it beside the chat.
        val menu = hasContentDescription(app.getString(R.string.conversations))
        val drawer = compose.onAllNodes(menu).fetchSemanticsNodes().isNotEmpty()
        if (drawer) compose.onNode(menu).performClick()
        compose.onNodeWithText(FIRST).performClick()

        compose.onNodeWithTag("conversation").performScrollToNode(hasText(FIRST))
        // One tap also closed the drawer: its title (text; the menu button's is a description) is off screen.
        if (drawer) compose.onNodeWithText(app.getString(R.string.conversations)).assertIsNotDisplayed()
    }

    private companion object {
        const val FIRST = "What time is it in Tokyo?"
    }
}
