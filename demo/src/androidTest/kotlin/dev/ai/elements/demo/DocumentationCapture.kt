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
    for (x in 0 until bitmap.width) {
        assertEquals("Documentation top gutter must be transparent", 0, bitmap.getPixel(x, 0) ushr 24)
        assertEquals("Documentation bottom gutter must be transparent", 0, bitmap.getPixel(x, bitmap.height - 1) ushr 24)
    }
    for (y in 0 until bitmap.height) {
        assertEquals("Documentation left gutter must be transparent", 0, bitmap.getPixel(0, y) ushr 24)
        assertEquals("Documentation right gutter must be transparent", 0, bitmap.getPixel(bitmap.width - 1, y) ushr 24)
    }
}
