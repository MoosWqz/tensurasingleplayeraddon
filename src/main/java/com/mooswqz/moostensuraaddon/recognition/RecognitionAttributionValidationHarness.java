package com.mooswqz.moostensuraaddon.recognition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Deterministic runtime validation for attribution and incarnation isolation. */
public final class RecognitionAttributionValidationHarness {

    private RecognitionAttributionValidationHarness() {
    }

    public static void main(String[] arguments) {
        Report report = validate();

        for (Check check : report.checks()) {
            System.out.println(
                    (check.passed() ? "[PASS] " : "[FAIL] ")
                            + check.name()
                            + " — "
                            + check.detail()
            );
        }

        if (!report.passed()) {
            throw new IllegalStateException(
                    report.failedChecks()
                            + " attribution validation checks failed."
            );
        }
    }

    public static Report validate() {
        List<Check> checks = new ArrayList<>();

        validateActorClassification(checks);
        validateNegativeDeedPriority(checks);
        validateCreditLifecycle(checks);

        return new Report(List.copyOf(checks));
    }

    private static void validateActorClassification(
            List<Check> checks
    ) {
        add(
                checks,
                "Direct player attribution",
                RecognitionAttributionPolicy.classifyActor(
                        false,
                        true,
                        false,
                        false
                ) == RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER,
                "A direct player action remains player-owned."
        );

        add(
                checks,
                "Projectile attribution",
                RecognitionAttributionPolicy.classifyActor(
                        true,
                        true,
                        false,
                        false
                ) == RecognitionAttributionPolicy.ActorKind.PLAYER_PROJECTILE,
                "A player-owned projectile retains projectile credit."
        );

        add(
                checks,
                "Tensura subordinate precedence",
                RecognitionAttributionPolicy.classifyActor(
                        false,
                        false,
                        true,
                        true
                ) == RecognitionAttributionPolicy.ActorKind.TENSURA_SUBORDINATE,
                "Tensura ownership wins over generic tame ownership."
        );

        add(
                checks,
                "Owned companion attribution",
                RecognitionAttributionPolicy.classifyActor(
                        false,
                        false,
                        false,
                        true
                ) == RecognitionAttributionPolicy.ActorKind.OWNED_COMPANION,
                "A genuinely owned tame action is credited to its owner."
        );

        add(
                checks,
                "Named-but-unowned entity rejected",
                RecognitionAttributionPolicy.classifyActor(
                        false,
                        false,
                        false,
                        false
                ) == RecognitionAttributionPolicy.ActorKind.NONE,
                "No owner evidence means no credit; a custom name is irrelevant."
        );
    }

    private static void validateNegativeDeedPriority(
            List<Check> checks
    ) {
        add(
                checks,
                "Owned subordinate betrayal priority",
                RecognitionAttributionPolicy.classifyNegativeDeed(
                        true,
                        true,
                        true,
                        true,
                        true
                ) == RecognitionAttributionPolicy.NegativeDeed
                        .OWNED_SUBORDINATE_KILLED,
                "Exactly one highest-priority negative deed is selected."
        );

        add(
                checks,
                "Civilian kill classification",
                RecognitionAttributionPolicy.classifyNegativeDeed(
                        false,
                        false,
                        true,
                        false,
                        false
                ) == RecognitionAttributionPolicy.NegativeDeed.CIVILIAN_KILLED,
                "A civilian death is classified independently of delivery method."
        );

        add(
                checks,
                "Passive baby classification",
                RecognitionAttributionPolicy.classifyNegativeDeed(
                        false,
                        false,
                        false,
                        false,
                        true
                ) == RecognitionAttributionPolicy.NegativeDeed
                        .PASSIVE_BABY_KILLED,
                "Passive baby morality remains the final negative category."
        );
    }

    private static void validateCreditLifecycle(
            List<Check> checks
    ) {
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        String life = "life-a";
        Map<UUID, String> lifeTokens = Map.of(
                player,
                life,
                otherPlayer,
                "life-b"
        );

        RecognitionCombatCreditLedger ledger =
                new RecognitionCombatCreditLedger(
                        3,
                        3,
                        20L,
                        20L
                );

        RecognitionCombatCreditLedger.VictimKey environmentalVictim =
                key("environmental");

        ledger.record(
                environmentalVictim,
                credit(
                        player,
                        life,
                        RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER
                ),
                100L
        );

        RecognitionCombatCreditLedger.Resolution environmental =
                ledger.consumeDeath(
                        environmentalVictim,
                        Optional.empty(),
                        110L,
                        lifeTokens::get
                );

        add(
                checks,
                "Environmental death keeps recent credit",
                environmental.status()
                        == RecognitionCombatCreditLedger.ResolutionStatus.CREDITED
                        && environmental.credit().isPresent()
                        && environmental.credit().orElseThrow().playerUuid()
                        .equals(player),
                "Recent player damage survives a later environmental death source."
        );

        RecognitionCombatCreditLedger.Resolution duplicate =
                ledger.consumeDeath(
                        environmentalVictim,
                        Optional.empty(),
                        111L,
                        lifeTokens::get
                );

        add(
                checks,
                "Duplicate death suppressed",
                duplicate.duplicateSuppressed(),
                "The same victim UUID cannot apply recognition twice inside the guard window."
        );

        RecognitionCombatCreditLedger.VictimKey overrideVictim =
                key("override");

        ledger.record(
                overrideVictim,
                credit(
                        otherPlayer,
                        "life-b",
                        RecognitionAttributionPolicy.ActorKind.OWNED_COMPANION
                ),
                200L
        );

        RecognitionCombatCreditLedger.Resolution directOverride =
                ledger.consumeDeath(
                        overrideVictim,
                        Optional.of(
                                credit(
                                        player,
                                        life,
                                        RecognitionAttributionPolicy.ActorKind
                                                .PLAYER_PROJECTILE
                                )
                        ),
                        201L,
                        lifeTokens::get
                );

        add(
                checks,
                "Lethal source overrides older credit",
                directOverride.credit().isPresent()
                        && directOverride.credit().orElseThrow().playerUuid()
                        .equals(player)
                        && directOverride.credit().orElseThrow().actorKind()
                        == RecognitionAttributionPolicy.ActorKind.PLAYER_PROJECTILE,
                "Current lethal player evidence wins over an older stored attacker."
        );

        RecognitionCombatCreditLedger.VictimKey expiredVictim =
                key("expired");

        ledger.record(
                expiredVictim,
                credit(
                        player,
                        life,
                        RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER
                ),
                300L
        );

        RecognitionCombatCreditLedger.Resolution expired =
                ledger.consumeDeath(
                        expiredVictim,
                        Optional.empty(),
                        321L,
                        lifeTokens::get
                );

        add(
                checks,
                "Combat credit expires",
                expired.status()
                        == RecognitionCombatCreditLedger.ResolutionStatus.EXPIRED,
                "Environmental attribution stops after the configured credit window."
        );

        RecognitionCombatCreditLedger.VictimKey staleLifeVictim =
                key("stale-life");

        ledger.record(
                staleLifeVictim,
                credit(
                        player,
                        "old-life",
                        RecognitionAttributionPolicy.ActorKind.TENSURA_SUBORDINATE
                ),
                400L
        );

        RecognitionCombatCreditLedger.Resolution staleLife =
                ledger.consumeDeath(
                        staleLifeVictim,
                        Optional.empty(),
                        401L,
                        lifeTokens::get
                );

        add(
                checks,
                "Old-incarnation credit rejected",
                staleLife.status()
                        == RecognitionCombatCreditLedger.ResolutionStatus.STALE_LIFE,
                "A Character Reset life-token change invalidates earlier combat credit."
        );

        RecognitionCombatCreditLedger.VictimKey clearedVictim =
                key("cleared-player");

        ledger.record(
                clearedVictim,
                credit(
                        player,
                        life,
                        RecognitionAttributionPolicy.ActorKind.OWNED_COMPANION
                ),
                500L
        );

        ledger.clearPlayer(player);

        add(
                checks,
                "Player credit cleared on reset",
                ledger.inspect(player).selectedPlayerCredits() == 0,
                "Reset cleanup removes all active credit belonging to that incarnation."
        );

        RecognitionCombatCreditLedger smallLedger =
                new RecognitionCombatCreditLedger(
                        2,
                        2,
                        20L,
                        20L
                );

        smallLedger.record(
                key("cap-a"),
                credit(player, life, RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER),
                1L
        );
        smallLedger.record(
                key("cap-b"),
                credit(player, life, RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER),
                2L
        );
        smallLedger.record(
                key("cap-c"),
                credit(player, life, RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER),
                3L
        );

        add(
                checks,
                "Combat credit ledger bounded",
                smallLedger.inspect(player).recentCredits() == 2,
                "Oldest credit is evicted when the strict runtime ceiling is reached."
        );
    }

    private static RecognitionCombatCreditLedger.VictimKey key(
            String label
    ) {
        return new RecognitionCombatCreditLedger.VictimKey(
                "test:" + label,
                UUID.randomUUID()
        );
    }

    private static RecognitionCombatCreditLedger.PlayerCredit credit(
            UUID player,
            String life,
            RecognitionAttributionPolicy.ActorKind kind
    ) {
        return new RecognitionCombatCreditLedger.PlayerCredit(
                player,
                life,
                kind
        );
    }

    private static void add(
            List<Check> checks,
            String name,
            boolean passed,
            String detail
    ) {
        checks.add(new Check(name, passed, detail));
    }

    public record Check(
            String name,
            boolean passed,
            String detail
    ) {
    }

    public record Report(List<Check> checks) {
        public Report {
            checks = checks == null
                    ? List.of()
                    : List.copyOf(checks);
        }

        public boolean passed() {
            return failedChecks() == 0;
        }

        public int passedChecks() {
            return (int) checks.stream()
                    .filter(Check::passed)
                    .count();
        }

        public int failedChecks() {
            return checks.size() - passedChecks();
        }
    }
}
