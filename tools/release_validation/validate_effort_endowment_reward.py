#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
JAVA = ROOT / "src/main/java/com/mooswqz/moostensuraaddon"

FAILURES: list[str] = []
PASSES: list[str] = []


def read(relative: str) -> str:
    path = JAVA / relative
    if not path.is_file():
        FAILURES.append(f"Missing {path.relative_to(ROOT).as_posix()}")
        return ""
    return path.read_text(encoding="utf-8")


def require(text: str, marker: str, label: str) -> None:
    if marker in text:
        PASSES.append(label)
    else:
        FAILURES.append(f"{label}: missing {marker!r}")


formula = read("recognition/RecognitionEndowmentEffortRewardFormula.java")
service = read("recognition/RecognitionEndowmentEffortRewardService.java")
strength = read("recognition/RecognitionStrengthRewardService.java")
native = read("lifecycle/RecognitionNativeEndowmentService.java")
incarnation = read("lifecycle/AddonIncarnationState.java")
unname = read("recognition/RecognitionUnnameService.java")
recognition_data = read("attachment/RecognitionData.java")
ritual = read("ritual/RecognitionNamingRitualManager.java")
getnamed = read("command/GetNamedCommand.java")
debug = read("command/RecognitionStrengthRewardDebugCommand.java")
root_command = read("command/MoosTensuraCommand.java")

if formula:
    require(formula, "1_000_000.0D", "Maximum extra EP allowance is exactly 1,000,000")
    require(formula, "extraEp / 2.0D", "Extra capacity is split evenly between magicules and aura")
    require(formula, "identityStrength", "Effort formula derives from frozen Identity Strength")
    if (
        "RecognitionPath" in formula
        or "import io.github.manasmods.tensura.storage.Alignment" in formula
    ):
        FAILURES.append("Effort formula depends on a path or alignment")
    else:
        PASSES.append("Effort formula is alignment-neutral")

if service:
    for marker, label in (
        ("recognition_effort_max_magicule", "Maximum-magicule modifier ID is stable"),
        ("recognition_effort_max_aura", "Maximum-aura modifier ID is stable"),
        ("AttributeModifier.Operation.ADD_VALUE", "Capacity uses additive attribute modifiers"),
        ("addOrReplacePermanentModifier", "Capacity modifiers are permanent and idempotent"),
        ("applyOnlyNewEnergyCapacity", "Current energy is isolated to a new-capacity method"),
        ("newMagiculeCapacity > EPSILON", "Magicule current energy requires a positive capacity delta"),
        ("newAuraCapacity > EPSILON", "Aura current energy requires a positive capacity delta"),
        ("ModifierChange.unchanged()", "Matching modifiers produce a zero-delta fast path"),
        ("clampCurrentEnergy", "Removal clamps current energy to restored maxima"),
    ):
        require(service, marker, label)

    if "setMagicule(maximumMagicule.getValue())" in service or "setAura(maximumAura.getValue())" in service:
        FAILURES.append("A synchronization path refills an energy pool directly to maximum")
    else:
        PASSES.append("No synchronization path refills a pool directly to maximum")

if strength:
    require(strength, "RecognitionEndowmentEffortRewardService.reconcile", "Normal reward synchronization repairs effort capacity")
    require(strength, "RecognitionEndowmentEffortRewardService", "Normal reward cleanup removes effort modifiers")

if native:
    require(native, "RecognitionEndowmentEffortRewardService.reconcile", "Native endowment applies the extension after naming")

if unname:
    require(unname, "RecognitionStrengthRewardService.reconcile", "Admin unname removes the effort extension immediately")
    require(unname, "clearNamingCommitPreservingLifeProgress", "Admin unname clears the complete frozen recognition result")
    require(unname, "clearNativeEndowmentState", "Admin unname permits a fresh native endowment on the next recognition")

if recognition_data:
    require(recognition_data, "public void clearNamingCommitPreservingLifeProgress()", "RecognitionData has a bounded same-life unname cleanup")

if incarnation:
    require(incarnation, "public void clearNativeEndowmentState()", "Lifecycle state can clear only the native-endowment anchor and retry")

if root_command:
    if "/checkrecognition" in root_command:
        FAILURES.append("Canonical command output still advertises removed /checkrecognition")
    else:
        PASSES.append("Canonical command output does not advertise /checkrecognition")
    require(root_command, "/moostensura debug recognition", "Admin unname points to the canonical recognition inspector")

if ritual:
    require(ritual, "RecognitionNamingService.commitRecognition(", "Altar freezes Identity Strength through the authoritative commit")

if getnamed:
    if "RecognitionEndowmentEffortRewardService" in getnamed or "RecognitionNamingService.commitRecognition" in getnamed:
        FAILURES.append("/getnamed can grant Soul Recognition effort rewards")
    else:
        PASSES.append("/getnamed remains native-only and cannot grant the effort extension")

if debug:
    require(debug, '"Endowment attributes match"', "Debug strength output reports effort modifier state")

print("Soul Recognition — Effort-Scaled Native Endowment Validation")
print("============================================================")
for item in PASSES:
    print(f"[PASS] {item}")

if FAILURES:
    print("")
    for item in FAILURES:
        print(f"[FAIL] {item}")
    print("")
    print("RESULT: FAIL")
    raise SystemExit(1)

print("")
print("RESULT: PASS")
