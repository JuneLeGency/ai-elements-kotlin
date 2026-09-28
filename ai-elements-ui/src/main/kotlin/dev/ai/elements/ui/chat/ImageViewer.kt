package dev.ai.elements.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.R
import dev.ai.elements.ui.icons.AiIcons

/**
 * An image full screen, on black: pinch to zoom, drag to pan, double-tap to zoom in or back, and
 * rotate a quarter turn at a time (for images whose orientation is wrong at the source).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageViewer(file: FilePart, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var quarterTurns by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(quarterTurns * 90f, label = "rotation")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("image-viewer")) {
            FileImage(
                file,
                contentScale = ContentScale.Fit,
                maxDecodePx = 4096,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                            else { scale = 2.5f; offset = (Offset(size.width / 2f, size.height / 2f) - tap) * 1.5f }
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        rotationZ = rotation
                    },
            )
            Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
                FilledTonalIconButton(onClick = { quarterTurns += 1f }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("image-rotate")) {
                    Icon(AiIcons.RotateRight, stringResource(R.string.ai_rotate))
                }
                FilledTonalIconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(start = 8.dp).testTag("image-close")) {
                    Icon(AiIcons.Close, stringResource(R.string.ai_close))
                }
            }
        }
    }
}
