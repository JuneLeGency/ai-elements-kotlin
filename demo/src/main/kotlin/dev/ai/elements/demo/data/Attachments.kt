package dev.ai.elements.demo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dev.ai.elements.core.model.FilePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Turn a picked image into a `data:` URL [FilePart], downscaled to [maxPx] and
 * re-encoded as JPEG so requests (and saved history) stay small.
 */
suspend fun Context.imageAttachment(uri: Uri, maxPx: Int = 1024): FilePart? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxPx * 2 || bounds.outHeight / sample > maxPx * 2) sample *= 2
        val decoded = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val scale = minOf(1f, maxPx.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        FilePart(
            id = UUID.randomUUID().toString(),
            mediaType = "image/jpeg",
            url = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
            filename = uri.lastPathSegment,
        )
    }.getOrNull()
}
