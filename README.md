# AI Elements for Kotlin

A Jetpack Compose (Android) counterpart of [Vercel AI Elements](https://elements.ai-sdk.dev): a
**Material 3 Expressive** chat UI kit plus a multi-protocol agent layer, with Markdown, Mermaid and
KaTeX rendering. Adaptive for phones, foldables and tablets.

```
demo (Android app)
 ├─ :ai-elements-ui    Compose components (M3 Expressive)
 │    chat/      Conversation · Message · Reasoning · ToolCall · Confirmation · Context · Sources
 │               PromptInput (+attachments) · Suggestions · Image · ChainOfThought · Plan · Task
 │               Artifact · WebPreview · Shimmer
 │    markdown/  MarkdownContent (GFM) · CodeBlock · MermaidDiagram · MathBlock (KaTeX)
 │    theme/     AiElementsTheme (MaterialExpressiveTheme, dynamic color, expressive motion)
 └─ :ai-elements-core  no Compose; pure Kotlin + OkHttp
      ChatController (≈ useChat) · MessageReducer · ChatBackend / ChatEvent · ToolApprover
      backend/   8 protocol backends (below)
      agent/     AgentTool (+ requiresApproval) · get_current_time · calculate
      config/    ProviderProfile presets · ProviderStore · SecretStore (Android Keystore)

server (Python, uv)   FastAPI + PydanticAI agent with tools
                      /api/chat → AI SDK UI Message Stream · /api/agui → AG-UI
```

## Protocols

| Provider kind | Protocol | Agent loop | Reasoning | Tools | Images in | Usage |
|---|---|---|---|---|---|---|
| AI SDK stream | Vercel AI SDK v5 UI Message Stream **and** v4 Data Stream (auto-detected) | server | ✅ | ✅ server | ✅ | v4 only |
| AG-UI | AG-UI events (PydanticAI, LangGraph, CrewAI, Mastra…) | server | ✅ | ✅ server | — | — |
| OpenAI Chat Completions | `/chat/completions` SSE | on-device | ✅ `reasoning(_content)` | ✅ | ✅ | ✅ |
| OpenAI Responses | `/responses` SSE, stateless + encrypted reasoning | on-device | ✅ summaries | ✅ | ✅ | ✅ |
| Anthropic Messages | `/v1/messages` SSE | on-device | ✅ thinking + signatures | ✅ | ✅ | ✅ |
| Gemini | `:streamGenerateContent?alt=sse` | on-device | ✅ thought summaries | ✅ (+ thoughtSignature) | ✅ | ✅ |
| Ollama native | `/api/chat` NDJSON | on-device | ✅ `think` | ✅ | ✅ | ✅ |
| Offline demo | scripted | on-device | ✅ | ✅ (real tools) | — | ✅ |

On-device agent loops run app-defined `AgentTool`s; tools with `requiresApproval` pause the run and
show a **Confirmation** (approve / deny) — the demo's `copy_to_clipboard` does this. Gateways that
answer `200` with a JSON error (e.g. `{"msg":"Model not support"}`) and empty replies are surfaced
as errors, not silently dropped.

**Verified live** against a real model (`qwen3:4b` via local Ollama, which serves four of these
protocols) — each run reasons, calls `calculate`, and answers — see `LiveAgentTest`. Gemini is
covered by recorded fixtures only.

## Component coverage vs AI Elements

| AI Elements | Here | | AI Elements | Here |
|---|---|---|---|---|
| Conversation | ✅ | | Tool | ✅ |
| Message / Actions | ✅ | | Confirmation | ✅ (end-to-end) |
| Response (Markdown) | ✅ GFM, code highlight, **Mermaid**, **KaTeX** | | Context | ✅ token usage |
| Prompt Input | ✅ + image attachments, model picker | | Image | ✅ |
| Reasoning | ✅ | | Chain of Thought | ✅ |
| Sources | ✅ | | Plan / Task | ✅ |
| Suggestion | ✅ | | Artifact | ✅ |
| Loader / Shimmer | ✅ (M3 LoadingIndicator) | | Web Preview | ✅ |
| Code Block | ✅ | | Branch, Checkpoint, Queue, Inline Citation, Open In Chat, Canvas/Node/Edge | ❌ not yet |

Plan, Task, Chain of Thought, Artifact and Web Preview are components you drive with your own data;
no wire protocol in the table emits them as standard parts.

## Device support

Tested on emulators: phone portrait / landscape (≥ 480dp-high rule: short windows never get the
three-pane layout; top bar hides while typing), foldable width (~686dp: rail + chat), tablet
(1280dp: rail + history pane + chat, two-pane settings), light/dark, font scale 1.5, API 36 and
**API 28** (Chrome 66 WebView). On outdated System WebViews KaTeX still renders; Mermaid 12 cannot
parse there and falls back to showing the diagram source with an "update WebView" note.

## Run

```bash
# Agent server (optional; the offline demo needs nothing)
cd server && uv sync
AGENT_MODEL=demo uv run uvicorn main:app --host 0.0.0.0 --port 8788          # scripted, no model
AGENT_BASE_URL=http://localhost:11434/v1 AGENT_MODEL=qwen3:4b \
  uv run uvicorn main:app --host 0.0.0.0 --port 8788                           # real model

# Free local model
ollama pull qwen3:4b          # then use the "Ollama" provider (http://10.0.2.2:11434 on the emulator)

# App
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :demo:installDebug
```

`10.0.2.2` is the emulator's alias for the host; on a physical device use the host's LAN IP.

## Test

```bash
./gradlew :ai-elements-core:testDebugUnitTest :ai-elements-ui:testDebugUnitTest   # fixtures, controller, LaTeX
./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveAgentTest*' \
  -PliveOllama=http://localhost:11434 -PliveModel=qwen3:4b -PliveAgentServer=http://localhost:8788
./gradlew :demo:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.ollama=http://10.0.2.2:11434    # E2E UI, live tests skip if unreachable
```

## Toolchain

AGP 9.4 (built-in Kotlin) · Gradle 9.7.1 · Kotlin 2.4.20 · Compose 1.13.0-alpha01 ·
Material3 **1.5.0-alpha29** (Expressive APIs ship only in the 1.5 alpha line) · adaptive 1.3.0 ·
multiplatform-markdown-renderer 0.45 · mermaid 12.0.0 · KaTeX 0.18.9 (both bundled offline).
