#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

NAMING = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionNamingService.java"
PROGRESS = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionProgressScreenService.java"
ENDOWMENT = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/lifecycle/RecognitionNativeEndowmentService.java"

failures: list[str] = []
passes: list[str] = []

def read(path: Path) -> str:
    if not path.is_file():
        failures.append(f"Missing {path.relative_to(ROOT).as_posix()}")
        return ""
    return path.read_text(encoding="utf-8")

naming = read(NAMING)
progress = read(PROGRESS)
endowment = read(ENDOWMENT)

if naming:
    if re.search(
        r"else\s+if\s*\(\s*nativeNamed\s*\)\s*\{.*?ALREADY_NAMED",
        naming,
        re.DOTALL,
    ):
        failures.append("Native Tensura naming still blocks Soul Recognition eligibility")
    else:
        passes.append("Native Tensura naming is no longer an eligibility blocker")

    if "boolean nativeNamed" in naming and "nativeNamed," in naming and "nativeName," in naming:
        passes.append("Native name metadata is still retained in RecognitionNamingEligibility")
    else:
        failures.append("Native name metadata was accidentally removed from eligibility")

    # The first real blocker after an existing commitment should now be the
    # recognition level/candidate policy, not native naming.
    committed_pos = naming.find("ALREADY_COMMITTED")
    level_pos = naming.find("REQUIRED_EXPERIENCE_LEVEL", committed_pos)
    no_selection_pos = naming.find("NO_RECOGNITION_SELECTION", committed_pos)
    ready_pos = naming.find("Status.READY", committed_pos)
    if committed_pos >= 0 and level_pos >= 0 and no_selection_pos >= 0 and ready_pos >= 0:
        passes.append("Normal level/path/READY eligibility chain remains present")
    else:
        failures.append("Could not prove the normal recognition eligibility chain remains intact")

    if (
        "eligibility.status()" in naming
        and "!= RecognitionNamingEligibility.Status.READY" in naming
        and "eligibility.candidate() == null" in naming
    ):
        passes.append("Recognition commitment still requires an authoritative READY candidate")
    else:
        failures.append("Recognition commit READY/candidate guard is missing or changed")

if progress:
    forbidden = (
        "Recognition naming cannot begin while a native name is active.",
        "Tensura already recognizes another name.",
    )
    for text in forbidden:
        if text in progress:
            failures.append(f"Blocking native-name UI wording remains: {text}")

    if "A native Tensura name is already present." in progress:
        passes.append("Defensive ALREADY_NAMED heading is informational")
    else:
        failures.append("Informational native-name fallback heading is missing")

    if "Soul Recognition remains available independently of the native name." in progress:
        passes.append("Defensive ALREADY_NAMED detail explicitly preserves Soul Recognition")
    else:
        failures.append("Non-blocking native-name fallback detail is missing")

if endowment:
    if (
        "committed && nativeNamed && !markerMatches" in endowment
        and "markNativeEndowmentApplied" in endowment
    ):
        passes.append("Already-native committed players are anchored without requiring another naming request")
    else:
        failures.append(
            "Could not prove already-native committed players use the existing native-endowment anchor path"
        )

    if "RequestNamingMenuPacket.NamingType.HIGH" in endowment:
        passes.append("Un-named committed players still use Tensura HIGH native endowment")
    else:
        failures.append("Tensura HIGH endowment route is missing")

print("Release Hardening B2B1 — Native-Named Compatibility Validation")
print("==============================================================")
for item in passes:
    print(f"[PASS] {item}")

if failures:
    print("")
    for item in failures:
        print(f"[FAIL] {item}")
    print("")
    print("RESULT: FAIL")
    raise SystemExit(1)

print("")
print("RESULT: PASS")
