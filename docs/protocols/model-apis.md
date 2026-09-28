# Model APIs

With the agent loop on the device, `ai-elements-core` calls model APIs directly. Each is a
`ChatBackend` with tools, reasoning, images and usage:

| API | Backend | Notes |
|---|---|---|
| OpenAI Chat Completions | `OpenAiChatBackend` | also OpenRouter, CLIProxyAPI, vLLM, LM Studio and other compatible servers |
| OpenAI Responses | `OpenAiResponsesBackend` | reasoning summaries |
| Anthropic Messages | `AnthropicBackend` | extended thinking |
| Gemini | `GeminiBackend` | |
| Ollama (native API) | `OllamaBackend` | local models |

`ProviderProfile` describes a provider (kind, base URL, model) and builds its backend or its model
binding for the harness:

```kotlin
val profile = ProviderProfile("openai", "OpenAI", ProviderKind.OPENAI_RESPONSES, "https://api.openai.com/v1", "gpt-5.5")
val backend = profile.createBackend(apiKey = key, approver = approver)   // built-in agent loop
val model = profile.model(apiKey = key)                                  // for AgentHarness
```

`profile.listModels(apiKey)` asks the provider which models it serves, for a model picker.

## Keys and sign-in

`ProviderStore` keeps API keys encrypted with an Android Keystore key (`SecretStore`); they are never
logged.

Its built-in profiles are `ProviderProfile.Presets` (OpenAI, Anthropic, Gemini, OpenRouter and the
offline demo agent). Add your own agent servers to them:
`ProviderStore(context, ProviderProfile.Presets + myServers)`. The demo adds the bundled reference
server over each protocol this way (`DemoPresets`).

Some providers support signing in instead of an API key, with OAuth 2.1 (PKCE `S256`, a loopback
redirect on `127.0.0.1` or the device flow, RFC 8628): OpenRouter, and, experimentally, ChatGPT
(Codex), xAI and Kimi Code subscriptions. Tokens are refreshed automatically.

## Koog

`ai-elements-koog` runs a [JetBrains Koog](https://github.com/JetBrains/koog) agent as a
`ChatBackend`, or as the harness's model binding, with AI Elements tools as Koog tools. Approvals and
progress work as with the built-in loop.
