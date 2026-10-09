---
description: Jetpack Compose components and agent tooling for AI apps on Android.
hide:
  - navigation
  - toc
---

<div class="aie-hero" markdown>
<div markdown>

<p class="aie-eyebrow">Kotlin · Jetpack Compose · Material 3 Expressive</p>

# Build the conversation.

<p class="aie-lead">A complete chat screen or just the elements you need. Render streaming answers, tool approvals, sub-agents and generated interfaces with your own agent backend.</p>

[Get started](getting-started/installation.md){ .md-button .md-button--primary }
[Explore components](components/index.md){ .md-button }

<div class="aie-facts"><span>65 illustrated examples</span><span>Android · minSdk 24 / 26</span><span>Apache 2.0</span><span>English · 简体中文</span></div>

[中文文档](/ai-elements-kotlin/zh/) · [GitHub](https://github.com/JuneLeGency/ai-elements-kotlin) · [API reference](api/index.html)

</div>
<div class="aie-shots">
<img src="assets/screenshots/landing-light-en.webp" alt="Native Compose chat in the light theme, with a plan, code and tool approval" width="280" height="620">
<img src="assets/screenshots/landing-dark-en.webp" alt="The same conversation in the dark theme" width="280" height="620">
</div>
</div>

## Start with one screen

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:minimal"
```

`Chat` connects the conversation and composer to a controller. For an app, keep that controller
in a ViewModel. Follow the [pure-client quick start](getting-started/pure-client.md) for dependencies,
permissions, a runnable server and the complete setup.

<div class="aie-cards" markdown>
<div class="aie-card" markdown>

### Connect an existing agent

Use an AI SDK, AG-UI, A2A or ACP backend. The app renders messages, progress and human approvals.
Your server keeps its agent runtime.

[Build a pure client →](getting-started/pure-client.md)

</div>
<div class="aie-card" markdown>

### Run the agent in your app

Combine a model API with files, planning, memory and other optional capabilities.
Use the same chat elements for on-device and server agents.

[Build an in-app agent →](getting-started/in-app-agent.md)

</div>
</div>

## See what you can compose

<div class="aie-gallery" markdown>
<figure markdown>

[![Tool call with progress and output](assets/components/tool-calls.webp){ loading=lazy }](components/tools.md#tool-calls)
<figcaption>Tool calls and approvals</figcaption>
</figure>
<figure markdown>

[![Plan with step status](assets/components/plan.webp){ loading=lazy }](components/agent-structure.md#plan)
<figcaption>Plans and task progress</figcaption>
</figure>
<figure markdown>

[![A native generated form](assets/components/a2ui.webp){ loading=lazy }](components/generative-ui.md#a2ui-surface)
<figcaption>Generated interfaces</figcaption>
</figure>
<figure markdown>

[![Command output in a terminal view](assets/components/terminal.webp){ loading=lazy }](components/developer-tools.md#terminal)
<figcaption>Developer tools</figcaption>
</figure>
<figure markdown>

[![Agent computer and execution timeline](assets/components/agent-computer.webp){ loading=lazy }](components/tools.md#agents-computer)
<figcaption>The agent's computer</figcaption>
</figure>
<figure markdown>

[![An image attachment in the conversation](assets/components/image.webp){ loading=lazy }](components/attachments-and-media.md#image)
<figcaption>Attachments and media</figcaption>
</figure>
</div>

Every catalog entry includes a real screenshot, imports, a compiled example and API links.
[Browse all 65 examples →](components/index.md)

## Keep your backend and your design

- **Use the level you need:** `Chat`, `Conversation` + `PromptInput`, or individual elements.
- **Customize rendering:** theme tokens, tool/data renderers and your own attachment loader.
- **Choose dependencies:** UI depends only on the chat model; protocols and harness capabilities are separate artifacts.
- **Use public protocols:** AI SDK, AG-UI, MCP/MCP Apps, A2A, ACP and A2UI.
- **Plan upgrades:** stable public APIs are preserved from the first release; native Mermaid is explicit opt-in.

[Choose your setup](getting-started/choose.md) · [Customize](guides/customizing.md) ·
[API stability](develop/api-compatibility.md) · [FAQ](getting-started/faq.md)
