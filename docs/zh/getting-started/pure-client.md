# 快速开始：纯客户端

Agent 在自己的服务端、远程 A2A Agent 或 ACP 编码 Agent 中运行，应用渲染会话。需要 `ai-elements-ui` 和对应 backend；AI SDK / AG-UI 使用 `ai-elements-core`。

## App 配置 { #app-setup }

在 `src/main/AndroidManifest.xml` 声明网络访问：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

仅测试本地参考服务时，在 debug manifest（`src/debug/AndroidManifest.xml`）允许 HTTP：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:usesCleartextTraffic="true" />
</manifest>
```

生产端点使用 HTTPS。模拟器用 `10.0.2.2` 访问电脑，`localhost` 指模拟器自己。连接失败时先检查服务 `/health`、bind address 和防火墙，再调整 backend。

## 一个 composable

在 Compose activity 的 `setContent` 中使用这些 imports：

```kotlin
import dev.ai.elements.ui.theme.AiElementsTheme
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.chat.rememberChat
import dev.ai.elements.core.protocol.agui.AgUiBackend
```

`Chat` 将会话、工具、审批、子 Agent、计划和输入连接到 `ChatController`：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:minimal"
```

`rememberChat` 向 backend 提供 `approver`，在对话中显示人工审批及表单。

## ViewModel { #in-a-viewmodel }

正式应用在 `ViewModel` 中持有 controller，避免配置变化丢失会话。需要更多控制时自行组合组件：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:viewmodel"
```

服务端的子 Agent、计划、共享状态和活动自动渲染。例如 `delegate_task` 或 AG-UI `SUBAGENT_*` 显示为带嵌套运行的 `Subagent` 卡片。

## 选择 backend

所有 backend 都是 `ChatBackend`，按 Agent 协议选择：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:backends"
```

[协议](../protocols/index.md) 对比各协议及服务端接入方式。

## 连接参考服务

仓库的 [参考服务](../develop/reference-server.md) 使用 Pydantic AI，通过全部协议提供服务，scripted model 不需要 API key：

```bash
cd server && uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

模拟器使用 `http://10.0.2.2:8788/api/agui`，真机使用电脑局域网地址。

`samples/pure-client` 是此模式的完整最小应用。

## 生命周期与错误

`rememberChat` 作用域为 composition，适合原型。ViewModel 保留配置变化中的状态，但不跨进程死亡：应用需保存消息，并在创建 controller 时通过 `initialMessages` 恢复。

在主线程调用 controller，使用 `viewModelScope`。Stop 取消本轮，传输错误写入 `ChatState.error`。Retry 发起新一轮，不从上一条流中途重连，见 [保存与回放](choose.md#4-storing-compacting-replaying-and-reconnecting)。
