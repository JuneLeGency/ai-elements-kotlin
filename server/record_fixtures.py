"""Record protocol fixtures for the Kotlin client tests from real implementations.

- `agui/*.sse`, `aisdk/*.sse`: responses of this server (Pydantic AI + Harness through the official
  AG-UI and Vercel AI adapters) for each capability, including the follow-up request of multi-step
  flows (tool approval, frontend tools).
- `agui/subagent-state-activity.sse`: AG-UI 1.0 events the server does not emit yet
  (SUBAGENT_*, STATE_DELTA, ACTIVITY_*, usage), encoded by the official `ag_ui` Python SDK.

Run with the server up:  uv run python record_fixtures.py [http://localhost:8788]
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import httpx
from ag_ui.core import (
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


def agui(keyword: str) -> None:
    user = {"id": "u1", "role": "user", "content": f"please {keyword}"}
    body = {"threadId": "t", "runId": "r1", "state": {}, "messages": [user], "tools": [DEVICE_TOOL], "context": [], "forwardedProps": {}}
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
    elif call["toolCallName"] == DEVICE_TOOL["name"]:
        tool = {"id": "result-1", "role": "tool", "toolCallId": call["toolCallId"], "content": "Pixel 10, Android 17"}
        second = post("/api/agui", {**body, "runId": "r2", "messages": [user, assistant, tool]})
        save(f"agui/{keyword}-continued.sse", second)


def aisdk(keyword: str) -> None:
    user = {"id": "u1", "role": "user", "parts": [{"type": "text", "text": f"please {keyword}"}]}
    first = post("/api/chat", {"trigger": "submit-message", "id": "c1", "messages": [user]})
    save(f"aisdk/{keyword}.sse", first)
    evs = events(first)
    approval = next((e for e in evs if e["type"] == "tool-approval-request"), None)
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


if __name__ == "__main__":
    for keyword in ["delegate", "plan", "skill", "note", "device"]:
        agui(keyword)
    for keyword in ["delegate", "plan", "skill", "note"]:
        aisdk(keyword)
    agui_spec_events()
