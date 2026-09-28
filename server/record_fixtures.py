"""Record protocol fixtures for the Kotlin client tests from real implementations.

- `agui/*.sse`, `aisdk/*.sse`: responses of this server (Pydantic AI + Harness through the official
  AG-UI and Vercel AI adapters) for each capability, including the follow-up request of multi-step
  flows (tool approval, frontend tools).
- `agui/hotel*.sse`, `aisdk/hotel*.sse`: an A2UI v1.0 form and the run its user action starts,
  each surface checked by the official `a2ui-core` message processor (strict validation).
- `mcp/elicitation-*.json`: MCP 2026-07-28 elicitation (SEP-2322) from the official `mcp` SDK — the
  `InputRequiredResult` of `book_table` and the result of the retry that answers it.
- `agui/input-interrupt.sse`, `agui/input-resumed.sse`: an interrupt that asks the user for input
  (no tool call, a `responseSchema`), encoded by the official `ag_ui` SDK.
- `agui/subagent-state-activity.sse`: AG-UI 1.0 events the server does not emit yet
  (SUBAGENT_*, STATE_DELTA, ACTIVITY_*, usage), encoded by the official `ag_ui` Python SDK.

- `../ai-elements-acp/src/test/resources/acp/*.jsonl`: Agent Client Protocol sessions with the
  agent served by the Pydantic AI Harness ACP adapter (`acp_agent.py`, stdio), driven by the
  official ACP Python SDK client: every JSON-RPC message the agent sent, one per line.

Run with the server up:  uv run python record_fixtures.py [http://localhost:8788]
"""

from __future__ import annotations

import base64
import json
import sys
from pathlib import Path

import httpx
from ag_ui.core import (
    Interrupt,
    RunFinishedInterruptOutcome,
    RunFinishedSuccessOutcome,
    ActivityDeltaEvent,
    ActivitySnapshotEvent,
    RunFinishedEvent,
    RunStartedEvent,
    StateDeltaEvent,
    StateSnapshotEvent,
    SubagentFinishedEvent,
    SubagentStartedEvent,
    TextMessageContentEvent,
    TextMessageEndEvent,
    TextMessageStartEvent,
    TokenUsage,
    ToolCallArgsEvent,
    ToolCallEndEvent,
    ToolCallResultEvent,
    ToolCallStartEvent,
)
from ag_ui.encoder import EventEncoder
from a2ui.core.basic_catalog.v1_0 import BasicCatalog
from a2ui.core.processing.message_processor import MessageProcessor, MessageProcessorOptions
from a2ui.core.validation.payload_validator import STRICT_VALIDATION

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8788"
OUT = Path(__file__).resolve().parent.parent / "ai-elements-core/src/test/resources/fixtures"
DEVICE_TOOL = {"name": "get_device_info", "description": "Device model and Android version", "parameters": {"type": "object", "properties": {}}}


def post(path: str, body: dict) -> str:
    with httpx.Client(timeout=60) as client:
        response = client.post(BASE + path, json=body, headers={"Accept": "text/event-stream"})
        response.raise_for_status()
        return response.text


def events(sse: str) -> list[dict]:
    return [json.loads(line[5:]) for line in sse.splitlines() if line.startswith("data:") and line[5:].strip() not in ("", "[DONE]")]


def save(name: str, text: str) -> None:
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    print("wrote", path.relative_to(OUT.parent.parent.parent.parent.parent))


# Pydantic AI Harness `ask_user_question`, advertised by the app as an AG-UI frontend tool.
from pydantic_ai_harness import AskUser as _AskUser
_ask_def = _AskUser(answerer=lambda r: None).get_toolset().tools["ask_user_question"].tool_def
ASK_USER_TOOL = {"name": _ask_def.name, "description": _ask_def.description, "parameters": _ask_def.parameters_json_schema}
ASK_USER_ANSWER = '{"Database":["Postgres"],"Features":["Sign-in","Search"]}'


def agui(keyword: str) -> None:
    user = {"id": "u1", "role": "user", "content": f"please {keyword}"}
    body = {"threadId": "t", "runId": "r1", "state": {}, "messages": [user], "tools": [DEVICE_TOOL, ASK_USER_TOOL], "context": [], "forwardedProps": {}}
    first = post("/api/agui", body)
    save(f"agui/{keyword}.sse", first)
    evs = events(first)
    finished = next(e for e in evs if e["type"] == "RUN_FINISHED")
    starts = [e for e in evs if e["type"] == "TOOL_CALL_START"]
    if not starts:
        return
    call = starts[0]
    args = "".join(e["delta"] for e in evs if e["type"] == "TOOL_CALL_ARGS" and e["toolCallId"] == call["toolCallId"])
    assistant = {
        "id": call.get("parentMessageId") or "a1",
        "role": "assistant",
        "toolCalls": [{"id": call["toolCallId"], "type": "function", "function": {"name": call["toolCallName"], "arguments": args or "{}"}}],
    }
    outcome = finished.get("outcome") or {}
    if outcome.get("type") == "interrupt":
        interrupt = outcome["interrupts"][0]
        resume = [{"interruptId": interrupt["id"], "status": "resolved", "payload": {"approved": True}}]
        second = post("/api/agui", {**body, "runId": "r2", "messages": [user, assistant], "resume": resume})
        save(f"agui/{keyword}-resumed.sse", second)
    elif call["toolCallName"] in (DEVICE_TOOL["name"], ASK_USER_TOOL["name"]):
        content = ASK_USER_ANSWER if call["toolCallName"] == ASK_USER_TOOL["name"] else "Pixel 10, Android 17"
        tool = {"id": "result-1", "role": "tool", "toolCallId": call["toolCallId"], "content": content}
        second = post("/api/agui", {**body, "runId": "r2", "messages": [user, assistant, tool]})
        save(f"agui/{keyword}-continued.sse", second)


def aisdk(keyword: str) -> None:
    user = {"id": "u1", "role": "user", "parts": [{"type": "text", "text": f"please {keyword}"}]}
    first = post("/api/chat", {"trigger": "submit-message", "id": "c1", "messages": [user]})
    save(f"aisdk/{keyword}.sse", first)
    evs = events(first)
    approval = next((e for e in evs if e["type"] == "tool-approval-request"), None)
    asked = next((e for e in evs if e["type"] == "tool-input-available" and e["toolName"] == ASK_USER_TOOL["name"]), None)
    if asked is not None:
        # A client-side tool: the app answers and sends the output back (AI SDK `output-available`).
        part = {"type": f"tool-{asked['toolName']}", "toolCallId": asked["toolCallId"], "state": "output-available",
                "input": asked["input"], "output": ASK_USER_ANSWER}
        second = post("/api/chat", {"trigger": "submit-message", "id": "c1", "messages": [user, {"id": "a1", "role": "assistant", "parts": [part]}]})
        save(f"aisdk/{keyword}-answered.sse", second)
    if approval is None:
        return
    call = next(e for e in evs if e["type"] == "tool-input-available" and e["toolCallId"] == approval["toolCallId"])
    part = {
        "type": f"tool-{call['toolName']}",
        "toolCallId": call["toolCallId"],
        "state": "approval-responded",
        "input": call["input"],
        "approval": {"id": approval["approvalId"], "approved": True},
    }
    second = post("/api/chat", {"trigger": "submit-message", "id": "c1", "messages": [user, {"id": "a1", "role": "assistant", "parts": [part]}]})
    save(f"aisdk/{keyword}-approved.sse", second)


def validate_a2ui(messages: list[dict]) -> None:
    """Fails unless the official A2UI processor accepts [messages] under strict validation."""
    MessageProcessor([BasicCatalog()], options=MessageProcessorOptions(validation_config=STRICT_VALIDATION)).process_messages(messages)


A2UI_ACTION = {
    "name": "book_hotel", "surfaceId": "booking-kyoto", "sourceComponentId": "book", "timestamp": "2026-09-27T10:00:00Z",
    "context": {"city": "Kyoto", "guest": "Jane", "date": "2026-10-01", "room": ["deluxe"]},
}


def agui_a2ui() -> None:
    """The form as an `a2ui-surface` activity, then the run its action starts (`forwardedProps.a2uiAction`)."""
    user = {"id": "u1", "role": "user", "content": "find me a hotel"}
    body = {"threadId": "t", "runId": "r1", "state": {}, "messages": [user], "tools": [], "context": [], "forwardedProps": {}}
    first = post("/api/agui", body)
    activity = next(e for e in events(first) if e["type"] == "ACTIVITY_SNAPSHOT" and e["activityType"] == "a2ui-surface")
    validate_a2ui(activity["content"]["a2ui_operations"])
    save("agui/hotel.sse", first)
    action = {"id": "u2", "role": "user", "content": "Book Hotel Lumen for Jane"}
    second = post("/api/agui", {**body, "runId": "r2", "messages": [user, action], "forwardedProps": {"a2uiAction": {"userAction": A2UI_ACTION}}})
    save("agui/hotel-action.sse", second)


def aisdk_a2ui() -> None:
    """The form as a `data-a2ui` part, then the run its action starts (a `data-a2ui` part of the user turn)."""
    user = {"id": "u1", "role": "user", "parts": [{"type": "text", "text": "find me a hotel"}]}
    first = post("/api/chat", {"trigger": "submit-message", "id": "c1", "messages": [user]})
    validate_a2ui(next(e for e in events(first) if e["type"] == "data-a2ui")["data"])
    save("aisdk/hotel.sse", first)
    action = {"id": "u2", "role": "user", "parts": [
        {"type": "text", "text": "Book Hotel Lumen for Jane"},
        {"type": "data-a2ui", "data": [{"version": "v1.0", "action": A2UI_ACTION}]},
    ]}
    second = post("/api/chat", {"trigger": "submit-message", "id": "c1", "messages": [user, action]})
    save("aisdk/hotel-action.sse", second)


MCP_META = {
    "io.modelcontextprotocol/protocolVersion": "2026-07-28",
    "io.modelcontextprotocol/clientInfo": {"name": "record_fixtures", "version": "1"},
    "io.modelcontextprotocol/clientCapabilities": {"elicitation": {"form": {}, "url": {}}},
}


def mcp_call(params: dict) -> dict:
    headers = {"Accept": "application/json, text/event-stream", "MCP-Protocol-Version": "2026-07-28",
               "Mcp-Method": "tools/call", "Mcp-Name": params["name"]}
    body = {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {**params, "_meta": MCP_META}}
    return httpx.post(f"{BASE}/mcp", json=body, headers=headers, timeout=30).json()


def mcp_elicitation() -> None:
    """`book_table` asks for the party (InputRequiredResult), then completes on the answered retry."""
    call = {"name": "book_table", "arguments": {"restaurant": "Sora"}}
    first = mcp_call(call)
    assert first["result"]["resultType"] == "input_required", first
    save("mcp/elicitation-input-required.json", json.dumps(first, ensure_ascii=False, indent=1) + "\n")
    [key] = first["result"]["inputRequests"]
    answer = {"action": "accept", "content": {"party_size": 4, "time": "19:30", "seating": "outdoor", "remind_me": False}}
    done = mcp_call({**call, "inputResponses": {key: answer}, "requestState": first["result"]["requestState"]})
    assert done["result"]["resultType"] == "complete", done
    save("mcp/elicitation-complete.json", json.dumps(done, ensure_ascii=False, indent=1) + "\n")


def agui_input_interrupt() -> None:
    """A run that pauses to ask the user (no tool call: a `responseSchema`), and the resumed run."""
    encoder = EventEncoder()
    schema = {"type": "object", "properties": {
        "destination": {"type": "string", "title": "Destination"},
        "nights": {"type": "integer", "minimum": 1, "maximum": 14, "title": "Nights"},
        "budget": {"type": "string", "enum": ["low", "mid", "high"], "title": "Budget"},
    }, "required": ["destination", "nights"]}
    asked = [
        RunStartedEvent(thread_id="t", run_id="r1"),
        TextMessageStartEvent(message_id="m1", role="assistant"),
        TextMessageContentEvent(message_id="m1", delta="A few details first."),
        TextMessageEndEvent(message_id="m1"),
        RunFinishedEvent(thread_id="t", run_id="r1", outcome=RunFinishedInterruptOutcome(type="interrupt", interrupts=[
            Interrupt(id="trip-details", reason="input_required", message="Where to, and for how long?", responseSchema=schema),
        ])),
    ]
    save("agui/input-interrupt.sse", "".join(encoder.encode(e) for e in asked))
    resumed = [
        RunStartedEvent(thread_id="t", run_id="r2"),
        TextMessageStartEvent(message_id="m2", role="assistant"),
        TextMessageContentEvent(message_id="m2", delta="Planning 5 nights in Kyoto on a mid budget."),
        TextMessageEndEvent(message_id="m2"),
        RunFinishedEvent(thread_id="t", run_id="r2", outcome=RunFinishedSuccessOutcome(type="success")),
    ]
    save("agui/input-resumed.sse", "".join(encoder.encode(e) for e in resumed))


def agui_spec_events() -> None:
    """AG-UI 1.0 events encoded by the official Python SDK (subagents, state/activity deltas, usage)."""
    encoder = EventEncoder()
    run = [
        RunStartedEvent(thread_id="t", run_id="r"),
        TextMessageStartEvent(message_id="m1", role="assistant"),
        TextMessageContentEvent(message_id="m1", delta="Delegating."),
        TextMessageEndEvent(message_id="m1"),
        ToolCallStartEvent(tool_call_id="call-1", tool_call_name="delegate_task", parent_message_id="m1"),
        ToolCallArgsEvent(tool_call_id="call-1", delta='{"agent_name":"researcher","task":"Explain AG-UI"}'),
        ToolCallEndEvent(tool_call_id="call-1"),
        SubagentStartedEvent(subagent_run_id="sub-1", name="researcher", description="Researches topics", parent_tool_call_id="call-1"),
        TextMessageStartEvent(message_id="s1", role="assistant", subagent_run_id="sub-1"),
        TextMessageContentEvent(message_id="s1", delta="AG-UI streams agent events.", subagent_run_id="sub-1"),
        TextMessageEndEvent(message_id="s1", subagent_run_id="sub-1"),
        SubagentFinishedEvent(subagent_run_id="sub-1", result="AG-UI streams agent events."),
        ToolCallResultEvent(message_id="r1", tool_call_id="call-1", content="AG-UI streams agent events.", role="tool"),
        StateSnapshotEvent(snapshot={"plan": {"title": "Plan", "steps": [{"label": "Research", "status": "active"}]}}),
        StateDeltaEvent(delta=[{"op": "replace", "path": "/plan/steps/0/status", "value": "complete"}]),
        ActivitySnapshotEvent(message_id="act-1", activity_type="search", content={"query": "AG-UI", "results": 0}),
        ActivityDeltaEvent(message_id="act-1", activity_type="search", patch=[{"op": "replace", "path": "/results", "value": 3}]),
        RunFinishedEvent(thread_id="t", run_id="r", outcome={"type": "success"}, usage=[TokenUsage(input_tokens=120, output_tokens=40)]),
    ]
    save("agui/subagent-state-activity.sse", "".join(encoder.encode(e) for e in run))


ACP_OUT = Path(__file__).resolve().parent.parent / "ai-elements-acp/src/test/resources/acp"


def acp_session(keyword: str, approve: bool = True) -> None:
    """One ACP prompt turn over stdio; records the agent's messages (the wire lines it wrote)."""
    import asyncio
    import acp
    from acp import schema
    from acp.connection import StreamDirection

    received: list[dict] = []

    class Recorder:
        async def session_update(self, session_id, update, **kwargs):
            pass

        async def request_permission(self, session_id, tool_call, options, **kwargs):
            kind = "allow_once" if approve else "reject_once"
            option = next(o for o in options if o.kind == kind)
            return schema.RequestPermissionResponse(outcome=schema.AllowedOutcome(outcome="selected", option_id=option.option_id))

    async def run() -> None:
        def observe(event) -> None:
            if event.direction == StreamDirection.INCOMING:
                received.append(event.message)

        async with acp.spawn_agent_process(lambda _agent: Recorder(), sys.executable, "acp_agent.py",
                                           cwd=Path(__file__).resolve().parent, observers=[observe]) as (conn, _process):
            await conn.initialize(protocol_version=acp.PROTOCOL_VERSION, client_capabilities=schema.ClientCapabilities())
            session = await conn.new_session(cwd="/tmp", mcp_servers=[])
            await conn.prompt(session_id=session.session_id, prompt=[acp.text_block(f"please {keyword}")])

    asyncio.run(run())
    path = ACP_OUT / f"{keyword}{'' if approve else '-denied'}.jsonl"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(m, ensure_ascii=False) + "\n" for m in received))
    print("wrote", path)


def agui_tool_media() -> None:
    """An AG-UI 1.x tool result with media content parts (a screenshot), encoded by the official
    `ag_ui` SDK: Pydantic AI's AG-UI adapter sends tool results as text only for now."""
    from ag_ui.core import DataSource, ImagePart, TextPart, UrlSource
    import main as server
    png = base64.b64encode(server._page_png("https://example.com", 0)).decode()
    encoder = EventEncoder()
    run = [
        RunStartedEvent(thread_id="t", run_id="r"),
        ToolCallStartEvent(tool_call_id="call-1", tool_call_name="browse", parent_message_id="m1"),
        ToolCallArgsEvent(tool_call_id="call-1", delta='{"url":"https://example.com"}'),
        ToolCallEndEvent(tool_call_id="call-1"),
        ToolCallResultEvent(message_id="r1", tool_call_id="call-1", role="tool", content=[
            TextPart(text="Opened https://example.com"),
            ImagePart(source=DataSource(value=png, mime_type="image/png")),
            ImagePart(source=UrlSource(value="https://example.com/full.png", mime_type="image/png")),
        ]),
        TextMessageStartEvent(message_id="m2", role="assistant"),
        TextMessageContentEvent(message_id="m2", delta="The page has a sign-up button."),
        TextMessageEndEvent(message_id="m2"),
        RunFinishedEvent(thread_id="t", run_id="r", outcome={"type": "success"}),
    ]
    save("agui/tool-media.sse", "".join(encoder.encode(e) for e in run))


if __name__ == "__main__":
    if sys.argv[2:] == ["browse"]:
        aisdk("browse")
        agui_tool_media()
        sys.exit()
    if sys.argv[2:] == ["acp"]:
        for keyword in ["plan", "time"]:
            acp_session(keyword)
        acp_session("note")
        acp_session("note", approve=False)
        sys.exit()
    if sys.argv[2:] == ["ask"]:
        agui("ask")
        aisdk("ask")
        sys.exit()
    for keyword in ["delegate", "plan", "skill", "note", "device", "ask"]:
        agui(keyword)
    for keyword in ["delegate", "plan", "skill", "note", "ask", "browse"]:
        aisdk(keyword)
    agui_a2ui()
    aisdk_a2ui()
    mcp_elicitation()
    agui_input_interrupt()
    agui_spec_events()
    agui_tool_media()
