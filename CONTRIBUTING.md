# Contributing

Thanks for helping! Read [AGENTS.md](AGENTS.md) first (open protocols only, no private wire formats). A few conventions keep the library consistent:

- **Build & test** before a PR: `./gradlew testDebugUnitTest lintDebug` and, with an emulator,
  `./gradlew :demo:connectedDebugAndroidTest`.
- **Elements** follow Material 3 Expressive: theme colors and shapes only (no hard-coded colors
  except where a Material role doesn't exist), 48 dp touch targets (`Modifier.compactIconButton()`),
  `AiSpacing` / `AiType` tokens, content descriptions for icons, `@Immutable` models.
- **Strings** are resources with an `ai_` prefix in `ai-elements-ui`, translated into all shipped
  languages (`values`, `values-zh-rCN`, `values-zh-rTW`, `values-ja`).
- **Public API** changes need a CHANGELOG entry; follow [the compatibility policy](docs/develop/api-compatibility.md), run `./gradlew apiCheck`, and explicitly review baseline updates.
- **Protocol changes** come with a fixture test recorded from a real implementation (`server/record_fixtures.py`), and a live test when the reference server can exercise them.
- **Icons** are Material Symbols Rounded: use `AiIcons.X` and run `python3 tools/generate-icons.py`.
- **Documentation** lives in `docs/` (the site, `uvx zensical serve`); its code comes from `demo/src/main/kotlin/…/samples/DocsSamples.kt` sections, compiled with the demo. Update the page that describes what you change.
- **Secrets** never appear in logs, exceptions or test output.

More detail: [docs/develop/contributing.md](docs/develop/contributing.md) and [docs/develop/testing.md](docs/develop/testing.md).
