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

import argparse
import re
import textwrap
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", type=Path)
    parser.add_argument("--check", action="store_true", help="Fail if committed pages differ; never rewrite them")
    parser.add_argument("--export-samples", type=Path, help="Export standalone Kotlin examples for the Maven consumer build")
    args = parser.parse_args()
    source = args.source
    def write(path: Path, text: str) -> None:
        if args.check:
            if not path.exists() or path.read_text() != text:
                raise SystemExit(f"Regenerate component docs: {path.relative_to(ROOT)}")
        else:
            path.write_text(text)
    ASSETS.mkdir(parents=True, exist_ok=True)
    if source:
        shutil.copy(source / "catalog.json", ASSETS / "catalog.json")
        for png in sorted(source.glob("*.png")):
            webp = ASSETS / f"{png.stem}.webp"
            # Half the device resolution is sharp on the site and keeps the repository small.
            subprocess.run(["cwebp", "-quiet", "-q", "82", "-resize", "600", "0", str(png), "-o", str(webp)], check=True)
    catalog = json.loads((ASSETS / "catalog.json").read_text())

    usage = json.loads((ROOT / "tools/component-usage.json").read_text())
    if set(usage) != {item["id"] for item in catalog}:
        raise SystemExit("Every catalog entry must have component usage metadata")
    sample_path = "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt"
    sample_source = (ROOT / sample_path).read_text()
    imports = component_imports()

    categories: dict[str, list[dict]] = {}
    for item in catalog:
        categories.setdefault(item["category"], []).append(item)

    PAGES.mkdir(parents=True, exist_ok=True)
    index = [
        "# Components",
        "",
        "Every element with the sample the demo app's **Components** screen shows, grouped as there.",
        "The pictures are rendered from that code by a test, so they show what the library draws.",
        "Each entry includes an example compiled with the demo, its artifact/imports, interaction notes,",
        "and a direct API reference. Wrap the examples in `AiElementsTheme` inside your activity's",
        "`setContent`; function parameters are the state or callbacks your app supplies.",
        "See [Installation](../getting-started/installation.md) for Gradle and manifest setup.",
        "",
        "These 65 entries are usage scenarios (some share an API), not the complete public symbol list.",
        "State factories, standalone forms and other supporting APIs are in [Supporting APIs](supporting.md).",
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
    write(PAGES / "index.md", "\n".join(index))

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
            entry = usage[item["id"]]
            symbol = entry["symbol"]
            filename = re.sub(r"([A-Z])", lambda m: "-" + m[1].lower(), symbol) + ".html"
            reference = entry.get("reference", f"../api/{entry['artifact']}/{entry['package']}/{filename}")
            marker = "component-" + item["id"]
            pattern = rf"// --8<-- \[start:{re.escape(marker)}\]\n(.*?)\s*// --8<-- \[end:{re.escape(marker)}\]"
            match = re.search(pattern, sample_source, re.S)
            if not match:
                raise SystemExit(f"Missing compiled snippet: {marker}")
            example = textwrap.dedent(match[1]).strip()
            used = set(re.findall(r"\b[A-Za-z_][A-Za-z_0-9]*\b", example))
            selected = sorted({imports[token] for token in used if token in imports})
            if " by " in example:
                selected += ["androidx.compose.runtime.getValue", "androidx.compose.runtime.setValue"]
            if args.export_samples:
                args.export_samples.mkdir(parents=True, exist_ok=True)
                (args.export_samples / (item["id"] + ".kt")).write_text(
                    "package dev.ai.elements.publishedsamples\n\n" +
                    "\n".join("import " + imp for imp in sorted(set(selected))) + "\n\n" + example + "\n"
                )
            page += [
                f"| Artifact | `{entry['artifact']}` |",
                f"| Reference | [{symbol}]({reference}) |",
                "", entry["note"], "",
                "```kotlin", *("import " + imp for imp in sorted(set(selected))), "```", "",
                "```kotlin", f'--8<-- "{sample_path}:{marker}"', "```", "",
            ]
        write(PAGES / f"{slug}.md", "\n".join(page))
    supporting = [
        "# Supporting APIs", "",
        "The catalog groups common scenarios. These public composables and state factories also",
        "have direct references below; click a name for its full parameter and lifecycle contract.",
        "Use `rememberAudioPlayerState`, `rememberSpeechInputState` and `rememberSpeechOutputState`",
        "inside composition so native resources are released when the screen leaves. A ViewModel",
        "owns the ChatController; Compose state factories own the visual/player state.", "",
        "`SchemaForm` renders a standalone JSON Schema form; pass onSubmit and onDecline.",
        "`Confirmation` can be used outside chat with onApprove/onDeny and optional onDecide.",
        "`ImageViewer`, `PdfViewerDialog` and `VideoPlayerDialog` require an onDismiss handler",
        "that removes the dialog from composition. File access goes through the documented loader",
        "or platform media APIs; requesting the file is the app's responsibility.", "",
        "| API | Artifact |", "|---|---|",
    ]
    for module in ("ai-elements-ui", "ai-elements-genui", "ai-elements-mcp-apps"):
        for path in sorted((ROOT / module / "src/main/kotlin").rglob("*.kt")):
            text = path.read_text()
            package = re.search(r"^package (.+)$", text, re.M)[1]
            for match in re.finditer(r"^fun (\w+)\(", text, re.M):
                # Annotations precede a top-level function; exclude ordinary utility functions.
                prefix = text[:match.start()].rsplit("}", 1)[-1]
                if "@Composable" not in prefix:
                    continue
                name = match[1]
                file = re.sub(r"([A-Z])", lambda m: "-" + m[1].lower(), name) + ".html"
                supporting.append(f"| [{name}](../api/{module}/{package}/{file}) | `{module}` |")
    write(PAGES / "supporting.md", "\n".join(supporting) + "\n")
    print(f"{len(catalog)} components in {len(categories)} groups → {PAGES.relative_to(ROOT)}")


def component_imports() -> dict[str, str]:
    """Resolve example types and functions to public source packages plus AndroidX imports."""
    imports = {}
    for module in ("ai-elements-chat", "ai-elements-ui", "ai-elements-genui"):
        for path in sorted((ROOT / module / "src/main/kotlin").rglob("*.kt")):
            source = path.read_text()
            package = re.search(r"^package (.+)$", source, re.M)[1]
            for match in re.finditer(r"^(?:(?:data|enum|sealed|fun) )?(?:class|interface|object|fun) (\w+)(?=[\s(<:{])", source, re.M):
                imports[match[1]] = package + "." + match[1]
    imports.update({
        "Composable": "androidx.compose.runtime.Composable",
        "remember": "androidx.compose.runtime.remember",
        "mutableStateOf": "androidx.compose.runtime.mutableStateOf",
        "rememberSaveable": "androidx.compose.runtime.saveable.rememberSaveable",
        "collectAsStateWithLifecycle": "androidx.lifecycle.compose.collectAsStateWithLifecycle",
        "Column": "androidx.compose.foundation.layout.Column",
        "height": "androidx.compose.foundation.layout.height",
        "Modifier": "androidx.compose.ui.Modifier",
        "dp": "androidx.compose.ui.unit.dp",
        "Text": "androidx.compose.material3.Text",
        "buildJsonObject": "kotlinx.serialization.json.buildJsonObject",
        "put": "kotlinx.serialization.json.put",
    })
    return imports


def anchor(title: str) -> str:
    """The heading id Python-Markdown's toc extension gives [title]."""
    import re
    import unicodedata
    value = unicodedata.normalize("NFKD", title).encode("ascii", "ignore").decode("ascii")
    value = re.sub(r"[^\w\s-]", "", value).strip().lower()
    return re.sub(r"[-\s]+", "-", value)


if __name__ == "__main__":
    main()
