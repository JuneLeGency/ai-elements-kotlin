# 在 App 内运行 Agent

AgentHarness 把模型 API 和能力组合成 ChatBackend，使用与服务端 Agent 相同的聊天组件。
先完成[安装](installation.md)和[网络权限配置](pure-client.md#app-setup)。

## 从文件和计划开始

```kotlin
dependencies {
    // 还需安装页中的 BOM、activity 和 lifecycle 依赖。
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency.harness:harness-core")
    implementation("io.github.junelegency.harness:harness-filesystem")
    implementation("io.github.junelegency.harness:harness-planning")
}
```

使用 minSdk 26。下面连接电脑上的 Ollama；示例要求该服务可从模拟器访问，并已安装 `qwen3:4b`。
你可以替换模型与地址。导入以下符号：

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

在 activity 的 setContent 内调用 AppAgentScreen()。可以先要求模型制定计划或读取工作区文件。
完整应用位于 `samples/in-app-agent`，不需要一开始就准备 MCP、Skills 或 Linux 沙箱。

## 扩展能力与生命周期

[Harness 指南](../guides/harness.md)列出可选能力。文件写入等更改默认走审批；系统权限仍需由 App 处理。
沙箱模块需要提取原生库，并在首次使用时下载经过校验的 Alpine rootfs。

controller 和 harness 由 ViewModel 管理。浏览器、语音等能力按各自生命周期释放。
切换到远端模型 API 时使用应用的安全凭据来源或用户自己的凭据，不要在 APK 中嵌入共用生产密钥。
