# AI Elements agent server

FastAPI + PydanticAI agent with tools (`get_current_time`, `calculate`), streamed to the app over
the Vercel AI SDK v5 **UI Message Stream** protocol via PydanticAI's `VercelAIAdapter`.

```bash
uv sync
uv run uvicorn main:app --host 0.0.0.0 --port 8788
```

| env | default | |
|---|---|---|
| `AGENT_MODEL` | `demo` | `demo` = offline scripted model; otherwise any model id on the upstream |
| `AGENT_BASE_URL` | `http://localhost:8317/v1` | OpenAI-compatible upstream (e.g. CLIProxyAPI) |
| `AGENT_API_KEY` | – | upstream key; stays on the server |

`POST /api/chat[?model=...]` takes `{trigger, id, messages: UIMessage[]}`; `GET /health`.
