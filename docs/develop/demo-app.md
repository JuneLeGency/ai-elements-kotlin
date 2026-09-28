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
- **Components**: every element with sample data, grouped in ten categories with a filter per
  group. The same samples make the [component catalog](../components/index.md): regenerate it with
  `ComponentCatalogScreenshots` and `tools/build-component-docs.py`.
- **Offline demos**: the offline provider's suggestions include a scripted browser run on the
  agent's computer (**Browse a web page**) and an answer with interface (**Generative UI (JSX form)**).
- **Settings**, two levels deep: a grouped home (Agent: providers, MCP servers, skills,
  sub-agents, the on-device agent; App: appearance, text and language, diagrams), each entry with
  where it stands now, opening its page. Editors (a provider, an MCP server, a sub-agent) open as
  dialogs. Tablets show the home and the open page side by side.

## Where things live

| File | Role |
|---|---|
| `AgentRuntime.kt` | builds the backend for the selected provider: protocol backends, or `AgentHarness` with the capabilities |
| `ChatViewModel.kt` | the `ChatController`, conversation persistence, model lists |
| `ui/ChatScreen.kt` | the chat screen: top bar with the provider picker, drawer, composer |
| `ui/SettingsScreen.kt` | providers, sign-in, MCP servers, capabilities, appearance |
| `ui/GalleryCatalog.kt` | the Components screen's samples by category, with what the docs catalog says about each |
| `samples/DocsSamples.kt` | the code on this site, compiled with the demo |

`samples/pure-client` and `samples/in-app-agent` are minimal single-screen apps for each mode.
