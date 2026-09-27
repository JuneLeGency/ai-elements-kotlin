# AI Elements agent server

FastAPI + PydanticAI agent with tools (`get_current_time`, `calculate`), streamed to the app over
two protocols via PydanticAI's UI adapters:

- `POST /api/chat[?model=]` — Vercel AI SDK v5 **UI Message Stream** (`VercelAIAdapter`), accepts image `file` parts
- `POST /api/agui[?model=]` — **AG-UI** (`AGUIAdapter`)
- `POST /a2a` — **A2A** research agent (official `a2a-sdk`; card at `/.well-known/agent-card.json`)
- `POST /concierge/a2a` — **A2A** hotel concierge that answers with **A2UI** v1.0 (A2UI A2A extension;
  card at `/concierge/.well-known/agent-card.json`)

Elicitation: the MCP server's `book_table` asks the user for the party size, time and seating
mid-call (official SDK `Resolve` + `Elicit`: an `InputRequiredResult` on 2026-07-28, `elicitation/create`
on older sessions); `legacy_mcp_server.py`'s `confirm_action` does the same on a 2025-xx session.

MCP Apps: the MCP server's `show_notes_board` tool has a view (`ui://notes/board`,
`mcp_apps/notes_board.html`, built on the official `@modelcontextprotocol/ext-apps` SDK from its CDN);
`board_notes` is only for that view (`visibility: ["app"]`), so the server's own agent never sees it.
`mcp_apps/record_fixtures.mjs` records the client's MCP Apps fixture from the official SDKs
(`cd mcp_apps && npm install && npm run record`).

Generative UI: say "hotel" and the agent sends an A2UI booking form (`a2ui_demo.py`) on the
protocol's A2UI binding — an AG-UI `a2ui-surface` activity, an AI SDK `data-a2ui` part or an A2A
`application/a2ui+json` DataPart. Its **Book** action comes back on the same binding and the agent
confirms it with `confirm_booking`. `record_fixtures.py` checks each form with the official
`a2ui-core` message processor (strict validation).

```bash
uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

| env | default | |
|---|---|---|
| `AGENT_MODEL` | `demo` | `demo` = offline scripted model; otherwise any model id on the upstream |
| `AGENT_BASE_URL` | `http://localhost:8317/v1` | OpenAI-compatible upstream (e.g. CLIProxyAPI) |
| `AGENT_API_KEY` | – | upstream key; stays on the server |

`GET /health` reports the default model and upstream. For a free local model:
`AGENT_BASE_URL=http://localhost:11434/v1 AGENT_MODEL=qwen3:4b`.
