# GOAL — 持续开发总纲（防跑偏）

本文件是这个项目的**唯一目标来源**。每次动手前先读它，每完成一步回来对照。
进度与状态记录在 [ROADMAP.md](ROADMAP.md)，硬性规约在 [../AGENTS.md](../AGENTS.md)。
三者冲突时，优先级依次为：AGENTS.md（规约）→ GOAL.md（目标）→ ROADMAP.md（进度）。

---

## 0. 一句话目标

打造**最优秀的开源 Kotlin / Jetpack Compose AI Elements 库和 Demo**：Material 3 Expressive 的精致 UI，
只走公开协议（AI SDK / AG-UI / MCP / A2A / Agent Skills），同时支持「App 只做前端」和「App 内置 Agent
Harness」两种形态，真实链路全部跑通，并在模拟器、手机和平板上验证。

---

## 1. 不可违背的原则（每次决策前逐条自检）

1. **不自造私有协议**。所有跨进程通信必须符合公开规范的当前版本，旧版本按规范做兼容回退。
   扩展只能走规范自带的扩展点（AI SDK 的 `data-*` / metadata / preliminary output / tool approval，
   AG-UI 的 state / activity / subagent / interrupt / CUSTOM，MCP 的 `_meta` / annotations，A2A 的 metadata / DataPart）。
2. **不自己造轮子**。写代码前先查生态：官方 SDK、规范仓库的参考实现、Maven Central、社区主流库。
   已有且满足需求的就直接用；缺功能的先用官方库并**向上游贡献**，本地只写最薄的兼容层（shim），
   并在 ROADMAP 里登记为待上游合并项。只有官方库确实不可用时才自研，而且要把理由写进 ROADMAP 的决策日志。
3. **对齐 Pydantic AI Harness**。服务端直接用 Pydantic AI 和 Harness 的最新版本。端侧的等价能力沿用同样的
   工具名、参数和语义，让两端表现一致、UI 渲染一致：`delegate_task`、`load_capability`、`write_plan`…、
   `read_file`…、`run_command`…、`write_memory`…。
4. **最佳实践优先**：安全默认值（改数据的工具默认需要审批，MCP annotations 视为不可信，PKCE S256 必需，
   密钥永不进日志）、可测试、可维护、API 小而清晰、依赖可选。
5. **许可证合规**：主库为 Apache-2.0。OpenMinis（GPL-3.0）只能参考能力和交互，**不得复制代码**。
   GPL 组件（如 proot）只能作为独立进程的二进制，放在单独的 artifact 里，并在 NOTICE 中提供源码获取方式。
6. **真实验证**：每个功能都必须有针对真实实现的测试（官方 SDK 或参考服务端），能跑真实模型的就跑。
   「编译通过」不等于完成。汇报时如实说明哪些验证了、哪些没有验证。

---

## 2. 产品形态

| 形态 | Agent 运行位置 | App 职责 | 参考 |
|---|---|---|---|
| **纯前端** | 远端：Pydantic AI + Harness 服务端，或任何 AI SDK / AG-UI / A2A Agent，或模型 API | 会话、流式渲染、审批、计划、子 Agent、产物展示 | Codex App、Claude App |
| **内置 Harness** | 设备上：Kotlin Harness（能力与 Pydantic AI Harness 同构），由内置循环或 Koog 驱动 | 同上，外加本地文件、Linux 沙箱、记忆、浏览器、设备能力 | OpenMinis 的能力集（重新实现） |

两种形态共用同一套 UI 组件与 `ChatController`，在 Demo 里可以按会话切换。

---

## 3. 模块与包分层（目标形态）

### 3.1 Maven 组与 artifact

| 组 | Artifact | 职责 | 依赖 |
|---|---|---|---|
| `io.github.junelegency` | `ai-elements-core` | 消息模型、`ChatController`、`ChatBackend`/事件、协议客户端（AI SDK、AG-UI、MCP）、模型 API（OpenAI/Anthropic/Gemini/Ollama）、Agent 契约（`AgentTool`、`Capability`、`SubAgents`、`Skills`）、OAuth、配置存储 | kotlinx、OkHttp、AG-UI `kotlin-core`、`kotlin-json-patch` |
| | `ai-elements-ui` | Compose 组件、主题 | core |
| | `ai-elements-a2a`（可选） | A2A 1.0：官方 `a2a-java-sdk`，provider 与子 Agent 两种用法 | core |
| | `ai-elements-koog`（可选） | Koog Agent → `ChatBackend`；`Capability` → Koog tools | core |
| | `ai-elements-mermaid-native`（可选） | Compose Canvas 渲染 Mermaid | ui |
| `io.github.junelegency.harness` | `harness-filesystem` | 工作区 + SAF 挂载目录；`read_file` `write_file` `edit_file` `list_directory` `search_files` `find_files` `create_directory` `file_info` | core |
| | `harness-shell` | `run_command` `start_command` `check_command` `stop_command`；可插拔 `ShellRuntime` | core |
| | `harness-sandbox-proot` | Alpine Linux：上游 proot 以独立进程运行，rootfs 首次使用时下载；GPL-2 二进制单独成包 | harness-shell |
| | `harness-memory` | `write_memory` `read_memory` `delete_memory` `search_memory` | core |
| | `harness-planning` | `write_plan` `read_plan` `add_task` `update_task_status(es)` `remove_task` | core |
| | `harness-browser` | WebView 浏览、页面读取和操作 | core |
| | `harness-device` | 剪贴板、日历、联系人、通知、定位、闹钟（运行时权限） | core |
| | `harness-speech` | 语音识别与朗读 | core |
| | `harness-scheduler` | 定时和后台 Agent（WorkManager、前台服务） | core |

### 3.2 core 包分层（按关注点，禁止把协议都堆进 `backend`）

```
dev.ai.elements.core
├── model/            Message, Part, ToolPart…（与 AI SDK UIMessage 同形）
├── chat/             ChatController, ChatBackend, ChatEvent, reducer
├── protocol/aisdk/   UI Message Stream v5/v6 + v4 Data Stream
├── protocol/agui/    AG-UI 1.x（官方 kotlin-core 类型 + 1.0 垫片）
├── mcp/              MCP 客户端、OAuth 发现、McpToolset
├── provider/         openai/, anthropic/, gemini/, ollama/, mock/（端侧循环）
├── agent/            AgentTool, Capability, ToolCallContext, SubAgents, 工具循环
├── skills/           Agent Skills 解析、库、安装
├── auth/             OAuth 2.1、TokenSource、LoopbackReceiver
└── config/           ProviderProfile, ProviderStore, SecretStore
```

**规则**：上层可以依赖下层，下层不能反向依赖；协议解析不依赖 Android UI；public API 最小化，实现细节用 `internal`。

---

## 4. 工作流与验收标准

每一项都要满足「完成定义（DoD）」才能在 ROADMAP 里标 ✅。

### W1 协议客户端
- **AI SDK 6**：发送完整 `UIMessage` 历史（含 tool parts）；支持 `tool-approval-request` → `approval-responded`、
  客户端工具自动续跑、preliminary 输出、`UIMessage` 形式的子 Agent 输出、metadata 与 usage、v4 Data Stream。
  - DoD：fixture 测试覆盖每种 chunk；`LiveHarnessServerTest`（ai-sdk）全部通过。✅ 已通过：delegate / plan / skill / 审批通过与拒绝。
- **AG-UI 1.0**：官方 `kotlin-core` 类型解码；前端工具（`tools` → 待执行调用 → 后续 run）；中断与恢复
  （`RUN_FINISHED.outcome=interrupt` → `resume`）；`SUBAGENT_*` 与 `subagentRunId` 路由；STATE / ACTIVITY（JSON Patch 用 `kotlin-json-patch`）；
  steps；`RUN_FINISHED.usage`；`context`。
  - DoD：fixture 测试（含 subagent 嵌套、state delta、activity delta）；live 测试 5/5 ✅；向 ag-ui 上游提交 SUBAGENT 等缺口的 issue/PR（需用户确认后对外发布）。
- **MCP**：自研双栈（2026-07-28 无状态优先，识别旧服务后回退到 `initialize` 会话）；`Mcp-Method` / `Mcp-Name` / `x-mcp-header` 与 base64 哨兵编码；
  SSE 进度通知；分页；401 → OAuth 发现（RFC 9728 → 8414/OIDC → 7591 DCR，带 8707 resource）；工具映射为 `AgentTool`（`<server>__<tool>`，默认除 `readOnlyHint` 外都需审批）。
  - DoD：fixture 测试覆盖新旧两代、会话过期重建、进度、x-mcp-header、错误结果；live 测试对 `server/mcp_server.py`（官方 `mcp` 2.2）通过；官方 Kotlin SDK 支持 2026-07-28 后评估迁移。
- **A2A**：官方 `a2a-java-sdk` + 官方 Android HTTP 客户端，放在可选模块。
  - DoD：live 3/3 ✅（卡片、流式任务与上下文延续、作为子 Agent）；在 Demo 真机上跑通。

### W2 Agent 能力（core 内与 Harness 同构的部分）
- `SubAgents`（`delegate_task`，任意 `ChatBackend` 都可以做子 Agent，嵌套运行实时流到 UI，嵌套工具复用同一审批）。
- `Skills`（`SKILL.md` 解析规则同 harness；`load_capability`；zip 安装防 zip-slip）。
- `McpToolset`（多服务并行、超时、缓存、失败隔离、状态上报）。
- DoD：单元测试（解析、校验、路径穿越、审批传递、嵌套事件）；端侧真实模型跑通一次委派和一次技能加载。

### W2b 内置 Harness 组（见 3.1）
- 每个 artifact：能力类实现 `Capability`；工具名和参数与 Pydantic AI Harness 一致；附带 README 和测试；需要权限时给出明确的 UI 请求流程。
- 覆盖 OpenMinis 的能力集：文件、Linux 沙箱、记忆、浏览器、设备集成、语音、定时任务，全部**重新实现**。
- 沙箱 DoD：在模拟器和平板上 `run_command("uname -a && apk --version")` 成功；超时、取消、输出截断（保留尾部）行为与 harness 一致。

### W3 参考服务端（`server/`）
- Pydantic AI + Harness 最新版；`Planning`、`SubAgents`、`Skills`、MCP toolset（写操作需审批）；
  AI SDK 6（`sdk_version=6`）与 AG-UI 1.0 两个端点；MCP 服务（官方 `mcp`）；A2A 服务（官方 `a2a-sdk`，开启 0.3 兼容）；
  离线脚本模型按关键词触发每种能力（delegate / plan / skill / note / device）。
- DoD：`/health` 正常；curl 验证四类端点；有模型密钥授权时再用真实模型各跑一次。

### W4 UI 组件
- `Subagent`：子 Agent 名称、实时活动摘要、可折叠的嵌套运行，内部工具可审批；三种来源统一渲染：`delegate_task`、AG-UI subagent、AI SDK `UIMessage` 输出。
- `ToolCall`：显示 `title`（MCP 工具显示「标题 · 服务器」）、preliminary 进度、技能和 MCP 标识。
- AG-UI state 渲染（`state.plan` 用 `Plan` 组件，其余用 JSON 卡片）；activity 数据部件；steps 用 `ChainOfThought`。
- A2A Agent 卡片（扩展 `Agent` 组件：技能、提供方、能力）。
- DoD：组件测试（Compose UI test）；Gallery 有示例；深色和浅色主题、四种语言、手机和平板截图都检查过。

### W5 Demo
- 后端选择：内置 Harness、AI SDK 服务、AG-UI 服务、A2A Agent、直连模型 API（含 ChatGPT / OpenRouter 登录）。
- 设置页：MCP 服务器（添加、测试连接、工具列表开关、审批策略、OAuth 登录）；Skills（内置加 zip 导入、详情）；
  Agents（子 Agent 定义、远端 A2A Agent 卡片）；Harness 能力开关与权限。
- 输入框「能力」面板：按会话开关 MCP、技能、Agent、Harness 能力。
- 内置技能与服务端共用仓库根目录的 `/skills`。
- DoD：模拟器和小米平板（横屏、竖屏）E2E 覆盖每种后端和每种能力；真实模型链路至少跑通 Codex（已授权凭据）和本地服务端。

### W6 结构与发布质量
- 按 3.2 重排包；API 审查（public 面最小化）；README、CHANGELOG、NOTICE（a2a-java-sdk、kotlin-json-patch、AG-UI kotlin-core、proot）；Dokka；CI。
- DoD：`./gradlew testDebugUnitTest lintDebug` 通过；所有模块 `publishToMavenLocal` 成功。

---

## 5. 执行顺序（一次只推进一个工作项，完成后再开下一个）

1. W1 收尾：AI SDK 和 AG-UI 的 fixture 测试，MCP 测试（fixture 加 live）。
2. W4 的 `Subagent` 组件，以及 `ToolCall` 的 title 和进度显示。
3. W5 第一阶段：后端选择加 MCP、Skills、Agents 设置页，内置技能；模拟器 E2E。
4. W6 包分层重排（在功能稳定后、Harness 组开工之前完成，避免二次迁移）。
5. W2b Harness 组：filesystem → memory → planning → shell → sandbox-proot → browser → device → speech → scheduler。
6. `ai-elements-koog` 适配。
7. W5 第二阶段：内置 Harness 模式全能力 E2E；真机（手机和平板）验证。
8. W6 发布质量收尾。

---

## 6. 防跑偏规则

- 每开始一项：先在 ROADMAP 里把它标成 🟡，写清楚这一步的 DoD。
- 每完成一项：跑它的测试；ROADMAP 标 ✅ 并写上验证结果；有意义的节点提交一次 commit。
- 用户在中途提出的新要求：先写进本文件或 ROADMAP，再决定插队还是排队，不要悄悄改变方向。
- 发现需要自研或偏离原则时：停下来，把理由和选项写进 ROADMAP 的决策日志，需要时请用户选择。
- 不做本文件以外的「顺手重构」；需要的话先登记成工作项。
- 绝不把「编译通过」当成完成；构建输出必须看到 `BUILD SUCCESSFUL`（不能只 grep 错误行，缺 SDK 路径这类失败会被漏掉）。

---

## 7. 安全与环境约束（长期有效）

- 与用户交流用中文。
- 不打印任何密钥或 token；Codex 凭据**不得刷新**（会让 CLI 登出）；注入 App 时去掉 refresh token；真机运行后清理凭据。
- `GEMINI_API_KEY` 只用于 Gemini 测试；代理（cliproxyapi）的密钥读取需要用户明确授权（当前被安全策略拦截，需用户处理）。
- 不使用 tinker-llm-gw（9090）或其他项目的凭据。
- 不自动操作浏览器登录页，不自动点击 MIUI 安全弹窗。
- MIUI 后台弹出权限：`appops set <pkg> 10021 allow`，测试结束后恢复 `default`。
- 不提供 Claude 订阅登录。
- 对外发布（上游 PR、推送、发 issue）前先征得用户同意。

---

## 8. 验证矩阵

| 维度 | 内容 |
|---|---|
| 设备 | 模拟器（API 36）、小米平板（横/竖）、手机（需要时） |
| 协议 | AI SDK 6、AG-UI 1.0、MCP（新/旧两代）、A2A 1.0（兼容 0.3） |
| 能力 | 子 Agent、Skills、MCP 工具与审批、Planning、前端工具、Harness 各能力 |
| 模型 | 离线脚本、本地服务端、Codex（已授权）、其他需授权后再跑 |
| UI | 深色/浅色、7 套配色、字号缩放、en / zh-CN / zh-TW / ja、TalkBack |

---

## 9. 可直接用于 `/goal` 的摘要

> 按 docs/GOAL.md 推进：打造最优秀的开源 Kotlin Compose AI Elements 库和 Demo。只用公开协议
> （AI SDK 6 / AG-UI 1.0 / MCP 2026-07-28 / A2A 1.0 / Agent Skills），先查生态、用官方 SDK、不造轮子；
> 服务端基于 Pydantic AI + Harness 最新版，端侧能力与 Harness 同构；支持「纯前端」（类 Codex/Claude App）和
> 「内置 Kotlin Harness」（独立 group，多个 artifact，覆盖 OpenMinis 能力，重新实现）两种形态；
> 合理的模块和包分层；Subagent 等组件补齐，UI 精致；每项都要在模拟器和平板上用真实链路验证，
> 并在 docs/ROADMAP.md 记录进度与决策。
