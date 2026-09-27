"""A small MCP server (official `mcp` SDK), mounted by `main.py` at `/mcp`.

It serves both MCP eras on one Streamable HTTP endpoint: the stateless
2026-07-28 revision and the session-based 2025-xx revisions, so it exercises
both paths of the Android client. Tools cover the cases a client must handle:
read-only vs. destructive annotations, progress notifications, structured
output, and error results.
"""

from __future__ import annotations

import asyncio
from pathlib import Path
from datetime import datetime, timezone

from mcp.server.mcpserver import Context, MCPServer
from typing import Annotated, Literal

from mcp.server.elicitation import AcceptedElicitation, DeclinedElicitation, ElicitationResult
from mcp.server.mcpserver.resolve import Elicit, Resolve
from mcp.types import CallToolResult, TextContent, ToolAnnotations
from pydantic import BaseModel, Field

mcp = MCPServer(
    name="ai-elements-notes",
    title="AI Elements notes",
    version="0.3.0",
    instructions="A notebook and unit converter. Save notes only when the user asks to remember something.",
)

_NOTES: dict[str, dict] = {}

_UNITS = {
    # length → metres
    "mm": 0.001, "cm": 0.01, "m": 1.0, "km": 1000.0, "in": 0.0254, "ft": 0.3048, "mi": 1609.344,
    # mass → kilograms
    "g": 0.001, "kg": 1.0, "lb": 0.45359237, "oz": 0.028349523125,
}
_KIND = {**dict.fromkeys(["mm", "cm", "m", "km", "in", "ft", "mi"], "length"), **dict.fromkeys(["g", "kg", "lb", "oz"], "mass")}


@mcp.tool(title="Convert units", annotations=ToolAnnotations(readOnlyHint=True, idempotentHint=True))
def convert_units(value: float, from_unit: str, to_unit: str) -> str:
    """Convert a length (mm, cm, m, km, in, ft, mi) or mass (g, kg, lb, oz) between units."""
    f, t = from_unit.lower(), to_unit.lower()
    if f not in _UNITS or t not in _UNITS:
        raise ValueError(f"Unknown unit. Supported: {', '.join(_UNITS)}")
    if _KIND[f] != _KIND[t]:
        raise ValueError(f"Cannot convert {_KIND[f]} to {_KIND[t]}")
    return f"{value} {f} = {value * _UNITS[f] / _UNITS[t]:.6g} {t}"


@mcp.tool(title="Save note", annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False))
def save_note(title: str, content: str) -> str:
    """Save a note the user wants to remember. Overwrites a note with the same title."""
    _NOTES[title] = {"title": title, "content": content, "saved_at": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    return f"Saved note “{title}”. {len(_NOTES)} note(s) in total."


@mcp.tool(title="List notes", annotations=ToolAnnotations(readOnlyHint=True))
def list_notes() -> list[dict]:
    """List all saved notes with their content."""
    return list(_NOTES.values())


@mcp.tool(title="Delete note", annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=True))
def delete_note(title: str) -> str:
    """Delete a saved note by title."""
    if _NOTES.pop(title, None) is None:
        raise ValueError(f"No note titled “{title}”")
    return f"Deleted “{title}”."


@mcp.tool(title="Count slowly", annotations=ToolAnnotations(readOnlyHint=True))
async def count_slowly(to: int, ctx: Context) -> str:
    """Count from 1 to `to` (max 10), one number per half second, reporting progress. Useful to test long-running tools."""
    to = max(1, min(to, 10))
    for i in range(1, to + 1):
        await ctx.report_progress(i, to, f"Counted {i}")
        await asyncio.sleep(0.5)
    return f"Counted to {to}."


# --- MCP Apps (io.modelcontextprotocol/ui, 2026-01-26) -------------------------------------
# `show_notes_board` renders in an interactive view (a `ui://` HTML resource built on the official
# `@modelcontextprotocol/ext-apps` SDK); `board_notes` is only for that view (visibility ["app"]).

BOARD_URI = "ui://notes/board"
MCP_APP_MIME = "text/html;profile=mcp-app"
_BOARD_HTML = (Path(__file__).resolve().parent / "mcp_apps" / "notes_board.html").read_text()


@mcp.resource(BOARD_URI, name="notes-board", title="Notes board", mime_type=MCP_APP_MIME,
              meta={"ui": {"csp": {"resourceDomains": ["https://cdn.jsdelivr.net"]}, "prefersBorder": True}})
def notes_board() -> str:
    """The interactive notes board (MCP App view)."""
    return _BOARD_HTML


def _board() -> CallToolResult:
    notes = list(_NOTES.values())
    text = f"{len(notes)} note(s): " + ", ".join(n["title"] for n in notes) if notes else "No notes yet."
    return CallToolResult(content=[TextContent(type="text", text=text)], structured_content={"notes": notes})


@mcp.tool(title="Show notes board", annotations=ToolAnnotations(readOnlyHint=True),
          meta={"ui": {"resourceUri": BOARD_URI}})
def show_notes_board() -> CallToolResult:
    """Show the user's notes on an interactive board where they can add notes and ask about them."""
    return _board()


@mcp.tool(title="Board notes", annotations=ToolAnnotations(readOnlyHint=True),
          meta={"ui": {"resourceUri": BOARD_URI, "visibility": ["app"]}})
def board_notes() -> CallToolResult:
    """The notes for the board view (called by the view, not the model)."""
    return _board()


# --- Elicitation (asking the user mid-call) ------------------------------------------------
# On 2026-07-28 the SDK answers with an `InputRequiredResult` and resumes on the client's retry;
# on the 2025-xx session revisions it sends `elicitation/create` mid-call.


class TableDetails(BaseModel):
    party_size: int = Field(ge=1, le=12, description="How many people")
    time: str = Field(description="Arrival time, e.g. 19:30")
    seating: Literal["indoor", "outdoor"] = Field(default="indoor", description="Where to sit")
    remind_me: bool = Field(default=True, description="Send a reminder an hour before")


def _ask_table(restaurant: str) -> Elicit[TableDetails]:
    return Elicit(f"Booking a table at {restaurant}: how many people, and when?", TableDetails)


@mcp.tool(title="Book a table", annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=False))
def book_table(restaurant: str, details: Annotated[ElicitationResult[TableDetails], Resolve(_ask_table)]) -> str:
    """Book a restaurant table; asks the user for the party size, time and seating."""
    if isinstance(details, AcceptedElicitation):
        d = details.data
        reminder = " I'll remind you an hour before." if d.remind_me else ""
        return f"Booked a table for {d.party_size} at {restaurant}, {d.time}, {d.seating}.{reminder}"
    if isinstance(details, DeclinedElicitation):
        return f"The user declined to book a table at {restaurant}."
    return "The booking was cancelled."


@mcp.resource("notes://all", name="all-notes", title="All notes", mime_type="text/markdown")
def all_notes() -> str:
    """Every saved note as Markdown."""
    return "\n\n".join(f"## {n['title']}\n{n['content']}" for n in _NOTES.values()) or "_No notes yet._"


@mcp.prompt(title="Summarize notes")
def summarize_notes(style: str = "bullet points") -> str:
    """Ask the model to summarize the saved notes."""
    return f"Summarize my saved notes as {style}. Read them with the list_notes tool first."
