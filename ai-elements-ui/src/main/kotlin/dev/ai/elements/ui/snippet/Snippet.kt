package dev.ai.elements.ui.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A single-line, read-only code "snippet" with a copy button, mirroring the web
 * `Snippet` component (`InputGroup` with a mono font, a muted addon, the code in
 * a read-only input, and a copy button).
 *
 * @param code the snippet text shown (read-only).
 * @param label an optional left addon label (e.g. the file name or "bash").
 */
@Composable
fun Snippet(
    code: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val tokens = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, tokens.outlineVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = tokens.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp),
            )
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 1.dp, height = 16.dp)
                    .background(tokens.outlineVariant),
            )
        }

        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = tokens.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (label != null) 8.dp else 12.dp, end = 4.dp),
        )

        SnippetCopyButton(text = code)
    }
}

@Composable
private fun SnippetCopyButton(
    text: String,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            scope.launch {
                runCatching { clipboard.setText(AnnotatedString(text)) }
                copied = true
                delay(2000)
                copied = false
            }
        },
        Modifier
            .size(28.dp)
            .padding(end = 4.dp),
        true,
        IconButtonDefaults.iconButtonColors(),
        remember { MutableInteractionSource() },
    ) {
        Icon(
            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}
