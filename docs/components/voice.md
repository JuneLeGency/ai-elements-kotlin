# Voice

Talking to the agent: voice mode, the persona, dictation and voice choices.

## Voice mode

A hands-free conversation over the chat's controller.

![Voice mode](../assets/components/voice-mode.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `VoiceMode(controller, onClose)` |
| Artifact | `ai-elements-ui` |
| Reference | [VoiceMode](../api/ai-elements-ui/dev.ai.elements.ui.voice/-voice-mode.html) |

Declare RECORD_AUDIO and let the permission flow complete. Requires installed recognition/TTS services. onClose should dismiss the hosting dialog or screen.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.ui.voice.VoiceMode
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-voice-mode"
```

## Persona

The assistant's animated presence: idle, listening, thinking, speaking.

![Persona](../assets/components/persona.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Persona(state, size, level)` |
| AI Elements | `Persona` |
| Artifact | `ai-elements-ui` |
| Reference | [Persona](../api/ai-elements-ui/dev.ai.elements.ui.voice/-persona.html) |

Visual state only; this does not listen or speak. Feed level from your audio state when showing LISTENING or SPEAKING.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.voice.Persona
import dev.ai.elements.ui.voice.PersonaState
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-persona"
```

## Speech input

Dictation with the platform speech recognizer.

![Speech input](../assets/components/speech-input.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `SpeechInput(onTranscript, state)` |
| AI Elements | `SpeechInput` |
| Artifact | `ai-elements-ui` |
| Reference | [SpeechInput](../api/ai-elements-ui/dev.ai.elements.ui.voice/-speech-input.html) |

Declare RECORD_AUDIO. State owns the recognizer lifecycle; show speech.error on failure and distinguish interim/final transcripts in onTranscript.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.ai.elements.ui.voice.SpeechInput
import dev.ai.elements.ui.voice.rememberSpeechInputState
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-speech-input"
```

## Mic & voice selectors

Choose the microphone and the reading voice.

![Mic & voice selectors](../assets/components/voice-selectors.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MicSelector(selected, onSelect), VoiceSelector(voices, selectedId, onSelect)` |
| AI Elements | `MicSelector, VoiceSelector` |
| Artifact | `ai-elements-ui` |
| Reference | [MicSelector](../api/ai-elements-ui/dev.ai.elements.ui.voice/-mic-selector.html) |

Selectors report choices; configure your audio engine or SpeechSettings separately. Available microphones and voices depend on the device.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.ai.elements.ui.voice.MicSelector
import dev.ai.elements.ui.voice.VoiceOption
import dev.ai.elements.ui.voice.VoiceSelector
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-voice-selectors"
```
