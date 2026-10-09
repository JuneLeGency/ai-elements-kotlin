#!/usr/bin/env python3
"""Validate the built user guide and ensure Dokka contains every published module."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import json
import re
import sys


class Page(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.language = None
        self.ids = set()
        self.targets = []
        self.feed(path.read_text())

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag == "html":
            self.language = attrs.get("lang")
        if "id" in attrs:
            self.ids.add(attrs["id"])
        key = "href" if tag == "a" else "src" if tag == "img" else None
        if key and key in attrs:
            self.targets.append(attrs[key])


def check(root):
    root = root.resolve()
    errors = []
    cache = {}
    def parse(path):
        if path not in cache:
            cache[path] = Page(path)
        return cache[path]
    pages = [p for p in root.rglob("*.html") if "api" not in p.relative_to(root).parts]
    if not pages:
        errors.append("No documentation pages generated")
    for page in pages:
        parsed = parse(page)
        expected_language = "zh" if page.relative_to(root).parts[0] == "zh" else "en"
        if not (parsed.language or "").startswith(expected_language):
            errors.append(f"{page.relative_to(root)}: expected {expected_language} HTML language")
        for target in parsed.targets:
            url = urlsplit(target)
            if (url.scheme or url.netloc) and not (
                url.netloc == "junelegency.github.io" and url.path.startswith("/ai-elements-kotlin/")
            ):
                continue
            path = unquote(url.path)
            if path.startswith("/ai-elements-kotlin/"):
                resolved = root / path.removeprefix("/ai-elements-kotlin/")
            elif path.startswith("/"):
                resolved = root / path.lstrip("/")
            else:
                resolved = (page.parent / path).resolve() if path else page
            if resolved.is_dir():
                resolved /= "index.html"
            if not resolved.is_file():
                errors.append(f"{page.relative_to(root)}: missing {target}")
            elif url.fragment and resolved.suffix == ".html":
                other = parse(resolved)
                if unquote(url.fragment) not in other.ids:
                    errors.append(f"{page.relative_to(root)}: missing anchor {target}")
    english_sources = {p.relative_to(Path("docs")) for p in Path("docs").rglob("*.md")
                       if "zh" not in p.relative_to(Path("docs")).parts and "assets" not in p.parts}
    for relative in english_sources:
        if not (Path("docs/zh") / relative).is_file():
            errors.append(f"Missing Chinese documentation counterpart: {relative}")
    for name in ("README.md", "README.zh-CN.md"):
        text = Path(name).read_text()
        for image in re.findall(r'(?:src="|!\[[^\]]*\]\()(docs/[^"\s)]+)', text):
            if not Path(image).is_file():
                errors.append(f"{name}: missing screenshot {image}")
    # Baselines are the published library inventory, excluding the BOM (no API).
    modules = sorted([*Path(".").glob("ai-elements-*/api/*.api"), *Path("harness").glob("*/api/*.api")])
    if not modules:
        errors.append("No published API baselines found")
    for baseline in modules:
        module = root / "api" / baseline.parent.parent
        symbols = [p for p in module.rglob("*.html") if p.name not in ("index.html", "navigation.html")]
        if not symbols:
            errors.append(f"Dokka generated no symbol pages for {baseline.stem}")
    search = root / "api/scripts/pages.json"
    if not search.is_file() or len(json.loads(search.read_text())) < len(modules):
        errors.append("Dokka search index is empty or incomplete")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"Checked {len(pages)} guide pages and {len(modules)} API modules: links, images, anchors and non-empty symbol output")


if __name__ == "__main__":
    check(Path(sys.argv[1] if len(sys.argv) > 1 else "site"))
