# AI Elements for Kotlin

A Kotlin/Compose (Android) port of [Vercel AI Elements](https://ai-elements.dev/) —
the shadcn/ui AI chat components — plus a real model-gateway backend that streams
over the **Vercel AI Data Stream Protocol** and **OpenAI-compatible SSE**.

```
demo (Android app, Compose)
  └── :ai-elements-ui    14 ported components (Conversation, Message, Reasoning,
                          PromptInput, Sources, Suggestions, Task, Plan,
                          Confirmation, Image, Terminal, Snippet, ...)
  └── :ai-elements-core  models + ChatController + theme/tokens
                           + gateway backends (Vercel Data Stream / OpenAI)
                           + GatewayConfig (SharedPreferences)
                           + auth/ (PKCE + OAuth code flow + encrypted token store)

server (Python, uv)
  FastAPI + PydanticAI agent → any OpenAI-compatible endpoint
  (your local CLIProxyAPI / tinker-llm-gw on :9090)
  streams the reply as the Vercel AI Data Stream Protocol
```

## Toolchain (all latest stable)

| Component        | Version  |
|------------------|----------|
| AGP              | 9.4.0    |
| Gradle           | 9.7.1    |
| Kotlin           | 2.4.20   |
| Compose          | 1.12.1   |
| Material3        | 1.4.0    |
| compileSdk       | 37       |
| Python (uv)      | pydantic-ai 2.42, fastapi 0.141, openai 3.13 |

> AGP 9.x ships **built-in Kotlin** — the `org.jetbrains.kotlin.android` plugin is
> removed and must not be applied (see `build.gradle.kts`).

## 1. Run the gateway server

```bash
cd server
uv sync                 # creates .venv + installs (uses uv.lock)
uv run uvicorn main:app --host 127.0.0.1 --port 8787
```

`/api/chat` takes `{messages, model, base_url, api_key, stream}` and streams
Vercel Data Stream frames:

```
0:{"id":...}                       start
9:<text delta>                     text
a:<reasoning delta>                reasoning
d:{"url":...,"title":...}          source-url
3:{"message":...}                  error
2:{"finishReason":"stop","usage":{...}}   finish
```

Point it at your OpenAI-compatible gateway (CLIProxyAPI) via request fields or
env:

```bash
export CLIPROXY_BASE_URL=http://localhost:9090/v1
export CLIPROXY_API_KEY=<your-cli-proxy-api-key>
export CLIPROXY_MODEL=gpt-4o
```

## 2. Run the demo

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :demo:assembleDebug
# or: open in Android Studio and Run `demo`.
```

In-app: tap **Settings** → pick **Provider**:

- **AI Elements gateway** — base URL `http://10.0.2.2:8787`, path `/api/chat`
  (`10.0.2.2` is the emulator alias for the host loopback). Paste the server's
  upstream API key if your gateway needs it.
- **OpenAI-compatible** — base URL `http://10.0.2.2:9090/v1` (CLIProxyAPI), paste
  the API key, set the model.
- **Mock (offline)** — canned reply, no network.

Changes persist to SharedPreferences and rebuild the backend on the next send.
Secrets (API keys, OAuth tokens) live in **EncryptedSharedPreferences**.

> The demo talks to the **host's** ports. On a physical device use your machine's
> LAN IP in the base URL instead of `10.0.2.2`.

## OAuth (authorization code + PKCE)

`core/auth/` is a generic, parameterised OAuth framework (no per-vendor class).
Add a provider by describing it with an `OAuthSpec`:

```kotlin
OAuthSpec(
    instanceId = "openai",
    providerLabel = "OpenAI",
    authUrl   = "https://auth.openai.com/oauth/authorize",
    tokenUrl  = "https://auth.openai.com/oauth/token",
    clientId  = "app_...",
    callbackPort = 8465,            // loopback port bound BEFORE the browser opens
    redirectPath = "/oauth/callback",
    tokenResponseFormat = TokenResponseFormat.JSON,
)
```

Flow (`OAuthManager.startLogin()`): build a `Pkce` triple → bind
`OAuthCallbackServer` on `localhost:port` → open the auth URL → await the redirect
(state-checked) → exchange `code` + `code_verifier` at `tokenUrl` → persist the
token to `OAuthTokenStore` (EncryptedSP). Tokens refresh **lazily** on demand
(`validAccessToken()`, coalesced by a per-instance `Mutex`). The backends read the
token via a suspend `authProvider`, so a refreshed token is used without rebuilding
the backend. In-app: **Settings → Sign in** (providers with `supportsOAuth`).

## Testing

```bash
# Parser unit tests (no network) — replay recorded Vercel/OpenAI fixtures against
# a local in-process HTTP server and assert the ChatEvent sequence.
ANDROID_HOME=$HOME/Library/Android/sdk \
  ./gradlew :ai-elements-core:testDebugUnitTest

# Real-service UI test (needs a connected device/emulator + a live gateway).
# Skips gracefully when no API key is set.
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :demo:connectedDebugAndroidTest \
  -PrealGatewayBaseUrl="http://10.0.2.2:9090/v1" \
  -PrealGatewayApiKey="<sk-...>" \
  -PrealGatewayModel="claude-sonnet-5"
```

## 3. Which libraries to distill

Reusable, framework-agnostic pieces worth extracting into their own artifacts:

- `core/backend/VercelDataStreamBackend.kt` — a tiny SSE→`ChatEvent` parser for
  the Vercel AI Data Stream Protocol. Pure Kotlin + OkHttp, KMP-ready.
- `core/backend/OpenAiBackend.kt` — the OpenAI-compatible SSE→`ChatEvent` parser.
- `core/ChatController.kt` + `ChatModels.kt` — the `ChatBackend`/`ChatEvent`
  contract and the AI-SDK-shaped `Message`/`Part` model.
- `core/config/GatewayConfig*.kt` — SharedPreferences-backed, observable
  `StateFlow` gateway settings (swap in `EncryptedSharedPreferences` for keys).
- `ui/*` — the 14 Compose components (already isolated, only depend on
  `:ai-elements-core` + Compose).

## Notes

- `LocalClipboardManager` is used in 3 components and is deprecated in Compose
  1.12 (the new `LocalClipboard` wraps Android `ClipData`); kept for now, 3
  deprecation warnings remain.
- API keys and OAuth tokens are stored in **EncryptedSharedPreferences**
  (`androidx.security:security-crypto`). The alpha (1.1.0) marks the old
  `MasterKey`/`create` API deprecated — still functional; migrate to the new
  `MasterKey` factory when a stable release lands.
