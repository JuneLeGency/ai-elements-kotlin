# 安装与环境

本库以 Android 库（AAR）形式发布，面向 Jetpack Compose。先用 BOM 统一所有模块版本，再引入需要的模块。

在 settings 文件中声明依赖仓库：

```kotlin title="settings.gradle.kts"
--8<-- "tools/doc-snippets/installation-repositories.gradle.kts"
```

```kotlin title="build.gradle.kts"
--8<-- "tools/doc-snippets/installation-dependencies.gradle.kts"
```

## 模块一览

以下模块的 group 为 `io.github.junelegency`。

| 模块 | 职责 | minSdk |
|---|---|---|
| `ai-elements-chat` | 与协议无关的聊天模型（类似 AI SDK `UIMessage`）、`ChatEvent`、`ChatBackend` 和 `ChatController`（类似 `useChat`）。无网络、无 Compose。 | 24 |
| `ai-elements-core` | AI SDK、AG-UI 协议客户端、模型 API、Agent 循环、`SubAgents`、`Skills`、MCP 和 OAuth。作为 `ChatBackend` 或能力构建在 chat 之上，无 Compose。 | 24 |
| `ai-elements-ui` | Compose 组件与 `AiElementsTheme`，只依赖 chat，因此也能渲染自定义 backend。 | 24 |
| `ai-elements-genui` | 原生 A2UI v1.0 surface（Basic Catalog，可扩展）和 `JsxPreview`。 | 26 |
| `ai-elements-mcp-apps` | MCP Apps 宿主：在沙箱 WebView 中显示 MCP 工具的 `ui://` 视图，并通过桥接与服务器通信。 | 24 |
| `ai-elements-notifications` | Agent 运行时的 Android 16 Live Update 和运行结束时的回复通知。此模块会引入通知权限，因此为可选模块。 | 24 |
| `ai-elements-a2a` | 基于官方 Java SDK 的 A2A 1.0，将远程 Agent 作为 provider 或子 Agent；需要 core library desugaring。 | 26 |
| `ai-elements-acp` | 基于官方 Kotlin SDK 的 Agent Client Protocol，将编码 Agent 作为 provider。 | 24 |
| `ai-elements-koog` | 将 JetBrains Koog Agent 用作 `ChatBackend` 或 harness 模型绑定。 | 26 |
| `ai-elements-mermaid-native` | 使用 Compose Canvas 而非 WebView 绘制 Mermaid；实验性模块。 | 24 |
| `ai-elements-bom` | 对齐这里所有模块的版本。 | — |

应用内 Agent 能力的 group 为 `io.github.junelegency.harness`：

| 模块 | 工具与职责 | minSdk |
|---|---|---|
| `harness-core` | `AgentHarness` 将模型与能力组合为 `ChatBackend`，支持本地及远程子 Agent。 | 24 |
| `harness-filesystem` | `read_file`、`write_file`、`edit_file`、`list_directory`、`search_files`、`find_files`、`create_directory`、`file_info`，操作工作区及用户分享的文件夹。 | 26 |
| `harness-memory` | `write_memory`、`read_memory`、`delete_memory`、`search_memory`；每轮注入 `MEMORY.md`。 | 26 |
| `harness-planning` | `write_plan`、`read_plan`、`add_task`、`update_task_status(es)`、`remove_task`；驱动 `Plan` 组件。 | 26 |
| `harness-shell` | 通过可插拔运行时提供 `run_command`、`start_command`、`check_command`、`stop_command`。 | 26 |
| `harness-sandbox-proot` | 通过 PRoot 运行 Alpine Linux；附带可执行文件遵循 GPL-2.0，见其 NOTICE。 | 26 |
| `harness-browser` | 在离屏 WebView 中提供 `navigate`、`snapshot`、`click`、`type_text`、`get_text`、`screenshot` 等工具。 | 26 |
| `harness-device` | 设备信息、剪贴板、日历、联系人、定位、闹钟和通知；按需申请权限。 | 26 |
| `harness-speech` | 使用平台 TTS 引擎提供 `speak`、`stop_speaking`。 | 26 |
| `harness-scheduler` | `schedule_task`、`list_scheduled_tasks`、`cancel_scheduled_task`：使用 WorkManager 在后台运行。 | 26 |

库会附带 R8 consumer rules。

## 环境要求

- 经过消费工程验证的基线为 JDK 21、Gradle 9.8.0、AGP 9.4.1（内置 Kotlin 2.4.20）、`compileSdk` 37.2 和 Java 17 字节码。更旧的消费端工具链尚未认证。`ai-elements-a2a` 需要 [core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)。
- 使用 Compose 和 Material 3 1.5（Expressive）。本库使用 AGP 9.4、Kotlin 2.4 和 Compose 1.13 构建。
- `harness-sandbox-proot` 从原生库目录运行 PRoot，因此必须解压原生库：`android { packaging { jniLibs.useLegacyPackaging = true } }`。

Compose 1.13.0-alpha03 和 Material 3 1.5.0-alpha29 是预发布依赖，其传递依赖要求也适用于消费工程。AI Elements BOM 只对齐本库模块，不会对齐应用任意选择的 AndroidX 版本。参见 [API 稳定性](../develop/api-compatibility.md)。

新建 Compose 应用时，启用 Compose compiler 插件和 `buildFeatures.compose = true`，将 Java source/target compatibility 设为 17，并添加 `androidx.activity:activity-compose:1.13.0`。ViewModel 示例还使用 `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0` 与 `androidx.lifecycle:lifecycle-runtime-compose:2.11.0`。

### A2A 消费端配置

```kotlin
--8<-- "tools/doc-snippets/installation-a2a.gradle.kts"
```

处理重复元数据时，仍需在分发包中保留依赖的许可证声明，详见仓库 NOTICE。仅添加这些 packaging exclusions 不能代替许可证署名。

### 验证发布包

[独立消费工程](https://github.com/JuneLeGency/ai-elements-kotlin/tree/main/samples/published-consumer)通过 Maven 坐标编译 UI-only、pure-client、in-app-agent 和 optional-integration 四类应用，包含使用 R8 的 release 构建；它与库本身的构建相互独立。
