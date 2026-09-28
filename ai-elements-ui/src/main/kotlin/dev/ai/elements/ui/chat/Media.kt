package dev.ai.elements.ui.chat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.icons.AiIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

val FilePart.isVideo: Boolean get() = mediaType.startsWith("video/")
val FilePart.isAudio: Boolean get() = mediaType.startsWith("audio/")

/**
 * A URI the platform media stack can open for [file]: `http(s)`, `content:` and `file:` URLs as
 * they are; a `data:` URL is written to the app's cache once (media players cannot read it).
 */
suspend fun playableUri(context: Context, file: FilePart): Uri = withContext(Dispatchers.IO) {
    val data = file.base64Data ?: return@withContext Uri.parse(file.url)
    val name = MessageDigest.getInstance("SHA-256").digest(file.url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    val extension = file.mediaType.substringAfter('/').substringBefore(';').substringBefore('+')
    val out = File(File(context.cacheDir, "ai-elements-media").apply { mkdirs() }, "$name.$extension")
    if (!out.exists()) out.writeBytes(Base64.decode(data, Base64.DEFAULT))
    Uri.fromFile(out)
}

private data class VideoInfo(val poster: ImageBitmap?, val width: Int, val height: Int, val durationMs: Long)

/**
 * A video in a message: its first frame, duration and a play button; tapping plays it full screen
 * (platform `VideoView`, with the system media controls).
 */
@Composable
fun VideoAttachment(file: FilePart, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by rememberSaveable { mutableStateOf(false) }
    val info by produceState<VideoInfo?>(null, file.url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = playableUri(context, file)
                MediaMetadataRetriever().run {
                    try {
                        if (uri.scheme == "http" || uri.scheme == "https") setDataSource(uri.toString(), emptyMap()) else setDataSource(context, uri)
                        VideoInfo(
                            poster = getFrameAtTime(0)?.asImageBitmap(),
                            width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 16,
                            height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 9,
                            durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                        ).let { v ->
                            // Portrait recordings report landscape sizes with a rotation.
                            val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                            if (rotation % 180 != 0) v.copy(width = v.height, height = v.width) else v
                        }
                    } finally {
                        release()
                    }
                }
            }.getOrNull()
        }
    }
    val ratio = info?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (16f / 9f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(max = 260.dp)
            .aspectRatio(ratio.coerceIn(0.5f, 2.4f))
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black)
            .clickable(onClickLabel = stringResource(R.string.ai_play_video)) { playing = true }
            .testTag("file-video"),
    ) {
        info?.poster?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White) {
            Icon(AiIcons.PlayArrow, stringResource(R.string.ai_play_video), Modifier.padding(12.dp).size(32.dp))
        }
        info?.durationMs?.takeIf { it > 0 }?.let { ms ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) { Text("%d:%02d".format(ms / 60_000, ms / 1000 % 60), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
    }
    if (playing) VideoPlayerDialog(file, onDismiss = { playing = false })
}

/** Plays [file] full screen with the platform player and media controls. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerDialog(file: FilePart, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri by produceState<Uri?>(null, file.url) { value = runCatching { playableUri(context, file) }.getOrNull() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("video-player"), contentAlignment = Alignment.Center) {
            uri?.let { source ->
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controls = MediaController(ctx).also { it.setAnchorView(this) }
                            setMediaController(controls)
                            setVideoURI(source)
                            setOnPreparedListener { start(); controls.show(2_000) }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FilledTonalIconButton(
                onClick = onDismiss,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp).testTag("video-close"),
            ) { Icon(AiIcons.Close, stringResource(R.string.ai_close)) }
        }
    }
}
