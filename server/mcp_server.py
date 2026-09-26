"""A small MCP server (official `mcp` SDK), mounted by `main.py` at `/mcp`.

It serves both MCP eras on one Streamable HTTP endpoint: the stateless
2026-07-28 revision and the session-based 2025-xx revisions, so it exercises
both paths of the Android client. Tools cover the cases a client must handle:
read-only vs. destructive annotations, progress notifications, structured
output, and error results.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone

from mcp.server.mcpserver import Context, MCPServer
from mcp.types import ToolAnnotations

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


@mcp.resource("notes://all", name="all-notes", title="All notes", mime_type="text/markdown")
def all_notes() -> str:
    """Every saved note as Markdown."""
    return "\n\n".join(f"## {n['title']}\n{n['content']}" for n in _NOTES.values()) or "_No notes yet._"


@mcp.prompt(title="Summarize notes")
def summarize_notes(style: str = "bullet points") -> str:
    """Ask the model to summarize the saved notes."""
    return f"Summarize my saved notes as {style}. Read them with the list_notes tool first."
