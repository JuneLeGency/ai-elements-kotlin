<div align="center">

<img src="docs/assets/logo.svg" width="64" height="64" alt="AI Elements logo">

# AI Elements for Kotlin

**Native Compose components for AI conversations and agents.**

[English](README.md) · [简体中文](README.zh-CN.md)

[![CI](https://github.com/JuneLeGency/ai-elements-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/JuneLeGency/ai-elements-kotlin/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-minSdk%2024%20%2F%2026-3DDC84)
![UI](https://img.shields.io/badge/UI-Material%203%20Expressive-6750A4)

[Documentation](https://junelegency.github.io/ai-elements-kotlin/) ·
[Components](https://junelegency.github.io/ai-elements-kotlin/components/) ·
[Quick start](docs/getting-started/pure-client.md) ·
[API reference](https://junelegency.github.io/ai-elements-kotlin/api/)

<img src="docs/assets/screenshots/landing-light-en.webp" width="280" alt="Light theme: chat, a plan, code and tool approval">
<img src="docs/assets/screenshots/landing-dark-en.webp" width="280" alt="The same native Compose example in the dark theme">

</div>

Build a complete chat screen or use just the elements you need. Streaming Markdown, reasoning,
tool approvals, sub-agents, plans, attachments and generated interfaces share one chat model.
Connect your own backend, or run the agent loop inside the app.

Screenshots are rendered from real Android components on an emulator with curated example data.
The [catalog](docs/components/index.md) includes **65 illustrated scenarios**, compiled examples and API links.
This is the Compose counterpart of [Vercel AI Elements](https://elements.ai-sdk.dev), on public protocols.

## Start with a screen

```kotlin
AiElementsTheme {
    Chat(rememberChat { approver ->
        AgUiBackend("https://agents.example.com/api/agui", approver = approver)
    })
}
```

Follow the [quick start](docs/getting-started/pure-client.md) for imports, Android permissions,
a runnable reference server and ViewModel ownership. The reference server's scripted model needs no API key.

## Choose where the agent runs

| Mode | Your app adds | Start here |
|---|---|---|
| Server agent | UI + an AI SDK, AG-UI, A2A or ACP backend | [Pure client](docs/getting-started/pure-client.md) |
| In-app agent | UI + a model API and optional harness capabilities | [In-app agent](docs/getting-started/in-app-agent.md) |
| Your own implementation | Individual elements, or a custom ChatBackend | [Custom backend](docs/guides/custom-backend.md) |

The UI depends only on `ai-elements-chat`, not the networking modules. Customize the theme, tool/data
renderers and attachment loading without forking the components.

## Install

```kotlin
dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:0.3.0-SNAPSHOT"))
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency:ai-elements-core")
}
```

`0.3.0` is not released yet. Build with `./gradlew publishToMavenLocal` and add `mavenLocal()`.
See [Installation](docs/getting-started/installation.md) for all artifacts, minSdk, desugaring and the
exact tested toolchain. Compose and Material 3 Expressive currently include alpha dependencies.
Stable public APIs are preserved from the first public release, including 0.x; see the
[compatibility policy](docs/develop/api-compatibility.md).

## Explore the elements

| Tools and approvals | Native generated UI | Developer tools |
|:---:|:---:|:---:|
| ![Tool calls](docs/assets/components/tool-calls.webp) | ![A2UI form](docs/assets/components/a2ui.webp) | ![Terminal](docs/assets/components/terminal.webp) |
| [Tools](docs/components/tools.md) | [Generative UI](docs/components/generative-ui.md) | [Developer tools](docs/components/developer-tools.md) |

Also included: [conversation controls](docs/components/conversation.md), [Markdown and diagrams](docs/components/content.md),
[media](docs/components/attachments-and-media.md), [voice](docs/components/voice.md) and [workflow canvas](docs/components/workflow.md).

Protocols: AI SDK v5/v6 + v4 compatibility · AG-UI 1.x · MCP · MCP Apps · A2A · ACP · A2UI · Agent Skills.
Optional capabilities include files, planning, memory, shell/sandbox, browser, device integration,
speech and scheduled tasks. [Compare backends](docs/getting-started/choose.md).

## Run and verify

```bash
./gradlew :demo:installDebug
./gradlew apiCheck testDebugUnitTest lintDebug
tools/check-published-consumer.sh
tools/build-docs.sh
```

The same repository contains the library, demo, samples and bilingual GitHub Pages site.
See [Testing](docs/develop/testing.md), [Contributing](CONTRIBUTING.md), [Releasing](docs/develop/releasing.md),
[CHANGELOG](CHANGELOG.md) and [Roadmap](docs/project/roadmap.md).

## License and security

Apache-2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). `harness-sandbox-proot` packages PRoot
(GPL-2.0) as a separate executable with its own [NOTICE](harness/harness-sandbox-proot/NOTICE).
Report vulnerabilities as described in [SECURITY.md](SECURITY.md).
