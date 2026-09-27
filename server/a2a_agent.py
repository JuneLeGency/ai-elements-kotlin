"""An A2A agent (official `a2a-sdk`) wrapping a Pydantic AI agent, mounted by `main.py`.

Agent card:  GET  /.well-known/agent-card.json
JSON-RPC:    POST /a2a   (A2A 1.0, plus 0.3 compatibility)

The executor streams the Pydantic AI run as A2A events: a `working` status,
the answer as appended artifact chunks, then `completed`. If the request is
empty it asks for input (`input-required`), so clients can exercise
multi-turn tasks. Tools' custom events that define `to_a2a_parts()` become
artifacts of their own (e.g. A2UI surfaces, per the A2UI A2A extension).
"""

from __future__ import annotations

import os
from collections.abc import Callable

from a2a.helpers.proto_helpers import new_task_from_user_message, new_text_part
from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.request_handlers.response_helpers import agent_card_to_dict
from a2a.server.routes import create_jsonrpc_routes
from a2a.utils.constants import AGENT_CARD_WELL_KNOWN_PATH
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route
from a2a.server.tasks import InMemoryTaskStore, TaskUpdater
from a2a.types import AgentCapabilities, AgentCard, AgentExtension, AgentInterface, AgentProvider, AgentSkill, TaskState
from google.protobuf.json_format import MessageToDict
from pydantic_ai import Agent, AgentRunResultEvent, CustomEvent, PartDeltaEvent, PartStartEvent, TextPart, TextPartDelta
from pydantic_ai.models import Model


class PydanticAIExecutor(AgentExecutor):
    """Runs a Pydantic AI agent for each A2A message, keeping history per A2A context."""

    def __init__(self, agent: Agent, model: Callable[[], Model], deps: Callable[[dict], object] | None = None) -> None:
        self.agent = agent
        self.model = model
        self.deps = deps
        self.history: dict[str, list] = {}

    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        task = context.current_task
        if task is None:
            task = new_task_from_user_message(context.message)
            await event_queue.enqueue_event(task)
        updater = TaskUpdater(event_queue, task.id, task.context_id)
        prompt = context.get_user_input().strip()
        if not prompt:
            await updater.requires_input(updater.new_agent_message([new_text_part("What should I research?")]))
            return

        await updater.start_work(updater.new_agent_message([new_text_part("Reading the question…")]))
        history = self.history.get(task.context_id, [])
        artifact_id = f"answer-{task.id}"
        first = True
        # `deps` sees the request message as A2A JSON (e.g. to read an A2UI action part).
        deps = self.deps(MessageToDict(context.message)) if self.deps else None
        await updater.update_status(TaskState.TASK_STATE_WORKING, updater.new_agent_message([new_text_part("Writing the answer…")]))
        async with self.agent.run_stream_events(prompt, message_history=history, model=self.model(), deps=deps) as events:
            async for event in events:
                delta = ""
                if isinstance(event, PartStartEvent) and isinstance(event.part, TextPart):
                    delta = event.part.content
                elif isinstance(event, PartDeltaEvent) and isinstance(event.delta, TextPartDelta):
                    delta = event.delta.content_delta
                elif isinstance(event, CustomEvent) and hasattr(event, "to_a2a_parts"):
                    artifact, parts = event.to_a2a_parts()
                    await updater.add_artifact(parts, artifact_id=f"{artifact}-{task.id}", name=artifact, last_chunk=True)
                elif isinstance(event, AgentRunResultEvent):
                    self.history[task.context_id] = event.result.all_messages()
                if delta:
                    await updater.add_artifact([new_text_part(delta)], artifact_id=artifact_id, name="answer", append=not first)
                    first = False
        await updater.add_artifact([new_text_part("")], artifact_id=artifact_id, name="answer", append=not first, last_chunk=True)
        await updater.complete()

    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        task = context.current_task
        if task is not None:
            await TaskUpdater(event_queue, task.id, task.context_id).cancel()


RESEARCH_SKILL = AgentSkill(
    id="research",
    name="Research",
    description="Explain protocols such as AG-UI, the AI SDK stream, MCP and A2A, with trade-offs.",
    tags=["research", "protocols"],
    examples=["Compare AG-UI and the AI SDK UI message stream"],
)


def a2a_routes(
    agent: Agent,
    model: Callable[[], Model],
    base_url: str,
    deps: Callable[[dict], object] | None = None,
    *,
    name: str = "Research agent",
    description: str = "Answers research questions about AI UI protocols and Android UI with concise, sourced summaries.",
    skills: list[AgentSkill] | None = None,
    extensions: list[AgentExtension] | None = None,
) -> list:
    """Starlette routes for the agent card and the JSON-RPC endpoint (mount them under a path for more agents)."""
    card = AgentCard(
        name=name,
        description=description,
        version="0.3.0",
        provider=AgentProvider(organization="AI Elements demo", url="https://github.com/junelegency"),
        supported_interfaces=[AgentInterface(url=f"{base_url}/a2a", protocol_binding="JSONRPC", protocol_version="1.0")],
        capabilities=AgentCapabilities(streaming=True, extensions=extensions or []),
        default_input_modes=["text/plain"],
        default_output_modes=["text/markdown"],
        skills=skills or [RESEARCH_SKILL],
    )
    handler = DefaultRequestHandler(agent_executor=PydanticAIExecutor(agent, model, deps), task_store=InMemoryTaskStore(), agent_card=card)

    async def card_for_request(request: Request) -> JSONResponse:
        # Advertise the interface at the address the client used (emulator alias, LAN IP,
        # localhost) and mount path, unless PUBLIC_URL pins it.
        served = AgentCard()
        served.CopyFrom(card)
        if not os.environ.get("PUBLIC_URL"):
            del served.supported_interfaces[:]
            served.supported_interfaces.append(
                AgentInterface(url=f"{str(request.url).split(AGENT_CARD_WELL_KNOWN_PATH)[0]}/a2a", protocol_binding="JSONRPC", protocol_version="1.0")
            )
        return JSONResponse(agent_card_to_dict(served))

    return [Route(AGENT_CARD_WELL_KNOWN_PATH, card_for_request, methods=["GET"])] + create_jsonrpc_routes(handler, rpc_url="/a2a", enable_v0_3_compat=True)
