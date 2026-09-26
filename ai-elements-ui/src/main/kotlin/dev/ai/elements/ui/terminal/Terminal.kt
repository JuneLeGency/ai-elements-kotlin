package dev.ai.elements.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A streaming terminal block, mirroring the web `Terminal` component: a dark
 * surface with a header (title + streaming status + copy/clear actions) and a
 * monospaced, auto-scrolling body.
 *
 * @param output the terminal text (ANSI is not rendered; plain text is shown).
 * @param isStreaming whether output is still arriving (shows a blinking cursor).
 * @param autoScroll keep the view pinned to the bottom while output grows.
 * @param onClear when non-null, a clear button is shown in the header.
 */
@Composable
fun Terminal(
    output: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    autoScroll: Boolean = true,
    onClear: (() -> Unit)? = null,
) {
    // The web uses a dark zinc surface regardless of theme; we keep it dark so the
    // "terminal" reads as a terminal in both light and dark apps.
    val bg = Color(0xFF09090B)
    val fg = Color(0xFFF4F4F5)
    val muted = Color(0xFFA1A1AA)
    val hairline = Color(0xFF27272A)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
    ) {
        // Header.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Terminal,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(start = 8.dp),
            )
            Box(modifier = Modifier.weight(1f))
            if (isStreaming) {
                Text(
                    text = "Running…",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            TerminalCopyButton(text = output)
            if (onClear != null) {
                IconButton(
                    onClick = onClear,
                    Modifier.size(28.dp),
                    true,
                    IconButtonDefaults.iconButtonColors(
                        contentColor = muted,
                    ),
                    remember { MutableInteractionSource() },
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Hairline divider under the header (web: border-b).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairline),
        )

        // Body.
        TerminalBody(
            output = output,
            isStreaming = isStreaming,
            autoScroll = autoScroll,
            fg = fg,
        )
    }
}

@Composable
private fun TerminalBody(
    output: String,
    isStreaming: Boolean,
    autoScroll: Boolean,
    fg: Color,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(output, autoScroll) {
        if (autoScroll) {
            // Scroll to the very bottom whenever new output arrives.
            scope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 384.dp)
            .verticalScroll(scrollState)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = output,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = fg,
                modifier = Modifier.weight(1f),
            )
            if (isStreaming) {
                // Blinking block cursor.
                var visible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(500)
                        visible = !visible
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, bottom = 2.dp)
                        .size(width = 7.dp, height = 14.dp)
                        .background(if (visible) fg else fg.copy(alpha = 0.2f)),
                )
            }
        }
    }
}

@Composable
private fun TerminalCopyButton(
    text: String,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val muted = Color(0xFFA1A1AA)

    IconButton(
        onClick = {
            scope.launch {
                runCatching { clipboard.setText(AnnotatedString(text)) }
                copied = true
                delay(2000)
                copied = false
            }
        },
        Modifier.size(28.dp),
        true,
        IconButtonDefaults.iconButtonColors(contentColor = muted),
        remember { MutableInteractionSource() },
    ) {
        Icon(
            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
    }
}
