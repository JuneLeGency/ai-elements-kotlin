# MCP

The [Model Context Protocol](https://modelcontextprotocol.io) client in `ai-elements-core` gives an
agent tools, resources and prompts from MCP servers. It speaks the **2026-07-28** revision over
Streamable HTTP and falls back to the **2025-xx session** revisions for older servers.

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:mcp-client"
```

## In an agent

`McpServerStore` keeps the servers the user configured, with credentials encrypted, and turns the
enabled ones into a capability:

```kotlin
AgentHarness(model = …, capabilities = { listOf(mcpServers.toolset()) })
```

`McpToolset` lists the servers' tools in parallel with a timeout per server. A server that fails is
skipped for that turn and reported in `status`, instead of failing the turn. Tool lists are cached.

MCP tools render with the server's name as their source. Tools that are not read-only ask the user
before they run. Tool annotations are treated as untrusted hints: only `readOnlyHint` relaxes the
default.

## Features

- Tools (`tools/list`, `tools/call`), resources, prompts, pagination.
- `notifications/progress`, shown live on the tool call.
- **Elicitation**, where the server asks the user something:
    - 2026-07-28: `InputRequiredResult` (`resultType: "input_required"`) is answered by retrying the call
      with `inputResponses` and `requestState` (up to 10 rounds);
    - legacy sessions: `elicitation/create` on the stream.

    Form and URL modes are declared. In a chat, the question shows as a form; see
    [Human in the loop](../guides/human-in-the-loop.md).
- **OAuth** for protected servers: protected resource metadata (RFC 9728), authorization server
  metadata (RFC 8414), dynamic client registration (RFC 7591), resource indicators (RFC 8707),
  PKCE `S256`. A server that needs sign-in is reported as `NeedsSignIn`.
- **MCP Apps** views of tools; see [MCP Apps](mcp-apps.md).

Sampling and roots are not declared.
