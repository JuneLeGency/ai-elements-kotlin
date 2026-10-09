# 连接服务端 Agent

Agent 在服务端运行，App 负责会话、流式渲染和人工确认。先引入 ui 与对应的协议模块；AI SDK / AG-UI 都在 core 中。

## App 配置 { #app-setup }

在 `src/main/AndroidManifest.xml` 中声明网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

测试本地 HTTP 服务时，在 **debug** manifest 中允许明文流量；生产地址使用 HTTPS。

```xml title="src/debug/AndroidManifest.xml"
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:usesCleartextTraffic="true" />
</manifest>
```

## 最小界面

把下面代码放到 Compose activity 的 `setContent` 中：

```kotlin
import dev.ai.elements.ui.theme.AiElementsTheme
import dev.ai.elements.ui.chat.Chat
import dev.ai.elements.ui.chat.rememberChat
import dev.ai.elements.core.protocol.agui.AgUiBackend
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:minimal"
```

`rememberChat` 的生命周期与 composition 一致，适合原型。正式应用使用 ViewModel 持有 controller：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:viewmodel"
```

完整 imports 和 activity 见仓库的 `samples/pure-client`，同一份示例会在独立 Maven 消费工程中编译。
只在主线程调用 controller，并使用 viewModelScope。重试会重新请求一轮回复，不是从断开的 token 位置续传。
ViewModel 不负责进程回收后的持久化；应用应保存消息并通过 initialMessages 恢复。

## 启动无密钥参考服务

```bash
cd server
uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

把示例地址改为 `http://10.0.2.2:8788/api/agui`。这是 Android 模拟器访问电脑的地址；真机使用电脑的局域网 IP。
先确认 `/health` 可访问，再发送 `plan` 或需要审批的请求。该服务默认可用脚本模型，不需要模型密钥。

## 更换后端

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:backends"
```

上面展示的是可选 backend 工厂；按选择的协议添加相应依赖即可，不需要全部安装。
协议差异见[协议支持](../protocols/index.md)，审批接线见[人工确认](../guides/human-in-the-loop.md)。
