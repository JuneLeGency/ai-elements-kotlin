# API 稳定性与兼容性

首个公开 release 起，发布模块中未标记的 public Kotlin 声明均为受支持的 API，包括 0.x。升级需保留既有调用源码、已编译客户端和文档行为。Patch / minor 不删除或重命名 API。废弃入口保留转发实现；major 也不是自动删除的许可。实验性 API 必须在发布前用 `RequiresOptIn` 标记，不能在用户采用后追溯降级。

## 稳定范围

| 范围 | 契约 |
|---|---|
| `ai-elements-chat` | 消息与 part 模型、controller、backend、事件、审批 |
| `ai-elements-core` | 协议客户端、provider backend、能力、工具、认证、配置 |
| `ai-elements-ui` | 公开组件、state factory、主题、renderer 扩展点 |
| 可选集成和 harness | 公开声明遵循相同政策；外部服务行为遵循其版本化协议 |
| `@ExperimentalNativeMermaidApi` | 原生 Mermaid 需 opt-in，API 和布局可在 minor 改变 |
| `internal`、private、Demo / server 实现 | 不属于受支持的库接口 |

Compose / Material 3 的 alpha 依赖不豁免本库的兼容审核。支持的消费工具链见 [安装](../getting-started/installation.md)，提高工具链或 minSdk 需发布审核。本库仅支持 Android，不是 Kotlin Multiplatform。

## 已审核的模型决策

保留 `Message`、`ToolPart`、`ChatState` 等公开 data class，用于不可变快照和便捷 `copy`。构造参数顺序、默认调用签名、`copy`、`componentN` 均属于契约。不能假定新增一个带默认值的构造属性就是兼容变更；应设计独立扩展类型或方法，或保留所有旧签名并验证旧编译客户端。

公开 sealed event / part 层级和 enum 同样冻结：新增分支会破坏用户穷尽 `when`。协议新增功能应按语义映射到现有中立模型和 `DataPart` / metadata 扩展点。新的基础模型需明确 API 设计与迁移审核，不自动新增 sealed subtype。不能把这些映射作为自定义 wire protocol。

序列化 `Message` 适合应用自有本地存储。保留旧字段名、`SerialName` 和默认值的可读性。应用负责存储 schema 版本与迁移，Kotlin serializer 不承诺公开网络格式。修改持久化模型需添加旧记录 fixture。

## 变更审核

1. 运行 `./gradlew apiCheck`；各库有 `api/<artifact>.api` baseline。
2. 有意新增时审核 diff、named argument、默认值、返回类型、overload resolution 和接口实现者兼容性；更新 CHANGELOG 与指南，再运行 `./gradlew apiDump` 并审核 diff。
3. 替换的函数保留 WARNING 级别的 `@Deprecated(message, replaceWith = ...)` 并转发。不能自动升级到 ERROR / HIDDEN，即使 binary symbol 存在也会破坏源码兼容。
4. 运行 `tools/check-published-consumer.sh` 和行为测试。变更已发布签名时，还需用新 artifact 运行旧编译客户端。只更新 baseline 不代表破坏性变更可接受。

检查使用 JetBrains 官方 Binary Compatibility Validator，对 AGP 公开 release AAR 中提取的 `classes.jar` 检查所有发布模块，包括可选模块。它适配 AGP 9 内置 Kotlin 的发现机制，不自定义 ABI parser；不能证明行为或全部 Kotlin 源码兼容，因此仍需人工审核。

[JetBrains 兼容性指南](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html) 解释默认参数、data class、返回类型和废弃策略。
