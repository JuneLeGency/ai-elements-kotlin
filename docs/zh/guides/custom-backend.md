# 实现自己的后端

只需实现 ChatBackend.stream(history)，返回一轮回复的冷 Flow<ChatEvent>。history 是完整历史，最后一条通常是新用户消息。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:custom-backend"
```

该例的 MyClient 代表你的应用客户端，需要替换为真实实现。

- 取消 flow 收集时，要取消底层网络或模型请求。
- 错误可以抛出，由 controller 变成错误状态；也可使用协议映射对应的 Error 事件。
- 文本 delta 使用稳定 id，工具的输入与输出共享调用 id。
- 工具类型和来源在上游填写，不让 UI 根据名称猜测。
- ChatEvent 和 Message 是进程内模型，不要直接作为自造网络协议发送。

同一个 backend 可以供 Chat、单独组合的界面或子 Agent 使用；业务持久化仍由应用负责。
