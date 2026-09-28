package dev.ai.elements.ui.chat

import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import dev.ai.elements.ui.icons.AiIcons
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.nio.ByteBuffer
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An image from a [FilePart] (AI Elements `<Image>`): decodes `data:` URLs and
 * fetches `http(s)` URLs off the main thread, downsampled to [maxDecodePx].
 *
 * With [fitToImage] the box takes the image's aspect ratio (for inline images
 * with a width but no height); otherwise it fills the size given by [modifier].
 */
@Composable
fun FileImage(
    file: FilePart,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fitToImage: Boolean = false,
    maxDecodePx: Int = 1600,
) {
    val loader = LocalFileLoader.current
    val bitmap by produceState<ImageBitmap?>(null, file.url) {
        value = runCatching {
            val bytes = file.base64Data?.let { Base64.decode(it, Base64.DEFAULT) } ?: loader.load(file.url)
            bytes?.let { withContext(Dispatchers.Default) { decode(it, maxDecodePx) } }
        }.getOrNull()
    }
    val image = bitmap
    val sized = when {
        !fitToImage -> modifier
        image != null -> modifier.aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
        else -> modifier.height(160.dp)
    }
    Box(sized.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, file.filename ?: stringResource(R.string.ai_image), Modifier.matchParentSize(), contentScale = contentScale)
        } else {
            Icon(AiIcons.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A file part inside a message: images, videos and audio inline; documents (PDF preview, others open in an app). */
@Composable
fun FileAttachment(file: FilePart, modifier: Modifier = Modifier, imageHeight: Dp = 220.dp) {
    if (file.isImage) {
        var viewing by rememberSaveable { mutableStateOf(false) }
        FileImage(
            file,
            modifier = modifier
                .heightIn(max = imageHeight)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = stringResource(R.string.ai_view_image)) { viewing = true }
                .testTag("file-image"),
            contentScale = ContentScale.Fit,
            fitToImage = true,
        )
        if (viewing) ImageViewer(file, onDismiss = { viewing = false })
    } else if (file.isVideo) {
        VideoAttachment(file, modifier)
    } else if (file.isAudio) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val source by produceState<String?>(null, file.url) { value = runCatching { playableUri(context, file).toString() }.getOrNull() }
        source?.let { dev.ai.elements.ui.voice.AudioPlayer(dev.ai.elements.ui.voice.rememberAudioPlayerState(it), modifier, title = file.filename) }
    } else {
        DocumentAttachment(file, modifier)
    }
}

/**
 * Pending attachments above the composer (AI Elements `<PromptInputAttachments>`),
 * each removable.
 */
@Composable
fun AttachmentStrip(
    attachments: List<FilePart>,
    onRemove: (FilePart) -> Unit,
    modifier: Modifier = Modifier,
    thumbSize: Dp = 64.dp,
) {
    if (attachments.isEmpty()) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments, key = { it.id }) { file ->
            Box(Modifier.size(thumbSize)) {
                FileImage(file, Modifier.matchParentSize().clip(MaterialTheme.shapes.medium))
                // 20dp visual, 48dp touch target, centred on the thumbnail's corner.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-12).dp)
                        .size(AiSize.touchTarget)
                        .clickable(onClickLabel = stringResource(R.string.ai_remove_attachment)) { onRemove(file) }
                        .testTag("remove-attachment"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(20.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.inverseSurface),
                    ) {
                        Icon(AiIcons.Close, stringResource(R.string.ai_remove_attachment), Modifier.size(AiSpacing.l), MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
    }
}

/**
 * Decodes [bytes] upright (camera photos store their rotation in EXIF) and downsampled so the
 * longer side is at most [maxPx]. Android 9+ decodes with `ImageDecoder`, which applies EXIF;
 * older versions rotate by the EXIF orientation themselves.
 */
internal fun decode(bytes: ByteArray, maxPx: Int): ImageBitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val scale = maxPx.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        bitmap?.let { upright(it, bytes) }?.asImageBitmap()
    }
}.getOrNull()

private fun upright(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val orientation = runCatching {
        ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
