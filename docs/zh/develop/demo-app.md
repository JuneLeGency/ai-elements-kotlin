# Demo 应用

Demo 展示完整会话、组件图册、模型与协议选择、工具审批、MCP、Skills 和端侧能力。
离线模式不需要模型密钥；远程协议演示需要[参考服务](reference-server.md)。

```bash
./gradlew :demo:installDebug
```

初次了解组件可打开 Components；实际接入则优先阅读 samples/pure-client 和 samples/in-app-agent，
它们比完整 Demo 更容易直接复制。独立 samples/published-consumer 使用 Maven 包，而不是 project 依赖。

网站中的单组件截图来自 ComponentCatalogScreenshots；首页中英文示例来自 ReleaseScreenshotsTest，
都由模拟器上的真实 Compose 组件渲染，示例内容用于展示，并非真实用户会话。
