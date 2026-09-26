"""AI Elements agent server.

A FastAPI service running a PydanticAI agent (with tools) and streaming its run
to the Android demo over two agent↔UI protocols, via PydanticAI's adapters:

    POST /api/chat   Vercel AI SDK v5 UI Message Stream (VercelAIAdapter)
    POST /api/agui   AG-UI (AGUIAdapter)

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
from dataclasses import dataclass, field
from datetime import datetime
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from fastapi import FastAPI, Request
from fastapi.responses import Response
from ag_ui.core import CustomEvent
from pydantic_ai import Agent, RunContext
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart, ToolReturn, ToolReturnPart
from pydantic_ai.models import Model
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel
from pydantic_ai.models.openai import OpenAIChatModel
from pydantic_ai.providers.openai import OpenAIProvider
from pydantic_ai.ui.ag_ui import AGUIAdapter
from pydantic_ai.ui.vercel_ai import VercelAIAdapter
from pydantic_ai.ui.vercel_ai.response_types import DataChunk, MessageMetadataChunk, SourceUrlChunk

BASE_URL = os.environ.get("AGENT_BASE_URL", os.environ.get("CLIPROXY_BASE_URL", "http://localhost:8317/v1"))
API_KEY = os.environ.get("AGENT_API_KEY", os.environ.get("CLIPROXY_API_KEY", ""))
DEFAULT_MODEL = os.environ.get("AGENT_MODEL", os.environ.get("CLIPROXY_MODEL", "demo"))

INSTRUCTIONS = """\
You are a helpful assistant inside an Android chat app that renders GitHub-flavoured
Markdown (headings, lists, tables, fenced code with a language tag), Mermaid
diagrams (```mermaid fenced blocks) and LaTeX math ($...$, $$...$$). Prefer a
Mermaid diagram when the user asks about flows, architectures, sequences or
relationships. Use the tools when they help: `get_current_time` for dates/times,
`calculate` for arithmetic, `search_docs` for questions about AI Elements,
Material 3, Mermaid, KaTeX, PydanticAI or AG-UI — cite its results inline as [n]
using the numbers it returns. For multi-step tasks call `make_plan` first and
`update_plan` as you finish steps. Answer in the user's language.
"""


@dataclass
class RunState:
    """Per-run state shared by the tools (plan progress, citation numbering)."""

    plan_title: str = ""
    plan_steps: list[str] = field(default_factory=list)
    sources: int = 0


agent = Agent(instructions=INSTRUCTIONS, name="ai-elements-agent", deps_type=RunState)


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


def _plan_chunk(state: RunState, completed: int) -> DataChunk:
    steps = [
        {"label": label, "status": "complete" if i < completed else "active" if i == completed else "pending"}
        for i, label in enumerate(state.plan_steps)
    ]
    return DataChunk(
        type="data-plan",
        id="plan",
        data={
            "title": state.plan_title,
            "description": f"{min(completed, len(steps))}/{len(steps)} steps done",
            "streaming": completed < len(steps),
            "steps": steps,
        },
    )


@agent.tool
def make_plan(ctx: RunContext[RunState], title: str, steps: list[str]) -> ToolReturn:
    """Show the user a short plan (3-6 steps) before doing multi-step work."""
    ctx.deps.plan_title, ctx.deps.plan_steps = title, steps
    return ToolReturn(return_value=f"Plan shown with {len(steps)} steps.", metadata=[_plan_chunk(ctx.deps, 0)])


@agent.tool
def update_plan(ctx: RunContext[RunState], completed_steps: int) -> ToolReturn:
    """Mark the first `completed_steps` steps of the current plan as done."""
    if not ctx.deps.plan_steps:
        return ToolReturn(return_value="No plan yet; call make_plan first.")
    return ToolReturn(return_value="Plan updated.", metadata=[_plan_chunk(ctx.deps, completed_steps)])


# A tiny local knowledge base, so citations work offline and deterministically.
DOCS = [
    ("AI Elements", "https://elements.ai-sdk.dev",
     "AI Elements is a component library built on shadcn/ui for AI-native apps: Conversation, Message, "
     "Response, Reasoning, Tool, Sources, Prompt Input, Branch, Checkpoint, Queue, Inline Citation and more."),
    ("AI SDK UI Message Stream", "https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol",
     "The AI SDK streams UI messages over SSE as typed chunks: text-delta, reasoning-delta, tool-input-available, "
     "tool-output-available, source-url, data-* parts and finish."),
    ("Material 3 Expressive", "https://m3.material.io/blog/building-with-m3-expressive",
     "M3 Expressive adds spring-based motion, a larger shape library (MaterialShapes), new components such as "
     "loading indicators, button groups and floating toolbars, and emphasized typography."),
    ("Mermaid", "https://mermaid.js.org/intro/",
     "Mermaid renders flowcharts, sequence, class, state, ER and pie diagrams from a Markdown-like text syntax."),
    ("KaTeX", "https://katex.org",
     "KaTeX is a fast TeX math renderer for the web that renders synchronously without reflow."),
    ("PydanticAI UI adapters", "https://ai.pydantic.dev/ui/overview/",
     "PydanticAI agents can stream to frontends through UI adapters for the Vercel AI protocol and AG-UI."),
    ("AG-UI protocol", "https://docs.ag-ui.com",
     "AG-UI is an open, event-based protocol connecting agents to user interfaces: text messages, tool calls, "
     "reasoning, state snapshots and custom events."),
]


@agent.tool
def search_docs(ctx: RunContext[RunState], query: str) -> ToolReturn:
    """Search documentation about AI Elements, AI SDK, Material 3, Mermaid, KaTeX, PydanticAI and AG-UI.
    Results are numbered; cite them inline as [n]."""
    words = {w for w in query.lower().split() if len(w) > 2}
    ranked = sorted(DOCS, key=lambda d: -sum(w in (d[0] + " " + d[2]).lower() for w in words))[:3]
    lines, chunks = [], []
    for title, url, text in ranked:
        ctx.deps.sources += 1
        n = ctx.deps.sources
        lines.append(f"[{n}] {title}: {text}")
        chunks.append(SourceUrlChunk(source_id=f"src-{n}", url=url, title=title))
    return ToolReturn(return_value="\n".join(lines), metadata=chunks)


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
    state = RunState()

    async def on_complete(result):
        # Models often forget update_plan; a finished run means the plan is done.
        if state.plan_steps:
            yield _plan_chunk(state, len(state.plan_steps))
        u = result.usage
        yield MessageMetadataChunk(message_metadata={"usage": {"inputTokens": u.input_tokens, "outputTokens": u.output_tokens}})

    return await VercelAIAdapter.dispatch_request(
        request,
        agent=agent,
        deps=state,
        model=resolve_model(model_name),
        allow_uploaded_files=True,  # image attachments arrive as data: URLs
        on_complete=on_complete,
    )


@app.post("/api/agui")
async def agui(request: Request) -> Response:
    model_name = request.query_params.get("model") or DEFAULT_MODEL
    async def usage(result):
        u = result.usage
        yield CustomEvent(name="usage", value={"inputTokens": u.input_tokens, "outputTokens": u.output_tokens})

    return await AGUIAdapter.dispatch_request(
        request, agent=agent, deps=RunState(), model=resolve_model(model_name), on_complete=usage
    )


@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "default_model": DEFAULT_MODEL,
        "upstream": BASE_URL,
        "has_key": bool(API_KEY),
        "tools": ["get_current_time", "calculate", "search_docs", "make_plan", "update_plan"],
    }

