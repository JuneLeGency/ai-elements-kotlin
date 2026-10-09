# A2A

ai-elements-a2a 基于官方 a2a-java-sdk，使用 A2A 1.0 JSON-RPC binding，并兼容 0.3。
可以把远端 Agent 当作聊天后端，也可以当作端侧 Agent 的子 Agent。

该模块要求 minSdk 26 和 core library desugaring，完整配置见[安装](../getting-started/installation.md)。

## 会话与任务

每轮发送最新用户消息及 contextId。前一轮任务处于 input-required 或 auth-required 时，回答会继续该任务。
contextId / taskId 保存在 Message.metadata 的 a2a 字段中。
产物和最终消息映射为文本、文件与数据；working 进度可以展示为 task。
failed、rejected、canceled 以错误结束当前轮次。

A2UI 使用 application/a2ui+json 数据片段及 A2A 绑定返回用户 action。

## 作为子 Agent

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:a2a-subagent"
```

cardOrNull 带超时并短暂缓存失败，避免不可达的远端 Agent 阻塞委派配置。
asSubAgent 将卡片变成可委派的 SubAgent。Agent 组件可展示卡片中的说明和技能。
