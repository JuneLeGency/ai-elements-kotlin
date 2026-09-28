package dev.ai.elements.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ai.elements.demo.ui.GalleryScreen
import dev.ai.elements.ui.theme.AiElementsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Components screen: filter by category, and interactive samples work. */
@RunWith(AndroidJUnit4::class)
class GalleryTest {
    @get:Rule val compose = createComposeRule()

    /** The registry the docs catalog is generated from: every title has a sample, ids are unique, every group is used. */
    @Test fun catalog_everySampleHasContent_uniqueIds_everyGroupUsed() {
        val catalog = dev.ai.elements.demo.ui.GalleryCatalog // looking up a missing sample throws
        org.junit.Assert.assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        org.junit.Assert.assertEquals(dev.ai.elements.demo.ui.GalleryCategory.entries.toList(), catalog.map { it.category }.distinct())
        org.junit.Assert.assertTrue(catalog.all { it.summary.isNotBlank() && it.api.isNotBlank() })
    }

    @Test fun filterShowsOneCategory_jsxFormSendsItsAction() {
        compose.setContent { AiElementsTheme(dynamicColor = false) { GalleryScreen() } }
        compose.onNodeWithTag("gallery-section-conversation").assertIsDisplayed()
        compose.onNodeWithTag("gallery-filters").performScrollToKey("generative-ui")
        compose.onNodeWithTag("gallery-filter-generative-ui").performClick()
        compose.onNodeWithTag("gallery-section-generative-ui").assertIsDisplayed()
        compose.onNodeWithTag("gallery-section-conversation").assertDoesNotExist()
        // A JSX sample: the button's onClick={subscribe} reaches the host with the data model.
        compose.onNodeWithTag("gallery").performScrollToKey("jsx-form")
        compose.onNodeWithText("Subscribe").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("Action → subscribe", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("gallery-filter-all").performClick()
        compose.onNodeWithTag("gallery-section-conversation").assertIsDisplayed()
    }
}
