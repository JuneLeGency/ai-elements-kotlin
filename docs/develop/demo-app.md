# Demo app

`demo/` is a complete assistant app built from the libraries. It runs offline out of the box (a
scripted provider) and connects to the [reference server](reference-server.md) or real providers.

```bash
./gradlew :demo:installDebug
```

## What it shows

- **Providers**: the offline demo, the reference server over AI SDK, AG-UI, A2A and ACP, OpenAI,
  Anthropic, Gemini, OpenRouter, Ollama and any OpenAI-compatible endpoint. Sign in with OAuth where
  a provider supports it; API keys are stored encrypted.
- **Conversations**: history with search, branches, checkpoints, a message queue, attachments and
  voice input. Tablets show the history as a side pane.
- **Agent capabilities**: MCP servers (with OAuth and MCP Apps), skills (bundled from the
  repository's `skills/` folder, or imported as `.zip`), sub-agents and remote A2A agents, the
  workspace, the Linux sandbox, memory, planning, the browser, device tools and scheduled tasks.
- **Components**: every element with sample data.
- **Settings**: themes, palettes, contrast, fonts, text size and languages (English, 简体中文,
  繁體中文, 日本語).

## Where things live

| File | Role |
|---|---|
| `AgentRuntime.kt` | builds the backend for the selected provider: protocol backends, or `AgentHarness` with the capabilities |
| `ChatViewModel.kt` | the `ChatController`, conversation persistence, model lists |
| `ui/ChatScreen.kt` | the chat screen: top bar with the provider picker, drawer, composer |
| `ui/SettingsScreen.kt` | providers, sign-in, MCP servers, capabilities, appearance |
| `samples/DocsSamples.kt` | the code on this site, compiled with the demo |

`samples/pure-client` and `samples/in-app-agent` are minimal single-screen apps for each mode.
