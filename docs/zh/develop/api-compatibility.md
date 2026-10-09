# 版本与 API 兼容性

从首个公开版本起，发布模块中未标记实验性的 public Kotlin 声明就是受支持的接口，**0.x 版本也遵守这个承诺**。
patch/minor 不删除或重命名稳定接口。旧入口通过 Deprecated 提示和转发实现保留，major 版本也不是自动删除接口的许可。

## 稳定接口与实验性接口

chat、core、ui、可选集成和 harness 的公开接口遵循同一政策。
原生 Mermaid 使用 ExperimentalNativeMermaidApi 显式 opt-in，布局和接口可以在 minor 中演进。
internal、private、Demo 和参考服务的实现细节不构成库的兼容承诺。

## 数据模型同样需要兼容

保留 Message、ToolPart、ChatState 等 data class 的现有构造参数顺序、默认调用、copy 和 componentN。
即使添加带默认值的参数，也可能破坏已编译调用者。
公开 sealed 层级与 enum 的新分支会影响用户的穷尽 when，也必须经过兼容性设计。
本地持久化要继续读取旧字段名、SerialName 和默认值；应用仍负责自己的存储版本与迁移。

## 发布检查

20 个库都保存官方 Binary Compatibility Validator 生成的 API 基线。`./gradlew apiCheck` 拦截未经审核的差异。
有意新增接口时，先审核源码、二进制与行为兼容性，再更新基线及 CHANGELOG。
仅仅重新 apiDump 不代表破坏性更改可以接受。

自动检查不能证明全部行为或源码兼容性，仍需消费工程和实际交互测试。
详细维护流程见[英文兼容政策](/ai-elements-kotlin/develop/api-compatibility/)。
