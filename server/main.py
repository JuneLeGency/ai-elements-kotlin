"""AI Elements demo gateway.

A tiny FastAPI service that runs a PydanticAI agent against an OpenAI-compatible
endpoint (the local CLIProxyAPI / tinker-llm-gw on :9090) and streams the reply
as the **Vercel AI Data Stream Protocol** so the Kotlin/Compose demo can consume
it with `VercelDataStreamBackend`.

Data Stream byte format (each line is `<code>:<json>\n\n`):
  0:{"id":...}          stream start
  9:<delta text>        text delta
  a:<delta reasoning>   reasoning delta
  d:{"url":...,"title":...}   source-url
  3:{"message":...}     error
  2:{"finishReason":...,"usage":...}   finish
"""

from __future__ import annotations

import json
import os
import time
import uuid

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

# --- CLIProxyAPI / OpenAI-compatible endpoint config -----------------------
# Default to the local tinker-llm-gw. Override via env / query params.
DEFAULT_BASE_URL = os.environ.get("CLIPROXY_BASE_URL", "http://localhost:9090/v1")
DEFAULT_API_KEY = os.environ.get("CLIPROXY_API_KEY", "")
DEFAULT_MODEL = os.environ.get("CLIPROXY_MODEL", "gpt-4o")

app = FastAPI(title="AI Elements gateway")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    messages: list[dict] = Field(default_factory=list)
    model: str = DEFAULT_MODEL
    base_url: str = DEFAULT_BASE_URL
    api_key: str = DEFAULT_API_KEY
    temperature: float = 0.7
    max_tokens: int | None = None
    stream: bool = True


def _build_agent(model: str, base_url: str, api_key: str):
    """Build a PydanticAI agent wired to the OpenAI-compatible endpoint."""
    from pydantic_ai import Agent
    from pydantic_ai.models.openai import OpenAIChatModel
    from pydantic_ai.providers.openai import OpenAIProvider

    provider = OpenAIProvider(
        base_url=base_url,
        # Fall back to the server-level key (env) when the request omits one.
        api_key=api_key or DEFAULT_API_KEY or "not-needed",
    )
    model_obj = OpenAIChatModel(model, provider=provider)
    return Agent(model_obj, system_prompt="You are a concise, helpful assistant.")


def _data_stream_line(code: str, payload: str) -> str:
    """One Data Stream frame: `<code>:<payload>\n\n`."""
    return f"{code}:{payload}\n\n"


async def _stream_chat(req: ChatRequest):
    """Yield Data Stream frames while the agent streams tokens."""
    stream_id = str(uuid.uuid4())
    yield _data_stream_line("0", json.dumps({"id": stream_id}))

    started = time.perf_counter()
    reasoning_emitted = False
    try:
        agent = _build_agent(req.model, req.base_url, req.api_key)
        # Use the most recent user message as the prompt (single-turn demo).
        prompt = next(
            (m.get("content", "") for m in reversed(req.messages) if m.get("role") == "user"),
            "Hello!",
        )

        async with agent.run_stream(prompt) as run:
            async for text in run.stream_text():
                yield _data_stream_line("9", text)

        # Emit a couple of sources so the Sources component has something to show.
        yield _data_stream_line(
            "d", json.dumps({"url": "https://ai-sdk.dev/docs/ai-sdk-ui/data-stream-protocol", "title": "Vercel AI Data Stream Protocol"})
        )
        yield _data_stream_line(
            "d", json.dumps({"url": "https://pydantic.ai", "title": "PydanticAI"})
        )

        elapsed_ms = int((time.perf_counter() - started) * 1000)
        yield _data_stream_line(
            "2",
            json.dumps(
                {
                    "finishReason": "stop",
                    "usage": {"durationMs": elapsed_ms},
                }
            ),
        )
    except Exception as exc:  # noqa: BLE001 - surface any provider/network error to the client
        import traceback

        traceback.print_exc()
        msg = f"{type(exc).__name__}: {exc}"
        yield _data_stream_line("3", json.dumps({"message": msg}))
        yield _data_stream_line("2", json.dumps({"finishReason": "error"}))


@app.post("/api/chat")
async def chat(req: ChatRequest):
    return StreamingResponse(
        _stream_chat(req),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "ai-elements-gateway",
        "default_model": DEFAULT_MODEL,
        "default_base_url": DEFAULT_BASE_URL,
        "has_key": bool(DEFAULT_API_KEY),
    }
