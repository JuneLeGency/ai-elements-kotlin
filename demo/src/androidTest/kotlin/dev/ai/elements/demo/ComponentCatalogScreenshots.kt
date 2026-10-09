package dev.ai.elements.demo

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ai.elements.demo.ui.GalleryCard
import dev.ai.elements.demo.ui.GalleryCatalog
import dev.ai.elements.demo.ui.GallerySample
import dev.ai.elements.ui.theme.AiElementsTheme
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The docs site's component catalog, from the Components screen itself: each sample card rendered
 * alone at phone width, captured, plus `catalog.json` (category, summary, API) that
 * `tools/build-component-docs.py` turns into `docs/components/`. Opt-in (`-e catalog true`); files go
 * to `catalog/` in the app's external files dir.
 */
@RunWith(AndroidJUnit4::class)
class ComponentCatalogScreenshots {
    @get:Rule val compose = createComposeRule()

    @Test fun catalog() {
        assumeTrue("Pass -e catalog true", InstrumentationRegistry.getArguments().getString("catalog") == "true")
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "catalog").apply { mkdirs() }
        var current by mutableStateOf<GallerySample?>(null)
        lateinit var captureLayer: GraphicsLayer
        compose.setContent {
            // In English, like the docs site, whatever the device's language.
            val base = LocalContext.current
            val deviceConfiguration = LocalConfiguration.current
            val english = remember(deviceConfiguration) { Configuration(deviceConfiguration).apply { setLocale(Locale.ENGLISH) } }
            // Wraps the activity (launchers find it through the wrapper chain), with English resources.
            val context = remember(base, english) {
                val resources = base.createConfigurationContext(english).resources
                object : android.content.ContextWrapper(base) { override fun getResources(): android.content.res.Resources = resources }
            }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides english, LocalResources provides context.resources) {
            AiElementsTheme(dynamicColor = false, darkTheme = false) {
                current?.let { sample ->
                    val layer = rememberGraphicsLayer()
                    captureLayer = layer
                    // Scroll measurement gives tall samples their complete natural height.
                    // The recorded child layer includes both rounded ends, not just the viewport.
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        Box(Modifier.testTag("catalog-card").recordDocumentationLayer(layer).padding(24.dp)) {
                            GalleryCard(sample.title, Modifier.width(400.dp).shadow(4.dp, MaterialTheme.shapes.extraLarge)) { sample.content() }
                        }
                    }
                }
            }
            }
        }
        // Samples animate forever (persona, streaming JSX): the clock moves only when told.
        compose.mainClock.autoAdvance = false
        GalleryCatalog.forEach { sample ->
            current = sample
            compose.mainClock.advanceTimeBy(2_500)
            Thread.sleep(if (sample.id.startsWith("mermaid") || sample.id in SLOW) 3_000 else 600) // WebViews, decoders
            compose.mainClock.advanceTimeBy(500)
            val image = documentationBitmap(captureLayer)
            File(out, "${sample.id}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(out, "catalog.json").writeText(
            buildJsonArray {
                GalleryCatalog.forEach { s ->
                    add(buildJsonObject {
                        put("id", s.id); put("title", s.title); put("category", s.category.slug); put("categoryTitle", s.category.title)
                        put("summary", s.summary); put("api", s.api); s.elements?.let { put("elements", it) }
                    })
                }
            }.toString(),
        )
        assertTrue(out.listFiles()!!.count { it.extension == "png" } == GalleryCatalog.size)
    }

    private companion object {
        val SLOW = setOf("web-preview", "math", "markdown", "video", "document", "agent-computer", "agent-computer-reply", "step-views", "attachments", "image", "conversation", "inline-citation")
    }
}
