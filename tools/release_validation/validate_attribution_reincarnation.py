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


policy = read("recognition/RecognitionAttributionPolicy.java")
attribution = read("recognition/RecognitionCombatAttribution.java")
ledger = read("recognition/RecognitionCombatCreditLedger.java")
tracker = read("recognition/RecognitionCombatCreditTracker.java")
subordinate = read("recognition/RecognitionSubordinateCombatTracker.java")
civilian = read("recognition/CivilianDefenseTracker.java")
events = read("event/RecognitionProgressionEvents.java")
runtime = read("event/RecognitionRuntimeStateEvents.java")
reset = read("lifecycle/AddonPlayerDataResetService.java")
data = read("attachment/RecognitionData.java")
command = read("command/RecognitionAttributionDebugCommand.java")
debug_root = read("command/DebugCommand.java")
confirmation = read("command/AdminConfirmationTracker.java")
harness = read("recognition/RecognitionAttributionValidationHarness.java")

expect(
    "Direct, projectile, subordinate and tame ownership use one policy",
    "RecognitionAttributionPolicy.classifyActor" in attribution
    and all(
        marker in policy
        for marker in (
            "PLAYER_PROJECTILE",
            "DIRECT_PLAYER",
            "TENSURA_SUBORDINATE",
            "OWNED_COMPANION",
        )
    ),
)
expect(
    "Named-but-unowned mobs cannot fabricate player ownership",
    "customName" not in attribution
    and "hasCustomName" not in attribution
    and "A custom name is" in policy,
)
expect(
    "Applied player-owned damage enters the recent-credit tracker",
    "LivingDamageEvent.Post" in events
    and "event.getNewDamage() <= 0.0F" in events
    and "RecognitionCombatCreditTracker.recordIncomingDamage" in events,
)
expect(
    "Death handling consumes exactly one attribution decision",
    "RecognitionCombatCreditTracker.consumeDeath" in events
    and "deathResolution.duplicateSuppressed()" in events
    and "deathResolution.credit()" in events
    and "EventPriority.LOWEST" in events,
)
expect(
    "Environmental deaths can use recent player credit",
    "directCredit.isPresent()" in ledger
    and "stored == null" in ledger
    and "stored.expirationGameTime()" in ledger,
)
expect(
    "Current lethal credit overrides older stored credit",
    "selected = directCredit.orElseThrow()" in ledger,
)
expect(
    "Combat credit is incarnation-bound",
    "selected.lifeToken()" in ledger
    and "currentLifeTokenResolver" in ledger
    and "STALE_LIFE" in ledger,
)
expect(
    "Duplicate death processing is explicitly suppressed",
    "processedDeaths" in ledger
    and "Resolution.duplicate()" in ledger
    and "DUPLICATE_SUPPRESSED" in ledger,
)
expect(
    "Combat credit and death guards are strictly bounded",
    "MAX_RECENT_CREDITS = 8192" in ledger
    and "MAX_PROCESSED_DEATHS = 8192" in ledger
    and "RecognitionRuntimeCapTable" in ledger,
)
expect(
    "Environmental credit expires after ten seconds",
    "CREDIT_WINDOW_TICKS = 20L * 10L" in ledger,
)
expect(
    "Subordinate participation cache is bounded",
    "MAX_ACTIVE_MAJOR_ENEMIES = 4096" in subordinate
    and "RecognitionRuntimeCapTable" in subordinate,
)
expect(
    "Reset removes only the resetting subordinate owner",
    "remainingOwners.remove(ownerUuid)" in subordinate
    and "remainingOwners.isEmpty()" in subordinate,
)
expect(
    "Negative deaths select exactly one priority category",
    "RecognitionAttributionPolicy.classifyNegativeDeed" in events
    and "Exactly one highest-priority negative deed" in events,
)
expect(
    "Civilian-defense credit shares resolved death ownership",
    "CivilianDefenseTracker.consumeDefense" in events
    and "responsiblePlayer" in events,
)
expect(
    "Character Reset clears combat credit",
    "RecognitionCombatCreditTracker.clearForPlayer" in reset,
)
expect(
    "Character Reset clears subordinate participation",
    "RecognitionSubordinateCombatTracker.forgetOwner" in reset,
)
expect(
    "Character Reset clears old civilian encounter windows",
    "CivilianDefenseTracker.clearServer" in reset,
)
expect(
    "Recognition reset clears every life-bound data map",
    all(
        marker in data[data.find("resetForNewIncarnation(") :
                       data.find("private void migrateToCurrentVersion")]
        for marker in (
            "counters.clear()",
            "measurements.clear()",
            "flags.clear()",
            "strings.clear()",
            "collections.clear()",
        )
    ),
)
expect(
    "Server lifecycle clears every attribution cache",
    "RecognitionCombatCreditTracker.clearAll()" in runtime
    and "RecognitionSubordinateCombatTracker.clearAll()" in runtime
    and "CivilianDefenseTracker.clearAll()" in runtime,
)
expect(
    "Runtime harness covers required attribution cases",
    all(
        marker in harness
        for marker in (
            "Direct player attribution",
            "Projectile attribution",
            "Tensura subordinate precedence",
            "Owned companion attribution",
            "Named-but-unowned entity rejected",
            "Environmental death keeps recent credit",
            "Combat credit expires",
            "Duplicate death suppressed",
            "Old-incarnation credit rejected",
        )
    ),
)
expect(
    "Reset fixture validates new-life isolation",
    "New-life progress remains isolated" in command
    and "RecognitionCombatCreditTracker.installResetFixture" in command
    and "RecognitionSubordinateCombatTracker.installResetFixture" in command
    and "CivilianDefenseTracker.installResetFixture" in command,
)
expect(
    "Attribution fixture requires dangerous debug permission and confirmation",
    "canUseDangerousDebugTools" in command
    and "INSTALL_ATTRIBUTION_RESET_FIXTURE" in confirmation
    and "AdminConfirmationTracker.consume" in command,
)
expect(
    "Pending reset fixtures are bounded and lifecycle-cleared",
    "MAX_PENDING_RESET_FIXTURES = 64" in command
    and "clearResetFixtures" in command
    and "RecognitionAttributionDebugCommand.clearResetFixtures" in runtime,
)
expect(
    "Attribution tools are attached only beneath the debug root",
    "RecognitionAttributionDebugCommand" in debug_root
    and ".createDebugNode()" in debug_root,
)

for accidental in (
    "Core 4.0.0.2 (manascore)",
    "tarted in 1 s 236 ms",
):
    expect(
        f"Accidental root artifact removed: {accidental}",
        not (ROOT / accidental).exists(),
    )


print("Release Hardening B2B3 — Attribution / Reincarnation Validation")
print("================================================================")

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
