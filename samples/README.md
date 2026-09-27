# Samples

Two minimal apps, one per way of using the library. Each is a single file you can copy.

| Sample | What it shows | Needs |
|---|---|---|
| [`pure-client`](pure-client) | The app only renders; the agent runs on an AG-UI server (swap for AI SDK or A2A). | `cd server && uv run uvicorn main:app --port 8788` |
| [`in-app-agent`](in-app-agent) | The agent runs in the app: `AgentHarness` over a model API with files and a plan. | A model API (the sample uses a local Ollama with `qwen3:4b`) |

```bash
./gradlew :samples:pure-client:installDebug
./gradlew :samples:in-app-agent:installDebug
```

Both use `Chat(controller)` from `ai-elements-ui`: the conversation, tools and approvals,
sub-agents, plans and the prompt input in one composable. The full-featured reference is
the [demo](../demo).
