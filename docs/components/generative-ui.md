# Generative UI

Answers with interface: JSX and A2UI rendered natively, artifacts and web previews.

## JSX · Text and layout

Model-written JSX rendered natively: headings, text, lists, dividers.

![JSX · Text and layout](../assets/components/jsx-typography.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx), jsxCodeBlocks()` |
| AI Elements | `JSXPreview` |
| Artifact | `ai-elements-genui` |
| Reference | [JsxPreview](../api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

Requires ai-elements-genui (minSdk 26). Supported JSX compiles to native A2UI components; arbitrary JavaScript is not executed.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-typography"
```

## JSX · Form with bindings and an action

Inputs bound to the data model; `onClick={subscribe}` sends an action with it.

![JSX · Form with bindings and an action](../assets/components/jsx-form.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings, onAction)` |
| AI Elements | `JSXPreview` |
| Artifact | `ai-elements-genui` |
| Reference | [JsxPreview](../api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

onAction receives the action name and current model. Validate and route it in your app; the renderer does not perform server writes.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-form"
```

## JSX · Choices, slider, date and tabs

`<select>` / `<option>`, `<Slider>`, `<DateTimeInput>` and `<Tabs>` in JSX.

![JSX · Choices, slider, date and tabs](../assets/components/jsx-controls.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings)` |
| AI Elements | `JSXPreview` |
| Artifact | `ai-elements-genui` |
| Reference | [JsxPreview](../api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

Requires ai-elements-genui (minSdk 26). Supported JSX compiles to native A2UI components; arbitrary JavaScript is not executed.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-controls"
```

## JSX · A row of cards from data

A layout of cards whose values come from bindings, and a link.

![JSX · A row of cards from data](../assets/components/jsx-cards.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings)` |
| AI Elements | `JSXPreview` |
| Artifact | `ai-elements-genui` |
| Reference | [JsxPreview](../api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

Requires ai-elements-genui (minSdk 26). Supported JSX compiles to native A2UI components; arbitrary JavaScript is not executed.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-cards"
```

## JSX · Streaming

JSX rendered while it streams in: half-written tags wait, what the user typed stays.

![JSX · Streaming](../assets/components/jsx-streaming.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(partialJsx)` |
| AI Elements | `JSXPreview` |
| Artifact | `ai-elements-genui` |
| Reference | [JsxPreview](../api/ai-elements-genui/dev.ai.elements.genui.jsx/-jsx-preview.html) |

Pass accumulated JSX and keep the composable at a stable position while streaming. Incomplete tags wait; keep initial bindings stable to retain edits.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.jsx.JsxPreview
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-jsx-streaming"
```

## A2UI surface

An A2UI v1.0 surface (the reference server's booking form) rendered natively.

![A2UI surface](../assets/components/a2ui.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `A2uiSurfaceView(surface, onAction), a2uiRenderer()` |
| Artifact | `ai-elements-genui` |
| Reference | [A2uiSurfaceView](../api/ai-elements-genui/dev.ai.elements.genui.a2ui/-a2ui-surface-view.html) |

Get a surface from A2uiState after processing standard A2UI events. For chat integration use a2uiRenderer and route actions through the transport binding.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.genui.a2ui.A2uiAction
import dev.ai.elements.genui.a2ui.A2uiSurface
import dev.ai.elements.genui.a2ui.A2uiSurfaceView
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-a2ui"
```

## Artifact

A generated artifact with its actions.

![Artifact](../assets/components/artifact.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Artifact(title, description, actions) { … }` |
| AI Elements | `Artifact` |
| Artifact | `ai-elements-ui` |
| Reference | [Artifact](../api/ai-elements-ui/dev.ai.elements.ui.code/-artifact.html) |

Supply action buttons through the actions slot. The app owns save/share operations and associated permissions.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.Artifact
import dev.ai.elements.ui.markdown.CodeBlock
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-artifact"
```

## Web preview

A generated page or URL in a sandboxed WebView.

![Web preview](../assets/components/web-preview.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `WebPreview(url, html)` |
| AI Elements | `WebPreview` |
| Artifact | `ai-elements-ui` |
| Reference | [WebPreview](../api/ai-elements-ui/dev.ai.elements.ui.code/-web-preview.html) |

Shows HTML or a URL in a sandboxed WebView. Do not treat preview content as trusted app UI; use MCP Apps for its standard server bridge.

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.WebPreview
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-web-preview"
```
