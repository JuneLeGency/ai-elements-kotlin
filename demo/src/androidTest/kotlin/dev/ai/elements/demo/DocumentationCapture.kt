package dev.ai.elements.demo

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

/** Records only the Compose drawing, so the activity background does not fill rounded corners. */
internal fun Modifier.recordDocumentationLayer(layer: GraphicsLayer) = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

internal fun documentationBitmap(layer: GraphicsLayer): Bitmap = runBlocking {
    layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
}.also { bitmap ->
    // The capture includes an outer transparent gutter, not a cropped card or opaque rectangle.
    for ((x, y) in listOf(0 to 0, bitmap.width - 1 to 0, 0 to bitmap.height - 1, bitmap.width - 1 to bitmap.height - 1)) {
        assertEquals("Documentation canvas corners must be transparent", 0, bitmap.getPixel(x, y) ushr 24)
    }
}
