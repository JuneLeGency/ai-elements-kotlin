# 参考服务

server/ 基于 Pydantic AI、Pydantic AI Harness 与官方 SDK，提供无密钥脚本模型和可选真实模型。
它是接入示例与测试服务，不是使用本库的强制依赖。

```bash
cd server
uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

| 地址 | 用途 |
|---|---|
| /health | 健康检查 |
| /api/chat | AI SDK |
| /api/agui | AG-UI |
| /mcp | MCP |
| /.well-known/agent-card.json | A2A Agent Card |
| /a2a | A2A JSON-RPC |
| /acp | ACP WebSocket |

模拟器使用 10.0.2.2，真机使用电脑的局域网地址。协议 fixture 的录制脚本位于 server/record_fixtures.py，
录制前请阅读其参数和服务版本，避免把实际凭据写入 fixture。
真实模型、OAuth 与额外 MCP 测试服务的详细配置见仓库 server/README.md。
