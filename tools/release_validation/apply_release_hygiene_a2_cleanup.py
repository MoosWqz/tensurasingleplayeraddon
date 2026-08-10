#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

REPLACEMENTS = {
    "src/main/java/com/mooswqz/moostensuraaddon/command/CheckNamedCommand.java": [
        (
            "Temporary compatibility alias for /checknamed.",
            "Deprecated standalone compatibility alias for /checknamed; not registered for release.",
        ),
    ],
    "src/main/java/com/mooswqz/moostensuraaddon/command/RecognitionDebugCommand.java": [
        (
            "Registers the nested debug inspector and the temporary legacy alias.",
            "Registers the canonical nested debug inspector; the old standalone alias is not registered.",
        ),
        (
            "Temporary compatibility alias for the old development command.",
            "Deprecated standalone compatibility alias retained for source compatibility.",
        ),
    ],
    "src/main/java/com/mooswqz/moostensuraaddon/command/UpgradeSageCommand.java": [
        (
            "Temporary compatibility alias for /upgradesage.",
            "Deprecated standalone compatibility alias for /upgradesage; not registered for release.",
        ),
    ],
    "src/main/java/com/mooswqz/moostensuraaddon/command/RecognitionFreedomDebugCommand.java": [
        (
            "Debug-only, read-only diagnostics for Packet 6G.5 Freedom progression.",
            "Debug-only, read-only diagnostics for recognition Freedom progression.",
        ),
    ],
    "src/main/java/com/mooswqz/moostensuraaddon/command/RecognitionTechnicalHardeningDebugCommand.java": [
        (
            "Debug-only runtime-state inspector for Packet 6G.8.",
            "Debug-only runtime-state inspector for recognition runtime hardening.",
        ),
    ],
    "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionEvaluation.java": [
        (
            "Compatibility constructor for Packet 6F/6G.2 call sites.",
            "Compatibility constructor for legacy recognition call sites.",
        ),
    ],
    "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionIdentityHistoryIntegration.java": [
        (
            "history introduced by Packet 6G.6A.",
            "history maintained by the recognition identity-history system.",
        ),
    ],
    "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionTechnicalHardeningValidationHarness.java": [
        (
            "Deterministic debug-only validation for the 6G.8 runtime hardening rules.",
            "Deterministic debug-only validation for the recognition runtime hardening rules.",
        ),
    ],
}

changed_files: list[str] = []
missing_files: list[str] = []
unmatched: list[str] = []

for relative, replacements in REPLACEMENTS.items():
    path = ROOT / relative
    if not path.is_file():
        missing_files.append(relative)
        continue

    original = path.read_text(encoding="utf-8")
    updated = original

    for old, new in replacements:
        if old in updated:
            updated = updated.replace(old, new)
        elif new in updated:
            pass  # idempotent rerun
        else:
            unmatched.append(f"{relative}: expected text not found: {old}")

    if updated != original:
        # Compatible with older Python versions where Path.write_text()
        # does not accept the newline= keyword.
        with path.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write(updated)
        changed_files.append(relative)

print("Release Hardening A2 cleanup")
print("============================")
for relative in changed_files:
    print(f"[CHANGED] {relative}")

if not changed_files:
    print("[INFO] No files changed; cleanup may already be applied.")

if missing_files:
    print("")
    print("MISSING FILES")
    for item in missing_files:
        print(f"[FAIL] {item}")

if unmatched:
    print("")
    print("UNMATCHED CURRENT-TREE TEXT")
    for item in unmatched:
        print(f"[FAIL] {item}")

if missing_files or unmatched:
    print("")
    print("RESULT: FAIL — no guess-based fallback was applied.")
    raise SystemExit(1)

print("")
print("RESULT: PASS")
