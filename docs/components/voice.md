# Voice

Talking to the agent: voice mode, the persona, dictation and voice choices.

## Voice mode

A hands-free conversation over the chat's controller.

![Voice mode](../assets/components/voice-mode.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `VoiceMode(controller, onClose)` |

## Persona

The assistant's animated presence: idle, listening, thinking, speaking.

![Persona](../assets/components/persona.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Persona(state, size, level)` |
| AI Elements | `Persona` |

## Speech input

Dictation with the platform speech recognizer.

![Speech input](../assets/components/speech-input.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `SpeechInput(onTranscript, state)` |
| AI Elements | `SpeechInput` |

## Mic & voice selectors

Choose the microphone and the reading voice.

![Mic & voice selectors](../assets/components/voice-selectors.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `MicSelector(selected, onSelect), VoiceSelector(voices, selectedId, onSelect)` |
| AI Elements | `MicSelector, VoiceSelector` |
