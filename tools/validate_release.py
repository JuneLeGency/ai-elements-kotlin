#!/usr/bin/env python3
"""Fail closed before any release side effects. Run from the repository root."""
import re
import sys
from pathlib import Path


def validate(tag: str, properties: str, changelog: str) -> str:
    if not re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", tag):
        raise ValueError("Expected a stable vX.Y.Z tag")
    version = tag[1:]
    if re.findall(r"^VERSION_NAME=(.+)$", properties, re.M) != [version]:
        raise ValueError("VERSION_NAME must exactly match the tag (no SNAPSHOT)")
    heading = f"## {version}"
    if changelog.splitlines().count(heading) != 1:
        raise ValueError("Expected exactly one final CHANGELOG heading: " + heading)
    section = changelog.split(heading + "\n", 1)[1].split("\n## ", 1)[0]
    if not section.strip():
        raise ValueError("Release notes must not be empty")
    return version


def validate_docs(version: str, documents: dict[str, str]) -> None:
    for name, text in documents.items():
        coordinates = re.findall(r'io\.github\.junelegency:ai-elements-bom:([^"\s)]+)', text)
        if not coordinates or set(coordinates) != {version}:
            raise ValueError(f"{name}: BOM coordinates must use release {version}")
        if "not released yet" in text.lower():
            raise ValueError(f"{name}: remove the unreleased installation notice")


if __name__ == "__main__":
    try:
        version = validate(sys.argv[1], Path("gradle.properties").read_text(), Path("CHANGELOG.md").read_text())
        validate_docs(version, {name: Path(name).read_text() for name in (
            "README.md", "docs/getting-started/installation.md",
        )})
    except (IndexError, ValueError) as error:
        sys.exit(str(error))
