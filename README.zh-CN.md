<div align="center">

<img src="docs/assets/logo.svg" width="64" height="64" alt="AI Elements 标志">

# AI Elements for Kotlin

**面向 AI 对话与 Agent 的原生 Compose 组件库。**

[English](README.md) · [简体中文](README.zh-CN.md)

[![CI](https://github.com/JuneLeGency/ai-elements-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/JuneLeGency/ai-elements-kotlin/actions/workflows/ci.yml)
[![许可证](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-minSdk%2024%20%2F%2026-3DDC84)
![UI](https://img.shields.io/badge/UI-Material%203%20Expressive-6750A4)

[中文文档](https://junelegency.github.io/ai-elements-kotlin/zh/) ·
[组件图册](https://junelegency.github.io/ai-elements-kotlin/zh/components/) ·
[快速开始](docs/zh/getting-started/pure-client.md) ·
[API 参考](https://junelegency.github.io/ai-elements-kotlin/api/)

<img src="docs/assets/screenshots/landing-light-zh-CN.webp" width="280" alt="中文浅色界面：消息、计划、代码与工具审批">
<img src="docs/assets/screenshots/landing-dark-zh-CN.webp" width="280" alt="相同原生 Compose 示例的中文深色界面">

</div>

可以从完整聊天界面开始，也可以只选择需要的组件。流式 Markdown、思考摘要、工具审批、子 Agent、计划、附件和生成式界面共用一套聊天模型。
连接现有服务端，或在 App 内运行 Agent 循环，都能使用相同的 UI。

截图由模拟器上的真实 Android 组件渲染，内容为专门准备的展示示例。
[组件图册](docs/zh/components/index.md)提供 **65 个带实图的场景**、可编译代码和 API 链接。
项目是 [Vercel AI Elements](https://elements.ai-sdk.dev) 的 Compose 对应实现，使用公开协议。

## 从一个界面开始

```kotlin
AiElementsTheme {
    Chat(rememberChat { approver ->
        AgUiBackend("https://agents.example.com/api/agui", approver = approver)
    })
}
```

[快速开始](docs/zh/getting-started/pure-client.md)包含 imports、Android 权限、可运行的参考服务和 ViewModel 接法。
参考服务的脚本模型不需要 API key。

## 选择 Agent 的运行位置

| 方式 | App 引入 | 入门 |
|---|---|---|
| 服务端 Agent | UI + AI SDK、AG-UI、A2A 或 ACP 后端 | [纯客户端](docs/zh/getting-started/pure-client.md) |
| 端侧 Agent | UI + 模型 API + 所需 harness 能力 | [端侧 Agent](docs/zh/getting-started/in-app-agent.md) |
| 自己实现 | 单个组件，或自定义 ChatBackend | [自定义后端](docs/zh/guides/custom-backend.md) |

UI 只依赖 ai-elements-chat，不依赖网络模块。主题、工具/数据 renderer 和附件加载都可替换，无需 fork 组件。

## 安装

```kotlin
dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:0.3.0"))
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency:ai-elements-core")
}
```

依赖仓库中添加 `google()` 和 `mavenCentral()`。
[安装指南](docs/zh/getting-started/installation.md)列出所有模块、minSdk、desugaring 和实际验证的工具链。
当前 Compose / Material 3 Expressive 包含 alpha 依赖。
从首个公开版本起保留稳定 public API，0.x 同样适用，详见[兼容性承诺](docs/zh/develop/api-compatibility.md)。

## 组件效果

| 工具与审批 | 原生生成式界面 | 开发者工具 |
|:---:|:---:|:---:|
| ![工具调用](docs/assets/components/tool-calls.webp) | ![A2UI 表单](docs/assets/components/a2ui.webp) | ![终端输出](docs/assets/components/terminal.webp) |
| [工具组件](docs/zh/components/tools.md) | [生成式界面](docs/zh/components/generative-ui.md) | [开发者工具](docs/zh/components/developer-tools.md) |

还包含[对话控件](docs/zh/components/conversation.md)、[Markdown 与图表](docs/zh/components/content.md)、
[附件媒体](docs/zh/components/attachments-and-media.md)、[语音](docs/zh/components/voice.md)和[工作流画布](docs/zh/components/workflow.md)。

支持 AI SDK、AG-UI、MCP/MCP Apps、A2A、ACP、A2UI 和 Agent Skills。
可选端侧能力包括文件、计划、记忆、命令/沙箱、浏览器、设备、语音和后台计划任务。
[比较接入方式](docs/zh/getting-started/choose.md)。

## 运行与验证

```bash
./gradlew :demo:installDebug
./gradlew apiCheck testDebugUnitTest lintDebug
tools/check-published-consumer.sh
tools/build-docs.sh
```

库源码、Demo、samples 与中英文 GitHub Pages 站点都在同一个仓库。
进一步阅读[测试](docs/zh/develop/testing.md)、[贡献指南](docs/zh/develop/contributing.md)、
[发布流程](docs/zh/develop/releasing.md)、[更新记录](CHANGELOG.md)和[项目进度](docs/zh/project/roadmap.md)。

## 许可证与安全

主库使用 Apache-2.0，见 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。
可选 harness-sandbox-proot 把 PRoot（GPL-2.0）作为独立可执行文件打包，附带[独立 NOTICE](harness/harness-sandbox-proot/NOTICE)。
漏洞报告请遵循 [SECURITY.md](SECURITY.md)。
