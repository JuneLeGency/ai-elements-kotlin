package dev.ai.elements.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.icons.AiIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

val FilePart.isPdf: Boolean get() = mediaType == "application/pdf" || filename?.endsWith(".pdf", ignoreCase = true) == true

/** The library's own FileProvider, so opening a file never collides with the app's provider. */
class AiElementsFileProvider : FileProvider()

/**
 * [file] as a file in the app's cache: `data:` URLs decoded, `http(s)` and `content:` URLs loaded
 * (through [loader], the app's [LocalFileLoader]); `file:` URLs as they are.
 */
suspend fun localFile(context: Context, file: FilePart, loader: FileLoader): File? = withContext(Dispatchers.IO) {
    val uri = Uri.parse(file.url)
    if (uri.scheme == "file") return@withContext uri.path?.let(::File)
    val name = MessageDigest.getInstance("SHA-256").digest(file.url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    val extension = file.filename?.substringAfterLast('.', "")?.ifEmpty { null } ?: file.mediaType.substringAfter('/').substringBefore(';')
    val out = File(File(context.cacheDir, "ai-elements-media").apply { mkdirs() }, "$name.$extension")
    if (out.exists()) return@withContext out
    val bytes = when {
        file.base64Data != null -> android.util.Base64.decode(file.base64Data, android.util.Base64.DEFAULT)
        uri.scheme == "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        else -> loader.load(file.url)
    } ?: return@withContext null
    out.writeBytes(bytes)
    out
}

/**
 * Opens [file] in an app that can view it (the user's PDF reader, WPS / Office for documents, …),
 * the mobile way to show formats without an in-app renderer. Returns false when no app can.
 */
suspend fun openExternally(context: Context, file: FilePart, loader: FileLoader): Boolean {
    val uri = Uri.parse(file.url)
    val target = if (uri.scheme == "http" || uri.scheme == "https") uri else {
        val local = localFile(context, file, loader) ?: return false
        FileProvider.getUriForFile(context, "${context.packageName}.aielements.files", local)
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(target, file.mediaType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(Intent.createChooser(intent, file.filename).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Renders PDF pages with the platform `PdfRenderer` (one page at a time: it is not thread-safe). */
private class PdfPages(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()
    val count: Int get() = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): ImageBitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.WHITE) }
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}

/**
 * A document in a message (an artifact the agent made or a file it read): its type and name, with
 * the first page and page count for a PDF. Tapping a PDF opens [PdfViewerDialog]; other formats
 * (Word, Excel, PowerPoint, …) open in an app that can show them.
 */
@Composable
fun DocumentAttachment(file: FilePart, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = LocalFileLoader.current
    val scope = rememberCoroutineScope()
    var viewing by rememberSaveable { mutableStateOf(false) }
    val preview by produceState<Pair<ImageBitmap, Int>?>(null, file.url) {
        if (!file.isPdf) return@produceState
        value = runCatching {
            val local = localFile(context, file, loader) ?: return@runCatching null
            PdfPages(local).use { pages -> pages.render(0, 480) to pages.count }
        }.onFailure { android.util.Log.w("AiElements", "PDF preview of ${file.filename ?: file.mediaType} failed", it) }.getOrNull()
    }
    Surface(
        onClick = { if (file.isPdf) viewing = true else scope.launch { openExternally(context, file, loader) } },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.widthIn(max = 360.dp).testTag("file-document"),
    ) {
        Column {
            preview?.let { (page, _) ->
                Image(
                    page, null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).aspectRatio(page.width.toFloat() / page.height, matchHeightConstraintsFirst = true).testTag("document-preview"),
                )
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (file.isPdf) AiIcons.PictureAsPdf else AiIcons.Description, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(file.filename ?: file.mediaType, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(documentType(file), preview?.second?.let { stringResource(R.string.ai_pages, it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { scope.launch { openExternally(context, file, loader) } }, modifier = Modifier.testTag("document-open")) {
                    Icon(AiIcons.OpenInNew, stringResource(R.string.ai_open_with))
                }
            }
        }
    }
    if (viewing) PdfViewerDialog(file, onDismiss = { viewing = false })
}

private fun documentType(file: FilePart): String = when {
    file.isPdf -> "PDF"
    else -> when (file.filename?.substringAfterLast('.', "")?.lowercase()) {
        "doc", "docx" -> "Word"
        "xls", "xlsx", "csv" -> "Excel"
        "ppt", "pptx" -> "PowerPoint"
        "txt", "md" -> "Text"
        else -> file.mediaType.substringAfter('/').substringBefore(';').uppercase().take(12)
    }
}

/** A PDF full screen: every page, pinch to zoom, and "Open with" for a full reader. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerDialog(file: FilePart, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val loader = LocalFileLoader.current
    val scope = rememberCoroutineScope()
    val pages by produceState<PdfPages?>(null, file.url) {
        value = runCatching { localFile(context, file, loader)?.let(::PdfPages) }
            .onFailure { android.util.Log.w("AiElements", "PDF viewer for ${file.filename ?: file.mediaType} failed", it) }.getOrNull()
        awaitDispose { value?.close() }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest).testTag("pdf-viewer")) {
            pages?.let { doc ->
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f) } }
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .safeDrawingPadding()
                        .padding(top = 56.dp, start = 12.dp, end = 12.dp)
                        .testTag("pdf-pages"),
                ) {
                    items(doc.count) { index ->
                        val page by produceState<ImageBitmap?>(null, doc, index) {
                            value = runCatching { doc.render(index, 1200) }.onFailure { android.util.Log.w("AiElements", "PDF page $index failed", it) }.getOrNull()
                        }
                        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth().aspectRatio(page?.let { it.width.toFloat() / it.height } ?: 0.707f).background(Color.White).testTag("pdf-page-$index")) {
                            page?.let { Image(it, null, Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
                FilledTonalIconButton(onClick = { scope.launch { openExternally(context, file, loader) } }, shapes = IconButtonDefaults.shapes()) {
                    Icon(AiIcons.OpenInNew, stringResource(R.string.ai_open_with))
                }
                FilledTonalIconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(start = 8.dp).testTag("pdf-close")) {
                    Icon(AiIcons.Close, stringResource(R.string.ai_close))
                }
            }
        }
    }
}
