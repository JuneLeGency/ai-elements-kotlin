package dev.ai.elements.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A generated-image component, mirroring the web `Image` (which renders a
 * `data:` URI from a base64 payload + media type).
 *
 * On Android we decode the base64 string with [kotlin.io.encoding.Base64] and turn
 * it into an [ImageBitmap] with [android.graphics.BitmapFactory]. When decoding
 * fails (empty payload, unsupported codec, or a non-bitmap media type) a neutral
 * placeholder box is shown instead of crashing.
 *
 * @param base64 the base64-encoded image bytes (no `data:` prefix).
 * @param mediaType the MIME type (e.g. `image/png`); used for the placeholder hint.
 * @param alt accessibility label.
 * @param contentScale how the bitmap is fitted into the available width.
 */
@OptIn(ExperimentalEncodingApi::class)
@Composable
fun GeneratedImage(
    base64: String,
    mediaType: String = "image/png",
    modifier: Modifier = Modifier,
    alt: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap = rememberBitmap(base64)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = alt,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 480.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = alt ?: "Image",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun rememberBitmap(base64: String): ImageBitmap? {
    // Memoize on the payload so we don't re-decode on every recomposition.
    return remember(base64) {
        if (base64.isEmpty()) {
            null
        } else {
            runCatching {
                val bytes = Base64.Mime.decode(base64)
                if (bytes.isEmpty()) {
                    null
                } else {
                    android.graphics.BitmapFactory
                        .decodeByteArray(bytes, 0, bytes.size)
                        ?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
}
