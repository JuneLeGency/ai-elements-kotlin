# 快速开始：应用内 Agent

先完成 [安装](installation.md) 和 [网络 manifest 配置](pure-client.md#app-setup)。按能力添加 harness 模块，注意其 minSdk 可能高于 UI 模块。

此模式在设备上运行 Agent 循环，直接调用模型 API。`AgentHarness` 将模型与能力（工具及说明）组合为 `ChatBackend`，仍由同一组组件渲染。

## 最小应用

先使用文件和计划能力，不要求沙箱、已安装技能或 MCP 服务器：

```kotlin title="build.gradle.kts"
dependencies {
    // Keep the BOM and activity/lifecycle dependencies from Installation.
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency.harness:harness-core")
    implementation("io.github.junelegency.harness:harness-filesystem")
    implementation("io.github.junelegency.harness:harness-planning")
}
```

使用 minSdk 26。示例连接电脑上已安装 `qwen3:4b` 的本地 Ollama；确保模拟器可以访问该端点，并按实际情况替换端点和模型。此本地配置不需要云端模型凭证。

```kotlin
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import dev.ai.elements.core.chat.ChatController
import dev.ai.elements.core.config.ProviderKind
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.harness.AgentHarness
import dev.ai.elements.harness.model
import dev.ai.elements.harness.filesystem.FileSystem
import dev.ai.elements.harness.planning.Planning
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.theme.AiElementsTheme
import java.io.File
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:minimal-in-app-agent"
```

在 activity 的 `setContent` 中调用 `AppAgentScreen()`，发送让它制定计划或读取工作区文件的 prompt。完整应用位于 `samples/in-app-agent`，其源码也在独立 Maven 消费工程中编译。通过 [完整 harness 示例](../guides/harness.md) 添加其他能力。

## 各项能力

- `FileSystem` 读写工作区和用户分享的文件夹。
- `Shell` 在挂载工作区到 `/workspace` 的 Alpine Linux / PRoot 沙箱中执行命令，可通过 `apk add` 安装包。
- `Memory` 跨会话保存笔记，每轮注入 `MEMORY.md`。
- `Planning` 保存计划并显示为 `Plan` 组件。
- `Skills` 提供 [Agent Skills](https://agentskills.io)（`SKILL.md` 文件夹），模型通过 `load_capability` 按需加载。
- `mcpServers.toolset()` 添加用户配置的 MCP 服务器工具。
- `localSubAgents` 提供可通过 `delegate_task` 委派的其他 Agent。

工具名称与行为对应 [Pydantic AI Harness](https://github.com/pydantic/pydantic-ai-harness)，因此设备端和服务端 Agent 呈现一致。完整能力见 [Harness](../guides/harness.md)。

## 默认安全策略

改变数据的工具先询问用户，包括文件写入、非只读 MCP 工具和沙箱外的 shell。审批显示在对话中，见 [人工参与](../guides/human-in-the-loop.md)。

## Linux 沙箱

PRoot 从原生库目录执行，因此应用必须解压原生库：

```kotlin title="build.gradle.kts"
android { packaging { jniLibs.useLegacyPackaging = true } }
```

Alpine root filesystem 约 4 MB，版本固定并校验 checksum，在首次使用时下载；`AlpineSandbox.state` 报告进度。

仓库的 `samples/in-app-agent` 是此模式的完整最小应用。

## 凭证与生命周期

从应用的已认证配置获取 provider 凭证；由本地 Ollama 切换到云端模型时提供 `apiKey`。不要在 APK 中嵌入共享的生产 provider secret，应根据部署模式使用自己的后端或用户自有凭证。

像 `samples/in-app-agent` 一样在 ViewModel 中持有 harness 和 controller。浏览器、沙箱、语音引擎等能力资源需遵循其生命周期；配置 UI 组件不会授予 Android 权限。[Harness](../guides/harness.md) 分别说明每项能力。
