#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src" / "main" / "java" / "com" / "mooswqz" / "moostensuraaddon"


def read(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")


checks: list[tuple[str, bool]] = []


def expect(name: str, condition: bool) -> None:
    checks.append((name, condition))


data = read("attachment/RecognitionData.java")
factory = read("attachment/RecognitionDataFixtureFactory.java")
state = read("lifecycle/AddonIncarnationState.java")
policy = read("lifecycle/AddonLifecyclePolicy.java")
native = read("lifecycle/RecognitionNativeEndowmentService.java")
reset = read("lifecycle/AddonPlayerDataResetService.java")
strength = read("recognition/RecognitionStrengthRewardService.java")
effort = read("recognition/RecognitionEndowmentEffortRewardService.java")
display = read("recognition/RecognitionDisplayNameService.java")
metadata = read("recognition/RecognitionCommittedMetadataService.java")
unname = read("recognition/RecognitionUnnameService.java")
command = read("command/RecognitionMigrationDebugCommand.java")
debug_root = read("command/DebugCommand.java")
confirmation = read("command/AdminConfirmationTracker.java")

expect(
    "RecognitionData exposes one future-version write barrier",
    "isWriteBlockedByFutureVersion" in data
    and "dataVersion > CURRENT_DATA_VERSION" in data
    and "CURRENT_RESULT_VERSION" in data
    and "CURRENT_RULES_VERSION" in data
    and "CURRENT_REWARD_PROFILE_VERSION" in data,
)
expect(
    "Raw legacy debug payload has a transient pre-codec mutation seal",
    "preserveRawLegacyUntilCodecReload" in data
    and "!migrateOnLoad" in data
    and "isMutationWriteBlocked()" in data
    and "preserveRawLegacyUntilCodecReload\n                || isWriteBlockedByFutureVersion()" in data,
)

for method in (
    "setCounter(",
    "setCounterMaximum(",
    "addToCounter(",
    "setMeasurement(",
    "setMeasurementMaximum(",
    "setFlag(",
    "setString(",
    "addUniqueValue(",
    "removeUniqueValue(",
    "commitNaming(",
    "completeCommittedPlayerMetadata(",
    "clearNamingCommitPreservingLifeProgress(",
    "resetForNewIncarnation(",
):
    start = data.find(method)
    block = data[start : start + 1_300] if start >= 0 else ""
    expect(
        f"{method[:-1]} is mutation write-blocked",
        "isMutationWriteBlocked()" in block,
    )

expect(
    "Future semantic versions prevent legacy constructor migration",
    "migrateOnLoad" in data
    and "&& !isWriteBlockedByFutureVersion()" in data,
)

for name, source in (
    ("native endowment", native),
    ("strength reward", strength),
    ("effort endowment", effort),
    ("display name", display),
    ("metadata backfill", metadata),
    ("unname", unname),
    ("incarnation reset", reset),
):
    expect(
        f"Future versions block {name} side effects",
        "isWriteBlockedByFutureVersion()" in source,
    )

expect(
    "Future fixture freezes schema/result/rules/reward sentinels",
    "createFutureVersionFixture" in factory
    and "FUTURE_VERSION_OFFSET" in factory
    and "fixture_future_collection" in factory,
)
expect(
    "Legacy fixture remains raw v1 until codec reload",
    "createUnmigratedLegacyCommitted" in factory
    and "false" in factory
    and "chaotic evil" in factory,
)
expect(
    "In-memory validation proves live writes cannot contaminate raw v1 fixture",
    "Raw legacy fixture rejects live writes until codec reload" in factory
    and "fixture_pre_reload_write" in factory,
)
expect(
    "In-memory validation covers migration idempotence",
    "Legacy migration is idempotent" in factory,
)
expect(
    "In-memory validation covers each independent future dimension",
    all(
        marker in factory
        for marker in (
            "Future schema alone is write-blocked",
            "Future result version alone is write-blocked",
            "Future rules version alone is write-blocked",
            "Future reward profile alone is write-blocked",
        )
    ),
)

expect(
    "Retry fixture uses the production capped backoff policy",
    "prepareNativeEndowmentRetryFixture" in state
    and "nextEndowmentAttemptEpochMillis" in state
    and "ENDOWMENT_MAX_RETRY_MILLIS = 60_000L" in policy,
)
expect(
    "Retry fixture can become due without fabricating success",
    "makeNativeEndowmentRetryDueForFixture" in state
    and "RecognitionNativeEndowmentService.synchronize(player)" in command,
)
expect(
    "Runtime retry probe performs repeated idempotence checks",
    "for (int attempt = 0; attempt < 3; attempt++)" in command
    and "EP delta across three synchronizations" in command,
)
expect(
    "Character Reset fixture installs pending retry state",
    'Commands.literal("hold")' in command
    and "Use Tensura's Character Reset Scroll now" in command,
)
expect(
    "Fixture mutations require dangerous debug permission",
    "canUseDangerousDebugTools" in command,
)
expect(
    "Both destructive fixture installs use confirmations",
    "INSTALL_LEGACY_MIGRATION_FIXTURE" in confirmation
    and "INSTALL_FUTURE_MIGRATION_FIXTURE" in confirmation
    and "AdminConfirmationTracker.consume" in command,
)
expect(
    "Migration fixture is attached only beneath the debug root",
    "RecognitionMigrationDebugCommand" in debug_root
    and ".createDebugNode()" in debug_root,
)
expect(
    "Runtime fixture instructs integrated-server Save and Quit",
    "Immediately use Save and Quit to Title" in command
    and "/save-all" not in command,
)

print("Release Hardening B2B2 — Migration / Retry Fixture Validation")
print("=============================================================")

failed = 0
for name, passed in checks:
    if passed:
        print(f"[PASS] {name}")
    else:
        print(f"[FAIL] {name}")
        failed += 1

print()
if failed:
    print(f"RESULT: FAIL ({failed} failed checks)")
    raise SystemExit(1)

print("RESULT: PASS")
