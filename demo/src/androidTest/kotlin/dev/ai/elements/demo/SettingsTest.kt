package dev.ai.elements.demo

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.demo.ui.CapabilityPage
import dev.ai.elements.demo.ui.SettingsPage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings are two levels deep: a grouped home, each entry opening its page; editors are dialogs. */
@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun home_opensEachPage_andProvidersEditInADialog() {
        resetDemoApp(ProviderProfile.Presets.first { it.kind == ProviderKind.MOCK })
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("capabilities-button").performClick()
        compose.onNodeWithTag("capabilities-manage").performClick()

        // Every first-level entry is on the home list.
        listOf(SettingsPage.PROVIDERS, CapabilityPage.MCP, CapabilityPage.SKILLS, CapabilityPage.AGENTS, SettingsPage.DEVICE, SettingsPage.APPEARANCE, SettingsPage.TEXT, SettingsPage.VOICE, SettingsPage.DIAGRAMS)
            .forEach { compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-$it")) }

        // A page, then its editor as a dialog, which closes back onto the page.
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-${SettingsPage.PROVIDERS}"))
        compose.onNodeWithTag("settings-${SettingsPage.PROVIDERS}").performClick()
        compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("settings-provider-openai"))
        compose.onNodeWithTag("settings-provider-openai").performClick()
        compose.onNodeWithTag("provider-close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("provider-close").assertDoesNotExist()
        compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("subscription-sign-in"))

        // Settings that were on the long list now live on their pages. One pane (phones): back to
        // the home; two panes (tablets): the home is beside the page.
        if (compose.onAllNodes(hasTestTag("settings-list")).fetchSemanticsNodes().isEmpty()) compose.activityBack()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-${SettingsPage.APPEARANCE}"))
        compose.onNodeWithTag("settings-${SettingsPage.APPEARANCE}").performClick()
        compose.onNodeWithTag("settings-page").performScrollToNode(hasTestTag("contrast-high"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.activityBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForIdle()
    }
}
