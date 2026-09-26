# AI Elements for Kotlin

A Jetpack Compose (Android) port of [Vercel AI Elements](https://elements.ai-sdk.dev) with a
**Material 3 Expressive** UI, a pluggable multi-provider agent layer, Markdown + Mermaid
rendering, and a PydanticAI agent server. Adaptive for phones, foldables and tablets.

```
demo (Android app)
 ├─ :ai-elements-ui    Compose components (M3 Expressive)
 │    chat/      Conversation · Message · Reasoning · ToolCall · Sources · PromptInput · Suggestions
 │    markdown/  MarkdownContent (GFM) · CodeBlock (syntax highlight) · MermaidDiagram (offline mermaid.js)
 │    theme/     AiElementsTheme (MaterialExpressiveTheme, dynamic color, expressive motion)
 └─ :ai-elements-core  no Compose; pure Kotlin + OkHttp
      ChatController (≈ useChat) · MessageReducer · ChatBackend/ChatEvent
      backend/   UiMessageStreamBackend · OpenAiChatBackend · AnthropicBackend · MockAgentBackend
      agent/     AgentTool + built-in tools (get_current_time, calculate)
      config/    ProviderProfile presets · ProviderStore · SecretStore (Android Keystore)

server (Python, uv)   FastAPI + PydanticAI agent with tools → Vercel AI SDK v5 UI Message Stream
```

## Agent providers

| Provider kind | Where the agent loop runs | Wire protocol |
|---|---|---|
| **Offline demo** | on-device, scripted | — (reasoning → real tool call → Markdown/Mermaid) |
| **Agent server** | server (PydanticAI, `server/main.py`) | AI SDK v5 UI Message Stream (SSE) |
| **OpenAI-compatible** | on-device tool loop | `/chat/completions` SSE (+ `reasoning_content`) |
| **Anthropic** | on-device tool loop | `/v1/messages` SSE (thinking, tool_use) |

Presets: Offline demo, PydanticAI agent server, CLIProxyAPI, Ollama, OpenAI, Anthropic, Google
Gemini, OpenRouter — all editable, plus custom providers. Switch provider/model from the chip in
the chat top bar; the conversation is kept. API keys are encrypted with the Android Keystore.

`10.0.2.2` is the emulator's alias for the host. On a physical device use the host's LAN IP.

## Run

```bash
# 1. Agent server (optional; the offline demo needs nothing)
cd server && uv sync
AGENT_MODEL=demo uv run uvicorn main:app --host 0.0.0.0 --port 8788
#   AGENT_MODEL=demo  → offline scripted model (exercises the whole chain, no key)
#   real model:  AGENT_BASE_URL=http://localhost:8317/v1 AGENT_API_KEY=... AGENT_MODEL=<model>

# 2. App
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :demo:installDebug
```

## Test

```bash
./gradlew :ai-elements-core:testDebugUnitTest      # protocol parsers, tool loops, controller
./gradlew :demo:connectedDebugAndroidTest          # E2E through the UI (agent-server test skips if :8788 is down)
```

## Toolchain

AGP 9.4 (built-in Kotlin) · Gradle 9.7.1 · Kotlin 2.4.20 · Compose 1.13.0-alpha01 ·
Material3 **1.5.0-alpha29** (the Expressive APIs — `MaterialExpressiveTheme`, `LoadingIndicator`,
`MaterialShapes`, connected button groups, `ShortNavigationBar`/`WideNavigationRail` — only ship in
the 1.5 alpha line) · adaptive 1.3.0 · multiplatform-markdown-renderer 0.45 · mermaid 12.0.0.
