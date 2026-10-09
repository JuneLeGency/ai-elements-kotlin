# A2A

`ai-elements-a2a` 使用官方 `a2a-java-sdk` 连接 [Agent2Agent](https://a2a-protocol.org) Agent，支持 A2A 1.0 JSON-RPC binding，并兼容 0.3 Agent。远程 Agent 可以作为聊天 provider，也可以作为自己 Agent 的子 Agent。

```kotlin
val agent = A2aAgent("https://agents.example.com")   // card at /.well-known/agent-card.json
ChatController(backend = { _ -> A2aBackend(agent) }, scope = viewModelScope)
```

SDK 使用 Java 17 库 API，因此需要 [core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)。

## 作为 provider

每轮发送最后一条用户消息及会话的 A2A `contextId`。如果上一轮任务处于 `input-required` 或 `auth-required`，回答会继续同一个任务。两个 ID 保存在回复的 `Message.metadata` 的 `a2a` 字段中。

- Artifact 和 Agent 的最终消息映射为文本、文件和数据 part。
- `working` 状态下的进度消息显示为 `data-task` 清单。
- `failed`、`rejected`、`canceled` 以错误结束本轮。
- A2UI part（`application/a2ui+json`）通过 [生成式 UI](../guides/generative-ui.md) 渲染；用户操作以 A2UI A2A part 返回。

## 作为子 Agent { #as-a-sub-agent }

`A2aAgent.asSubAgent(card)` 将远程 Agent 转换为 `SubAgent`。应用内 Agent 可通过 `delegate_task` 委派任务，运行过程显示为 `Subagent` 卡片：

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:a2a-subagent"
```

`cardOrNull()` 在后台获取 Agent card，有超时并短期缓存失败，避免不可达的 Agent 阻塞一轮对话。

## Agent card

`Agent` 组件显示 A2A Agent card 的描述和技能。
