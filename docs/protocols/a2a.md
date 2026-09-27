# A2A

`ai-elements-a2a` connects to [Agent2Agent](https://a2a-protocol.org) agents with the official
`a2a-java-sdk` (A2A 1.0 JSON-RPC binding, compatible with 0.3 agents), as a chat provider or as a
sub-agent of your own agent.

```kotlin
val agent = A2aAgent("https://agents.example.com")   // card at /.well-known/agent-card.json
ChatController(backend = { _ -> A2aBackend(agent) }, scope = viewModelScope)
```

The module needs [core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)
because the SDK uses Java 17 library APIs.

## As a provider

Each turn sends the last user message with the conversation's A2A `contextId`. When the previous
reply left the task `input-required` or `auth-required`, the answer continues that task. Both ids
are kept in the reply's `Message.metadata` under `a2a`.

- Artifacts and the agent's final message become text, files and data parts.
- Progress messages while the task is `working` show as a `data-task` checklist.
- `failed`, `rejected` and `canceled` end the turn with an error.
- A2UI parts (`application/a2ui+json`) render as [generative UI](../guides/generative-ui.md); user
  actions go back as A2UI A2A parts.

## As a sub-agent

`A2aAgent.asSubAgent(card)` turns a remote agent into a `SubAgent`, so an in-app agent can delegate
to it with `delegate_task`, and its run shows as a `Subagent` card:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:a2a-subagent"
```

`cardOrNull()` fetches the card in the background with a timeout and remembers failures for a
while, so an unreachable agent never blocks a turn.

## Agent cards

The `Agent` element renders an A2A agent card: its description and skills.
