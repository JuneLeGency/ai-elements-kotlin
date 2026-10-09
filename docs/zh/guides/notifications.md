# 运行通知与 Live Updates

Agent 可能运行数分钟。用户离开应用后仍应能看到进度，并在回复就绪时收到通知。本库使用 Android 的标准通知能力跟踪运行：

- **运行中**：持续通知跟随运行。Android 16 及以上使用 [Live Update](https://developer.android.com/develop/ui/views/notifications/live-update)，通过 `ProgressStyle` 提升到状态栏 chip 和锁屏（对应 iOS Live Activities / Dynamic Island）。显示当前操作（例如“Running command · ./gradlew test”）、计划进度分段、已用时间、chip 简短文本（“2/5”“Step 3”“Review”）和 **Stop** 操作。较旧 Android 显示相同内容的普通进度通知。
- **等待用户**：显示例如“Waiting for your approval · Save note”，提醒一次，提供 **Review** 打开聊天。审批不在通知中直接回答，用户需先查看调用内容。
- **结束时**：用户不在应用内时，“reply ready” 通知显示回复开头或停止原因。

## 运行进度

`ai-elements-ui` 的 `AgentProgress.of(chatState)` 只读取模型，因此所有协议和设备端 Agent 的行为一致。

| 阶段 | 条件 |
|---|---|
| `THINKING` | 请求已发送或模型正在推理 |
| `WORKING` | 工具调用执行中，`tool` 表示当前步骤 |
| `WRITING` | 回复文本正在流式输出 |
| `NEEDS_APPROVAL` / `NEEDS_INPUT` | 等待审批或用户回答 |
| `DONE` / `FAILED` | 本轮结束或因错误停止 |

`plan` 来自 `plan` 数据 part 或共享状态中的 `plan`（Pydantic AI Harness `Planning`、AG-UI state）；`steps` 统计工具调用，`reply` 是当前回复文本。也可用于 widget 或手表 tile。`ToolPart.activityLabel(resources)` 在 composition 外提供步骤文本。

## 通知接入

可选模块 `ai-elements-notifications` 的 `AgentRunNotifications` 根据 `AgentProgress` 构建通知：

```kotlin
val notifications = AgentRunNotifications(context).also { it.ensureChannel() }
val progress = AgentProgress.of(controller.state.value) ?: return
notifications.post(RUN_ID, notifications.running(progress, title = "Fix the build", contentIntent = openChat, stopIntent = stop))
// …when the run ends and the app is in the background:
notifications.post(DONE_ID, notifications.finished(progress, title = "Fix the build", contentIntent = openChat))
```

模块 manifest 声明 `POST_NOTIFICATIONS` 和 Live Update 所需的 `POST_PROMOTED_NOTIFICATIONS`；未引入模块的应用不会获得这些权限。应用需在合适的场景请求运行时 `POST_NOTIFICATIONS`，Demo 在用户首次发送消息时请求。

## 后台持续运行

运行是用户主动发起并期待完成的工作。应用进入后台后，Android 或厂商电池管理可能停止进程。标准做法是 foreground service，并显示运行通知。Demo 的 `AgentRunService`：

- 按 Android 要求，在应用前台且运行开始时启动。
- 使用 `dataSync` foreground service 类型。
- 随聊天状态更新通知。
- 运行结束后移除持续通知，用户在其他页面时留下“reply ready”，然后停止服务。

服务类型和运行时长属于应用策略，因此服务实现位于 Demo；库提供进度模型和通知。

## 厂商界面

Android 16 Live Updates 是平台标准，厂商皮肤可在自己的状态栏区域显示 promoted notification。一些厂商另有私有 API，例如 Xiaomi HyperOS focus notifications。本库不使用私有 API，需要时由应用自行接入。
