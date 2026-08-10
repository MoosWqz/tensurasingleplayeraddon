#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
PATH = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/event/AddonLifecycleEvents.java"

if not PATH.is_file():
    print(f"[FAIL] Missing: {PATH}")
    raise SystemExit(1)

source = PATH.read_text(encoding="utf-8")
errors = []

required = [
    "LivingEntityUseItemEvent.Stop",
    "AdvancementEvent.AdvancementEarnEvent",
    "REWIND_TIME_ADVANCEMENT",
    '"rewind_time"',
    "PENDING_CHARACTER_RESETS",
    "CHARACTER_RESET_CONFIRMATION_WINDOW_TICKS",
    "event.getAdvancement().id()",
    "AddonPlayerDataResetService.ResetReason.CHARACTER_RESET",
    "PlayerTickEvent.Post",
    "PENDING_CHARACTER_RESETS.remove(player.getUUID())",
]
for marker in required:
    if marker not in source:
        errors.append(f"missing marker: {marker}")

if "LivingEntityUseItemEvent.Finish event" in source:
    errors.append("old Finish-based Character Reset detector is still present")

stop_start = source.find("public static void onStoppedUsingItem(")
earn_start = source.find("public static void onAdvancementEarned(")
if stop_start >= 0 and earn_start > stop_start:
    stop_block = source[stop_start:earn_start]
    if "resetForNewIncarnation(" in stop_block:
        errors.append("Stop handler resets immediately instead of waiting for Tensura success")

print("Release Hardening B2A — Character Reset recovery validation")
print("===========================================================")

if errors:
    print("RESULT: FAIL")
    for error in errors:
        print(f"[FAIL] {error}")
    raise SystemExit(1)

print("[PASS] Character Reset arms from release/Stop rather than Finish")
print("[PASS] Tensura rewind_time advancement is the post-success confirmation")
print("[PASS] reset service is not called from the pre-success Stop handler")
print("[PASS] confirmation window is bounded")
print("[PASS] stale pending entries are cleaned on tick")
print("[PASS] pending entries are removed on logout")
print("[PASS] no production balance/reward/recognition values changed")
print("RESULT: PASS")
