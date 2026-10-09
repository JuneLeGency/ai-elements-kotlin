# 参考服务

`server/` 使用 Pydantic AI 和 Pydantic AI Harness 的 Planning、SubAgents（researcher 与 writer）、Skills，以及写工具需要审批的 MCP toolset，通过本库支持的各协议提供服务。Demo、live test 和录制 fixture 均使用它。

| 端点 | 协议 | 实现 |
|---|---|---|
| `POST /api/chat` | AI SDK 6 UI Message Stream | Pydantic AI `VercelAIAdapter` |
| `POST /api/agui` | AG-UI 1.x | Pydantic AI `AGUIAdapter` |
| `WS /acp` | ACP，Kotlin SDK WebSocket transport | Pydantic AI Harness ACP adapter |
| `POST /mcp` | MCP Streamable HTTP，包含 `show_notes_board` MCP App | 官方 `mcp` SDK、`@modelcontextprotocol/ext-apps` |
| `/.well-known/agent-card.json`、`POST /a2a` | A2A researcher | 官方 `a2a-sdk` |
| `/concierge/…` | 返回 A2UI 的 A2A 酒店 concierge | 官方 `a2a-sdk` |

同一个 Agent 也支持编辑器启动 Agent 时使用的 stdio ACP：`uv run python acp_agent.py`。

## 启动

```bash
cd server && uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788                 # offline scripted model, no key
AGENT_BASE_URL=… AGENT_API_KEY=… AGENT_MODEL=… uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

`AGENT_BASE_URL` 可指向任意 OpenAI-compatible endpoint。Android 模拟器的 `10.0.2.2` 指向电脑；真机应使用电脑的局域网地址。

回退功能另由两个服务验证：`legacy_mcp_server.py` 使用 2025-xx session 修订版，端口 8790；`mcp_auth_server.py` 提供 OAuth 保护的 MCP，端口 8791。

## Scripted model

未配置模型 key 时，Agent 按 prompt 关键词选择能力，可离线验证全部流程：

| 关键词 | 行为 |
|---|---|
| `delegate` | 委派给 researcher 子 Agent |
| `plan` | 写计划，显示 Plan 组件 |
| `skill` | 加载技能 |
| `note` | 通过 MCP 保存笔记并请求审批 |
| `device` | 调用设备前端工具 |
| `ask` | 使用 `ask_user_question` 提两个问题，在应用中回答 |
| `browse` | 使用 `browse` 工具模拟打开页面并返回截图 |
| `hotel` | 显示 A2UI 预订表单，Book 操作返回为 `book` |
| 其他 | 调用时钟工具，返回 Markdown 和 Mermaid 图 |

## 录制 fixture { #recording-fixtures }

`server/record_fixtures.py` 从参考服务与官方 SDK 录制客户端测试使用的协议 fixture：

```bash
uv run python record_fixtures.py http://localhost:8788          # AI SDK, AG-UI, A2UI, MCP
uv run python record_fixtures.py http://localhost:8788 acp      # Agent Client Protocol
```
