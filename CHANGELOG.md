# Changelog

## 0.3.0 (unreleased)

### Added
- 17 elements to match AI Elements: `Agent`, `ModelSelector`, `Question`, `Persona`, `SpeechInput`,
  `AudioPlayer` (+ `rememberAudioPlayerState`), `Transcription`, `MicSelector`, `VoiceSelector`,
  `Terminal`, `StackTrace`, `TestResults`, `FileTree`, `Commit`, `SchemaDisplay`, `PackageInfo`,
  `EnvironmentVariables`, `Sandbox`, `Snippet`.
- Theming: `AiPalette` (7 generated Material 3 schemes), `AiContrast`, `fontFamily` and
  `codeFontFamily` on `AiElementsTheme`, `LocalCodeFontFamily`.
- OAuth sign-in in core: PKCE loopback and device flows, single-flight refresh, OpenRouter, and
  experimental subscription sign-ins (ChatGPT, xAI, Kimi).
- Localization: English, Simplified Chinese, Traditional Chinese, Japanese.
- `MermaidRenderer` interface; the Compose Canvas renderer moved to the optional
  `ai-elements-mermaid-native` artifact.
- Consumer R8 rules in both libraries; Maven Central publishing.

### Changed
- `LocalMermaidRenderer` now holds a `MermaidRenderer` (was an enum).
- Denser chat reading type scale (`AiType`).

### Fixed
- OAuth on Android 7 (API 24–25): `java.util.Base64` replaced with Kotlin's codec.
- Inline Mermaid/KaTeX snapshots could capture a mid-resize frame (enlarged corner).
- Soft keyboard popping up by itself on tablets (single-pane list-detail).

## 0.2.0
- Initial Material 3 Expressive rebuild: conversation, messages, tools, Markdown/Mermaid/KaTeX,
  8 protocol backends, adaptive layouts.
