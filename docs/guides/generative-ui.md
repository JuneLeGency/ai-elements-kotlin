# Generative UI

Agents can answer with interface, not only text. AI Elements supports two open formats:

- **[A2UI](https://a2ui.org) v1.0** surfaces, rendered natively with Compose (`ai-elements-genui`);
- **[MCP Apps](../protocols/mcp-apps.md)** views of MCP tools, rendered in a sandboxed WebView
  (`ai-elements-mcp-apps`).

## A2UI

A2UI surfaces arrive as `DataPart.A2UI` whichever protocol carried them: AG-UI `a2ui-surface`
activities, A2A `application/a2ui+json` parts, or AI SDK `data-a2ui` parts. User actions go back on
the same binding (`forwardedProps.a2uiAction`, an A2UI A2A part, a `data-a2ui` part).

Register the renderer:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:genui"
```

`controller.send(action)` is an extension in `dev.ai.elements.genui.a2ui`: it sends the action's
user message together with its data parts.

- **Catalogs.** The Basic Catalog is built in. Add your design system with
  `A2uiCatalog.Basic.extend(id = "https://example.com/catalog", components = …)`.
- **State.** A form keeps what the user typed while more messages stream in and while it scrolls out
  of sight: sessions live in the renderer, not in the lazy list.
- **Outside the chat.** `A2uiSurfaceView` and `A2uiState` render a surface anywhere.
- **Lifecycle.** A part holding `{"status": "building" | "retrying" | "failed"}` (the AG-UI
  middleware's pre-paint lifecycle) shows as a placeholder.

The [reference server](../develop/reference-server.md)'s hotel concierge (`hotel`) answers with an
A2UI booking form on AG-UI, AI SDK and A2A.

## JSX

`JsxPreview` renders a JSX subset (the AI Elements `JSXPreview`) with the same native components. It
evaluates bindings only, never code, and keeps its state while the JSX streams. `jsxCodeBlocks`
renders ```` ```jsx ```` fences in answers as live previews.
