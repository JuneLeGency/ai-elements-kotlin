package dev.ai.elements.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.theme.AiType

/** A timed piece of a transcript. */
@Immutable
data class TranscriptSegment(val text: String, val startMs: Long, val endMs: Long, val speaker: String? = null)

/**
 * A transcript that follows playback (AI Elements `<Transcription>`): the
 * segment at [currentTimeMs] is highlighted, what's already been said stays
 * full-strength, what's ahead is dimmed, and tapping a segment calls
 * [onSeek] with its start. Consecutive segments from the same [TranscriptSegment.speaker]
 * form one paragraph.
 */
@Composable
fun Transcription(
    segments: List<TranscriptSegment>,
    modifier: Modifier = Modifier,
    currentTimeMs: Long = -1,
    onSeek: ((Long) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val paragraphs = segments.fold(mutableListOf<MutableList<TranscriptSegment>>()) { acc, s ->
        if (acc.isEmpty() || acc.last().first().speaker != s.speaker) acc += mutableListOf(s) else acc.last() += s
        acc
    }
    Column(modifier.fillMaxWidth().testTag("transcription"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        paragraphs.forEach { paragraph ->
            Column {
                paragraph.first().speaker?.let {
                    Text(
                        "$it · ${clock(paragraph.first().startMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    buildAnnotatedString {
                        paragraph.forEachIndexed { i, s ->
                            if (i > 0) append(' ')
                            val active = currentTimeMs in s.startMs until s.endMs
                            val ahead = currentTimeMs in 0 until s.startMs
                            val style = SpanStyle(
                                color = when {
                                    active -> colors.onPrimaryContainer
                                    ahead -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                                    else -> colors.onSurface
                                },
                                background = if (active) colors.primaryContainer else androidx.compose.ui.graphics.Color.Unspecified,
                                fontWeight = if (active) FontWeight.Medium else null,
                            )
                            if (onSeek != null) {
                                withLink(LinkAnnotation.Clickable("seg-$i-${s.startMs}", TextLinkStyles(style)) { onSeek(s.startMs) }) { append(s.text) }
                            } else {
                                withStyle(style) { append(s.text) }
                            }
                        }
                    },
                    style = AiType.body,
                )
            }
        }
    }
}
