package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, manually invoked validation for versioned identity history and event integration.
 *
 * <p>Every scenario uses temporary {@link RecognitionData}. No player, world,
 * attachment, advancement or committed recognition is modified.</p>
 */
public final class RecognitionIdentityHistoryValidationHarness {

    private static final double EPSILON = 1.0E-6D;

    private RecognitionIdentityHistoryValidationHarness() {
    }

    public static Report run() {
        List<Check> checks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateFreshMigration(checks);
        validateUnversionedRepair(checks);
        validateV1Migration(checks);
        validateFuturePreservation(checks);
        validateLazyMoralDecay(checks);
        validateIncrementalDecayAnchors(checks);
        validateAxisIsolation(checks);
        validateCapsAndPeaks(checks);
        validateExplicitReversalCounters(checks);
        validateEventIntegration(checks);
        validateAuthorityCounterIntegration(checks);
        validateAutomaticReversalDetection(checks);
        validateModifierResolution(checks);
        validateModifierPersistence(checks);
        validateModifierIdentifiers(checks);
        validateCommittedFieldsUnchanged(checks);
        validateRecognitionEvaluationUnchanged(checks, warnings);
        validateIncarnationReset(checks);

        boolean passed = checks.stream().allMatch(Check::passed);

        return new Report(
                passed,
                RecognitionIdentityHistoryService.CURRENT_HISTORY_VERSION,
                checks,
                warnings
        );
    }

    private static void validateFreshMigration(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        RecognitionIdentityHistorySnapshot before =
                RecognitionIdentityHistoryService.inspect(data);

        RecognitionIdentityHistoryService.MigrationResult result =
                RecognitionIdentityHistoryService.ensureCurrent(data);

        RecognitionIdentityHistorySnapshot after =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Fresh data begins uninitialized",
                before.migrationState()
                        == RecognitionIdentityHistoryService.MigrationState.UNINITIALIZED,
                before.migrationState().displayName()
        );

        addCheck(
                checks,
                "Fresh data initializes the current native history version",
                result == RecognitionIdentityHistoryService.MigrationResult.INITIALIZED_NATIVE
                        && after.storedVersion()
                        == RecognitionIdentityHistoryService.CURRENT_HISTORY_VERSION
                        && after.migrationState()
                        == RecognitionIdentityHistoryService.MigrationState.CURRENT,
                "result=" + result
                        + ", version=" + after.storedVersion()
        );

        addCheck(
                checks,
                "Fresh initialization records the native history source",
                RecognitionIdentityHistoryService.MIGRATION_SOURCE_NATIVE
                        .equals(after.migrationSource()),
                after.migrationSource()
        );

        addCheck(
                checks,
                "Fresh initialization installs semantic none modifier",
                RecognitionIdentityHistoryModifier.NONE.id()
                        .equals(after.rawModifierId()),
                after.rawModifierId()
        );

        RecognitionIdentityHistoryService.MigrationResult second =
                RecognitionIdentityHistoryService.ensureCurrent(data);

        addCheck(
                checks,
                "History migration is idempotent",
                second == RecognitionIdentityHistoryService.MigrationResult.ALREADY_CURRENT,
                second.name()
        );
    }

    private static void validateUnversionedRepair(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setMeasurement(
                RecognitionStatKeys.GOOD_MOMENTUM,
                31.0D
        );

        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_GOOD_COMMITMENT,
                12.0D
        );

        RecognitionIdentityHistorySnapshot before =
                RecognitionIdentityHistoryService.inspect(data);

        RecognitionIdentityHistoryService.MigrationResult result =
                RecognitionIdentityHistoryService.ensureCurrent(data);

        RecognitionIdentityHistorySnapshot after =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Unversioned payload is detected without mutation",
                before.migrationState()
                        == RecognitionIdentityHistoryService.MigrationState.UNVERSIONED_PAYLOAD,
                before.migrationState().displayName()
        );

        addCheck(
                checks,
                "Unversioned migration preserves momentum and repairs peak",
                result == RecognitionIdentityHistoryService.MigrationResult.MIGRATED_FROM_UNVERSIONED
                        && approximately(after.goodMomentum(), 31.0D)
                        && approximately(after.highestGoodCommitment(), 31.0D),
                "momentum=" + after.goodMomentum()
                        + ", peak=" + after.highestGoodCommitment()
        );
    }

    private static void validateV1Migration(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setCounter(
                RecognitionStatKeys.IDENTITY_HISTORY_VERSION,
                1
        );

        data.setMeasurement(
                RecognitionStatKeys.GOOD_MOMENTUM,
                24.0D
        );

        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_GOOD_COMMITMENT,
                24.0D
        );

        data.setString(
                RecognitionStatKeys.LAST_GOOD_DEED_GAME_TIME,
                "12000"
        );

        RecognitionIdentityHistorySnapshot before =
                RecognitionIdentityHistoryService.inspect(data);

        RecognitionIdentityHistoryService.MigrationResult result =
                RecognitionIdentityHistoryService.ensureCurrent(data);

        RecognitionIdentityHistorySnapshot after =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "History version 1 is identified as a legacy namespace",
                before.migrationState()
                        == RecognitionIdentityHistoryService.MigrationState.LEGACY_VERSION,
                before.migrationState().displayName()
        );

        addCheck(
                checks,
                "Version 1 migrates to version 2 without losing momentum",
                result == RecognitionIdentityHistoryService.MigrationResult.MIGRATED_FROM_V1
                        && after.storedVersion()
                        == RecognitionIdentityHistoryService.CURRENT_HISTORY_VERSION
                        && approximately(after.goodMomentum(), 24.0D)
                        && RecognitionIdentityHistoryService.MIGRATION_SOURCE_V1
                        .equals(after.migrationSource()),
                "result=" + result
                        + ", version=" + after.storedVersion()
                        + ", momentum=" + after.goodMomentum()
        );

        addCheck(
                checks,
                "Version 1 migration seeds the incremental decay anchor",
                "12000".equals(
                        data.getString(
                                RecognitionStatKeys
                                        .GOOD_DECAY_ANCHOR_GAME_TIME
                        )
                )
                        && RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
                        .equals(
                                data.getString(
                                        RecognitionStatKeys
                                                .MORAL_ESTABLISHED_DIRECTION
                                )
                        ),
                "anchor="
                        + data.getString(
                        RecognitionStatKeys
                                .GOOD_DECAY_ANCHOR_GAME_TIME
                )
                        + ", direction="
                        + data.getString(
                        RecognitionStatKeys
                                .MORAL_ESTABLISHED_DIRECTION
                )
        );
    }

    private static void validateFuturePreservation(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setCounter(
                RecognitionStatKeys.IDENTITY_HISTORY_VERSION,
                RecognitionIdentityHistoryService.CURRENT_HISTORY_VERSION + 5
        );

        data.setMeasurement(
                RecognitionStatKeys.GOOD_MOMENTUM,
                44.0D
        );

        data.setString(
                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER,
                "future_semantic_modifier"
        );

        RecognitionIdentityHistoryService.MigrationResult migration =
                RecognitionIdentityHistoryService.ensureCurrent(data);

        RecognitionIdentityHistoryService.MutationResult mutation =
                RecognitionIdentityHistoryService.recordMoralDeed(
                        data,
                        RecognitionIdentityHistoryService.MoralDirection.GOOD,
                        10.0D,
                        50_000L
                );

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Future history versions are preserved and write-blocked",
                migration == RecognitionIdentityHistoryService.MigrationResult.FUTURE_VERSION_PRESERVED
                        && !mutation.applied()
                        && snapshot.futureVersion()
                        && approximately(snapshot.goodMomentum(), 44.0D)
                        && "future_semantic_modifier".equals(snapshot.rawModifierId()),
                "migration=" + migration
                        + ", mutationApplied=" + mutation.applied()
                        + ", modifier=" + snapshot.rawModifierId()
        );
    }

    private static void validateLazyMoralDecay(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();
        RecognitionIdentityHistoryService.ensureCurrent(data);

        long firstTime = 10_000L;

        RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                RecognitionIdentityHistoryService.MoralDirection.GOOD,
                20.0D,
                firstTime
        );

        long secondTime = firstTime
                + RecognitionIdentityHistoryService.DECAY_GRACE_TICKS
                + RecognitionIdentityHistoryService.DECAY_INTERVAL_TICKS * 2L;

        RecognitionIdentityHistoryService.MutationResult result =
                RecognitionIdentityHistoryService.recordMoralDeed(
                        data,
                        RecognitionIdentityHistoryService.MoralDirection.GOOD,
                        5.0D,
                        secondTime
                );

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        double expected = 20.0D
                - RecognitionIdentityHistoryService.DECAY_PER_INTERVAL * 2.0D
                + 5.0D;

        addCheck(
                checks,
                "Moral momentum decays lazily when the next moral deed arrives",
                result.applied()
                        && approximately(snapshot.goodMomentum(), expected)
                        && approximately(
                        result.totalDecayApplied(),
                        RecognitionIdentityHistoryService.DECAY_PER_INTERVAL * 2.0D
                ),
                "expected=" + expected
                        + ", actual=" + snapshot.goodMomentum()
                        + ", decay=" + result.totalDecayApplied()
        );

        addCheck(
                checks,
                "Historical Good peak survives later decay",
                approximately(snapshot.highestGoodCommitment(), 20.0D),
                Double.toString(snapshot.highestGoodCommitment())
        );
    }

    private static void validateIncrementalDecayAnchors(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();
        RecognitionIdentityHistoryService.ensureCurrent(data);

        long firstTime = 1_000L;

        RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                RecognitionIdentityHistoryService.MoralDirection.GOOD,
                20.0D,
                firstTime
        );

        long firstOpposingTime = firstTime
                + RecognitionIdentityHistoryService.DECAY_GRACE_TICKS
                + RecognitionIdentityHistoryService.DECAY_INTERVAL_TICKS;

        RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                RecognitionIdentityHistoryService.MoralDirection.EVIL,
                3.0D,
                firstOpposingTime
        );

        RecognitionIdentityHistorySnapshot afterFirst =
                RecognitionIdentityHistoryService.inspect(data);

        long secondOpposingTime =
                firstOpposingTime + 1_000L;

        RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                RecognitionIdentityHistoryService.MoralDirection.EVIL,
                3.0D,
                secondOpposingTime
        );

        RecognitionIdentityHistorySnapshot afterSecond =
                RecognitionIdentityHistoryService.inspect(data);

        double expectedAfterFirst =
                20.0D
                        - RecognitionIdentityHistoryService
                        .DECAY_PER_INTERVAL;

        double incrementalSecondDecay =
                1_000.0D
                        / RecognitionIdentityHistoryService
                        .DECAY_INTERVAL_TICKS
                        * RecognitionIdentityHistoryService
                        .DECAY_PER_INTERVAL;

        double expectedAfterSecond =
                expectedAfterFirst
                        - incrementalSecondDecay;

        addCheck(
                checks,
                "The first lazy evaluation applies the elapsed decay once",
                approximately(
                        afterFirst.goodMomentum(),
                        expectedAfterFirst
                ),
                "expected=" + expectedAfterFirst
                        + ", actual=" + afterFirst.goodMomentum()
        );

        addCheck(
                checks,
                "Incremental anchors prevent repeated subtraction of old elapsed time",
                approximately(
                        afterSecond.goodMomentum(),
                        expectedAfterSecond
                ),
                "expected=" + expectedAfterSecond
                        + ", actual=" + afterSecond.goodMomentum()
        );
    }

    private static void validateAxisIsolation(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();
        RecognitionIdentityHistoryService.ensureCurrent(data);

        RecognitionIdentityHistoryService.recordTemperamentDeed(
                data,
                RecognitionIdentityHistoryService.TemperamentDirection.ORDER,
                20.0D,
                1_000L
        );

        long muchLater = 1_000L
                + RecognitionIdentityHistoryService.DECAY_GRACE_TICKS
                + RecognitionIdentityHistoryService.DECAY_INTERVAL_TICKS * 3L;

        RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                RecognitionIdentityHistoryService.MoralDirection.EVIL,
                8.0D,
                muchLater
        );

        RecognitionIdentityHistorySnapshot afterMoral =
                RecognitionIdentityHistoryService.inspect(data);

        RecognitionIdentityHistoryService.recordTemperamentDeed(
                data,
                RecognitionIdentityHistoryService.TemperamentDirection.FREEDOM,
                4.0D,
                muchLater
        );

        RecognitionIdentityHistorySnapshot afterTemperament =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Moral deeds do not decay the temperament axis",
                approximately(afterMoral.orderMomentum(), 20.0D),
                Double.toString(afterMoral.orderMomentum())
        );

        addCheck(
                checks,
                "Temperament deeds lazily decay both Order and Freedom sides",
                approximately(
                        afterTemperament.orderMomentum(),
                        20.0D
                                - RecognitionIdentityHistoryService.DECAY_PER_INTERVAL * 3.0D
                )
                        && approximately(afterTemperament.freedomMomentum(), 4.0D),
                "order=" + afterTemperament.orderMomentum()
                        + ", freedom=" + afterTemperament.freedomMomentum()
        );
    }

    private static void validateCapsAndPeaks(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();
        RecognitionIdentityHistoryService.ensureCurrent(data);

        for (int index = 0; index < 10; index++) {
            RecognitionIdentityHistoryService.recordTemperamentDeed(
                    data,
                    RecognitionIdentityHistoryService.TemperamentDirection.FREEDOM,
                    500.0D,
                    10_000L
            );
        }

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Deed weights and momentum are capped safely",
                approximately(
                        snapshot.freedomMomentum(),
                        RecognitionIdentityHistoryService.MAXIMUM_MOMENTUM
                )
                        && approximately(
                        snapshot.highestFreedomCommitment(),
                        RecognitionIdentityHistoryService.MAXIMUM_MOMENTUM
                ),
                "momentum=" + snapshot.freedomMomentum()
                        + ", peak=" + snapshot.highestFreedomCommitment()
        );
    }

    private static void validateExplicitReversalCounters(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        int moralOne = RecognitionIdentityHistoryService
                .registerMoralReversal(data);

        int moralTwo = RecognitionIdentityHistoryService
                .registerMoralReversal(data);

        int temperament = RecognitionIdentityHistoryService
                .registerTemperamentReversal(data);

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Reversal counters persist independently",
                moralOne == 1
                        && moralTwo == 2
                        && temperament == 1
                        && snapshot.moralReversalCount() == 2
                        && snapshot.temperamentReversalCount() == 1,
                "moral=" + snapshot.moralReversalCount()
                        + ", temperament=" + snapshot.temperamentReversalCount()
        );
    }

    private static void validateEventIntegration(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        RecognitionIdentityHistoryIntegration.EventResult good =
                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .VILLAGER_CURED,
                        10_000L
                );

        RecognitionIdentityHistoryIntegration.EventResult evil =
                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .PASSIVE_BABY_KILLED,
                        10_000L
                );

        RecognitionIdentityHistoryIntegration.EventResult order =
                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .SUBORDINATE_ASSISTED_MAJOR_VICTORY,
                        10_000L
                );

        RecognitionIdentityHistoryIntegration.EventResult freedom =
                RecognitionIdentityHistoryIntegration
                        .recordIndependenceMilestone(
                                data,
                                5.0D,
                                10_000L
                        );

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Tracked deeds apply their semantic axis weights",
                good.applied()
                        && evil.applied()
                        && order.applied()
                        && freedom.applied()
                        && approximately(snapshot.goodMomentum(), 6.0D)
                        && approximately(snapshot.evilMomentum(), 2.0D)
                        && approximately(snapshot.orderMomentum(), 6.0D)
                        && approximately(snapshot.freedomMomentum(), 5.0D),
                "good=" + snapshot.goodMomentum()
                        + ", evil=" + snapshot.evilMomentum()
                        + ", order=" + snapshot.orderMomentum()
                        + ", freedom=" + snapshot.freedomMomentum()
        );

        RecognitionData scaled = new RecognitionData();

        RecognitionIdentityHistoryIntegration.EventResult raids =
                RecognitionIdentityHistoryIntegration.recordOccurrences(
                        scaled,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .RAID_VICTORY,
                        4,
                        20_000L
                );

        RecognitionIdentityHistorySnapshot scaledSnapshot =
                RecognitionIdentityHistoryService.inspect(scaled);

        addCheck(
                checks,
                "Batched event deltas use the service deed-weight safety cap",
                raids.applied()
                        && approximately(
                        raids.appliedWeight(),
                        RecognitionIdentityHistoryService
                                .MAXIMUM_DEED_WEIGHT
                )
                        && approximately(
                        scaledSnapshot.goodMomentum(),
                        RecognitionIdentityHistoryService
                                .MAXIMUM_DEED_WEIGHT
                ),
                "appliedWeight=" + raids.appliedWeight()
                        + ", momentum=" + scaledSnapshot.goodMomentum()
        );

        addCheck(
                checks,
                "Event integration does not resolve a live modifier",
                RecognitionIdentityHistoryModifier.NONE.id()
                        .equals(snapshot.rawModifierId())
                        && snapshot.moralReversalCount() == 0
                        && snapshot.temperamentReversalCount() == 0,
                "modifier=" + snapshot.rawModifierId()
        );
    }

    private static void validateAuthorityCounterIntegration(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.addUniqueValue(
                RecognitionStatKeys
                        .UNIQUE_SUBORDINATES_EMPOWERED,
                "legacy-subordinate"
        );

        data.setCounter(
                RecognitionStatKeys.MASS_GRANTS_PERFORMED,
                2
        );

        data.setCounter(
                RecognitionStatKeys.GLOBAL_TAKE_BACKS_PERFORMED,
                1
        );

        data.setCounter(
                RecognitionStatKeys.SKILLS_SEIZED,
                3
        );

        RecognitionIdentityHistoryIntegration.AuthoritySyncResult baseline =
                RecognitionIdentityHistoryIntegration
                        .synchronizeAuthorityCounters(
                                data,
                                10_000L
                        );

        RecognitionIdentityHistorySnapshot afterBaseline =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Existing authority totals initialize baselines without retroactive chronology",
                !baseline.changed()
                        && approximately(afterBaseline.orderMomentum(), 0.0D)
                        && approximately(afterBaseline.evilMomentum(), 0.0D),
                "order=" + afterBaseline.orderMomentum()
                        + ", evil=" + afterBaseline.evilMomentum()
        );

        data.addUniqueValue(
                RecognitionStatKeys
                        .UNIQUE_SUBORDINATES_EMPOWERED,
                "new-subordinate"
        );

        data.setCounter(
                RecognitionStatKeys.MASS_GRANTS_PERFORMED,
                3
        );

        data.setCounter(
                RecognitionStatKeys.GLOBAL_TAKE_BACKS_PERFORMED,
                3
        );

        data.setCounter(
                RecognitionStatKeys.SKILLS_SEIZED,
                4
        );

        RecognitionIdentityHistoryIntegration.AuthoritySyncResult delta =
                RecognitionIdentityHistoryIntegration
                        .synchronizeAuthorityCounters(
                                data,
                                10_000L
                        );

        RecognitionIdentityHistorySnapshot afterDelta =
                RecognitionIdentityHistoryService.inspect(data);

        double expectedOrder = 4.0D
                + 6.0D
                + 10.0D;

        addCheck(
                checks,
                "New authority counter deltas are consumed once into history",
                delta.changed()
                        && delta.empoweredSubordinateDelta() == 1
                        && delta.massGrantDelta() == 1
                        && delta.globalTakeBackDelta() == 2
                        && delta.skillSeizedDelta() == 1
                        && approximately(
                        afterDelta.orderMomentum(),
                        expectedOrder
                )
                        && approximately(
                        afterDelta.evilMomentum(),
                        8.0D
                ),
                "order=" + afterDelta.orderMomentum()
                        + ", evil=" + afterDelta.evilMomentum()
        );

        RecognitionIdentityHistoryIntegration.AuthoritySyncResult repeated =
                RecognitionIdentityHistoryIntegration
                        .synchronizeAuthorityCounters(
                                data,
                                10_000L
                        );

        RecognitionIdentityHistorySnapshot afterRepeated =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Unchanged authority counters cannot be consumed twice",
                !repeated.changed()
                        && approximately(
                        afterRepeated.orderMomentum(),
                        expectedOrder
                )
                        && approximately(
                        afterRepeated.evilMomentum(),
                        8.0D
                ),
                "order=" + afterRepeated.orderMomentum()
                        + ", evil=" + afterRepeated.evilMomentum()
        );
    }

    private static void validateAutomaticReversalDetection(
            List<Check> checks
    ) {
        RecognitionData moral = new RecognitionData();

        for (int index = 0; index < 4; index++) {
            RecognitionIdentityHistoryIntegration.record(
                    moral,
                    RecognitionIdentityHistoryIntegration
                            .TrackedDeed
                            .VILLAGER_CURED,
                    10_000L
            );
        }

        RecognitionIdentityHistorySnapshot goodEstablished =
                RecognitionIdentityHistoryService.inspect(moral);

        addCheck(
                checks,
                "Initial moral establishment does not count as a reversal",
                goodEstablished.moralReversalCount() == 0
                        && RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
                        .equals(
                                moral.getString(
                                        RecognitionStatKeys
                                                .MORAL_ESTABLISHED_DIRECTION
                                )
                        ),
                "direction="
                        + moral.getString(
                        RecognitionStatKeys
                                .MORAL_ESTABLISHED_DIRECTION
                )
        );

        RecognitionIdentityHistoryIntegration.EventResult evilReversal =
                RecognitionIdentityHistoryIntegration.EventResult
                        .rejected("");

        for (int index = 0; index < 3; index++) {
            evilReversal =
                    RecognitionIdentityHistoryIntegration.record(
                            moral,
                            RecognitionIdentityHistoryIntegration
                                    .TrackedDeed
                                    .OWNED_SUBORDINATE_KILLED,
                            10_000L
                    );
        }

        RecognitionIdentityHistorySnapshot evilEstablished =
                RecognitionIdentityHistoryService.inspect(moral);

        addCheck(
                checks,
                "Replacing established Good with established Evil registers one reversal",
                evilReversal.reversalRegistered()
                        && evilEstablished.moralReversalCount() == 1
                        && RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
                        .equals(
                                moral.getString(
                                        RecognitionStatKeys
                                                .MORAL_ESTABLISHED_DIRECTION
                                )
                        ),
                "reversals=" + evilEstablished.moralReversalCount()
                        + ", direction="
                        + moral.getString(
                        RecognitionStatKeys
                                .MORAL_ESTABLISHED_DIRECTION
                )
        );

        RecognitionIdentityHistoryIntegration.EventResult repeatedEvil =
                RecognitionIdentityHistoryIntegration.record(
                        moral,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .OWNED_SUBORDINATE_KILLED,
                        10_000L
                );

        addCheck(
                checks,
                "Continuing in the same established direction does not farm reversals",
                !repeatedEvil.reversalRegistered()
                        && RecognitionIdentityHistoryService
                        .inspect(moral)
                        .moralReversalCount() == 1,
                "reversals="
                        + RecognitionIdentityHistoryService
                        .inspect(moral)
                        .moralReversalCount()
        );

        RecognitionIdentityHistoryIntegration.EventResult redemption =
                RecognitionIdentityHistoryIntegration.EventResult
                        .rejected("");

        for (int index = 0; index < 5; index++) {
            redemption =
                    RecognitionIdentityHistoryIntegration.record(
                            moral,
                            RecognitionIdentityHistoryIntegration
                                    .TrackedDeed
                                    .RAID_VICTORY,
                            10_000L
                    );

            if (redemption.reversalRegistered()) {
                break;
            }
        }

        addCheck(
                checks,
                "A later clear return to Good registers the next moral reversal",
                redemption.reversalRegistered()
                        && RecognitionIdentityHistoryService
                        .inspect(moral)
                        .moralReversalCount() == 2
                        && RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
                        .equals(
                                moral.getString(
                                        RecognitionStatKeys
                                                .MORAL_ESTABLISHED_DIRECTION
                                )
                        ),
                "reversals="
                        + RecognitionIdentityHistoryService
                        .inspect(moral)
                        .moralReversalCount()
        );

        RecognitionData temperament = new RecognitionData();

        for (int index = 0; index < 4; index++) {
            RecognitionIdentityHistoryIntegration.record(
                    temperament,
                    RecognitionIdentityHistoryIntegration
                            .TrackedDeed
                            .SUBORDINATE_ASSISTED_MAJOR_VICTORY,
                    10_000L
            );
        }

        RecognitionIdentityHistoryIntegration.EventResult defiance =
                RecognitionIdentityHistoryIntegration.EventResult
                        .rejected("");

        for (int index = 0; index < 5; index++) {
            defiance =
                    RecognitionIdentityHistoryIntegration.record(
                            temperament,
                            RecognitionIdentityHistoryIntegration
                                    .TrackedDeed
                                    .SOLO_MAJOR_VICTORY,
                            10_000L
                    );

            if (defiance.reversalRegistered()) {
                break;
            }
        }

        RecognitionIdentityHistorySnapshot temperamentSnapshot =
                RecognitionIdentityHistoryService.inspect(
                        temperament
                );

        addCheck(
                checks,
                "Order-to-Freedom reversal detection is independent from morality",
                defiance.reversalRegistered()
                        && temperamentSnapshot
                        .temperamentReversalCount() == 1
                        && temperamentSnapshot.moralReversalCount() == 0
                        && RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
                        .equals(
                                temperament.getString(
                                        RecognitionStatKeys
                                                .TEMPERAMENT_ESTABLISHED_DIRECTION
                                )
                        ),
                "moral=" + temperamentSnapshot.moralReversalCount()
                        + ", temperament="
                        + temperamentSnapshot
                        .temperamentReversalCount()
        );
    }

    private static void validateModifierResolution(
            List<Check> checks
    ) {
        RecognitionData uncommitted = new RecognitionData();
        RecognitionIdentityHistoryService.ensureCurrent(uncommitted);

        setMoralHistory(
                uncommitted,
                0.0D,
                32.0D,
                30.0D,
                32.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        );

        RecognitionIdentityHistoryResolution uncommittedResolution =
                RecognitionIdentityHistoryResolver.resolve(
                        uncommitted
                );

        addCheck(
                checks,
                "History modifiers require a frozen committed identity",
                !uncommittedResolution.committedIdentityAvailable()
                        && uncommittedResolution.modifier()
                        == RecognitionIdentityHistoryModifier.NONE,
                uncommittedResolution.reason()
        );

        RecognitionData isolatedDeed = committedData(
                RecognitionPath.LAWFUL_GOOD,
                null
        );

        RecognitionIdentityHistoryIntegration.record(
                isolatedDeed,
                RecognitionIdentityHistoryIntegration
                        .TrackedDeed
                        .OWNED_SUBORDINATE_KILLED,
                1_000L
        );

        RecognitionIdentityHistoryResolution isolatedResolution =
                RecognitionIdentityHistoryResolver.resolve(
                        isolatedDeed
                );

        addCheck(
                checks,
                "One isolated deed cannot create a history modifier",
                isolatedResolution.modifier()
                        == RecognitionIdentityHistoryModifier.NONE,
                isolatedResolution.reason()
        );

        RecognitionData fallen = committedData(
                RecognitionPath.LAWFUL_GOOD,
                null
        );

        setMoralHistory(
                fallen,
                4.0D,
                34.0D,
                36.0D,
                34.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        );

        addCheck(
                checks,
                "Good recognition can resolve to Fallen",
                RecognitionIdentityHistoryResolver
                        .resolve(fallen)
                        .modifier()
                        == RecognitionIdentityHistoryModifier.FALLEN,
                RecognitionIdentityHistoryResolver
                        .resolve(fallen)
                        .reason()
        );

        RecognitionData redeemed = committedData(
                RecognitionPath.LAWFUL_EVIL,
                null
        );

        setMoralHistory(
                redeemed,
                35.0D,
                3.0D,
                35.0D,
                38.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
        );

        addCheck(
                checks,
                "Evil recognition can resolve to Redeemed",
                RecognitionIdentityHistoryResolver
                        .resolve(redeemed)
                        .modifier()
                        == RecognitionIdentityHistoryModifier.REDEEMED,
                RecognitionIdentityHistoryResolver
                        .resolve(redeemed)
                        .reason()
        );

        RecognitionData oathbreaker = committedData(
                RecognitionPath.LAWFUL_NEUTRAL,
                null
        );

        setTemperamentHistory(
                oathbreaker,
                3.0D,
                35.0D,
                37.0D,
                35.0D,
                1,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
        );

        addCheck(
                checks,
                "Lawful recognition can resolve to Oathbreaker",
                RecognitionIdentityHistoryResolver
                        .resolve(oathbreaker)
                        .modifier()
                        == RecognitionIdentityHistoryModifier.OATHBREAKER,
                RecognitionIdentityHistoryResolver
                        .resolve(oathbreaker)
                        .reason()
        );

        RecognitionData crowned = committedData(
                RecognitionPath.CHAOTIC_NEUTRAL,
                null
        );

        setTemperamentHistory(
                crowned,
                36.0D,
                4.0D,
                36.0D,
                40.0D,
                1,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id()
        );

        addCheck(
                checks,
                "Chaotic recognition can resolve to Crowned or Bound",
                RecognitionIdentityHistoryResolver
                        .resolve(crowned)
                        .modifier()
                        == RecognitionIdentityHistoryModifier
                        .CROWNED_OR_BOUND,
                RecognitionIdentityHistoryResolver
                        .resolve(crowned)
                        .reason()
        );

        RecognitionData defiant = committedData(
                RecognitionPath.NEUTRAL_GOOD,
                null
        );

        setTemperamentHistory(
                defiant,
                3.0D,
                34.0D,
                32.0D,
                34.0D,
                1,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
        );

        addCheck(
                checks,
                "Neutral temperament can resolve to Defiant after real Order history",
                RecognitionIdentityHistoryResolver
                        .resolve(defiant)
                        .modifier()
                        == RecognitionIdentityHistoryModifier.DEFIANT,
                RecognitionIdentityHistoryResolver
                        .resolve(defiant)
                        .reason()
        );

        RecognitionData neutralBound = committedData(
                RecognitionPath.NEUTRAL_EVIL,
                null
        );

        setTemperamentHistory(
                neutralBound,
                34.0D,
                3.0D,
                34.0D,
                32.0D,
                1,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id()
        );

        addCheck(
                checks,
                "Neutral temperament can resolve to Crowned or Bound after Freedom history",
                RecognitionIdentityHistoryResolver
                        .resolve(neutralBound)
                        .modifier()
                        == RecognitionIdentityHistoryModifier
                        .CROWNED_OR_BOUND,
                RecognitionIdentityHistoryResolver
                        .resolve(neutralBound)
                        .reason()
        );

        RecognitionData fractured = committedData(
                RecognitionPath.LAWFUL_GOOD,
                null
        );

        setMoralHistory(
                fractured,
                2.0D,
                36.0D,
                38.0D,
                36.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        );

        setTemperamentHistory(
                fractured,
                2.0D,
                35.0D,
                37.0D,
                35.0D,
                1,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
        );

        RecognitionIdentityHistoryResolution fracturedResolution =
                RecognitionIdentityHistoryResolver.resolve(
                        fractured
                );

        addCheck(
                checks,
                "Two simultaneous identity contradictions resolve to Fractured",
                fracturedResolution.modifier()
                        == RecognitionIdentityHistoryModifier.FRACTURED
                        && fracturedResolution.moralContradiction()
                        && fracturedResolution.temperamentContradiction(),
                fracturedResolution.reason()
        );

        RecognitionData contested = committedData(
                RecognitionPath.LAWFUL_GOOD,
                null
        );

        setMoralHistory(
                contested,
                24.0D,
                22.0D,
                35.0D,
                34.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        );

        setTemperamentHistory(
                contested,
                23.0D,
                21.0D,
                34.0D,
                33.0D,
                1,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
        );

        RecognitionIdentityHistoryResolution contestedResolution =
                RecognitionIdentityHistoryResolver.resolve(
                        contested
                );

        addCheck(
                checks,
                "Two unresolved contested axes resolve to Fractured",
                contestedResolution.modifier()
                        == RecognitionIdentityHistoryModifier.FRACTURED
                        && contestedResolution.moralContested()
                        && contestedResolution.temperamentContested(),
                contestedResolution.reason()
        );

        RecognitionData reconciledMoral = committedData(
                RecognitionPath.NEUTRAL_GOOD,
                null
        );

        setMoralHistory(
                reconciledMoral,
                36.0D,
                3.0D,
                36.0D,
                34.0D,
                2,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
        );

        RecognitionIdentityHistoryResolution reconciledMoralResolution =
                RecognitionIdentityHistoryResolver.resolve(
                        reconciledMoral
                );

        addCheck(
                checks,
                "Returning to the original moral direction after two reversals resolves to Reconciled",
                reconciledMoralResolution.modifier()
                        == RecognitionIdentityHistoryModifier.RECONCILED
                        && reconciledMoralResolution.moralReturned(),
                reconciledMoralResolution.reason()
        );

        RecognitionData reconciledTemperament = committedData(
                RecognitionPath.CHAOTIC_NEUTRAL,
                null
        );

        setTemperamentHistory(
                reconciledTemperament,
                3.0D,
                36.0D,
                34.0D,
                36.0D,
                2,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
        );

        RecognitionIdentityHistoryResolution reconciledTemperamentResolution =
                RecognitionIdentityHistoryResolver.resolve(
                        reconciledTemperament
                );

        addCheck(
                checks,
                "Returning to the original temperament after two reversals resolves to Reconciled",
                reconciledTemperamentResolution.modifier()
                        == RecognitionIdentityHistoryModifier.RECONCILED
                        && reconciledTemperamentResolution
                        .temperamentReturned(),
                reconciledTemperamentResolution.reason()
        );

        RecognitionData crossingOrigin = committedData(
                RecognitionPath.LAWFUL_NEUTRAL,
                RecognitionPath.LAWFUL_GOOD
        );

        setMoralHistory(
                crossingOrigin,
                2.0D,
                34.0D,
                35.0D,
                34.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        );

        RecognitionIdentityHistoryResolution crossingResolution =
                RecognitionIdentityHistoryResolver.resolve(
                        crossingOrigin
                );

        addCheck(
                checks,
                "A directional secondary path supplies the frozen axis for a crossing",
                crossingResolution.modifier()
                        == RecognitionIdentityHistoryModifier.FALLEN
                        && RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
                        .equals(
                                crossingResolution
                                        .originalMoralDirection()
                        ),
                "original moral="
                        + crossingResolution
                        .originalMoralDirection()
        );
    }

    private static void validateModifierPersistence(
            List<Check> checks
    ) {
        RecognitionData fallen = committedData(
                RecognitionPath.LAWFUL_GOOD,
                null
        );

        setMoralHistory(
                fallen,
                2.0D,
                35.0D,
                36.0D,
                35.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        );

        String primaryBefore = fallen.getString(
                RecognitionStatKeys.PRIMARY_PATH
        );

        String titleBefore = fallen.getString(
                RecognitionStatKeys.BESTOWED_TITLE
        );

        String frozenModifierBefore = fallen.getString(
                RecognitionStatKeys.CONTRADICTION_MODIFIER
        );

        RecognitionIdentityHistoryResolver.StoreResult firstStore =
                RecognitionIdentityHistoryResolver
                        .resolveAndStoreAfterDeed(fallen);

        RecognitionIdentityHistoryResolver.StoreResult secondStore =
                RecognitionIdentityHistoryResolver
                        .resolveAndStoreAfterDeed(fallen);

        addCheck(
                checks,
                "A resolved known modifier is stored after a qualifying deed",
                firstStore.changed()
                        && RecognitionIdentityHistoryModifier.FALLEN.id()
                        .equals(
                                fallen.getString(
                                        RecognitionStatKeys
                                                .IDENTITY_HISTORY_MODIFIER
                                )
                        ),
                firstStore.detail()
        );

        addCheck(
                checks,
                "Storing the same resolved modifier is idempotent",
                !secondStore.changed()
                        && RecognitionIdentityHistoryModifier.FALLEN.id()
                        .equals(secondStore.storedModifierId()),
                secondStore.detail()
        );

        addCheck(
                checks,
                "History modifier storage never rewrites frozen recognition",
                primaryBefore.equals(
                        fallen.getString(
                                RecognitionStatKeys.PRIMARY_PATH
                        )
                )
                        && titleBefore.equals(
                        fallen.getString(
                                RecognitionStatKeys.BESTOWED_TITLE
                        )
                )
                        && frozenModifierBefore.equals(
                        fallen.getString(
                                RecognitionStatKeys.CONTRADICTION_MODIFIER
                        )
                ),
                "committed identity preserved"
        );

        RecognitionData unknown = committedData(
                RecognitionPath.LAWFUL_GOOD,
                null
        );

        setMoralHistory(
                unknown,
                2.0D,
                35.0D,
                36.0D,
                35.0D,
                1,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        );

        unknown.setString(
                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER,
                "future_modifier_id"
        );

        RecognitionIdentityHistoryResolver.StoreResult unknownStore =
                RecognitionIdentityHistoryResolver
                        .resolveAndStoreAfterDeed(unknown);

        addCheck(
                checks,
                "Automatic resolution preserves unknown future modifier IDs",
                unknownStore.unknownModifierPreserved()
                        && "future_modifier_id".equals(
                        unknown.getString(
                                RecognitionStatKeys
                                        .IDENTITY_HISTORY_MODIFIER
                        )
                ),
                unknownStore.detail()
        );

        RecognitionData eventDriven = committedData(
                RecognitionPath.LAWFUL_GOOD,
                null
        );

        for (int index = 0; index < 4; index++) {
            RecognitionIdentityHistoryIntegration.record(
                    eventDriven,
                    RecognitionIdentityHistoryIntegration
                            .TrackedDeed
                            .RAID_VICTORY,
                    10_000L
            );
        }

        for (int index = 0; index < 4; index++) {
            RecognitionIdentityHistoryIntegration.record(
                    eventDriven,
                    RecognitionIdentityHistoryIntegration
                            .TrackedDeed
                            .OWNED_SUBORDINATE_KILLED,
                    10_000L
            );
        }

        addCheck(
                checks,
                "Event integration resolves the modifier only after qualifying deeds",
                RecognitionIdentityHistoryModifier.FALLEN.id()
                        .equals(
                                eventDriven.getString(
                                        RecognitionStatKeys
                                                .IDENTITY_HISTORY_MODIFIER
                                )
                        )
                        && eventDriven.getCounter(
                        RecognitionStatKeys.MORAL_REVERSAL_COUNT
                ) == 1,
                "modifier="
                        + eventDriven.getString(
                        RecognitionStatKeys
                                .IDENTITY_HISTORY_MODIFIER
                )
                        + ", reversals="
                        + eventDriven.getCounter(
                        RecognitionStatKeys.MORAL_REVERSAL_COUNT
                )
        );
    }

    private static RecognitionData committedData(
            RecognitionPath primary,
            RecognitionPath secondary
    ) {
        RecognitionData data = new RecognitionData();
        RecognitionIdentityHistoryService.ensureCurrent(data);

        data.setFlag(
                RecognitionStatKeys.NAMING_COMMITTED,
                true
        );

        data.setFlag(
                RecognitionStatKeys.PURE_RECOGNITION,
                secondary == null
        );

        data.setString(
                RecognitionStatKeys.PRIMARY_PATH,
                primary == null ? "" : primary.getId()
        );

        data.setString(
                RecognitionStatKeys.SECONDARY_PATH,
                secondary == null ? "" : secondary.getId()
        );

        data.setString(
                RecognitionStatKeys.BESTOWED_TITLE,
                "the Validation Bearer"
        );

        data.setString(
                RecognitionStatKeys.CONTRADICTION_MODIFIER,
                RecognitionIdentityHistoryModifier.NONE.id()
        );

        return data;
    }

    private static void setMoralHistory(
            RecognitionData data,
            double goodMomentum,
            double evilMomentum,
            double highestGood,
            double highestEvil,
            int reversals,
            String storedDirection
    ) {
        data.setMeasurement(
                RecognitionStatKeys.GOOD_MOMENTUM,
                goodMomentum
        );

        data.setMeasurement(
                RecognitionStatKeys.EVIL_MOMENTUM,
                evilMomentum
        );

        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_GOOD_COMMITMENT,
                highestGood
        );

        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_EVIL_COMMITMENT,
                highestEvil
        );

        data.setCounter(
                RecognitionStatKeys.MORAL_REVERSAL_COUNT,
                reversals
        );

        data.setString(
                RecognitionStatKeys.MORAL_ESTABLISHED_DIRECTION,
                storedDirection
        );
    }

    private static void setTemperamentHistory(
            RecognitionData data,
            double orderMomentum,
            double freedomMomentum,
            double highestOrder,
            double highestFreedom,
            int reversals,
            String storedDirection
    ) {
        data.setMeasurement(
                RecognitionStatKeys.ORDER_MOMENTUM,
                orderMomentum
        );

        data.setMeasurement(
                RecognitionStatKeys.FREEDOM_MOMENTUM,
                freedomMomentum
        );

        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_ORDER_COMMITMENT,
                highestOrder
        );

        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_FREEDOM_COMMITMENT,
                highestFreedom
        );

        data.setCounter(
                RecognitionStatKeys.TEMPERAMENT_REVERSAL_COUNT,
                reversals
        );

        data.setString(
                RecognitionStatKeys.TEMPERAMENT_ESTABLISHED_DIRECTION,
                storedDirection
        );
    }

    private static void validateModifierIdentifiers(
            List<Check> checks
    ) {
        boolean allRoundTrip = true;

        for (RecognitionIdentityHistoryModifier modifier :
                RecognitionIdentityHistoryModifier.values()) {
            allRoundTrip &= RecognitionIdentityHistoryModifier
                    .byId(modifier.id())
                    .orElse(null) == modifier;
        }

        RecognitionData data = new RecognitionData();
        RecognitionIdentityHistoryService.ensureCurrent(data);

        data.setString(
                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER,
                "future_modifier_id"
        );

        RecognitionIdentityHistoryService.ensureCurrent(data);

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "Stable modifier IDs round-trip without enum ordinals",
                allRoundTrip,
                "known modifiers="
                        + RecognitionIdentityHistoryModifier.values().length
        );

        addCheck(
                checks,
                "Unknown future modifier IDs are preserved",
                "future_modifier_id".equals(snapshot.rawModifierId())
                        && snapshot.knownModifier().isEmpty(),
                snapshot.rawModifierId()
        );
    }

    private static void validateCommittedFieldsUnchanged(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setFlag(
                RecognitionStatKeys.NAMING_COMMITTED,
                true
        );

        data.setFlag(
                RecognitionStatKeys.PURE_RECOGNITION,
                false
        );

        data.setString(
                RecognitionStatKeys.PRIMARY_PATH,
                RecognitionPath.LAWFUL_EVIL.getId()
        );

        data.setString(
                RecognitionStatKeys.SECONDARY_PATH,
                RecognitionPath.LAWFUL_NEUTRAL.getId()
        );

        data.setString(
                RecognitionStatKeys.BESTOWED_TITLE,
                "the Dread Sovereign"
        );

        data.setString(
                RecognitionStatKeys.CONTRADICTION_MODIFIER,
                "none"
        );

        String primaryBefore = data.getString(RecognitionStatKeys.PRIMARY_PATH);
        String secondaryBefore = data.getString(RecognitionStatKeys.SECONDARY_PATH);
        String titleBefore = data.getString(RecognitionStatKeys.BESTOWED_TITLE);
        String frozenModifierBefore = data.getString(
                RecognitionStatKeys.CONTRADICTION_MODIFIER
        );

        RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                RecognitionIdentityHistoryService.MoralDirection.GOOD,
                20.0D,
                1_000L
        );

        RecognitionIdentityHistoryService.registerMoralReversal(data);

        RecognitionIdentityHistoryService.setResolvedModifier(
                data,
                RecognitionIdentityHistoryModifier.REDEEMED
        );

        addCheck(
                checks,
                "History mutations never rewrite frozen committed identity",
                primaryBefore.equals(data.getString(RecognitionStatKeys.PRIMARY_PATH))
                        && secondaryBefore.equals(data.getString(RecognitionStatKeys.SECONDARY_PATH))
                        && titleBefore.equals(data.getString(RecognitionStatKeys.BESTOWED_TITLE))
                        && frozenModifierBefore.equals(
                        data.getString(RecognitionStatKeys.CONTRADICTION_MODIFIER)
                ),
                "committed path/title/modifier preserved"
        );
    }

    private static void validateRecognitionEvaluationUnchanged(
            List<Check> checks,
            List<String> warnings
    ) {
        try {
            RecognitionData data = new RecognitionData();

            data.setCounter(
                    RecognitionStatKeys.CIVILIANS_DEFENDED,
                    12
            );

            data.setCounter(
                    RecognitionStatKeys.CIVILIAN_KILLS,
                    2
            );

            data.setCounter(
                    RecognitionStatKeys.HIGHEST_SUBORDINATES,
                    8
            );

            data.addUniqueValue(
                    RecognitionStatKeys.DISCOVERY_MILESTONES,
                    "minecraft:the_nether"
            );

            RecognitionEvaluation before =
                    RecognitionPathEvaluator.evaluate(data);

            RecognitionIdentityHistoryService.recordMoralDeed(
                    data,
                    RecognitionIdentityHistoryService.MoralDirection.EVIL,
                    23.0D,
                    100_000L
            );

            RecognitionIdentityHistoryService.recordTemperamentDeed(
                    data,
                    RecognitionIdentityHistoryService.TemperamentDirection.FREEDOM,
                    21.0D,
                    100_000L
            );

            RecognitionIdentityHistoryService.registerMoralReversal(data);
            RecognitionIdentityHistoryService.registerTemperamentReversal(data);

            RecognitionEvaluation after =
                    RecognitionPathEvaluator.evaluate(data);

            boolean scoresEqual = scoreMapsEqual(
                    before.getPathScores(),
                    after.getPathScores()
            );

            boolean selectionsEqual = selectionSignature(before)
                    .equals(selectionSignature(after));

            addCheck(
                    checks,
                    "History foundation does not affect live path evaluation",
                    scoresEqual && selectionsEqual,
                    "before=" + selectionSignature(before)
                            + ", after=" + selectionSignature(after)
            );
        } catch (RuntimeException exception) {
            addCheck(
                    checks,
                    "History foundation does not affect live path evaluation",
                    false,
                    exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );

            warnings.add(
                    "Path-isolation validation raised an exception; inspect the current evaluator integration."
            );
        }
    }

    private static void validateIncarnationReset(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                RecognitionIdentityHistoryService.MoralDirection.GOOD,
                17.0D,
                1_000L
        );

        RecognitionIdentityHistoryService.registerMoralReversal(data);

        data.resetForNewIncarnation("validation-incarnation");

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        addCheck(
                checks,
                "New-incarnation reset clears contradiction history",
                snapshot.storedVersion() == 0
                        && approximately(snapshot.goodMomentum(), 0.0D)
                        && snapshot.moralReversalCount() == 0
                        && snapshot.migrationState()
                        == RecognitionIdentityHistoryService.MigrationState.UNINITIALIZED,
                "version=" + snapshot.storedVersion()
                        + ", momentum=" + snapshot.goodMomentum()
                        + ", reversals=" + snapshot.moralReversalCount()
        );
    }

    private static boolean scoreMapsEqual(
            Map<RecognitionPath, Double> first,
            Map<RecognitionPath, Double> second
    ) {
        for (RecognitionPath path : RecognitionPath.values()) {
            if (!approximately(
                    first.getOrDefault(path, 0.0D),
                    second.getOrDefault(path, 0.0D)
            )) {
                return false;
            }
        }

        return true;
    }

    private static String selectionSignature(
            RecognitionEvaluation evaluation
    ) {
        return evaluation.getSelection()
                .map(selection ->
                        selection.primaryPath().getId()
                                + "/"
                                + (
                                selection.hasSecondaryPath()
                                        ? selection.secondaryPath().getId()
                                        : "none"
                        )
                                + "/"
                                + selection.pure()
                )
                .orElse("none");
    }

    private static void addCheck(
            List<Check> checks,
            String name,
            boolean passed,
            String detail
    ) {
        checks.add(
                new Check(
                        name,
                        passed,
                        detail == null ? "" : detail
                )
        );
    }

    private static boolean approximately(
            double first,
            double second
    ) {
        return Math.abs(first - second) <= EPSILON;
    }

    public record Check(
            String name,
            boolean passed,
            String detail
    ) {

        public Check {
            name = name == null ? "Unnamed check" : name;
            detail = detail == null ? "" : detail;
        }
    }

    public record Report(
            boolean passed,
            int historyVersion,
            List<Check> checks,
            List<String> warnings
    ) {

        public Report {
            historyVersion = Math.max(1, historyVersion);
            checks = checks == null ? List.of() : List.copyOf(checks);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public long passedChecks() {
            return checks.stream().filter(Check::passed).count();
        }

        public long failedChecks() {
            return checks.size() - passedChecks();
        }
    }
}