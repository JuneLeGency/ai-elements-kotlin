# Generative UI

Answers with interface: JSX and A2UI rendered natively, artifacts and web previews.

## JSX · Text and layout

Model-written JSX rendered natively: headings, text, lists, dividers.

![JSX · Text and layout](../assets/components/jsx-typography.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx), jsxCodeBlocks()` |
| AI Elements | `JSXPreview` |

## JSX · Form with bindings and an action

Inputs bound to the data model; `onClick={subscribe}` sends an action with it.

![JSX · Form with bindings and an action](../assets/components/jsx-form.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings, onAction)` |
| AI Elements | `JSXPreview` |

## JSX · Choices, slider, date and tabs

`<select>` / `<option>`, `<Slider>`, `<DateTimeInput>` and `<Tabs>` in JSX.

![JSX · Choices, slider, date and tabs](../assets/components/jsx-controls.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings)` |
| AI Elements | `JSXPreview` |

## JSX · A row of cards from data

A layout of cards whose values come from bindings, and a link.

![JSX · A row of cards from data](../assets/components/jsx-cards.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(jsx, bindings)` |
| AI Elements | `JSXPreview` |

## JSX · Streaming

JSX rendered while it streams in: half-written tags wait, what the user typed stays.

![JSX · Streaming](../assets/components/jsx-streaming.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `JsxPreview(partialJsx)` |
| AI Elements | `JSXPreview` |

## A2UI surface

An A2UI v1.0 surface (the reference server's booking form) rendered natively.

![A2UI surface](../assets/components/a2ui.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `A2uiSurfaceView(surface, onAction), a2uiRenderer()` |

## Artifact

A generated artifact with its actions.

![Artifact](../assets/components/artifact.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Artifact(title, description, actions) { … }` |
| AI Elements | `Artifact` |

## Web preview

A generated page or URL in a sandboxed WebView.

![Web preview](../assets/components/web-preview.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `WebPreview(url, html)` |
| AI Elements | `WebPreview` |
