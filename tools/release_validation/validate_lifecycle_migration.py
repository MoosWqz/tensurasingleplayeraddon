#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
JAVA = ROOT / "src/main/java/com/mooswqz/moostensuraaddon"

FAIL: list[str] = []
WARN: list[str] = []
PASS: list[str] = []
INFO: list[str] = []


def read(relative: str) -> str:
    path = JAVA / relative
    if not path.is_file():
        FAIL.append(f"Missing production source: {path.relative_to(ROOT).as_posix()}")
        return ""
    return path.read_text(encoding="utf-8")


def all_java() -> list[tuple[Path, str]]:
    result = []
    if not JAVA.exists():
        return result
    for path in JAVA.rglob("*.java"):
        try:
            result.append((path, path.read_text(encoding="utf-8")))
        except UnicodeDecodeError:
            pass
    return result


def require(text: str, marker: str, label: str):
    if marker in text:
        PASS.append(label)
    else:
        FAIL.append(f"{label}: missing marker {marker!r}")


def require_any(text: str, markers: tuple[str, ...], label: str):
    if any(marker in text for marker in markers):
        PASS.append(label)
    else:
        FAIL.append(f"{label}: none of the expected markers were found")


def warn_unless(text: str, marker: str, label: str):
    if marker in text:
        PASS.append(label)
    else:
        WARN.append(label)


# ---------------------------------------------------------------------------
# 1. Core lifecycle architecture
# ---------------------------------------------------------------------------
incarnation = read("lifecycle/AddonIncarnationState.java")
reset_service = read("lifecycle/AddonPlayerDataResetService.java")
lifecycle_events = read("event/AddonLifecycleEvents.java")
lifecycle_policy = read("lifecycle/AddonLifecyclePolicy.java")
endowment = read("lifecycle/RecognitionNativeEndowmentService.java")
effort_endowment = read("recognition/RecognitionEndowmentEffortRewardService.java")
acquisition_policy = read("lifecycle/GranterAcquisitionPolicy.java")
acquisition_tracker = read("lifecycle/GranterAcquisitionTracker.java")
lifecycle_harness = read("lifecycle/AddonLifecycleValidationHarness.java")

if incarnation:
    require(incarnation, "getLifeToken", "Incarnation state exposes a life token")
    require_any(
        incarnation,
        (
            "beginNewIncarnation",
            "beginResetGuard",
            "enterResetGuard",
            "markReset",
        ),
        "Incarnation state exposes reset-guard/new-incarnation entry",
    )
    require_any(
        incarnation,
        ("isResetGuardActive", "resetGuard"),
        "Incarnation state exposes reset-guard state",
    )
    require_any(
        incarnation,
        ("getNativeEndowmentIncarnation", "nativeEndowmentIncarnation"),
        "Native endowment is keyed to incarnation state",
    )
    require(
        incarnation,
        "clearNativeEndowmentState",
        "Protected unname can clear the native-endowment anchor without changing incarnation",
    )
    require_any(
        incarnation,
        ("authorityObservationInitialized", "isAuthorityObservationInitialized"),
        "Granter acquisition observation is persisted",
    )

if lifecycle_policy:
    require(
        lifecycle_policy,
        "shouldSuppressDuplicateReset",
        "Duplicate-reset suppression policy exists",
    )
    require(
        lifecycle_policy,
        "shouldAttemptNativeEndowment",
        "Native-endowment attempt policy exists",
    )
    require(
        lifecycle_policy,
        "nextEndowmentAttemptEpochMillis",
        "Native-endowment retry backoff policy exists",
    )

if lifecycle_events:
    require(
        lifecycle_events,
        "CHARACTER_RESET_SCROLL",
        "Character Reset Scroll is explicitly detected",
    )
    require(
        lifecycle_events,
        "resetForNewIncarnation",
        "Character Reset routes through centralized addon reset",
    )
    require(
        lifecycle_events,
        "AddonIncarnationState.load",
        "Login loads incarnation state",
    )
    require(
        lifecycle_events,
        "GranterAcquisitionTracker.initializeOnLogin",
        "Login establishes Granter acquisition baseline",
    )
    require(
        lifecycle_events,
        "RecognitionNativeEndowmentService.synchronize",
        "Login synchronizes native endowment state",
    )
    require(
        lifecycle_events,
        "copyPersistentState",
        "Player clone copies persistent incarnation state",
    )

if reset_service:
    require(
        reset_service,
        "resetForNewIncarnation",
        "Central reset service exposes new-incarnation transaction",
    )
    require_any(
        reset_service,
        ("RecognitionData", "RECOGNITION_DATA"),
        "Central reset touches recognition data",
    )
    require_any(
        reset_service,
        ("GranterProgressData", "GRANTER_PROGRESS_DATA"),
        "Central reset touches Granter life progression",
    )
    require_any(
        reset_service,
        ("RecognitionStrength", "modifier", "Modifier"),
        "Central reset reconciles/removes recognition reward state",
    )
    require_any(
        reset_service,
        ("RecognitionDisplayName", "refreshAndBroadcast", "clear"),
        "Central reset refreshes/suppresses recognition display identity",
    )

# ---------------------------------------------------------------------------
# 2. Native endowment safety
# ---------------------------------------------------------------------------
if endowment:
    require(
        endowment,
        "isResetGuardActive",
        "Native endowment honors reset guard",
    )
    require(
        endowment,
        "shouldAttemptNativeEndowment",
        "Native endowment uses lifecycle policy gate",
    )
    require(
        endowment,
        "RequestNamingMenuPacket.name",
        "Native endowment uses Tensura HIGH naming route",
    )
    require(
        endowment,
        "NamingType.HIGH",
        "Native endowment explicitly requests HIGH naming",
    )
    require_any(
        endowment,
        ("recordNativeEndowmentFailure", "recordEndowmentFailure"),
        "Native endowment records bounded retry state",
    )
    require_any(
        endowment,
        ("markNativeEndowmentApplied", "nativeEndowmentIncarnation"),
        "Successful/already-native endowment is anchored per incarnation",
    )
    require(
        endowment,
        "RecognitionEndowmentEffortRewardService.reconcile",
        "Native endowment reconciles the effort-scaled capacity extension",
    )

if effort_endowment:
    require(
        effort_endowment,
        "MAX_MAGICULE_MODIFIER_ID",
        "Effort endowment has a stable maximum-magicule modifier",
    )
    require(
        effort_endowment,
        "MAX_AURA_MODIFIER_ID",
        "Effort endowment has a stable maximum-aura modifier",
    )
    require(
        effort_endowment,
        "applyOnlyNewEnergyCapacity",
        "Effort endowment grants current energy only for newly added capacity",
    )

# ---------------------------------------------------------------------------
# 3. Granter acquisition lifecycle
# ---------------------------------------------------------------------------
if acquisition_policy:
    require_any(
        acquisition_policy,
        ("shouldAwardAdvancement", "awardAdvancement"),
        "Granter acquisition policy returns explicit award decision",
    )
    require_any(
        acquisition_policy,
        ("lastOwnedAuthority", "lastOwned"),
        "Granter acquisition tracks previous ownership",
    )
    require_any(
        acquisition_policy,
        ("acquisitionConfirmedThisLife", "acquiredThisLife"),
        "Granter acquisition is life-bound",
    )

if acquisition_tracker:
    require(
        acquisition_tracker,
        "initializeOnLogin",
        "Granter tracker has login-baseline initialization",
    )
    require_any(
        acquisition_tracker,
        ("observe", "evaluate"),
        "Granter tracker evaluates ownership transitions",
    )

if lifecycle_harness:
    for marker, label in (
        ("fresh baseline initializes without award", "Harness covers fresh login baseline"),
        ("old owned authority does not false-trigger", "Harness covers old-save owned Granter"),
        ("false-to-true transition awards", "Harness covers real Granter acquisition"),
        ("relog does not duplicate", "Harness covers relog duplicate suppression"),
        ("confirmed acquisition repairs missing advancement", "Harness covers advancement repair"),
        ("temporary removal retains life confirmation", "Harness covers temporary authority removal"),
        ("eligible committed result attempts endowment", "Harness covers eligible native endowment"),
        ("guard suppresses endowment", "Harness covers reset-guard endowment suppression"),
        ("matching marker suppresses endowment", "Harness covers exactly-once endowment marker"),
        ("retry deadline enforced", "Harness covers endowment retry deadline"),
    ):
        warn_unless(lifecycle_harness, marker, label)

# ---------------------------------------------------------------------------
# 4. Recognition schema / committed record / future preservation
# ---------------------------------------------------------------------------
recognition_data = read("attachment/RecognitionData.java")
commit_record = read("recognition/RecognitionCommitRecord.java")

if recognition_data:
    require(
        recognition_data,
        "CURRENT_DATA_VERSION",
        "RecognitionData declares a current schema version",
    )
    require(
        recognition_data,
        "resetForNewIncarnation",
        "RecognitionData has explicit incarnation reset semantics",
    )
    require_any(
        recognition_data,
        ("getCommittedResult", "RecognitionCommittedResult"),
        "RecognitionData exposes immutable committed-result inspection",
    )

if commit_record:
    for marker, label in (
        ("CURRENT_RESULT_VERSION", "Committed result version is explicit"),
        ("CURRENT_RULES_VERSION", "Committed rules version is explicit"),
        ("CURRENT_REWARD_PROFILE_VERSION", "Reward profile version is explicit"),
    ):
        require(commit_record, marker, label)

# Search whole source for future-preservation language/logic.
java_files = all_java()
whole = "\n".join(text for _, text in java_files)
require_any(
    whole,
    ("FUTURE_VERSION_PRESERVED", "FUTURE_VERSION", "future version preserved"),
    "Unknown future persistent versions have an explicit preservation path",
)
require_any(
    whole,
    ("LEGACY_BACKFILLED", "MIGRATED_FROM_V1", "MIGRATED_FROM_UNVERSIONED"),
    "Legacy migration/backfill state is explicit",
)

# ---------------------------------------------------------------------------
# 5. Recognition reveal / commitment interruption safety
# ---------------------------------------------------------------------------
require_any(
    whole,
    ("REVEAL_PENDING", "revealPending", "reveal_pending"),
    "Committed recognition has reveal-pending state",
)
require_any(
    whole,
    ("isNamingCommitted", "namingCommitted"),
    "Recognition commitment is represented independently of reveal",
)
require_any(
    whole,
    ("resume", "recover"),
    "Source contains reveal/recovery continuation behavior",
)

# ---------------------------------------------------------------------------
# 6. Legacy /getnamed compatibility decision
# ---------------------------------------------------------------------------
registry = read("command/ModCommandRegistry.java")
getnamed = read("command/GetNamedCommand.java")
root_command = read("command/MoosTensuraCommand.java")

getnamed_registered = "GetNamedCommand.register(" in registry if registry else False
if getnamed_registered:
    INFO.append("/getnamed is still registered at the command root")
else:
    INFO.append("/getnamed is not registered")

if getnamed:
    writes_addon_persistent_data = any(
        token in getnamed
        for token in (
            "AttachmentRegistry",
            "RecognitionData",
            "AddonIncarnationState",
            "GranterProgressData",
            "BorrowedSkillData",
            "GrantedSkillData",
        )
    )
    if writes_addon_persistent_data:
        WARN.append(
            "/getnamed touches addon persistent state; do NOT unregister it until those writes are reviewed"
        )
    else:
        PASS.append(
            "/getnamed has no detected addon-persistent-state dependency"
        )

    native_only = (
        "RequestNamingMenuPacket.name" in getnamed
        and "TensuraStorages.getExistenceFrom" in getnamed
    )
    if native_only:
        PASS.append(
            "/getnamed performs native Tensura naming/endowment rather than creating a recognition commitment"
        )
    else:
        WARN.append(
            "Could not prove /getnamed is only a native Tensura naming route"
        )

if root_command:
    if "/getnamed" in root_command:
        WARN.append("/getnamed is still mentioned by MoosTensuraCommand help/guidance")
    else:
        PASS.append("/getnamed is not advertised by the canonical /moostensura root")

# Decision emitted only when static evidence is strong.
if getnamed and not writes_addon_persistent_data and native_only:
    if ".hasPermission(2)" in getnamed:
        INFO.append(
            "STATIC /getnamed DECISION: retained as an administrator-only "
            "native recovery/testing route and excluded from normal progression. "
            "Runtime old-save smoke testing is still required."
        )
    else:
        WARN.append(
            "/getnamed is retained without the agreed administrator permission gate"
        )

# ---------------------------------------------------------------------------
# 7. Old/future save matrix summary
# ---------------------------------------------------------------------------
matrix = [
    ("Brand-new player", "runtime test"),
    ("Old unrecognized player", "runtime test"),
    ("Legacy /getnamed player", "runtime compatibility test"),
    ("Natively named but addon-unrecognized player", "runtime test"),
    ("Current committed recognition", "static + runtime"),
    ("Legacy committed recognition", "static migration + runtime"),
    ("Current reward profile", "static + runtime"),
    ("Legacy reward profile", "static migration + runtime"),
    ("Unknown future reward/profile/schema", "must preserve and write-block"),
    ("Reveal-pending committed result", "runtime interruption test"),
    ("Native-endowment retry pending", "runtime restart/reset test"),
    ("Granter already owned on old save", "static harness + runtime"),
    ("Character Reset", "runtime"),
    ("Ordinary death / respawn", "runtime"),
    ("Dimension travel", "runtime"),
    ("Logout / reconnect", "runtime"),
]
INFO.append("Migration matrix rows: " + str(len(matrix)))

# ---------------------------------------------------------------------------
# Result
# ---------------------------------------------------------------------------
print("Release Hardening B1 — Lifecycle / Save / Migration Audit")
print("=========================================================")

for item in PASS:
    print(f"[PASS] {item}")

if WARN:
    print("")
    print(f"WARNINGS ({len(WARN)})")
    for item in WARN:
        print(f"[WARN] {item}")

if FAIL:
    print("")
    print(f"FAILURES ({len(FAIL)})")
    for item in FAIL:
        print(f"[FAIL] {item}")

print("")
print("INFO")
for item in INFO:
    print(f"[INFO] {item}")

print("")
print("Required runtime migration matrix")
for index, (state, requirement) in enumerate(matrix, start=1):
    print(f"{index:02d}. {state} — {requirement}")

print("")
print("RESULT: " + ("FAIL" if FAIL else ("PASS WITH WARNINGS" if WARN else "PASS")))
raise SystemExit(1 if FAIL else 0)
