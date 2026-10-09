# Demo 应用

`demo/` 是使用本库构建的完整助手应用。默认 scripted provider 可离线运行，也可连接 [参考服务](reference-server.md) 或真实 provider。

```bash
./gradlew :demo:installDebug
```

## 展示内容

- **Provider**：离线 Demo，AI SDK / AG-UI / A2A / ACP 参考服务，OpenAI、Anthropic、Gemini、OpenRouter、Ollama 和其他 OpenAI-compatible endpoint。支持时可 OAuth 登录，API key 加密保存。
- **会话**：可搜索历史、分支、checkpoint、消息队列、附件、语音输入，平板以侧栏显示历史。
- **Agent 能力**：带 OAuth / MCP Apps 的 MCP 服务器、来自仓库 `skills/` 或导入 `.zip` 的技能、子 Agent 和远程 A2A Agent、工作区、Linux 沙箱、记忆、计划、浏览器、设备工具、定时任务。
- **组件**：十个分类的真实样例，支持分类筛选。同一批样例生成 [组件图册](../components/index.md)，通过 `ComponentCatalogScreenshots` 和 `tools/build-component-docs.py` 重新生成。
- **离线流程**：建议包含 **Browse a web page** 的电脑操作和 **Generative UI (JSX form)** 的界面回复。
- **设置**：两级分组首页，Agent 组包括 provider、MCP、技能、子 Agent、设备端 Agent；App 组包括外观、文字/语言、图表。每项显示当前状态，打开详情页；provider / MCP / 子 Agent 编辑为对话框。平板同时显示首页与详情。

## 文件位置

| 文件 | 职责 |
|---|---|
| `AgentRuntime.kt` | 根据 provider 构建协议 backend 或带能力的 `AgentHarness` |
| `ChatViewModel.kt` | `ChatController`、会话持久化、模型列表 |
| `ui/ChatScreen.kt` | provider picker 顶栏、drawer、composer |
| `ui/SettingsScreen.kt` | provider、登录、MCP、能力、外观 |
| `ui/GalleryCatalog.kt` | 分类组件样例与图册说明 |
| `samples/DocsSamples.kt` | 随 Demo 编译的网站代码 |

`samples/pure-client` 和 `samples/in-app-agent` 是两种模式的单页最小应用。
