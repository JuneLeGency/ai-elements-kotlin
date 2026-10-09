# 端侧 Harness

AgentHarness 把模型与 Capability 组合成 ChatBackend。每个能力可以提供指令、工具和每轮上下文。
工具命名与 Pydantic AI Harness 保持一致，使端侧与服务端的展示一致。

## 完整组合示例

此例需要 context、模型 key、已配置的 McpServerStore 和 ViewModel，并引入所使用的各个 harness 模块。
首次接入请先使用[最小端侧示例](../getting-started/in-app-agent.md)。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:in-app-agent"
```

## 按需添加能力

| 能力 | 用途与宿主责任 |
|---|---|
| FileSystem | 工作区与 SAF 文件访问；共享目录由用户通过系统选择器授权 |
| Memory | 保存和搜索记忆，按轮注入 MEMORY.md |
| Planning | 管理计划并渲染为 Plan |
| Shell | 执行命令，可接 AlpineSandbox；处理取消、超时与输出 |
| Browser | WebView 浏览和页面操作；管理其生命周期 |
| Device | 剪贴板、日历、联系人、定位等；由 App 请求系统权限 |
| Speech | 使用平台 TextToSpeech 朗读；处理引擎可用性 |
| Scheduler | WorkManager 后台任务；无人值守时拒绝需要人工审批的工具 |
| Skills | 从 SKILL.md 目录加载能力说明 |
| MCP | 把已配置服务器的工具加入能力集 |
| SubAgents | 通过 delegate_task 委派，嵌套运行共用审批机制 |

PRoot 作为独立可执行文件放在单独模块，首次下载 rootfs 并验证校验和。
使用沙箱时启用 jniLibs.useLegacyPackaging；这不是给任意外部 shell 自动授予权限。
