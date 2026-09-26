# Contributing

Thanks for helping! A few conventions keep the library consistent:

- **Build & test** before a PR: `./gradlew testDebugUnitTest lintDebug` and, with an emulator,
  `./gradlew :demo:connectedDebugAndroidTest`.
- **Elements** follow Material 3 Expressive: theme colors and shapes only (no hard-coded colors
  except where a Material role doesn't exist), 48 dp touch targets (`Modifier.compactIconButton()`),
  `AiSpacing` / `AiType` tokens, content descriptions for icons, `@Immutable` models.
- **Strings** are resources with an `ai_` prefix in `ai-elements-ui`, translated into all shipped
  languages (`values`, `values-zh-rCN`, `values-zh-rTW`, `values-ja`).
- **Public API** changes need a CHANGELOG entry; keep defaults source-compatible where possible.
- **Protocol changes** come with a recorded fixture test in `ai-elements-core`.
- **Secrets** never appear in logs, exceptions or test output.
