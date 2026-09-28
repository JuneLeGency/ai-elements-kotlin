"""AI Elements agent server.

A FastAPI service running a Pydantic AI agent built from Pydantic AI Harness
capabilities — Planning, SubAgents (researcher, writer), Skills (../skills) and an
MCP toolset — and exposing it over open protocols only:

    POST /api/chat                      Vercel AI SDK 6 UI Message Stream (VercelAIAdapter)
    POST /api/agui                      AG-UI 1.0 (AGUIAdapter)
    POST /mcp                           MCP server, Streamable HTTP (official `mcp` SDK)
    GET  /.well-known/agent-card.json   A2A agent card; JSON-RPC at POST /a2a (official `a2a-sdk`)
    WS   /acp                           Agent Client Protocol (Pydantic AI Harness ACP adapter);
                                        stdio: `uv run python acp_agent.py`

Upstream model (any OpenAI-compatible endpoint, e.g. CLIProxyAPI):

    AGENT_BASE_URL   default http://localhost:8317/v1
    AGENT_API_KEY    upstream key (never sent to the app)
    AGENT_MODEL      default model id; the client may override with ?model=
                     use "demo" for an offline scripted model (no key needed)
    CODEX_AUTH_FILE  optional Codex CLI auth.json: `gpt-5*` models then run on that ChatGPT
                     subscription (read-only; the login is never refreshed)

Run:  uv run uvicorn main:app --host 0.0.0.0 --port 8788
"""

from __future__ import annotations

import ast
import asyncio
import json
import operator
import os
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from datetime import datetime
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from contextlib import asynccontextmanager
from pathlib import Path

from ag_ui.core import ActivitySnapshotEvent, StateSnapshotEvent
from fastapi import FastAPI, Request, WebSocket
from fastapi.responses import HTMLResponse, Response
from mcp.server.transport_security import TransportSecuritySettings
from pydantic_ai import Agent, BinaryImage, CustomEvent, DeferredToolRequests, RunContext
from pydantic_ai.mcp import MCPToolset
from pydantic_ai_harness import Planning, Skills, SubAgent, SubAgents
from pydantic_ai_harness import AskUser
from pydantic_ai_harness.ask_user import TOOL_NAME as ASK_USER_TOOL_NAME
from pydantic_ai.toolsets import ExternalToolset
from pydantic_ai_harness.planning import (
    InMemoryPlanStore,
    PlanCompletedEvent,
    PlanCreatedEvent,
    PlanDeletedEvent,
    PlanStatusChangedEvent,
    PlanUpdatedEvent,
)
from pydantic_ai.messages import ModelMessage, ModelResponse, RetryPromptPart, TextPart, ToolCallPart, ToolReturn, ToolReturnPart
from pydantic_ai.models import Model
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel
from openai import AsyncOpenAI
from pydantic_ai.models.openai import OpenAIChatModel, OpenAIResponsesModel, OpenAIResponsesModelSettings
from pydantic_ai.models.wrapper import WrapperModel
from pydantic_ai.providers.openai import OpenAIProvider
from pydantic_ai.ui.ag_ui import AGUIAdapter
from pydantic_ai.ui.vercel_ai import VercelAIAdapter
from pydantic_ai.ui.vercel_ai.response_types import DataChunk, FileChunk, MessageMetadataChunk, SourceUrlChunk

import a2ui_demo
from a2a.types import AgentExtension, AgentSkill
from a2a_agent import a2a_routes
from starlette.routing import Mount
from mcp_server import mcp

BASE_URL = os.environ.get("AGENT_BASE_URL", os.environ.get("CLIPROXY_BASE_URL", "http://localhost:8317/v1"))
API_KEY = os.environ.get("AGENT_API_KEY", os.environ.get("CLIPROXY_API_KEY", ""))
DEFAULT_MODEL = os.environ.get("AGENT_MODEL", os.environ.get("CLIPROXY_MODEL", "demo"))
# Optional: a Codex CLI login (auth.json) to run `gpt-5*` models on a ChatGPT subscription.
CODEX_AUTH_FILE = os.environ.get("CODEX_AUTH_FILE")

INSTRUCTIONS = """\
You are a helpful assistant inside an Android chat app that renders GitHub-flavoured
Markdown (headings, lists, tables, fenced code with a language tag), Mermaid
diagrams (```mermaid fenced blocks) and LaTeX math ($...$, $$...$$). Use the tools
when they help: `get_current_time` for dates/times, `calculate` for arithmetic,
`search_docs` for questions about AI Elements, Material 3, Mermaid, KaTeX,
PydanticAI or AG-UI — cite its results inline as [n] using the numbers it returns.
For multi-step tasks keep a plan with the planning tools. Answer in the user's language.
"""

# Harness capabilities surface as ordinary tool calls on the wire (`delegate_task`,
# `load_capability`, `write_plan`, MCP tools); the plan is additionally mirrored to the UI
# through each protocol's own state channel (see `mirror_plan`).
SKILLS_DIR = Path(__file__).resolve().parent.parent / "skills"
MCP_URL = os.environ.get("MCP_URL", f"http://127.0.0.1:{os.environ.get('PORT', '8788')}/mcp")
PUBLIC_URL = os.environ.get("PUBLIC_URL", f"http://127.0.0.1:{os.environ.get('PORT', '8788')}")
# MCP tools that change data ask the user first (AG-UI interrupt / AI SDK 6 tool approval).
MCP_WRITE_TOOLS = {"save_note", "delete_note"}


@dataclass
class RunState:
    """Per-run state shared by the tools (plan store, citation numbering, wire protocol)."""

    protocol: str = "vercel"
    plan: InMemoryPlanStore = field(default_factory=InMemoryPlanStore)
    sources: int = 0
    # The A2UI user action that started this run, if any (see a2ui_demo.py).
    a2ui_action: dict | None = None
    # ACP: the session's client connection and id, for `session/update`s (see acp_agent.py).
    acp: tuple | None = None


def _model_visible(ctx, tool) -> bool:
    """MCP Apps: tools with `_meta.ui.visibility` lacking "model" are only for the server's views."""
    return "model" in (((tool.metadata or {}).get("meta") or {}).get("ui") or {}).get("visibility", ["model"])


notes = (MCPToolset(MCP_URL, id="notes")
         .filtered(_model_visible)
         .approval_required(lambda ctx, tool, args: tool.name in MCP_WRITE_TOOLS))

researcher = Agent(
    name="researcher",
    description="Searches the AI Elements knowledge base and reports findings with numbered citations.",
    instructions="Research the task with `search_docs` and report concise findings, citing sources as [n].",
    deps_type=RunState,
)
writer = Agent(
    name="writer",
    description="Turns notes or findings into polished prose, release notes or diagrams.",
    instructions="Write the requested text. Load a skill first when one matches the task.",
    capabilities=[Skills(SKILLS_DIR)],
    deps_type=RunState,
)

agent = Agent(
    instructions=INSTRUCTIONS,
    # Tools that need approval end the run with DeferredToolRequests; the UI adapters turn
    # them into an AG-UI interrupt / AI SDK 6 tool-approval-request.
    output_type=[str, DeferredToolRequests],
    name="ai-elements-agent",
    deps_type=RunState,
    toolsets=[notes],
    capabilities=[
        Planning(store_resolver=lambda ctx: ctx.deps.plan),
        SubAgents(agents=[SubAgent(researcher), SubAgent(writer)]),
        Skills(SKILLS_DIR),
    ],
)


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


# --- Plan → UI -----------------------------------------------------------------
# `Planning` emits typed capability events; the UI adapters don't forward those by
# default, so re-emit the whole plan in each protocol's native channel: an AI SDK
# `data-plan` part (the AI Elements <Plan> shape) or an AG-UI STATE_SNAPSHOT.

_STATUS = {"pending": "pending", "in_progress": "active", "completed": "complete", "cancelled": "complete", "blocked": "pending"}


@dataclass(kw_only=True)
class PlanSnapshot(CustomEvent, name="plan"):
    protocol: str
    items: list

    def to_payload(self):
        steps = [{"label": i.active_form if i.status == "in_progress" and i.active_form else i.content, "status": _STATUS.get(i.status, "pending")} for i in self.items]
        done = sum(i.status in ("completed", "cancelled") for i in self.items)
        plan = {"title": "Plan", "description": f"{done}/{len(steps)} steps done", "streaming": done < len(steps), "steps": steps}
        if self.protocol == "agui":
            return StateSnapshotEvent(snapshot={"plan": plan})
        return DataChunk(type="data-plan", id="plan", data=plan)


@agent.on_event(PlanCreatedEvent, PlanUpdatedEvent, PlanStatusChangedEvent, PlanCompletedEvent, PlanDeletedEvent)
async def mirror_plan(ctx: RunContext[RunState], event) -> None:
    items = await ctx.deps.plan.get_items()
    if ctx.deps.acp:
        # ACP has a plan update of its own (`sessionUpdate: "plan"`), sent on the session.
        import acp
        client, session_id = ctx.deps.acp
        entries = [acp.plan_entry(i.content, status="completed" if i.status in ("completed", "cancelled") else i.status if i.status == "in_progress" else "pending") for i in items]
        await client.session_update(session_id=session_id, update=acp.update_plan(entries))
        return
    await ctx.emit(PlanSnapshot(protocol=ctx.deps.protocol, items=items))


# --- Generative UI (A2UI) -----------------------------------------------------
# Tools return interface as A2UI v1.0 messages, carried on each protocol's A2UI binding.


@dataclass(kw_only=True)
class A2uiSurface(CustomEvent, name="a2ui"):
    protocol: str
    surface: str
    messages: list

    def to_payload(self):
        if self.protocol == "agui":
            return ActivitySnapshotEvent(message_id=f"a2ui-surface-{self.surface}", activity_type="a2ui-surface",
                                         content={"a2ui_operations": self.messages}, replace=True)
        return DataChunk(type="data-a2ui", id=f"a2ui-{self.surface}", data=self.messages)

    def to_a2a_parts(self):
        # A2UI A2A extension: a DataPart holding the message list, tagged with the A2UI media type.
        return "a2ui", [a2ui_demo.a2a_part(self.messages)]


async def show_booking_form(ctx: RunContext[RunState], city: str) -> str:
    """Show the user an interactive hotel booking form for a city (name, check-in date, room)."""
    messages = a2ui_demo.booking_form(city)
    surface = messages[0]["createSurface"]["surfaceId"]
    await ctx.emit(A2uiSurface(protocol=ctx.deps.protocol, surface=surface, messages=messages))
    return f"Showed the booking form for {city} (surface {surface}); wait for the user to submit it."


async def confirm_booking(ctx: RunContext[RunState], guest: str | None = None, date: str | None = None, room: str | None = None) -> str:
    """Confirm a hotel booking the user submitted from the booking form."""
    submitted = (ctx.deps.a2ui_action or {}).get("context") or {}
    guest = guest or submitted.get("guest") or "the guest"
    date = date or submitted.get("date") or "the chosen date"
    room = room or ", ".join(submitted.get("room") or []) or "standard"
    return f"Booked a {room} room at Hotel Lumen for {guest}, checking in {date}."


def a2ui_action_instructions(ctx: RunContext[RunState]) -> str | None:
    return a2ui_demo.describe(ctx.deps.a2ui_action) + " Confirm it with `confirm_booking`." if ctx.deps.a2ui_action else None


# The same tools on the main agent and on a small A2A concierge (served at /concierge).
concierge = Agent(
    name="concierge",
    description="Books hotels with an interactive form (A2UI).",
    instructions="Help the user book a hotel: show the booking form, then confirm what they submit.",
    deps_type=RunState,
)
for _a in (agent, concierge):
    _a.tool(show_booking_form)
    _a.tool(confirm_booking)
    _a.instructions(a2ui_action_instructions)


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


agent.tool(search_docs)


def _page_png(url: str, step: int, width: int = 360, height: int = 240) -> bytes:
    """A small PNG standing in for a browser screenshot: address bar, heading, text lines, a button."""
    import struct, zlib
    rows = []
    for y in range(height):
        row = bytearray()
        for x in range(width):
            if y < 28:                                           # browser chrome with an address field
                c = (241, 243, 247) if not (60 < x < width - 20 and 7 < y < 21) else (255, 255, 255)
            elif 44 < y < 64 and 20 < x < 20 + 160 + step * 12:  # heading
                c = (40, 44, 60)
            elif any(80 + i * 22 < y < 90 + i * 22 for i in range(4)) and 20 < x < width - 40 - (y % 3) * 30:
                c = (170, 176, 190)                              # paragraph lines
            elif 180 < y < 210 and 20 < x < 130:                 # a button
                c = (98, 80, 180)
            else:
                c = (255, 255, 255)
            row += bytes(c)
        rows.append(b"\x00" + bytes(row))
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b"")


def browse(ctx: RunContext[RunState], url: str) -> ToolReturn:
    """Open a web page and look at it. Returns what the page says and a screenshot of it."""
    image = BinaryImage(data=_page_png(url, ctx.deps.sources), media_type="image/png")
    ctx.deps.sources += 1
    # The screenshot reaches the model as tool-result content (AG-UI: an ImagePart in TOOL_CALL_RESULT)
    # and the AI SDK UI as a `file` part after the tool output.
    return ToolReturn(
        return_value=f"Opened {url}: an example page with a heading, four paragraphs and a sign-up button.",
        content=[image],
        metadata=[FileChunk(url=image.data_uri, media_type="image/png")],
    )


agent.tool(browse)
researcher.tool(search_docs)


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


# Keyword → the harness tool the scripted model calls first, so every capability can be
# exercised end to end (and recorded as protocol fixtures) without an LLM.
_DEMO_SCRIPTS = [
    # Pydantic AI Harness `ask_user_question`, answered by the user on the device (a client tool).
    ("browse", "browse", {"url": "https://example.com/pricing"}),
    ("ask", "ask_user_question", {"questions": [
        {"header": "Database", "question": "Which database should the app use?", "options": [
            {"label": "SQLite", "description": "Local file, no server"},
            {"label": "Postgres", "description": "A managed server"}]},
        {"header": "Features", "question": "What should the first release include?", "multi_select": True, "options": [
            {"label": "Sign-in"}, {"label": "Payments"}, {"label": "Search"}]},
    ]}),
    ("delegate", "delegate_task", {"agent_name": "researcher", "task": "Explain the AG-UI protocol in two sentences."}),
    ("plan", "write_plan", {"items": [
        {"content": "Read the question", "active_form": "Reading the question", "status": "completed"},
        {"content": "Look up the docs", "active_form": "Looking up the docs", "status": "in_progress"},
        {"content": "Write the answer", "active_form": "Writing the answer", "status": "pending"},
    ]}),
    ("skill", "load_capability", {"id": "mermaid-diagrams"}),
    ("note", "save_note", {"title": "Demo", "content": "Saved from the scripted demo model."}),
    # A frontend tool the client advertises (AG-UI `tools`), executed on the device.
    ("device", "get_device_info", {}),
    # Generative UI: "book" (the form's action) before "hotel" (asking for the form).
    ("book", "confirm_booking", {}),
    ("hotel", "show_booking_form", {"city": "Kyoto"}),
]


def _prompt_text(part) -> str | None:
    """A user prompt's text, or None for other parts and for prompts the harness injects."""
    if type(part).__name__ != "UserPromptPart":
        return None
    # A string, or a list of content (ACP sends content blocks).
    content = part.content if isinstance(part.content, str) else " ".join(c for c in part.content if isinstance(c, str))
    return None if content.lstrip().startswith("<plan-reminder>") else content


def _user_prompt(messages: list[ModelMessage]) -> str:
    """The latest user prompt (earlier turns must not re-trigger their capability)."""
    for message in reversed(messages):
        for part in reversed(getattr(message, "parts", [])):
            if (text := _prompt_text(part)) is not None:
                return text.lower()
    return ""


def _demo_call(messages: list[ModelMessage], info: AgentInfo) -> ToolCallPart | None:
    """The scripted model's one tool call: a keyword-selected harness tool, else the clock or docs search."""
    # One tool call per user turn: stop once a result came back after the latest prompt (by part:
    # AG-UI history can put the previous turn's tool result before the new prompt in one request),
    # or once this run has called a tool (capabilities such as Planning add prompts after results).
    parts = [p for m in messages for p in getattr(m, "parts", [])]
    last_prompt = max((i for i, p in enumerate(parts) if _prompt_text(p) is not None), default=0)
    if any(isinstance(p, (ToolReturnPart, RetryPromptPart)) for p in parts[last_prompt:]):
        return None
    run_id = getattr(messages[-1], "run_id", None) if messages else None
    if run_id and any(isinstance(m, ModelResponse) and m.run_id == run_id and m.tool_calls for m in messages):
        return None
    names = {t.name for t in info.function_tools}
    prompt = _user_prompt(messages)
    for keyword, tool, args in _DEMO_SCRIPTS:
        if keyword in prompt and tool in names:
            return ToolCallPart(tool, args)
    if "get_current_time" in names:
        return ToolCallPart("get_current_time", {"timezone": "Asia/Shanghai"})
    if "search_docs" in names:
        return ToolCallPart("search_docs", {"query": "AG-UI protocol"})
    return None


async def _demo_stream(messages: list[ModelMessage], info: AgentInfo) -> AsyncIterator[str | dict[int, DeltaToolCall]]:
    call = _demo_call(messages, info)
    if call is not None:
        yield {0: DeltaToolCall(name=call.tool_name, json_args=call.args_as_json_str())}
        return
    tool_result = next((p.content for p in getattr(messages[-1], "parts", []) if isinstance(p, ToolReturnPart)), "no tool")
    text = DEMO_ANSWER.replace("{time}", str(tool_result).splitlines()[0][:80])
    for i in range(0, len(text), 12):
        await asyncio.sleep(0.02)
        yield text[i : i + 12]


def _demo_sync(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
    call = _demo_call(messages, info)
    if call is not None:
        return ModelResponse(parts=[call])
    result = next((p.content for m in reversed(messages) for p in getattr(m, "parts", []) if isinstance(p, ToolReturnPart)), None)
    return ModelResponse(parts=[TextPart(f"AG-UI streams agent events to user interfaces. (Scripted answer; the tool said: {str(result)[:120]})")])


class StreamingOnly(WrapperModel):
    """Serve non-streamed requests (e.g. sub-agent runs) by streaming, for backends that only stream."""

    async def request(self, messages, model_settings, model_request_parameters):
        async with self.wrapped.request_stream(messages, model_settings, model_request_parameters) as stream:
            async for _ in stream:
                pass
            return stream.get()


def codex_model(name: str) -> Model:
    """A ChatGPT (Codex) subscription through its Responses endpoint.

    Reads the Codex CLI login from `CODEX_AUTH_FILE` on each call and never refreshes it
    (refreshing would sign the CLI out); the token never leaves this process.
    """
    tokens = json.loads(Path(CODEX_AUTH_FILE).read_text())["tokens"]
    client = AsyncOpenAI(
        base_url="https://chatgpt.com/backend-api/codex",
        api_key=tokens["access_token"],
        default_headers={"OpenAI-Beta": "responses=experimental", "originator": "codex_cli_rs", "chatgpt-account-id": tokens["account_id"]},
    )
    model = OpenAIResponsesModel(name, provider=OpenAIProvider(openai_client=client), settings=OpenAIResponsesModelSettings(openai_store=False))
    return StreamingOnly(model)


def resolve_model(name: str) -> Model:
    if name == "demo":
        return FunctionModel(_demo_sync, stream_function=_demo_stream, model_name="demo")
    if CODEX_AUTH_FILE and (name.startswith("gpt-5") or name.startswith("codex")):
        return codex_model(name)
    provider = OpenAIProvider(base_url=BASE_URL, api_key=API_KEY or "not-needed")
    return OpenAIChatModel(name, provider=provider)


# --- HTTP ---------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with mcp.session_manager.run():
        yield


app = FastAPI(title="AI Elements agent server", lifespan=lifespan)

# MCP (Streamable HTTP) at /mcp. DNS-rebinding protection is off because the demo is
# reached through the emulator alias / LAN IP; keep it on for anything public.
mcp_app = mcp.streamable_http_app(
    streamable_http_path="/mcp",
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)
app.router.routes.extend(mcp_app.routes)

# A2A: agent card at /.well-known/agent-card.json, JSON-RPC at /a2a.
app.router.routes.extend(a2a_routes(researcher, lambda: resolve_model(DEFAULT_MODEL), PUBLIC_URL, deps=lambda _: RunState()))
# A second A2A agent that answers with A2UI (A2UI A2A extension), under /concierge.
app.router.routes.append(Mount("/concierge", routes=a2a_routes(
    concierge, lambda: resolve_model(DEFAULT_MODEL), f"{PUBLIC_URL}/concierge",
    deps=lambda message: RunState(protocol="a2a", a2ui_action=a2ui_demo.action_from_a2a(message)),
    name="Hotel concierge", description="Books hotels with an interactive form.",
    skills=[AgentSkill(id="book-hotel", name="Book a hotel", description="Shows a booking form and confirms the booking.",
                       tags=["hotel", "a2ui"], examples=["Find me a hotel in Kyoto"])],
    extensions=[AgentExtension(uri=a2ui_demo.A2A_EXTENSION, description="Renders A2UI v1.0 surfaces.",
                               params={"supportedCatalogIds": [a2ui_demo.BASIC_CATALOG]})],
)))


# Pydantic AI Harness `AskUser` answered by the app: the tool has no server-side answerer. AG-UI
# clients advertise it as a frontend tool (`RunAgentInput.tools`); the AI SDK has no way for a client
# to declare tools, so the chat endpoint declares it as a client-side (external) tool — AI SDK's own
# pattern for tools without `execute` — and the app returns the answer as the tool's output.
_ask_user = AskUser(answerer=lambda request: None)
ask_user_client_tool = ExternalToolset([_ask_user.get_toolset().tools[ASK_USER_TOOL_NAME].tool_def], id="ask_user")


@app.post("/api/chat")
async def chat(request: Request) -> Response:
    model_name = request.query_params.get("model") or DEFAULT_MODEL

    async def on_complete(result):
        u = result.usage
        yield MessageMetadataChunk(message_metadata={"usage": {"inputTokens": u.input_tokens, "outputTokens": u.output_tokens}})

    body = await request.json()
    return await VercelAIAdapter.dispatch_request(
        request,
        agent=agent,
        deps=RunState(protocol="vercel", a2ui_action=a2ui_demo.action_from_ui_messages(body)),
        model=resolve_model(model_name),
        sdk_version=6,  # AI SDK 6: tool approval (human in the loop)
        allow_uploaded_files=True,  # image attachments arrive as data: URLs
        toolsets=[ask_user_client_tool],
        instructions=_ask_user.get_instructions(),
        on_complete=on_complete,
    )


@app.websocket("/acp")
async def acp_websocket(websocket: WebSocket) -> None:
    # Agent Client Protocol over the ACP Kotlin SDK's WebSocket transport (see acp_agent.py).
    import acp_agent
    await acp_agent.serve_websocket(websocket)


@app.post("/api/agui")
async def agui(request: Request) -> Response:
    model_name = request.query_params.get("model") or DEFAULT_MODEL
    body = await request.json()
    deps = RunState(protocol="agui", a2ui_action=a2ui_demo.action_from_agui(body))
    return await AGUIAdapter.dispatch_request(request, agent=agent, deps=deps, model=resolve_model(model_name))


# A small page for the in-app browser's end-to-end test (harness-browser).
_BROWSER_TEST_PAGE = """<!doctype html><html><head><title>Order form</title></head><body>
<h1>Order a coffee</h1>
<form action="/browser-test/done" method="get">
  <label>Name <input name="name" placeholder="Your name"></label>
  <label>Size <select name="size"><option value="s">Small</option><option value="l">Large</option></select></label>
  <button type="submit">Order</button>
</form>
<a href="/browser-test/about">About this shop</a>
</body></html>"""


@app.get("/browser-test", response_class=HTMLResponse)
async def browser_test() -> str:
    return _BROWSER_TEST_PAGE


@app.get("/browser-test/done", response_class=HTMLResponse)
async def browser_test_done(name: str = "", size: str = "") -> str:
    return f"<!doctype html><html><head><title>Thanks</title></head><body><h1>Thanks {name}!</h1><p>Your {'large' if size == 'l' else 'small'} coffee is on its way.</p></body></html>"


@app.get("/browser-test/about", response_class=HTMLResponse)
async def browser_test_about() -> str:
    return "<!doctype html><html><head><title>About</title></head><body><h1>About</h1><p>Open since 2026.</p></body></html>"


@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "default_model": DEFAULT_MODEL,
        "upstream": BASE_URL,
        "has_key": bool(API_KEY),
        "capabilities": ["Planning", "SubAgents(researcher, writer)", "Skills", "MCP(notes)"],
        "mcp": "/mcp",
        "a2a": "/.well-known/agent-card.json",
    }
