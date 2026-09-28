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

`JsxPreview` renders JSX (the AI Elements `JSXPreview`) natively: the JSX compiles onto A2UI
components and renders with the same catalog as A2UI surfaces, so it looks like the rest of the app.
It evaluates data only, never code, and renders while the JSX streams in.

```kotlin
JsxPreview(
    jsx = source,                                            // what the model wrote
    bindings = buildJsonObject { put("email", "") },         // the data model {name} reads
    onAction = { action -> controller.send(action) },        // onClick={subscribe} → "subscribe"
)
```

In a chat, `jsxCodeBlocks` renders ```` ```jsx ```` (and ```` ```tsx ````) fences in answers as live
previews, with a toggle to show the source. Register it with
`AiElementsRenderers(codeBlocks = mapOf("jsx" to jsxCodeBlocks(), "tsx" to jsxCodeBlocks()))`.

### What it understands

| JSX | Renders as |
|---|---|
| `h1`–`h6`, `p`, `span`, `b` / `strong`, `i` / `em`, `code`, `small` | Text (headings and inline styles as Markdown) |
| `div`, `section`, `main`, … (`className="flex …"` or `flex-row` makes a Row) | Column / Row |
| `ul` / `ol` with `li` | a bulleted or numbered Column |
| `hr` | Divider |
| `img src alt` | Image |
| `a href` | a link button (opens the URL) |
| `button` / `<Button variant>` | Button, with `onClick` as its action |
| `input` (`type` text, `email`, `password`, `number`), `textarea` | TextField; `name="x"` binds it to `/x` |
| `input type="checkbox" checked={x}` | CheckBox |
| `select name` with `option value` | ChoicePicker (`multiple` for several) |
| `<Tabs>` with `<Tab title>` | Tabs |
| Any Basic Catalog component by name: `Card`, `Row`, `Column`, `List`, `Text`, `Image`, `Icon`, `Video`, `AudioPlayer`, `Slider`, `CheckBox`, `ChoicePicker`, `DateTimeInput`, `TextField`, `Modal`, … | that component, with its properties |
| Unknown tags (`<Fragment>`, `<section>`, …) | their children |

### Values

- **Bindings.** `{name}` or `{a.b}` read the data model (`bindings`). Inputs bound to a path write
  back into it, locally, and an action carries the current data model.
- **Literals.** Strings, numbers, `true` / `false`, and JSON arrays and objects (`options={[{"label": "S", "value": "s"}]}`).
- **Nothing else runs.** `{alert(1)}`, arrow functions and other code are dropped, never evaluated:
  model output cannot execute in the app.
- **Actions.** `onClick={save}` or `onClick="save"` sends the action `save` to `onAction`.

### While it streams

Half-written tags wait until they close, and unclosed tags close at the end. The preview keeps one
surface for the whole stream, so what the user typed stays while more JSX arrives.

See every JSX sample, with its source, under [Generative UI](../components/generative-ui.md) in the
component catalog. In the demo app, tap **Generative UI (JSX form)**: the offline agent answers
with a JSX booking form.
