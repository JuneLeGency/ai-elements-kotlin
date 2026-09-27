# Customizing

Elements are protocol-independent: they render the chat model (`ToolPart.kind` / `source`, data
parts, messages), whichever backend produced it. Customize how things render app-wide, without
forking `Conversation`.

## Renderers

`LocalAiElementsRenderers` replaces how parts render:

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:renderers"
```

| Key | Replaces |
|---|---|
| `tools` | a tool call, by tool name |
| `tool` | any tool call you choose (return `null` to keep the built-in rendering) |
| `data` | a data part, by name (`data-chart` → `"chart"`), including keys of the agent's shared state |
| `codeBlocks` | a fenced code block, by language |

`LocalFileLoader` loads attachment previews with your HTTP stack, cache or authentication.

## Theme

```kotlin
AiElementsTheme(colorScheme = brandColors, typography = brandType, shapes = brandShapes) {
    Chat(controller)
}
```

Links open through Compose's `LocalUriHandler`; provide your own to route them in the app.

## Tools say what they are

A tool's meaning is a model field, never guessed from its name: `ToolKind.Function`,
`ToolKind.Delegation` (renders as a `Subagent`) or `ToolKind.Skill` (a skill badge), and `source`
(for example an MCP server's name, shown as a chip). On-device tools declare them with
`AgentTool.kindFor` and `AgentTool.source`, so custom tools render like built-in ones. Protocol
mappings in `ai-elements-core` set them for server tools.

If an element needs more information, extend the model rather than matching a tool name in the UI.
