# 常见问题

## 可以只使用 UI 吗？

可以。`ai-elements-ui` 只依赖 `ai-elements-chat`，不依赖网络或 Agent 模块。可显示单个组件，或提供自己的 `ChatBackend`。

## 必须使用参考服务或 Pydantic AI 吗？

不需要。它们是示例及测试基础设施。兼容的 AI SDK / AG-UI / A2A / ACP 服务、模型 API、自定义 backend 都能提供消息。参见 [选择接入方式](choose.md)。

## 为什么本地端点连接失败？

声明 `INTERNET`；测试 HTTP 服务时仅在 debug manifest 放开明文流量。模拟器通过 `10.0.2.2` 访问电脑，不是自己的 `localhost`。检查 `/health`、服务 bind address 和防火墙，见 [App 配置](pure-client.md#app-setup)。

## 为什么工具一直等待审批？

使用 `Chat(controller)`，或把 `Conversation.onToolDecision` 转发到 `controller.respondToApproval`，表单还需转发 `onInputResponse`。只显示工具卡片不能回答 backend。

## ViewModel 会在进程死亡后恢复会话吗？

不会。它只保留配置变化中的状态。应用需持久化消息，在进程重建后通过 `initialMessages` 初始化 controller。

## 为什么有些模块 minSdk 26？

可选集成和能力使用较新的平台 API。参见 [逐模块安装表](installation.md)。构建工具链要求与运行时 minSdk 是两回事。

## 依赖全部是稳定版吗？

不是。当前 Material 3 Expressive 和 Compose 包含 alpha 版本，安装页列出确切版本。这不影响本库稳定公开 API 的 [兼容性策略](../develop/api-compatibility.md)。

## 能直接复制组件示例吗？

可以。添加对应模块及页面打印的 imports，包入 `AiElementsTheme`，提供参数中的状态和回调。同一示例在独立 Maven 消费工程中编译；截图展示 Demo 场景，不是独立 Web 实现。
