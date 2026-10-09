# 构建与验证

```bash
./gradlew apiCheck testDebugUnitTest lintDebug :demo:assembleRelease
tools/check-published-consumer.sh
tools/build-docs.sh
python3 -m unittest discover -s tools -p 'test_*.py'
```

API 检查覆盖 20 个库。独立消费工程验证 Maven 坐标、传递依赖和 R8，并编译两种语言共用的 65 个组件示例。
站点检查验证语言、链接、图片和实际 Dokka 符号页，防止只有空索引却构建成功。

## 模拟器与 live 测试

```bash
ANDROID_SERIAL=emulator-5582 ./gradlew :demo:connectedDebugAndroidTest
```

设备 id 以 adb devices 的实际输出为准。先启动参考服务；需要真实模型或额外 OAuth 服务的测试为 opt-in，
Gradle 成功不代表这些测试没有跳过。应检查测试数量、失败与 skipped 统计。
发布工作流会执行完整验证；文档改动不应被表述为重新验证了所有模型能力。

## 截图

首页与 README 的四张中英文、明暗主题截图可以通过以下命令重新生成：

```bash
tools/capture-release-screenshots.sh emulator-5582
```

脚本直接运行 instrumentation 后取回图片，避免 Gradle 测试结束卸载 App 时删除截图。
ComponentCatalogScreenshots 接收 catalog=true，ReleaseScreenshotsTest 接收 releaseScreenshots=true。
图片由实际组件渲染，检查布局后才更新到 docs/assets。
详细基准测试、完整 fixture/live 参数和设备说明见[英文测试指南](/ai-elements-kotlin/develop/testing/)。

`SharedFoldersTest` 根据系统文件选择器当前目录导航，支持直接打开存储根目录且不显示设备名的界面。
测试仍通过 App 选择目录，使用 SAF 读取和修改真实文件，并核对共享存储中的最终内容。

选择器测试仅在模拟器注册 UI Automator watcher，处理 CI 中实际观察到的 Pixel Launcher ANR 弹窗，
用例结束后注销。Demo 自身的 ANR 或崩溃弹窗不会被关闭，仍作为失败信号保留。
