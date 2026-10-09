# 执行步骤与回放

浏览网页、运行命令、编辑文件等工具会形成 Agent 工作步骤。回复中的预览卡可打开工作面板，展示截图、终端、差异和时间线。

## 分类来自模型字段

ToolPart.category 和 location 在上游声明或由协议映射；UI 不通过工具名字猜测行为。
端侧工具可以实现 categoryFor / locationFor，截图通过文件 part 传递。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:categories"
```

## 自适应布局

AgentComputerScaffold 按自身容器宽度选择侧栏或底部抽屉，默认分界为 720 dp。
已有自适应布局时使用 Hosted，把面板放入自己的辅助区域。

## 回放与保存

AG-UI 可以通过 AgUiEventLog 保存事件并逐事件回放；ACP 使用代理自己的 session/load 记录。
普通消息历史也可用来浏览已保存的步骤，但不等于完整事件回放。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:replay"
```

应用决定日志存放位置、保留时间与删除策略。只在不再需要原始逐事件回放时压缩日志；压缩可能把状态变化整理到运行末尾。
断线后的 Retry 会重新请求，不代表从丢失的流位置自动续传。
