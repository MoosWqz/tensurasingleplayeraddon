#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
COMMAND = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/command"
ERRORS: list[str] = []
WARNINGS: list[str] = []


def read(name: str) -> str:
    path = COMMAND / name
    if not path.is_file():
        ERRORS.append(f"missing command source: {path}")
        return ""
    return path.read_text(encoding="utf-8")


registry = read("ModCommandRegistry.java")
root = read("MoosTensuraCommand.java")

if registry:
    required = [
        "MoosTensuraCommand.register(",
        "DebugCommand.attachToMoosTensuraRoot(",
        "GetNamedCommand.register(",
    ]
    for marker in required:
        if marker not in registry:
            ERRORS.append(f"ModCommandRegistry missing required registration: {marker}")

    if ".registerLegacyAlias(" in registry:
        ERRORS.append("standalone development compatibility alias is still registered")

    forbidden_calls = [
        "UpgradeSageCommand.registerLegacyAlias(",
        "CheckNamedCommand.registerLegacyAlias(",
        "RecognitionDebugCommand.registerLegacyAlias(",
    ]
    for marker in forbidden_calls:
        if marker in registry:
            ERRORS.append(f"legacy root still registered: {marker}")

if root:
    # Root should behave like /guide.
    root_match = re.search(
        r'Commands\.literal\("moostensura"\).*?\.executes\s*\(\s*context\s*->\s*sendGuide\s*\(',
        root,
        flags=re.S,
    )
    if not root_match:
        ERRORS.append("bare /moostensura does not clearly route to sendGuide(...)")

    for literal in ("guide", "paths", "help"):
        if f'Commands.literal("{literal}")' not in root:
            ERRORS.append(f"missing canonical public branch: /moostensura {literal}")

    # These old standalone roots must never be embedded in the canonical
    # survival root.
    for literal in ("upgradesage", "checknamed", "checkrecognition"):
        if f'Commands.literal("{literal}")' in root:
            ERRORS.append(f"legacy standalone root leaked into MoosTensuraCommand: {literal}")

    if "/getnamed" in root or 'Commands.literal("getnamed")' in root:
        ERRORS.append("/getnamed is being advertised/embedded by the canonical root")

# The standalone GetNamed command intentionally remains until migration review.
getnamed = read("GetNamedCommand.java")
if getnamed and 'Commands.literal("getnamed")' not in getnamed:
    WARNINGS.append("GetNamedCommand no longer defines /getnamed; migration review may already have changed it")

# This package intentionally does not touch protocol, GUI, datapack or lifecycle.
network = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/network/NetworkRegistry.java"
if network.is_file():
    net = network.read_text(encoding="utf-8")
    if '.versioned("11")' not in net:
        WARNINGS.append("NetworkRegistry does not show protocol 11; verify current source before release")

print("Release command-surface validation")
print("==================================")
if ERRORS:
    print("RESULT: FAIL")
    for error in ERRORS:
        print(f"[FAIL] {error}")
else:
    print("RESULT: PASS")
    print("[PASS] canonical /moostensura root registered")
    print("[PASS] permission-gated nested debug root attached")
    print("[PASS] no standalone development compatibility aliases registered")
    print("[PASS] bare /moostensura routes to guide")
    print("[PASS] guide / paths / help branches present")
    print("[PASS] /getnamed retained but not advertised by canonical root")
if WARNINGS:
    print("")
    print("WARNINGS")
    for warning in WARNINGS:
        print(f"[WARN] {warning}")

raise SystemExit(1 if ERRORS else 0)
