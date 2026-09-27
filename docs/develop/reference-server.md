# Reference server

`server/` is a Pydantic AI agent built from Pydantic AI Harness capabilities (Planning, SubAgents
with a researcher and a writer, Skills, an MCP toolset whose write tools need approval), served over
every protocol the library speaks. The demo app, the live tests and the recorded fixtures use it.

| Endpoint | Protocol | Built with |
|---|---|---|
| `POST /api/chat` | AI SDK 6 UI Message Stream | Pydantic AI `VercelAIAdapter` |
| `POST /api/agui` | AG-UI 1.x | Pydantic AI `AGUIAdapter` |
| `WS /acp` | Agent Client Protocol (Kotlin SDK WebSocket transport) | Pydantic AI Harness ACP adapter |
| `POST /mcp` | MCP Streamable HTTP, with an MCP App (`show_notes_board`) | official `mcp` SDK, `@modelcontextprotocol/ext-apps` |
| `/.well-known/agent-card.json`, `POST /a2a` | A2A: a researcher | official `a2a-sdk` |
| `/concierge/…` | A2A: a hotel concierge that answers with A2UI | official `a2a-sdk` |

The same agent also runs over ACP on stdio, the way editors launch agents:
`uv run python acp_agent.py`.

## Run it

```bash
cd server && uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788                 # offline scripted model, no key
AGENT_BASE_URL=… AGENT_API_KEY=… AGENT_MODEL=… uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

`AGENT_BASE_URL` is any OpenAI-compatible endpoint. `10.0.2.2` is the Android emulator's alias for
your computer; on a device, use your computer's LAN address.

Two more servers exercise the fallbacks: `legacy_mcp_server.py` (an MCP server on a 2025-xx session
revision, port 8790) and `mcp_auth_server.py` (an MCP server behind OAuth, port 8791).

## The scripted model

Without a model key the agent uses a scripted model that picks a capability by keyword in the
prompt, so every flow can be exercised offline:

| Keyword | What happens |
|---|---|
| `delegate` | delegates to the researcher sub-agent |
| `plan` | writes a plan (the Plan element) |
| `skill` | loads a skill |
| `note` | saves a note through MCP, which asks for approval |
| `device` | calls a frontend tool on the device |
| `ask` | asks the user two questions with `ask_user_question`, answered in the app |
| `hotel` | shows an A2UI booking form; its **Book** action comes back as `book` |
| anything else | asks the clock tool and answers with Markdown and a Mermaid diagram |

## Recording fixtures

`server/record_fixtures.py` records the protocol fixtures the client tests replay, from this server
and the official SDKs:

```bash
uv run python record_fixtures.py http://localhost:8788          # AI SDK, AG-UI, A2UI, MCP
uv run python record_fixtures.py http://localhost:8788 acp      # Agent Client Protocol
```
