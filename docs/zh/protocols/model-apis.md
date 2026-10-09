# 模型 API

在设备上运行 Agent 循环时，core 可直接调用模型 API；每个后端都实现 ChatBackend。

| API | 后端 |
|---|---|
| OpenAI Chat Completions 及兼容服务 | OpenAiChatBackend |
| OpenAI Responses | OpenAiResponsesBackend |
| Anthropic Messages | AnthropicBackend |
| Gemini | GeminiBackend |
| Ollama 原生 API | OllamaBackend |

ProviderProfile 描述 provider kind、base URL 和模型，createBackend 创建内置循环，model 扩展创建 harness 的 ModelBinding。
listModels 查询服务实际提供的模型。不要仅凭模型名称推断工具、视觉或 reasoning 能力。

ProviderStore / SecretStore 通过 Android Keystore 保护密钥。部分提供方支持 OAuth、loopback redirect 或 device flow；
不同服务的订阅能力与可用性不等同于普通 API key，部分登录集成仍为实验性。

JetBrains Koog 用户可以引入 ai-elements-koog，用 KoogBackend 或 harness model binding 接入相同的工具审批与进度展示。
完整可编译的端侧组合见[端侧 Agent](../getting-started/in-app-agent.md)。
