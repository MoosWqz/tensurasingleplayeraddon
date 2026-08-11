#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

NAMING = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionNamingService.java"
PROGRESS = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionProgressScreenService.java"
ENDOWMENT = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/lifecycle/RecognitionNativeEndowmentService.java"
NATIVE_NAME_STORAGE = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionNativeNameStorageService.java"
ROUTER = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/ritual/GreatCrystalAltarInteractionRouter.java"
RITUAL = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/ritual/RecognitionNamingRitualManager.java"

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
native_name_storage = read(NATIVE_NAME_STORAGE)
router = read(ROUTER)
ritual = read(RITUAL)

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
    reveal_state_pos = endowment.find("boolean revealPending")
    reveal_guard_pos = endowment.find("if (revealPending)")
    native_repair_pos = endowment.find("if (committed && nativeNamed)")
    high_request_pos = endowment.find(
        "RequestNamingMenuPacket.NamingType.HIGH"
    )

    if (
        0 <= reveal_state_pos < reveal_guard_pos < native_repair_pos
        and reveal_guard_pos < high_request_pos
    ):
        passes.append(
            "Pending reveals block both stored-name repair and fresh native HIGH endowment"
        )
    else:
        failures.append(
            "Native naming can still publish the frozen result before reveal completion"
        )

    if (
        "committed && nativeNamed" in endowment
        and "RecognitionNativeNameStorageService.write(" in endowment
        and "nativeRecognitionName" in endowment
        and "markNativeEndowmentApplied" in endowment
    ):
        passes.append(
            "Already-native committed players synchronize the frozen title and anchor without another naming request"
        )
    else:
        failures.append(
            "Could not prove already-native committed players synchronize their stored title before anchoring"
        )

    if (
        "!nativeNameMatches" in endowment
        and "!customNameMatches" in endowment
        and "getNativeEndowmentNextAttemptEpochMillis" in endowment
        and "recordNativeEndowmentFailure" in endowment
    ):
        passes.append("Native stored/custom-name repair uses the bounded lifecycle retry state")
    else:
        failures.append("Native stored/custom-name repair is missing bounded retry handling")

    if "RequestNamingMenuPacket.NamingType.HIGH" in endowment:
        passes.append("Un-named committed players still use Tensura HIGH native endowment")
    else:
        failures.append("Tensura HIGH endowment route is missing")

    if (
        "RecognitionDisplayNameService" in endowment
        and ".buildNativeTensuraName(" in endowment
        and "RecognitionStatKeys.BESTOWED_TITLE" in endowment
        and re.search(
            r"RequestNamingMenuPacket\.name\(.*?nativeRecognitionName\s*\)",
            endowment,
            re.DOTALL,
        )
    ):
        passes.append("Fresh native naming receives the frozen recognition display name")
    else:
        failures.append("Fresh native naming still omits the frozen recognition title")

if native_name_storage:
    if (
        'method.getName().equals("setName")' in native_name_storage
        and "existence.markDirty()" in native_name_storage
        and "storedName.equals(expectedName)" in native_name_storage
    ):
        passes.append("Stored Tensura name writes are verified through the runtime setter")
    else:
        failures.append("Stored Tensura name writes are not setter-backed and verified")

    if "RequestNamingMenuPacket" in native_name_storage:
        failures.append("Stored-name synchronization can accidentally invoke native endowment")
    else:
        passes.append("Stored-name synchronization is isolated from native HIGH endowment")

    if (
        "ServerPlayer player" in native_name_storage
        and "player.setCustomName(" in native_name_storage
        and "currentCustomName(player)" in native_name_storage
        and "storedCustomName.equals(expectedName)" in native_name_storage
    ):
        passes.append("Stored Tensura and Minecraft custom names are written and verified together")
    else:
        failures.append("Minecraft player custom-name synchronization is missing or unverified")

if router:
    pending_pos = router.find("hasPendingReveal(player)")
    sage_pos = router.find("shouldPrioritizeGreatSage(player)")
    naming_pos = router.find("shouldHandleNaming(player)")

    if 0 <= pending_pos < sage_pos < naming_pos:
        passes.append(
            "Altar resumes committed reveals, then prioritizes Great Sage, then starts new recognition"
        )
    else:
        failures.append("Great Crystal Altar ritual priority is not pending reveal -> Great Sage -> recognition")

    for marker, label in (
        ("SAGE_UPGRADE_ENABLED", "Disabled Great Sage progression cannot deadlock recognition"),
        ("GREAT_SAGE_RITUAL_ENABLED", "Disabled Great Sage altar ritual cannot deadlock recognition"),
        ("GreatSageAwakeningHelper.hasGreatSage", "Existing Great Sage bypasses Sage-first routing"),
        ("GreatSageAwakeningHelper.findSage", "Sage presence controls Great Sage priority"),
    ):
        if marker in router:
            passes.append(label)
        else:
            failures.append(f"{label}: missing {marker}")

if ritual:
    if "public static boolean hasPendingReveal(" in ritual:
        passes.append("Interrupted committed recognition exposes an explicit resume guard")
    else:
        failures.append("Interrupted committed recognition resume guard is missing")

    if "return !data.isNamingCommitted();" in ritual:
        passes.append("Native-named but unrecognized players remain eligible for Soul Recognition")
    else:
        failures.append("Native naming can still bypass Soul Recognition at the altar")

    if "RecognitionNamingService.commitRecognition(" in ritual:
        passes.append("Altar commitment uses the authoritative frozen-result service")
    else:
        failures.append("Altar still bypasses the authoritative recognition commitment service")

    if "RecognitionNativeEndowmentService.synchronize(" in ritual:
        passes.append("Ritual completion uses the exactly-once native-endowment lifecycle service")
    else:
        failures.append("Ritual completion does not use the native-endowment lifecycle service")

    completion_pos = ritual.find("private static void completeRitual(")
    reveal_clear_pos = ritual.find(
        "RecognitionStatKeys.REVEAL_PENDING",
        completion_pos,
    )
    native_sync_pos = ritual.find(
        "RecognitionNativeEndowmentService.synchronize(",
        reveal_clear_pos,
    )
    display_refresh_pos = ritual.find(
        "RecognitionDisplayNameSyncService",
        native_sync_pos,
    )
    if (
        0 <= completion_pos < reveal_clear_pos < native_sync_pos
        < display_refresh_pos
    ):
        passes.append("Native and visible identities publish only after the reveal guard clears")
    else:
        failures.append("Recognition reveal publication does not observe the reveal-completion boundary")

    native_failure_pos = ritual.find(
        "if (!isNativeIdentityPublished(",
        native_sync_pos,
    )
    pending_restore_pos = ritual.find(
        "RecognitionStatKeys.REVEAL_PENDING",
        native_failure_pos,
    )
    pending_true_pos = ritual.find(
        "true",
        pending_restore_pos,
    )
    if (
        0 <= native_sync_pos < native_failure_pos
        < pending_restore_pos < pending_true_pos < display_refresh_pos
    ):
        passes.append("Rejected native naming restores the same pending committed reveal")
    else:
        failures.append("Rejected native naming can clear the pending reveal permanently")

    if (
        "private static boolean isNativeIdentityPublished(" in ritual
        and "storedNativeName.equals(expectedName)" in ritual
        and "storedCustomName.equals(expectedName)" in ritual
    ):
        passes.append("Reveal completion verifies both native identity fields before publication")
    else:
        failures.append("Reveal completion can accept a partially synchronized native identity")

    if "RequestNamingMenuPacket.name(" in ritual:
        failures.append("Ritual still sends a direct naming request and can duplicate native endowment")
    else:
        passes.append("Ritual no longer sends duplicate direct HIGH naming requests")

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
