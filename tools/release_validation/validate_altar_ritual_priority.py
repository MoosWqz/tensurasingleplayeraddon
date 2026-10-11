#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ROUTER_PATH = ROOT / (
    "src/main/java/com/mooswqz/moostensuraaddon/ritual/"
    "GreatCrystalAltarInteractionRouter.java"
)

failures: list[str] = []
passes: list[str] = []

if not ROUTER_PATH.is_file():
    failures.append("GreatCrystalAltarInteractionRouter.java is missing")
    router = ""
else:
    router = ROUTER_PATH.read_text(encoding="utf-8-sig")

pending_pos = router.find("hasPendingReveal(player)")
priority_pos = router.find("shouldPrioritizeGreatSage(player)")
naming_pos = router.find("shouldHandleNaming(player)")
fallback_pos = router.rfind("GreatSageRitualManager.tryStartRitual(")

if 0 <= pending_pos < priority_pos < naming_pos < fallback_pos:
    passes.append(
        "Altar order remains pending reveal -> eligible Great Sage -> "
        "Soul Recognition -> Great Sage feedback fallback"
    )
else:
    failures.append("Great Crystal Altar interaction order changed unexpectedly")

priority_match = re.search(
    r"private static boolean shouldPrioritizeGreatSage\s*\(.*?\)\s*\{(.*?)\n\s*\}",
    router,
    re.DOTALL,
)
priority_body = priority_match.group(1) if priority_match else ""

if (
    "GreatSageAwakeningHelper.findSage(player)" in priority_body
    and ".isPresent()" in priority_body
):
    passes.append("Great Sage priority still requires Sage to be present")
else:
    failures.append("Great Sage priority no longer requires Sage to be present")

if (
    "GreatSageAwakeningHelper" in priority_body
    and ".checkAltarRequirements(player)" in priority_body
    and ".successful()" in priority_body
):
    passes.append("Great Sage priority requires the complete altar check to succeed")
else:
    failures.append("Great Sage priority does not use the complete altar requirements")

legacy_markers = (
    "SAGE_UPGRADE_ENABLED.get()",
    "GREAT_SAGE_RITUAL_ENABLED.get()",
    "!GreatSageAwakeningHelper.hasGreatSage(player)",
)
if any(marker in priority_body for marker in legacy_markers):
    failures.append("Legacy partial Great Sage priority checks remain in the router")
else:
    passes.append("Legacy partial eligibility checks were removed from altar routing")

print("Great Crystal Altar Ritual Priority Validation")
print("=" * 47)
for item in passes:
    print(f"[PASS] {item}")
for item in failures:
    print(f"[FAIL] {item}")

if failures:
    raise SystemExit(1)

print(f"\nResult: PASS ({len(passes)} checks)")
