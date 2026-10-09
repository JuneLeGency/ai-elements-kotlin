# 贡献

先阅读 [开放协议](../concepts/open-protocols.md)，严格遵循公开规范，不创造 wire format。根目录 `AGENTS.md` 是对人类和 AI Agent 均生效的规则。

## 约定

- Push 前构建测试：`./gradlew testDebugUnitTest lintDebug`，并在模拟器或设备上运行 `./gradlew :demo:connectedDebugAndroidTest`，见 [测试](testing.md)。
- 组件遵循 Material 3 Expressive，仅使用主题颜色和 shape、48 dp 点击区域（`Modifier.compactIconButton()`）、`AiSpacing` / `AiType` token、图标 content description、`@Immutable` 模型。只渲染聊天模型，见 [架构](../concepts/architecture.md#ui-elements-are-protocol-independent)。
- 图标使用 Material Symbols Rounded 的 `AiIcons.X`，通过 `python3 tools/generate-icons.py` 生成使用中的图标。
- 字符串位于 `ai-elements-ui`，使用 `ai_` 前缀，在 `values`、`values-zh-rCN`、`values-zh-rTW`、`values-ja` 翻译。
- 公开 API 变更记录到 CHANGELOG，尽可能保持默认参数源码兼容。
- 协议变更附真实录制 fixture，参考服务可验证时附 live test。
- 文档代码通过 `--8<--` 引用 `demo/src/main/kotlin/…/samples/DocsSamples.kt`，随 Demo 编译；用 `uvx zensical serve` 预览。
- Secret 不出现在日志、异常或测试输出。
