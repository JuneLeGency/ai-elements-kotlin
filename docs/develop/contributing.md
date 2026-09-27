# Contributing

Read [Open protocols only](../concepts/open-protocols.md) first: the library follows public
specifications exactly and never invents wire formats. The binding rules for humans and AI agents
are in `AGENTS.md` at the root of the repository.

## Conventions

- **Build and test** before you push: `./gradlew testDebugUnitTest lintDebug` and, with an emulator
  or device, `./gradlew :demo:connectedDebugAndroidTest`. See [Testing](testing.md).
- **Elements** follow Material 3 Expressive: theme colors and shapes only, 48 dp touch targets
  (`Modifier.compactIconButton()`), the `AiSpacing` / `AiType` tokens, content descriptions for
  icons, `@Immutable` models. Elements render the chat model only; see
  [Architecture](../concepts/architecture.md#ui-elements-are-protocol-independent).
- **Icons** are Material Symbols Rounded. Use `AiIcons.X` and run `python3 tools/generate-icons.py`
  to generate the icons in use.
- **Strings** are resources with an `ai_` prefix in `ai-elements-ui`, translated into every shipped
  language (`values`, `values-zh-rCN`, `values-zh-rTW`, `values-ja`).
- **Public API** changes need a CHANGELOG entry; keep defaults source-compatible where possible.
- **Protocol changes** come with a recorded fixture test, and a live test when the reference server
  can exercise them.
- **Documentation**: code on this site comes from `demo/src/main/kotlin/…/samples/DocsSamples.kt`
  through `--8<--` sections, so it compiles with the demo. Preview the site with `uvx zensical serve`.
- **Secrets** never appear in logs, exceptions or test output.
