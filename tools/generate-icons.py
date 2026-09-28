#!/usr/bin/env python3
"""Generates each module's icons from Material Symbols Rounded (Apache 2.0, google/material-design-icons).

Material Symbols replace the frozen `material-icons-extended` set: one consistent rounded style,
weight 400, 24dp grid. The icons a module uses are found in its sources (`AiIcons.X`,
`DemoIcons.X`, …, named like the Compose icons they replaced), fetched once and generated as
`ImageVector`s — no extra dependency, and only the icons that are used.

    python3 tools/generate-icons.py            # regenerate all modules
    python3 tools/generate-icons.py --migrate  # also rewrite `Icons.Outlined.X` usages first

Names: CamelCase of the symbol (`expand_more` → `ExpandMore`); `…Filled` uses the filled variant.
A few Compose names map to renamed symbols (see RENAMED).
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "build" / "material-symbols"
BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android"

# module sources, generated file, object name, visibility, package
MODULES = [
    ("ai-elements-ui/src/main/kotlin", "ai-elements-ui/src/main/kotlin/dev/ai/elements/ui/icons/AiIcons.kt", "AiIcons", "", "dev.ai.elements.ui.icons"),
    ("ai-elements-genui/src/main/kotlin", "ai-elements-genui/src/main/kotlin/dev/ai/elements/genui/icons/GenUiIcons.kt", "GenUiIcons", "internal ", "dev.ai.elements.genui.icons"),
    ("demo/src/main/kotlin", "demo/src/main/kotlin/dev/ai/elements/demo/ui/DemoIcons.kt", "DemoIcons", "internal ", "dev.ai.elements.demo.ui"),
]

# Compose icon names whose symbol has another name.
RENAMED = {
    "HelpOutline": "help", "ErrorOutline": "error", "BookmarkBorder": "bookmark", "InsertDriveFile": "draft",
    "FavoriteBorder": "favorite", "StarBorder": "star", "WarningAmber": "warning", "Forward10": "forward_10",
    "Replay10": "replay_10", "Inventory2": "inventory_2", "CameraAlt": "photo_camera", "CloudQueue": "cloud",
    "FileUpload": "upload_file", "DeleteOutline": "delete", "Headset": "headphones", "Phone": "call",
    "Payment": "credit_card",
}

# Icons that flip in right-to-left layouts (Compose's AutoMirrored set).
MIRRORED = {"ArrowBack", "ArrowForward", "Send", "OpenInNew", "HelpOutline", "Login", "Logout", "Subject",
            "PlaylistAdd", "Chat", "ChatFilled", "Forward", "VolumeUp", "VolumeDown", "VolumeMute", "VolumeOff",
            "StarHalf", "Undo", "Redo", "List", "Notes", "ReceiptLong"}


def symbol(name: str) -> tuple[str, bool]:
    filled = name.endswith("Filled")
    base = name[: -len("Filled")] if filled else name
    return RENAMED.get(base) or re.sub(r"(?<!^)(?=[A-Z])", "_", base).lower(), filled


def fetch(sym: str, filled: bool) -> str:
    CACHE.mkdir(parents=True, exist_ok=True)
    file = CACHE / f"{sym}{'_fill1' if filled else ''}.xml"
    if not file.exists():
        url = f"{BASE}/{sym}/materialsymbolsrounded/{sym}{'_fill1' if filled else ''}_24px.xml"
        subprocess.run(["curl", "-sfL", "-o", str(file), url], check=True)
    return file.read_text()


def migrate(src: Path, obj: str, package: str) -> None:
    """Rewrites Compose `Icons.*.X` usages to `obj.X` (`Icons.Filled.X` → `obj.XFilled`)."""
    for kt in src.rglob("*.kt"):
        text = kt.read_text()
        new = re.sub(r"Icons\.(?:AutoMirrored\.)?Filled\.(\w+)", rf"{obj}.\1Filled", text)
        new = re.sub(r"Icons\.(?:AutoMirrored\.)?(?:Outlined|Rounded|Default)\.(\w+)", rf"{obj}.\1", new)
        if new == text:
            continue
        new = re.sub(r"^import androidx\.compose\.material\.icons\..*\n", "", new, flags=re.M)
        own = kt.read_text().startswith(f"package {package}\n")
        if not own and f"import {package}.{obj}\n" not in new:
            new = re.sub(r"^(package [\w.]+\n\n?)", rf"\1import {package}.{obj}\n", new, count=1)
        kt.write_text(new)


def generate(src: Path, out: Path, obj: str, visibility: str, package: str) -> int:
    used = sorted({m for kt in src.rglob("*.kt") if kt != out for m in re.findall(rf"\b{obj}\.(\w+)", kt.read_text())})
    props = []
    for name in used:
        sym, filled = symbol(name)
        xml = fetch(sym, filled)
        paths = re.findall(r'android:pathData="([^"]+)"', xml)
        # Most symbols draw on a 960 grid; a few ship with a 24 viewport.
        viewport = re.search(r'android:viewportWidth="([\d.]+)"', xml)
        size = f"{float(viewport.group(1)):g}" if viewport else "960"
        if not paths:
            sys.exit(f"{name}: no path in {sym}")
        args = ", ".join(f'"{p}"' for p in paths)
        mirror = ", autoMirror = true" if name in MIRRORED else ""
        # Guard: every coordinate must fit the viewport, or the icon draws off its box.
        extent = max((abs(float(n)) for d in paths for n in re.findall(r"-?\d+(?:\.\d+)?", d)), default=0)
        if extent > float(size) * 1.01:
            sys.exit(f"{name}: path reaches {extent}, outside the {size} viewport of {sym}")
        grid = "" if size == "960" else f", viewport = {size}f"
        props.append(f'    val {name}: ImageVector by lazy {{ symbol("{name}", {args}{mirror}{grid}) }}')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(f'''// Generated by tools/generate-icons.py from Material Symbols Rounded (Apache License 2.0,
// https://github.com/google/material-design-icons). Do not edit; add a usage and regenerate.
package {package}

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Material Symbols Rounded (weight 400) used by this module. */
{visibility}object {obj} {{
{chr(10).join(props)}
}}

private fun symbol(name: String, vararg paths: String, autoMirror: Boolean = false, viewport: Float = 960f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport, autoMirror = autoMirror).apply {{
        paths.forEach {{ addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }}
    }}.build()
''')
    return len(used)


if __name__ == "__main__":
    for src, out, obj, visibility, package in MODULES:
        if "--migrate" in sys.argv:
            migrate(ROOT / src, obj, package)
        print(f"{obj}: {generate(ROOT / src, ROOT / out, obj, visibility, package)} icons")
