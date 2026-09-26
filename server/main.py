"""AI Elements agent server.

A FastAPI service running a PydanticAI agent (with tools) and streaming its run
to the Android demo over the **Vercel AI SDK v5 UI Message Stream protocol**
(SSE, `data: {"type": "text-delta", ...}`), via PydanticAI's built-in
`VercelAIAdapter`.

Upstream model (any OpenAI-compatible endpoint, e.g. CLIProxyAPI):

    AGENT_BASE_URL   default http://localhost:8317/v1
    AGENT_API_KEY    upstream key (never sent to the app)
    AGENT_MODEL      default model id; the client may override with ?model=
                     use "demo" for an offline scripted model (no key needed)

Run:  uv run uvicorn main:app --host 0.0.0.0 --port 8788
"""

from __future__ import annotations

import ast
import asyncio
import operator
import os
from collections.abc import AsyncIterator
from datetime import datetime
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from fastapi import FastAPI, Request
from fastapi.responses import Response
from pydantic_ai import Agent
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart, ToolReturnPart
from pydantic_ai.models import Model
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel
from pydantic_ai.models.openai import OpenAIChatModel
from pydantic_ai.providers.openai import OpenAIProvider
from pydantic_ai.ui.vercel_ai import VercelAIAdapter

BASE_URL = os.environ.get("AGENT_BASE_URL", os.environ.get("CLIPROXY_BASE_URL", "http://localhost:8317/v1"))
API_KEY = os.environ.get("AGENT_API_KEY", os.environ.get("CLIPROXY_API_KEY", ""))
DEFAULT_MODEL = os.environ.get("AGENT_MODEL", os.environ.get("CLIPROXY_MODEL", "demo"))

INSTRUCTIONS = """\
You are a helpful assistant inside an Android chat app that renders GitHub-flavoured
Markdown (headings, lists, tables, fenced code with a language tag) and Mermaid
diagrams (```mermaid fenced blocks). Prefer a Mermaid diagram when the user asks
about flows, architectures, sequences or relationships. Use the tools when they
help: `get_current_time` for dates/times, `calculate` for arithmetic.
Answer in the user's language.
"""

agent = Agent(instructions=INSTRUCTIONS, name="ai-elements-agent")


@agent.tool_plain
def get_current_time(timezone: str = "UTC") -> str:
    """Return the current date and time in an IANA timezone, e.g. "Asia/Shanghai"."""
    try:
        tz = ZoneInfo(timezone)
    except ZoneInfoNotFoundError:
        return f"Unknown timezone: {timezone}"
    return datetime.now(tz).strftime("%Y-%m-%d %H:%M:%S %Z (%A)")


_OPS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
    ast.USub: operator.neg,
    ast.UAdd: operator.pos,
}


def _eval(node: ast.AST) -> float:
    if isinstance(node, ast.Constant) and isinstance(node.value, int | float):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in _OPS:
        if isinstance(node.op, ast.Pow) and abs(_eval(node.right)) > 100:
            raise ValueError("exponent too large")
        return _OPS[type(node.op)](_eval(node.left), _eval(node.right))
    if isinstance(node, ast.UnaryOp) and type(node.op) in _OPS:
        return _OPS[type(node.op)](_eval(node.operand))
    raise ValueError("unsupported expression")


@agent.tool_plain
def calculate(expression: str) -> str:
    """Evaluate an arithmetic expression, e.g. "(3 + 4) * 12 / 5"."""
    try:
        return str(_eval(ast.parse(expression, mode="eval").body))
    except Exception as exc:  # noqa: BLE001 - reported back to the model
        return f"error: {exc}"


# --- Offline "demo" model -------------------------------------------------------
# A scripted FunctionModel so the whole app ↔ server ↔ agent ↔ tool chain can be
# exercised without an upstream key: it calls `get_current_time`, then answers
# with Markdown + a Mermaid diagram.

DEMO_ANSWER = """\
## Agent run complete

The request went **app → agent server → PydanticAI agent → tool → model** and
back as a UI Message Stream. The tool reported: `{time}`.

| Stage | Protocol |
|---|---|
| App → server | `POST /api/chat` (AI SDK `UIMessage[]`) |
| Server → app | SSE UI Message Stream v1 |
| Agent → tools | PydanticAI function tools |

```mermaid
sequenceDiagram
    participant App as Android app
    participant S as Agent server
    participant A as PydanticAI agent
    participant T as get_current_time
    App->>S: POST /api/chat
    S->>A: run(messages)
    A->>T: call()
    T-->>A: {time}
    A-->>S: text deltas
    S-->>App: SSE text-delta
```

```kotlin
val controller = ChatController(backend)
controller.send("Hello")
```
"""


async def _demo_stream(messages: list[ModelMessage], info: AgentInfo) -> AsyncIterator[str | dict[int, DeltaToolCall]]:
    last = messages[-1]
    tool_result = next(
        (p.content for p in getattr(last, "parts", []) if isinstance(p, ToolReturnPart)),
        None,
    )
    if tool_result is None:
        yield {0: DeltaToolCall(name="get_current_time", json_args='{"timezone": "Asia/Shanghai"}')}
        return
    text = DEMO_ANSWER.replace("{time}", str(tool_result))
    for i in range(0, len(text), 12):
        await asyncio.sleep(0.02)
        yield text[i : i + 12]


def _demo_sync(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
    last = messages[-1]
    if not any(isinstance(p, ToolReturnPart) for p in getattr(last, "parts", [])):
        return ModelResponse(parts=[ToolCallPart("get_current_time", {"timezone": "Asia/Shanghai"})])
    return ModelResponse(parts=[TextPart("done")])


def resolve_model(name: str) -> Model:
    if name == "demo":
        return FunctionModel(_demo_sync, stream_function=_demo_stream, model_name="demo")
    provider = OpenAIProvider(base_url=BASE_URL, api_key=API_KEY or "not-needed")
    return OpenAIChatModel(name, provider=provider)


# --- HTTP ---------------------------------------------------------------------

app = FastAPI(title="AI Elements agent server")


@app.post("/api/chat")
async def chat(request: Request) -> Response:
    model_name = request.query_params.get("model") or DEFAULT_MODEL
    return await VercelAIAdapter.dispatch_request(request, agent=agent, model=resolve_model(model_name))


@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "default_model": DEFAULT_MODEL,
        "upstream": BASE_URL,
        "has_key": bool(API_KEY),
        "tools": ["get_current_time", "calculate"],
    }

