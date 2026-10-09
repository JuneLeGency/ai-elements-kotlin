# 模型 API

Agent 循环在设备上运行时，`ai-elements-core` 直接调用模型 API。每个 API 都有支持工具、推理、图片和用量的 `ChatBackend`：

| API | Backend | 说明 |
|---|---|---|
| OpenAI Chat Completions | `OpenAiChatBackend` | 也支持 OpenRouter、CLIProxyAPI、vLLM、LM Studio 等兼容服务 |
| OpenAI Responses | `OpenAiResponsesBackend` | 推理摘要 |
| Anthropic Messages | `AnthropicBackend` | extended thinking |
| Gemini | `GeminiBackend` | |
| Ollama（原生 API） | `OllamaBackend` | 本地模型 |

`ProviderProfile` 描述 provider 的类型、base URL 和模型，生成 backend 或 harness 模型绑定：

```kotlin
val profile = ProviderProfile("openai", "OpenAI", ProviderKind.OPENAI_RESPONSES, "https://api.openai.com/v1", "gpt-5.5")
val backend = profile.createBackend(apiKey = key, approver = approver)   // built-in agent loop
val model = profile.model(apiKey = key)                                  // for AgentHarness
```

`profile.listModels(apiKey)` 获取服务端模型列表，可用于模型选择器。

## 密钥与登录

`ProviderStore` 使用 Android Keystore 密钥（`SecretStore`）加密保存 API key，不会记录到日志。

内置配置为 `ProviderProfile.Presets`，包含 OpenAI、Anthropic、Gemini、OpenRouter 和离线演示 Agent。通过 `ProviderStore(context, ProviderProfile.Presets + myServers)` 添加自己的 Agent 服务器。Demo 使用 `DemoPresets` 按此方式添加各协议的参考服务。

部分 provider 支持 OAuth 2.1 登录而非 API key：PKCE `S256`，使用 `127.0.0.1` loopback 回调或 RFC 8628 device flow。包括 OpenRouter，以及实验性的 ChatGPT（Codex）、xAI、Kimi Code 订阅登录。Token 自动刷新。

## Koog

`ai-elements-koog` 将 [JetBrains Koog](https://github.com/JetBrains/koog) Agent 作为 `ChatBackend` 或 harness 模型绑定，并把 AI Elements 工具转换为 Koog 工具。审批和进度行为与内置循环一致。
