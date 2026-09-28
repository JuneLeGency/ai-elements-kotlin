#!/usr/bin/env python3
"""Builds the docs site's component catalog from the demo's Components screen.

The demo's `ComponentCatalogScreenshots` test renders every sample card and writes one PNG per
component plus `catalog.json` (category, summary, API; from `GalleryCatalog.kt`). This script turns
them into `docs/assets/components/<id>.webp` and `docs/components/*.md`, so the catalog shows exactly
what the code renders.

    adb shell am instrument -w -e class dev.ai.elements.demo.ComponentCatalogScreenshots -e catalog true \
        dev.ai.elements.demo.test/androidx.test.runner.AndroidJUnitRunner
    adb pull /sdcard/Android/data/dev.ai.elements.demo/files/catalog build/component-catalog
    python3 tools/build-component-docs.py build/component-catalog

Without an argument it regenerates the pages from the committed `docs/assets/components/catalog.json`.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "docs" / "assets" / "components"
PAGES = ROOT / "docs" / "components"

INTROS = {
    "conversation": "The chat itself: the message list, messages, the composer and what surrounds them.",
    "content": "What a reply contains: Markdown, code, math, diagrams, reasoning and sources.",
    "tools": "Tool calls, delegated agents, and the agent's computer that shows each step as the agent saw it.",
    "human-in-the-loop": "Where the agent asks the user: approvals, questions and forms.",
    "agent-structure": "How the agent works through a request: plans, tasks, chains of thought and data parts.",
    "generative-ui": "Answers with interface: JSX and A2UI rendered natively, artifacts and web previews.",
    "attachments-and-media": "Files in a conversation: images, attachments, video, documents and audio.",
    "voice": "Talking to the agent: voice mode, the persona, dictation and voice choices.",
    "developer-tools": "Elements for coding agents: terminals, stack traces, tests, files, commits and more.",
    "workflow": "An agent run as a graph.",
}


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    ASSETS.mkdir(parents=True, exist_ok=True)
    if source:
        shutil.copy(source / "catalog.json", ASSETS / "catalog.json")
        for png in sorted(source.glob("*.png")):
            webp = ASSETS / f"{png.stem}.webp"
            # Half the device resolution is sharp on the site and keeps the repository small.
            subprocess.run(["cwebp", "-quiet", "-q", "82", "-resize", "600", "0", str(png), "-o", str(webp)], check=True)
    catalog = json.loads((ASSETS / "catalog.json").read_text())

    categories: dict[str, list[dict]] = {}
    for item in catalog:
        categories.setdefault(item["category"], []).append(item)

    PAGES.mkdir(parents=True, exist_ok=True)
    index = [
        "# Components",
        "",
        "Every element with the sample the demo app's **Components** screen shows, grouped as there.",
        "The pictures are rendered from that code by a test, so they show what the library draws.",
        "Each entry names its main API; the [API reference](../api/index.html) has every parameter.",
        "",
        "| Group | Components |",
        "|---|---|",
    ]
    for slug, items in categories.items():
        title = items[0]["categoryTitle"]
        names = ", ".join(f"[{i['title']}]({slug}.md#{anchor(i['title'])})" for i in items)
        index.append(f"| [{title}]({slug}.md) | {names} |")
    index += [
        "",
        "Where the names differ from [Vercel AI Elements](https://elements.ai-sdk.dev), each entry says",
        "which of its components it corresponds to.",
        "",
        "!!! note \"MCP Apps\"",
        "    Interactive views of MCP tools render in a sandboxed WebView and need their MCP server, so they",
        "    are not in this catalog. See [MCP Apps](../protocols/mcp-apps.md); the demo app shows them with the",
        "    reference server.",
        "",
    ]
    (PAGES / "index.md").write_text("\n".join(index))

    for slug, items in categories.items():
        title = items[0]["categoryTitle"]
        page = [f"# {title}", "", INTROS.get(slug, ""), ""]
        for item in items:
            page += [
                f"## {item['title']}",
                "",
                item["summary"],
                "",
                f"![{item['title']}](../assets/components/{item['id']}.webp){{ loading=lazy width=\"400\" }}",
                "",
                "| | |",
                "|---|---|",
                f"| API | `{item['api']}` |",
            ]
            if item.get("elements"):
                page.append(f"| AI Elements | `{item['elements']}` |")
            page.append("")
        (PAGES / f"{slug}.md").write_text("\n".join(page))
    print(f"{len(catalog)} components in {len(categories)} groups → {PAGES.relative_to(ROOT)}")


def anchor(title: str) -> str:
    """The heading id Python-Markdown's toc extension gives [title]."""
    import re
    import unicodedata
    value = unicodedata.normalize("NFKD", title).encode("ascii", "ignore").decode("ascii")
    value = re.sub(r"[^\w\s-]", "", value).strip().lower()
    return re.sub(r"[-\s]+", "-", value)


if __name__ == "__main__":
    main()
