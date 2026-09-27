# Agent Client Protocol (ACP)

`ai-elements-acp` makes the app an [Agent Client Protocol](https://agentclientprotocol.com)
*client*, like an editor such as Zed or JetBrains IDEs. Coding agents that speak ACP become chat
providers: Claude Code, Codex and Gemini CLI through their ACP adapters, or any agent built on an
ACP SDK, such as Pydantic AI Harness's `run_acp_stdio`. It is built on the official
[ACP Kotlin SDK](https://github.com/agentclientprotocol/kotlin-sdk).

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:acp-chat"
```

## Connecting

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:acp-agents"
```

- **stdio** is the transport the ACP specification defines. `AcpAgent.process` starts the agent as a
  subprocess. That suits a desktop JVM, or a device that can run the agent, for example in the
  harness sandbox.
- **WebSocket** reaches an agent on another machine. ACP's remote transport (Streamable HTTP) is
  still a draft, so this uses the ACP Kotlin SDK's WebSocket transport: one JSON-RPC message per
  text frame, the same messages as stdio. The [reference server](../develop/reference-server.md)
  serves it at `ws://…/acp`.

!!! warning "Security"
    An ACP agent can read and change files and run commands on the machine it runs on. Only connect
    to agents you trust, over `wss://` with authentication outside your own network.

`cwd` is the session's working directory on the agent's machine (an absolute path, as ACP
`session/new` requires). One connection is shared by all conversations using the agent and reopens
after a failure. Close the agent when you are done with it.

## Sessions

Each conversation is one ACP session. The session id is kept in the reply's `Message.metadata`
under `acp`, so the next turn continues the same session and only the new user message is sent:
the agent keeps the history. When a session is gone, for example after the app restarted, it is
resumed with `session/load` if the agent supports it; otherwise a new session starts with the
earlier turns as context.

## Mapping

| ACP | In the chat |
|---|---|
| `agent_message_chunk` | text; images as files, resource links as sources |
| `agent_thought_chunk` | reasoning |
| `tool_call`, `tool_call_update` | a tool call: `title` as its name, `rawInput` as input; `content` (text, diffs as unified diffs, terminals) or `rawOutput` as output; `failed` as an error |
| `plan` | the Plan element |
| `session/request_permission` | an approval: approve picks the agent's allow-once option, deny its reject-once option |
| stop | `session/cancel` |
| `PromptResponse` | usage; `refusal`, `max_tokens`, `max_turn_requests` end the turn with an error |

ACP permission answers carry only the chosen option, so the approval offers yes / no, without a
reason or edited arguments. See [Human in the loop](../guides/human-in-the-loop.md).

## Offering files

The client advertises no file system by default. Pass an `AcpFileSystem` to answer
`fs/read_text_file` (and `fs/write_text_file` unless it is read-only), for example over the harness
workspace or a folder the user shared:

```kotlin
AcpAgent.webSocket(url, files = object : AcpFileSystem {
    override suspend fun read(path: String, line: Int?, limit: Int?) = workspaceFile(path).readText()
})
```

Terminals and ACP elicitation (still unstable in ACP) are not offered.
