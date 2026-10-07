# Attachments and media

Files in a conversation: images, attachments, video, documents and audio.

## Image

An image, upright by EXIF; tap for the viewer (zoom, pan, rotate).

![Image](../assets/components/image.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `FileAttachment(file), FileImage(file), ImageViewer(file, onDismiss)` |
| AI Elements | `Image` |
| Artifact | `ai-elements-ui` |
| Reference | [FileAttachment](../api/ai-elements-ui/dev.ai.elements.ui.chat/-file-attachment.html) |

Set mediaType and url, optionally filename. LocalFileLoader customizes byte loading and authentication; image taps open the built-in viewer.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.chat.FileAttachment
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-image"
```

## Attachments

Files attached to a prompt, removable, and images that open full screen.

![Attachments](../assets/components/attachments.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `AttachmentStrip(attachments, onRemove), FileAttachment(file)` |
| AI Elements | `Attachments` |
| Artifact | `ai-elements-ui` |
| Reference | [AttachmentStrip](../api/ai-elements-ui/dev.ai.elements.ui.chat/-attachment-strip.html) |

The app owns the list and file picker. Removal changes the list only; it does not delete the underlying file.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.chat.AttachmentStrip
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-attachments"
```

## Video

A video's first frame and duration; plays full screen.

![Video](../assets/components/video.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `VideoAttachment(file)` |
| Artifact | `ai-elements-ui` |
| Reference | [VideoAttachment](../api/ai-elements-ui/dev.ai.elements.ui.chat/-video-attachment.html) |

Use mediaType=video/mp4 (or the actual type). The platform codec must support the file; preview and playback need a readable source.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.chat.VideoAttachment
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-video"
```

## Document (PDF)

A PDF's first page and page count; every page in the viewer. Other formats open in an app.

![Document (PDF)](../assets/components/document.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `DocumentAttachment(file)` |
| Artifact | `ai-elements-ui` |
| Reference | [DocumentAttachment](../api/ai-elements-ui/dev.ai.elements.ui.chat/-document-attachment.html) |

PDF previews use PdfRenderer. Other formats need an installed viewer; files are shared through the library FileProvider.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.ui.chat.DocumentAttachment
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-document"
```

## Audio player + Transcription

Audio with a transcript that follows it and seeks on tap.

![Audio player + Transcription](../assets/components/audio.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `AudioPlayer(state), Transcription(segments, currentTimeMs, onSeek)` |
| AI Elements | `AudioPlayer, Transcription` |
| Artifact | `ai-elements-ui` |
| Reference | [AudioPlayer](../api/ai-elements-ui/dev.ai.elements.ui.voice/-audio-player.html) |

rememberAudioPlayerState creates and releases the player with composition. Keep the source stable. Transcript times and seek positions are milliseconds.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.voice.AudioPlayer
import dev.ai.elements.ui.voice.TranscriptSegment
import dev.ai.elements.ui.voice.Transcription
import dev.ai.elements.ui.voice.rememberAudioPlayerState
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-audio"
```
