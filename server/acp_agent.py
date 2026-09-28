"""The AI Elements agent over the Agent Client Protocol (ACP), with Pydantic AI Harness.

`PydanticAIACPAgent` (pydantic_ai_harness.experimental.acp) adapts the same agent the HTTP
endpoints serve: text, reasoning and tool calls stream as `session/update`s, tools that need
approval ask with `session/request_permission`, and the plan kept by the `Planning` capability is
mirrored as ACP `plan` updates (see `main.mirror_plan`).

    uv run python acp_agent.py      # stdio, the transport ACP defines (editors launch this)

`main.py` also serves it at `ws://…/acp` — the ACP Kotlin SDK's WebSocket transport, one JSON-RPC
message per text frame — so a phone can reach it.
"""
from __future__ import annotations

import asyncio
import contextlib
import warnings
from typing import Any

import acp
from pydantic_ai_harness.experimental import HarnessExperimentalWarning

with warnings.catch_warnings():
    warnings.simplefilter("ignore", HarnessExperimentalWarning)
    from pydantic_ai_harness.experimental.acp import AcpSession, AcpSessionConfig, InMemorySessionStore, PydanticAIACPAgent, run_acp_stdio

import main


def session_config(session: AcpSession) -> AcpSessionConfig[main.RunState]:
    return AcpSessionConfig(deps=main.RunState(protocol="acp", acp=(session.client, session.session_id)))


# Sessions outlive their connection, so a client can reopen one with `session/load`: the adapter
# replays its transcript as `session/update`s (how an ACP client restores or replays a session).
SESSIONS = InMemorySessionStore()


def build_adapter() -> PydanticAIACPAgent:
    return PydanticAIACPAgent(
        main.agent,
        name="AI Elements agent",
        session_config=session_config,
        models=[main.DEFAULT_MODEL],
        model_resolver=main.resolve_model,
        session_store=SESSIONS,
    )


# --- WebSocket transport ------------------------------------------------------
# The ACP Python SDK runs an agent over any asyncio stream pair; each WebSocket text frame is one
# NDJSON line of the stdio transport.


class _FrameTransport(asyncio.WriteTransport):
    """Writes each complete line the agent emits as one queued frame."""

    def __init__(self, frames: asyncio.Queue[str | None]) -> None:
        super().__init__()
        self._frames = frames
        self._buffer = b""
        self._closing = False

    def write(self, data: bytes) -> None:  # type: ignore[override]
        if self._closing:
            return
        self._buffer += data
        *lines, self._buffer = self._buffer.split(b"\n")
        for line in lines:
            if line.strip():
                self._frames.put_nowait(line.decode())

    def is_closing(self) -> bool:  # type: ignore[override]
        return self._closing

    def close(self) -> None:  # type: ignore[override]
        self._closing = True
        self._frames.put_nowait(None)

    def can_write_eof(self) -> bool:  # type: ignore[override]
        return False

    def abort(self) -> None:  # type: ignore[override]
        self.close()

    def get_extra_info(self, name: str, default: Any = None) -> Any:  # type: ignore[override]
        return default


class _Protocol(asyncio.Protocol):
    async def _drain_helper(self) -> None:
        return None


async def serve_websocket(websocket) -> None:
    """Run one ACP connection over a Starlette/FastAPI WebSocket until either side closes."""
    await websocket.accept()
    loop = asyncio.get_running_loop()
    reader = asyncio.StreamReader(limit=50 * 1024 * 1024)
    frames: asyncio.Queue[str | None] = asyncio.Queue()
    writer = asyncio.StreamWriter(_FrameTransport(frames), _Protocol(), None, loop)

    async def receive() -> None:
        try:
            while True:
                reader.feed_data((await websocket.receive_text()).encode() + b"\n")
        except Exception:
            pass
        finally:
            reader.feed_eof()

    async def send() -> None:
        while (frame := await frames.get()) is not None:
            await websocket.send_text(frame)

    tasks = [asyncio.create_task(receive()), asyncio.create_task(send())]
    try:
        await acp.run_agent(build_adapter(), writer, reader, use_unstable_protocol=True)
    finally:
        frames.put_nowait(None)
        for task in tasks:
            task.cancel()
        with contextlib.suppress(Exception):
            await websocket.close()


if __name__ == "__main__":
    asyncio.run(run_acp_stdio(
        main.agent,
        name="AI Elements agent",
        session_config=session_config,
        models=[main.DEFAULT_MODEL],
        model_resolver=main.resolve_model,
        session_store=SESSIONS,
    ))
