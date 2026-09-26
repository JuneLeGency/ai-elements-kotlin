# AI Elements for Kotlin

**Material 3 Expressive** Jetpack Compose components for AI chat and agent apps — the Android
counterpart of [Vercel AI Elements](https://elements.ai-sdk.dev) — plus a multi-protocol agent
layer that streams from AI SDK / AG-UI servers or runs the agent loop on the device.

- **Complete element set**: conversation, messages, reasoning, tools with approval, sources and
  inline citations, branches, checkpoints, queue, plan / task / chain of thought, artifacts, web
  preview, workflow canvas, voice (speech input, persona, audio player, transcription), and
  developer tools (terminal, stack trace, test results, file tree, commit, schema…).
- **Rich answers**: GitHub-flavoured Markdown, syntax highlighting, **Mermaid** and **KaTeX**
  (bundled, offline).
- **Streaming that stays readable**: replies are virtualized block by block, the list follows the
  stream until the user scrolls, and nothing moves under their finger.
- **Adaptive**: phones, foldables and tablets (M3 canonical list-detail), dark mode, dynamic color,
  7 built-in palettes × 3 contrast levels, custom fonts, font scaling, TalkBack.
- **8 protocols, on-device tools, OAuth**: AI SDK v5/v4, AG-UI, OpenAI Chat Completions and
  Responses, Anthropic, Gemini, Ollama — with human-in-the-loop tool approval.
- **Localized**: English, 简体中文, 繁體中文, 日本語.

## Modules

| Artifact | What it is |
|---|---|
| `ai-elements-core` | Chat state and protocols; no Compose. `ChatController` (≈ `useChat`), `ChatBackend`s, `AgentTool`s, OAuth, encrypted provider storage. |
| `ai-elements-ui` | The Compose elements and `AiElementsTheme`. Depends on core. |
| `ai-elements-mermaid-native` | *Optional.* Mermaid drawn with Compose Canvas instead of a WebView (experimental, ~4 MB of fonts). |

```kotlin
dependencies {
    implementation("io.github.junelegency:ai-elements-ui:0.3.0-SNAPSHOT")
    // implementation("io.github.junelegency:ai-elements-mermaid-native:0.3.0-SNAPSHOT") // optional
}
```

minSdk 24 · R8 rules ship with the libraries (consumer rules), no app-side configuration needed.

## Quick start

```kotlin
class ChatViewModel : ViewModel() {
    private val profile = ProviderProfile(
        id = "openai", name = "OpenAI", kind = ProviderKind.OPENAI,
        baseUrl = "https://api.openai.com/v1", model = "gpt-5.5",
    )
    val chat = ChatController(
        backend = { approver -> profile.createBackend(apiKey = BuildConfig.OPENAI_KEY, approver = approver) },
        scope = viewModelScope,
    )
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val state by vm.chat.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    AiElementsTheme {
        Column(Modifier.fillMaxSize().imePadding()) {
            Conversation(
                state = state,
                modifier = Modifier.weight(1f),
                onRegenerate = vm.chat::regenerate,
                onToolApproval = vm.chat::respondToApproval,
                onSelectVersion = vm.chat::selectVersion,
                onRestoreCheckpoint = vm.chat::restoreCheckpoint,
            )
            PromptInput(
                value = input,
                onValueChange = { input = it },
                onSubmit = { if (vm.chat.send(input)) input = "" },
                onStop = vm.chat::stop,
                busy = state.isBusy,
                allowQueue = true,
                toolbar = { SpeechInput(onTranscript = { text, _ -> input = text }) },
            )
        }
    }
}
```

The `demo` app wires everything together: provider settings, conversation history, list-detail on
tablets, OAuth sign-in, languages, palettes and fonts.

## Elements

| Group (package) | Elements |
|---|---|
| Chat (`ui.chat`) | `Conversation` (stick-to-bottom, jump to latest) · `MessageItem` / actions · `PromptInput` (attachments, queue, hardware-keyboard send) · `Suggestions` · `Reasoning` · `ToolCall` + `Confirmation` · `Sources` · `InlineCitation` · `ContextUsage` · `BranchSelector` · `Checkpoint` · `Queue` · `OpenInChat` · `ModelSelector` · `Question` · `Agent` |
| Agent structure (`ui.chat`) | `ChainOfThought` · `Plan` · `Task` · `DataPartView` |
| Workflow (`ui.workflow`) | `WorkflowCanvas` (nodes, edges, pan / zoom) · `agentRunGraph` |
| Content (`ui.markdown`) | `MarkdownContent` · `CodeBlock` · `MermaidDiagram` · `MathBlock` · `FileImage` / attachments |
| Voice (`ui.voice`) | `Persona` · `SpeechInput` · `AudioPlayer` · `Transcription` · `MicSelector` · `VoiceSelector` |
| Vibe coding (`ui.code`) | `Artifact` · `WebPreview` · `Terminal` (ANSI) · `StackTrace` · `TestResults` · `FileTree` · `Commit` · `SchemaDisplay` · `PackageInfo` · `EnvironmentVariables` · `Sandbox` · `Snippet` |

Packages follow the AI Elements docs categories. All 50 AI Elements are covered except `JSXPreview` (web-only; `WebPreview` renders HTML). The
canvas `Panel` / `Toolbar` / `Controls` / `Connection` are part of `WorkflowCanvas`.

## Theming

```kotlin
AiElementsTheme(
    darkTheme = isSystemInDarkTheme(),
    dynamicColor = true,                 // Android 12+: wallpaper colors
    palette = AiPalette.OCEAN,           // otherwise: VIOLET, OCEAN, JADE, FOREST, SUNSET, SAKURA, GRAPHITE
    contrast = AiContrast.MEDIUM,        // Material 3 contrast levels
    fontFamily = myFontFamily,           // every type style; CJK falls back to system fonts
    codeFontFamily = myMonoFamily,       // code blocks, inline code, tool arguments
) { … }
```

Palettes are full Material 3 schemes generated by Google's material-color-utilities
(`tools/generate-schemes.mjs`); `aiColorScheme(palette, dark, contrast)` returns one directly.
Chat content uses a reading type scale (`AiType`) derived from your typography, and spacing tokens
(`AiSpacing`, `AiSize`) that keep 48 dp touch targets.

**Mermaid**: `LocalMermaidRenderer` picks the renderer (`MermaidRenderer.WebView` by default,
`NativeMermaidRenderer` from the optional module), `LocalMermaidSizing` the inline size.

## Protocols

| Provider kind | Protocol | Agent loop | Reasoning | Tools | Images in | Usage |
|---|---|---|---|---|---|---|
| AI SDK stream | Vercel AI SDK v5 UI Message Stream and v4 Data Stream (auto-detected) | server | ✅ | ✅ server | ✅ | ✅ |
| AG-UI | AG-UI events (PydanticAI, LangGraph, CrewAI, Mastra…) | server | ✅ | ✅ server | — | ✅ |
| OpenAI Chat Completions | `/chat/completions` SSE | on-device | ✅ | ✅ | ✅ | ✅ |
| OpenAI Responses | `/responses` SSE, stateless + encrypted reasoning | on-device | ✅ | ✅ | ✅ | ✅ |
| Anthropic Messages | `/v1/messages` SSE | on-device | ✅ thinking | ✅ | ✅ | ✅ |
| Gemini | `:streamGenerateContent` SSE | on-device | ✅ thought summaries | ✅ | ✅ | ✅ |
| Ollama | `/api/chat` NDJSON | on-device | ✅ | ✅ | ✅ | ✅ |
| Offline demo | scripted | on-device | ✅ | ✅ real tools | — | ✅ |

On-device agent loops run app-defined `AgentTool`s; tools with `requiresApproval` pause for a
`Confirmation`. Provider errors — including gateways that answer `200` with a JSON error — surface
as errors instead of empty replies.

### Sign-in (OAuth)

`ai-elements-core` implements OAuth from the RFCs: authorization code + PKCE through a loopback
redirect (RFC 6749 / 7636 / 8252), the device flow (RFC 8628), single-flight token refresh, and a
retry after `401`. Tokens are stored encrypted with the Android Keystore.

- **OpenRouter** uses OpenRouter's documented PKCE flow for apps and yields a user-owned API key.
- **ChatGPT (Codex), xAI Grok, Kimi Code** are *subscription* sign-ins through each vendor's own
  CLI client. They are not endorsed by the vendors, may stop working, and can put accounts at risk;
  the demo keeps them behind an explicit, warned, off-by-default switch.
- Claude.ai subscriptions are intentionally not offered: Anthropic permits them only in its own
  products. Use an Anthropic API key.

## Run the demo

```bash
./gradlew :demo:installDebug                      # the "Offline demo" provider needs nothing else

# Optional: a real agent server (FastAPI + PydanticAI) and a free local model
ollama pull qwen3:4b
cd server && uv sync
AGENT_BASE_URL=http://localhost:11434/v1 AGENT_MODEL=qwen3:4b uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

`10.0.2.2` is the emulator's alias for your computer; on a phone use its LAN IP.

## Test

```bash
./gradlew testDebugUnitTest                        # protocol fixtures, controller, OAuth (local mock server)
./gradlew :demo:connectedDebugAndroidTest          # UI end-to-end; live-model cases skip when unreachable
# Live, opt-in:
./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveAgentTest*' -PliveOllama=http://localhost:11434
./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveCodexTest*' -PliveCodexAuth=$HOME/.codex/auth.json
GEMINI_API_KEY=… ./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveGeminiTest*'
LIVE_PROXY_KEY=… ./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveProxyTest*' -PliveProxy=http://localhost:8317   # multi-format gateway
```

The live Codex test reads an existing Codex CLI login and never refreshes it (refreshing would sign
the CLI out).

On MIUI / HyperOS devices, instrumentation started in the background cannot bring the app to the
front until the app may "display pop-up windows while running in the background"
(`adb shell appops set dev.ai.elements.demo 10021 allow`); reset it afterwards with `… 10021 default`.

## Toolchain

AGP 9.4 (built-in Kotlin) · Gradle 9.7 · Kotlin 2.4 · Compose 1.13 alpha · Material3 1.5 alpha
(Expressive APIs) · adaptive 1.3 · OkHttp 4.12 · kotlinx.serialization · mermaid 12 and KaTeX 0.18
(bundled).

## License

Apache License 2.0 — see [LICENSE](LICENSE). Bundled third-party code and fonts are listed in
[NOTICE](NOTICE).
