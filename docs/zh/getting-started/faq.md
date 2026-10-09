# 常见问题

## 能不能只使用 UI？

可以。ai-elements-ui 只依赖 ai-elements-chat，不依赖网络或 Agent 模块。可以渲染单个组件，也可以自己实现 ChatBackend。

## 必须使用 Pydantic AI 吗？

不需要。仓库中的 Pydantic AI 服务是参考实现与测试基础设施。兼容 AI SDK、AG-UI、A2A、ACP 的服务或你自己的 backend 都可以接入。

## 为什么连不上本地服务？

检查 INTERNET 权限、debug 明文 HTTP 配置、服务监听地址和防火墙。模拟器通过 10.0.2.2 访问电脑，不是通过自己的 localhost。
请先测试服务的 /health，再检查聊天接口。

## 工具为什么一直等待审批？

使用 Chat(controller)，或把 Conversation.onToolDecision 接到 controller.respondToApproval。
表单还需转发 onInputResponse。只展示卡片而不接回调，无法恢复后端等待中的工具。

## ViewModel 能处理进程回收吗？

不能。它保留配置变化期间的状态；进程重建后的消息持久化与恢复由应用负责，通过 initialMessages 传入。

## 为什么有的模块要求 API 26？

可选能力使用较新的平台 API，具体见安装页的逐模块表格。构建工具链要求与运行时 minSdk 是两回事。

## 依赖都是稳定版本吗？

不是。当前 Compose 和 Material 3 Expressive 使用 alpha 版本，安装页列出了实测版本。本库自己的稳定 API 仍遵守兼容性政策。

## 图册里的代码可以直接使用吗？

添加对应模块与 imports，在 AiElementsTheme 下调用，并提供示例函数参数要求的状态与回调。
所有 65 个场景的代码都会导出到独立工程，用 Maven 包编译。截图来自真实 Android Demo，不是网页模拟图。
