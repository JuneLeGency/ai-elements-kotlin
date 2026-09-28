#!/usr/bin/env python3
"""Fails when a module's instrumented tests did not really run.

`connectedDebugAndroidTest` passes when the instrumentation process dies before the first test
reports: the result has zero tests and no failures. This checks every module with androidTest
sources has a result with tests in it, and that no run ended with a crashed process.

    python3 tools/check-android-test-results.py      # after ./gradlew connectedDebugAndroidTest
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    problems = []
    modules = sorted({
        ROOT.joinpath(*p.relative_to(ROOT).parts[: p.relative_to(ROOT).parts.index("src")])
        for p in ROOT.glob("**/src/androidTest/**/*.kt")
        if "build" not in p.relative_to(ROOT).parts
    })
    for module in modules:
        name = module.relative_to(ROOT)
        results = list((module / "build/outputs/androidTest-results/connected").glob("**/TEST-*.xml"))
        if not results:
            problems.append(f"{name}: no test results")
            continue
        tests = 0
        for result in results:
            suite = ET.parse(result).getroot()
            tests += int(suite.get("tests", "0"))
            text = result.read_text(errors="replace")
            if "Process crashed" in text or "Instrumentation run failed" in text:
                problems.append(f"{name}: the test process crashed ({result.name})")
        if tests == 0:
            problems.append(f"{name}: 0 tests ran")
        print(f"{name}: {tests} tests")
    for p in problems:
        print(f"ERROR {p}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
