# 应用内 Agent

`harness-core` 的 `AgentHarness` 在设备上运行 Agent：模型绑定与能力（说明和工具）组合为 `ChatBackend`。能力与 [Pydantic AI Harness](https://github.com/pydantic/pydantic-ai-harness) 的工具名、参数和行为一致，因此设备端和服务端呈现一致。

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:in-app-agent"
```

## 能力

| 能力 | 模块 | 工具 |
|---|---|---|
| `FileSystem` | `harness-filesystem` | `read_file`、`write_file`、`edit_file`、`list_directory`、`search_files`、`find_files`、`create_directory`、`file_info` |
| `Shell` + `AlpineSandbox` | `harness-shell`、`harness-sandbox-proot` | `run_command`、`start_command`、`check_command`、`stop_command` |
| `Memory` | `harness-memory` | `write_memory`、`read_memory`、`delete_memory`、`search_memory` |
| `Planning` | `harness-planning` | `write_plan`、`read_plan`、`add_task`、`update_task_status(es)`、`remove_task` |
| `WebBrowser` | `harness-browser` | `navigate`、`snapshot`、`click`、`type_text`、`get_text`、`screenshot` 等 |
| `DeviceTools` | `harness-device` | 设备信息、剪贴板、日历、联系人、定位、闹钟、通知 |
| `Speech` | `harness-speech` | `speak`、`stop_speaking` |
| `Scheduler` | `harness-scheduler` | `schedule_task`、`list_scheduled_tasks`、`cancel_scheduled_task` |
| `Skills` | `ai-elements-core` | `load_capability` |
| `AskUser` | `ai-elements-core` | `ask_user_question` |
| `McpToolset` | `ai-elements-core` | 用户 MCP 服务器的工具 |

`Capability` 是说明与工具的组合。可实现此接口提供自己的能力，或直接传入 `AgentTool`。

## 子 Agent

- `localSubAgents` 有独立说明，可选不同模型。模型使用 `delegate_task(agent_name, task)` 委派，名单作为静态 instruction。
- `remoteSubAgents` 是现成的远程 Agent，例如 [A2A 子 Agent](../protocols/a2a.md#as-a-sub-agent)。

委派显示为包含嵌套运行的 `Subagent` 卡片。

## 技能

[Agent Skills](https://agentskills.io) 是含 `SKILL.md` 的文件夹（YAML frontmatter 与说明）。`Skills` 在 instruction 中列出技能，模型通过 `load_capability(id)` 加载，返回 `# Skill: <name>` 与正文。可从 assets、文件或 `.zip` 加载；Demo 打包仓库 `skills/`。

## 文件与文件夹

`FileSystem` 操作应用工作区及用户通过 Storage Access Framework 分享的 `SharedFolders`，挂载到 `/mnt/<name>`。写操作请求审批。

## 浏览器 { #the-browser }

`WebBrowser` 在离屏 `WebView` 中提供与 Pydantic AI Harness browser 相同的工具：`navigate`、`snapshot`（交互元素带 `aria-ref`）、`click`、`type_text`、`press_key`、`select_option`、`hover`、`wait_for`、`get_text`、`scroll`、`go_back`、`go_forward`、`screenshot`。只加载 `http(s)` 页面。

默认 `BrowserViewport.Desktop` 为 1280 × 720 CSS 像素，与 Playwright / Harness 默认 viewport 一致，以便 Agent 获取完整桌面布局。使用当前 WebView 版本的 Chrome 桌面 user agent，类似 Chrome 的 Desktop site。需要手机布局时传入 `BrowserViewport.Mobile`。

截图采用 CSS 像素，因此模型从图中读取的 `x,y` 对应 `click("x,y")`。步骤视图可在手机上全屏缩放截图。

截图有两条路径：

- **模型**：`screenshot(full_page?)` 返回 viewport 或整页 PNG；`screenshotOnNavigate = true` 为每次 `navigate` 添加截图。遵循 Harness 契约：文本为 `Screenshot captured. URL: …`，图片通过 Pydantic AI `ToolReturn.content` 发送。设备端循环在工具结果后以用户消息发送图片，适用于各模型 API。超过 5 MB 的图片返回简短错误。
- **用户**：默认 `screenshots = true`，每次改变页面的调用附带较小的 viewport JPEG，供 Agent 电脑视图显示，并勾勒操作元素；这些图片不发给模型。

自定义工具通过 `ToolCallContext.current()?.content(mediaType, url)` 向模型返回图片。

Demo 离线 Agent 提供 scripted browser-use：点击 **Browse a web page**，打开页面、`snapshot`、跟随链接并 `screenshot`，每步显示在 Agent 电脑视图。

## Linux 沙箱

`AlpineSandbox` 用 PRoot 运行 Alpine Linux，工作区位于 `/workspace`，可用 `apk add` 安装包并运行代码。约 4 MB 的 root filesystem 固定版本、校验 checksum，首次使用时下载。应用需解压原生库（`jniLibs.useLegacyPackaging = true`）。

## 定时运行

`Scheduler` 通过 WorkManager 调度后台运行。在 `Application` 实现 `ScheduledAgentHost`，应用关闭时也可通过 `runHeadless` 创建 Agent。

## 模型绑定

模型为 `ModelBinding`。内置循环使用 `ProviderProfile.model(apiKey = …)`，或通过 `ai-elements-koog` 在 JetBrains Koog 上运行循环，见 [模型 API](../protocols/model-apis.md)。
