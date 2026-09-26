"""A legacy-only MCP server (official `mcp` 1.x SDK, session-based 2025-xx revisions).

Used by the live tests to check that the Android client falls back from the 2026-07-28
stateless protocol to an `initialize` session. Run in its own environment:

    uv run --no-project --with "mcp<2" --with uvicorn python legacy_mcp_server.py   # port 8790
"""

import asyncio

from mcp.server.fastmcp import Context, FastMCP
from mcp.server.transport_security import TransportSecuritySettings
from mcp.types import ToolAnnotations

mcp = FastMCP(
    "legacy-notes",
    host="0.0.0.0",
    port=8790,
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
def echo(text: str) -> str:
    """Echo the text back."""
    return f"echo: {text}"


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True))
async def count_slowly(to: int, ctx: Context) -> str:
    """Count to `to` (max 5) with progress notifications."""
    to = max(1, min(to, 5))
    for i in range(1, to + 1):
        await ctx.report_progress(i, to, f"Counted {i}")
        await asyncio.sleep(0.2)
    return f"Counted to {to}."


@mcp.tool()
def fail(reason: str) -> str:
    """Always fails with the given reason."""
    raise ValueError(reason)


if __name__ == "__main__":
    mcp.run(transport="streamable-http")
