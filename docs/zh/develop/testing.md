# 测试

```bash
./gradlew testDebugUnitTest lintDebug                 # unit and recorded-fixture tests, lint
./gradlew :demo:connectedDebugAndroidTest            # UI end to end on an emulator or device
```

## 录制 fixture

协议行为使用真实实现录制的响应验证，见 [录制 fixture](reference-server.md#recording-fixtures)：Pydantic AI adapter 的 AI SDK / AG-UI 流、官方 `mcp` SDK 的 elicitation、经官方 `a2ui-core` processor 校验的 A2UI surface，以及重放给官方 ACP Kotlin SDK 的 session。

## Live test

Live test 连接真实服务，未传入地址时跳过：

```bash
cd server && uv run uvicorn main:app --port 8788     # in another terminal

./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveHarnessServerTest*' -PliveAgentServer=http://localhost:8788
./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveMcpTest*' -PliveMcp=http://localhost:8788/mcp -PliveMcpLegacy=http://localhost:8790/mcp
./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveMcpOAuthTest*' -PliveMcpOAuth=http://127.0.0.1:8791/mcp
./gradlew :ai-elements-a2a:testDebugUnitTest -PliveA2a=http://localhost:8788
./gradlew :ai-elements-acp:testDebugUnitTest --tests '*LiveAcpTest*' \
    -PliveAcpCommand="uv run --directory server python acp_agent.py" -PliveAcp=ws://localhost:8788/acp
```

## 端到端测试

`demo/src/androidTest` 驱动 Demo。需要参考服务的用例在不可达时跳过；通过 `-e agentServer http://<host>:8788` 指定地址，默认模拟器 `10.0.2.2`。`ScreenshotMatrixTest` 使用 `-e screenshots true` 捕获同一会话在各语言、浅色和深色下的截图。

!!! tip "Xiaomi / HyperOS 设备"
    后台启动 instrumentation 需要 `adb shell appops set dev.ai.elements.demo 10021 allow`，结束后以 `… 10021 default` 恢复；测试期间设备保持唤醒和竖屏。

## 性能

`:benchmark` 在 release build 上用 [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) 测量聊天关键路径：

| Benchmark | 测量内容 |
|---|---|
| `startup` | 冷启动 `StartupTimingMetric` |
| `scrollLongConversation` | 快速上下滚动长回复 |
| `streamLongAnswer` | 流式输出 24 节 Markdown |
| `openHistoryAndSwitchConversation` | 打开历史抽屉并切换到另一长会话 |

各运行两种模式：不使用 ahead-of-time compilation（全新安装），以及使用 Baseline Profile（用户实际配置）。查看 `frameDurationCpuMs` 的 P50 / P90 / P99 和 `frameOverrunMs`，后者大于 0 表示 missed frame。应在真机运行，模拟器数据不具代表性。

```bash
ANDROID_SERIAL=<device> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=dev.ai.elements.benchmark.ChatBenchmarks
```

Demo 附带 [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview)，位于 `demo/src/release/generated/baselineProfiles/`，由 `BaselineProfileGenerator` 通过相同路径生成。重大 UI 变更后重新生成：

```bash
ANDROID_SERIAL=<device> ./gradlew :demo:generateReleaseBaselineProfile
```

消费应用应针对自己的页面生成 profile，路径经过的本库代码也会包含在内。

## API 参考

```bash
./gradlew :dokkaGenerate      # build/dokka/html
```

## 首次发布及升级检查

```bash
./gradlew apiCheck testDebugUnitTest lintDebug :demo:assembleRelease
tools/check-published-consumer.sh
tools/build-docs.sh
python3 -m unittest discover -s tools -p 'test_*.py'
```

消费工程使用独立 Gradle settings 和 exclusive file Maven 仓库。导出的组件示例使用与网站完全相同的 imports 编译。网站检查验证每个库都有 Dokka symbol 页面，而不仅是索引。主动调整 baseline 前参见 [API 兼容性](api-compatibility.md)。

连接多个设备时，设置 `ANDROID_SERIAL=emulator-...` 只使用目标模拟器。Gradle task 成功不代表 live test 实际执行：检查 skipped 数量，并在协议 E2E 前启动参考服务。云端模型测试仍需 opt-in 和独立证据。

## 双语首页截图

```bash
tools/capture-release-screenshots.sh emulator-5582
```

使用 `adb devices` 中的模拟器 ID。脚本运行 `ReleaseScreenshotsTest`，以受控消息和真实 Compose 组件捕获中英文、浅深色四张 WebP。不调用模型，也不修改系统语言或夜间模式。直接 instrumentation 避免 Gradle 测试后卸载应用导致截图丢失。提交前逐张检查。

## 双语文档

英文位于 `docs/`，中文位于 `docs/zh/`。两份 Zensical 配置分别构建 `site/` 和 `site/zh/`，共用组件图、可编译 Kotlin 示例和英文 Dokka。`tools/check-docs.py` 检查语言标签、链接、截图以及每篇英文指南的中文对应页。组件翻译在 `tools/component-usage.zh.json` 修改，不直接改生成页。

`SharedFoldersTest` 从系统 picker 的当前目录导航，支持不显示设备名称的存储根目录。仍通过应用选取目录、读写真实 SAF 文件，并验证共享存储字节。

Picker 测试在模拟器上注册 UI Automator watcher，仅关闭 CI 中观察到的精确 Pixel Launcher ANR 对话框，测试后取消注册；不会关闭 Demo 的 ANR 或 crash 对话框。

中英文安装示例共用 `tools/doc-snippets/installation-*.gradle.kts`。
`tools/check-doc-parity.py` 在网站构建前检查全部 45 对页面的 fenced example 一致性。
在代码块之外翻译说明，共享依赖只修改一次。
