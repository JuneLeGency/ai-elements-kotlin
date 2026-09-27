# MCP Apps

An MCP tool can ship an interactive view: an [MCP App](https://github.com/modelcontextprotocol/ext-apps)
declared with `_meta.ui.resourceUri`. With `ai-elements-mcp-apps` the chat shows the view under the
tool call.

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:mcp-apps"
```

`McpAppsHost` also shows the views' dialogs, such as tool approvals and full screen, at the screen
level, so place it around the whole chat.

## Sandbox

Each view runs in a WebView served from its own origin (one per MCP server) with the
Content-Security-Policy its resource declares, and talks JSON-RPC over a message port. No JavaScript
interface is exposed; the view cannot navigate the page, read files or content URIs, or get
permissions such as the camera or location.

## What a view can do

- Call its own server's tools whose `visibility` includes `app`. Tools that are not read-only ask
  the user first.
- `ui/message`: send a user message (`McpAppActions.message`).
- `ui/update-model-context`: give the model context for its next turn. It becomes a
  `DataPart.MODEL_CONTEXT`, which on-device models read as text and AG-UI sends as
  `RunAgentInput.context`.
- `ui/open-link`: open an `http(s)` link.
- Resize itself and ask for full screen.

Tools with `visibility: ["app"]` are never given to the model.

Views keep running while they scroll out of sight and are torn down (`ui/resource-teardown`) when
evicted or when the host leaves the composition.
