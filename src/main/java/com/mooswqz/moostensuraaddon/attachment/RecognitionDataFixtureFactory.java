package com.mooswqz.moostensuraaddon.attachment;

import com.mooswqz.moostensuraaddon.recognition.RecognitionCommitRecord;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCommittedResult;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPath;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPathSelection;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic debug fixtures for release migration testing.
 *
 * <p>The factory is used only by the permission-level-four debug command and
 * its in-game validation harness. It deliberately avoids live evaluators and
 * title pools so every fixture has a stable, auditable payload.</p>
 */
public final class RecognitionDataFixtureFactory {

    public static final int FUTURE_VERSION_OFFSET = 41;

    public static final String LEGACY_TITLE =
            "the Legacy Witness";

    public static final String FUTURE_TITLE =
            "the Future Sentinel";

    private static final String FUTURE_COUNTER_KEY =
            "fixture_future_counter";

    private static final String FUTURE_MEASUREMENT_KEY =
            "fixture_future_measurement";

    private static final String FUTURE_FLAG_KEY =
            "fixture_future_flag";

    private static final String FUTURE_STRING_KEY =
            "fixture_future_string";

    private static final String FUTURE_COLLECTION_KEY =
            "fixture_future_collection";

    private RecognitionDataFixtureFactory() {
    }

    /**
     * Creates a version-1 committed payload without running its constructor
     * migration. Saving and reopening the player exercises the real codec
     * migration path.
     */
    public static RecognitionData createUnmigratedLegacyCommitted() {
        return new RecognitionData(
                1,
                Map.of(),
                Map.of(
                        RecognitionStatKeys.PRIMARY_SCORE_AT_COMMIT,
                        160.0D,
                        RecognitionStatKeys.SECONDARY_SCORE_AT_COMMIT,
                        0.0D
                ),
                Map.of(
                        RecognitionStatKeys.NAMING_COMMITTED,
                        true,
                        RecognitionStatKeys.PURE_RECOGNITION,
                        true
                ),
                Map.of(
                        RecognitionStatKeys.PRIMARY_PATH,
                        "chaotic evil",
                        RecognitionStatKeys.BESTOWED_TITLE,
                        LEGACY_TITLE
                ),
                Map.of(),
                false
        );
    }

    /**
     * Creates one payload whose schema, result, rules and reward versions are
     * all newer than this build. Every map also contains a sentinel.
     */
    public static RecognitionData createFutureVersionFixture() {
        return new RecognitionData(
                RecognitionData.CURRENT_DATA_VERSION
                        + FUTURE_VERSION_OFFSET,
                Map.of(
                        RecognitionStatKeys.RECOGNITION_RESULT_VERSION,
                        RecognitionCommitRecord.CURRENT_RESULT_VERSION
                                + FUTURE_VERSION_OFFSET,
                        RecognitionStatKeys.RECOGNITION_RULES_VERSION,
                        RecognitionCommitRecord.CURRENT_RULES_VERSION
                                + FUTURE_VERSION_OFFSET,
                        RecognitionStatKeys.REWARD_PROFILE_VERSION,
                        RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION
                                + FUTURE_VERSION_OFFSET,
                        FUTURE_COUNTER_KEY,
                        731
                ),
                Map.of(
                        RecognitionStatKeys.PRIMARY_SCORE_AT_COMMIT,
                        987.25D,
                        FUTURE_MEASUREMENT_KEY,
                        412.5D
                ),
                Map.of(
                        RecognitionStatKeys.NAMING_COMMITTED,
                        true,
                        RecognitionStatKeys.PURE_RECOGNITION,
                        true,
                        RecognitionStatKeys.REVEAL_PENDING,
                        false,
                        FUTURE_FLAG_KEY,
                        true
                ),
                Map.ofEntries(
                        Map.entry(
                                RecognitionStatKeys.PRIMARY_PATH,
                                RecognitionPath.CHAOTIC_EVIL.getId()
                        ),
                        Map.entry(
                                RecognitionStatKeys.BESTOWED_TITLE,
                                FUTURE_TITLE
                        ),
                        Map.entry(
                                RecognitionStatKeys.FROZEN_DISPLAY_NAME,
                                "Fixture " + FUTURE_TITLE
                        ),
                        Map.entry(
                                RecognitionStatKeys.INCARNATION_ID,
                                "future-fixture-incarnation"
                        ),
                        Map.entry(
                                RecognitionStatKeys.CONTRADICTION_MODIFIER,
                                "future-semantic-modifier"
                        ),
                        Map.entry(
                                RecognitionStatKeys.BALANCE_SOURCE_AT_COMMIT,
                                "future-fixture-balance"
                        ),
                        Map.entry(
                                RecognitionStatKeys.RECOGNITION_MIGRATION_SOURCE,
                                "future-fixture-source"
                        ),
                        Map.entry(
                                FUTURE_STRING_KEY,
                                "future-string-sentinel"
                        )
                ),
                Map.of(
                        FUTURE_COLLECTION_KEY,
                        List.of("alpha", "beta")
                )
        );
    }

    public static FutureInspection inspectFutureFixture(
            RecognitionData data
    ) {
        if (data == null) {
            return new FutureInspection(
                    false,
                    false,
                    "missing",
                    "missing",
                    "No recognition attachment is present."
            );
        }

        RecognitionData expected = createFutureVersionFixture();
        RecognitionData.PersistentState actualState =
                data.persistentStateForFixture();
        RecognitionData.PersistentState expectedState =
                expected.persistentStateForFixture();

        boolean exactMatch = actualState.equals(expectedState);
        boolean futureBlocked = data.isWriteBlockedByFutureVersion();

        return new FutureInspection(
                exactMatch,
                futureBlocked,
                fingerprint(expectedState),
                fingerprint(actualState),
                exactMatch && futureBlocked
                        ? "Future fixture is byte-semantically unchanged and write-blocked."
                        : "Future fixture differs from its frozen expected payload."
        );
    }

    public static ValidationReport validate() {
        List<Check> checks = new ArrayList<>();

        validateLegacyMigration(checks);
        validateFutureWriteBlocking(checks);

        return new ValidationReport(checks);
    }

    private static void validateLegacyMigration(
            List<Check> checks
    ) {
        RecognitionData raw = createUnmigratedLegacyCommitted();
        RecognitionData.PersistentState rawState =
                raw.persistentStateForFixture();

        raw.setCounter(
                "fixture_pre_reload_write",
                9
        );
        raw.completeCommittedPlayerMetadata(
                "Dev the Premature Rewrite",
                "premature-incarnation"
        );
        raw.clearNamingCommitPreservingLifeProgress();

        add(
                checks,
                "Raw legacy fixture rejects live writes until codec reload",
                rawState.equals(
                        raw.persistentStateForFixture()
                ),
                "before=" + fingerprint(rawState)
                        + ", after="
                        + fingerprint(
                        raw.persistentStateForFixture()
                )
        );

        add(
                checks,
                "Raw legacy fixture remains version 1 until reload",
                raw.getDataVersion() == 1
                        && "chaotic evil".equals(
                        raw.getCommittedPrimaryPathId()
                ),
                "version=" + raw.getDataVersion()
                        + ", path=" + raw.getCommittedPrimaryPathId()
        );

        RecognitionData migrated = new RecognitionData(
                rawState.dataVersion(),
                rawState.counters(),
                rawState.measurements(),
                rawState.flags(),
                rawState.strings(),
                rawState.collections()
        );

        add(
                checks,
                "Version-1 commitment migrates to the current schema",
                migrated.getDataVersion()
                        == RecognitionData.CURRENT_DATA_VERSION
                        && RecognitionPath.CHAOTIC_EVIL.getId().equals(
                        migrated.getCommittedPrimaryPathId()
                )
                        && RecognitionCommitRecord.CURRENT_RESULT_VERSION
                        == migrated.getRecognitionResultVersion()
                        && RecognitionCommitRecord.CURRENT_RULES_VERSION
                        == migrated.getRecognitionRulesVersion()
                        && RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION
                        == migrated.getRewardProfileVersion(),
                "version=" + migrated.getDataVersion()
                        + ", path=" + migrated.getCommittedPrimaryPathId()
        );

        add(
                checks,
                "Legacy migration records explicit provenance",
                "v1_to_v2".equals(
                        migrated.getRecognitionMigrationSource()
                ),
                "source=" + migrated.getRecognitionMigrationSource()
        );

        RecognitionData.PersistentState migratedState =
                migrated.persistentStateForFixture();
        RecognitionData migratedAgain = new RecognitionData(
                migratedState.dataVersion(),
                migratedState.counters(),
                migratedState.measurements(),
                migratedState.flags(),
                migratedState.strings(),
                migratedState.collections()
        );

        add(
                checks,
                "Legacy migration is idempotent",
                migratedState.equals(
                        migratedAgain.persistentStateForFixture()
                ),
                "first=" + fingerprint(migratedState)
                        + ", second="
                        + fingerprint(
                        migratedAgain.persistentStateForFixture()
                )
        );
    }

    private static void validateFutureWriteBlocking(
            List<Check> checks
    ) {
        RecognitionData future = createFutureVersionFixture();
        RecognitionData.PersistentState before =
                future.persistentStateForFixture();

        future.setCounter(FUTURE_COUNTER_KEY, 999);
        future.setCounterMaximum(FUTURE_COUNTER_KEY, 1_000);
        future.incrementCounter(FUTURE_COUNTER_KEY);
        future.addToCounter(FUTURE_COUNTER_KEY, 10);
        future.setMeasurement(FUTURE_MEASUREMENT_KEY, 999.0D);
        future.setMeasurementMaximum(FUTURE_MEASUREMENT_KEY, 1_000.0D);
        future.setFlag(FUTURE_FLAG_KEY, false);
        future.setString(FUTURE_STRING_KEY, "mutated");
        future.addUniqueValue(FUTURE_COLLECTION_KEY, "gamma");
        future.removeUniqueValue(FUTURE_COLLECTION_KEY, "alpha");
        future.markRevealPresented();
        future.completeCommittedPlayerMetadata(
                "Mutated Display",
                "mutated-incarnation"
        );
        future.clearNamingCommitPreservingLifeProgress();
        future.resetForNewIncarnation("mutated-life");

        add(
                checks,
                "Future schema/result/rules/reward payload is write-blocked",
                future.isWriteBlockedByFutureVersion()
                        && before.equals(
                        future.persistentStateForFixture()
                ),
                "before=" + fingerprint(before)
                        + ", after="
                        + fingerprint(
                        future.persistentStateForFixture()
                )
        );

        validateIndividualFutureDimension(
                checks,
                "Future schema alone is write-blocked",
                RecognitionData.CURRENT_DATA_VERSION
                        + FUTURE_VERSION_OFFSET,
                RecognitionCommitRecord.CURRENT_RESULT_VERSION,
                RecognitionCommitRecord.CURRENT_RULES_VERSION,
                RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION
        );
        validateIndividualFutureDimension(
                checks,
                "Future result version alone is write-blocked",
                RecognitionData.CURRENT_DATA_VERSION,
                RecognitionCommitRecord.CURRENT_RESULT_VERSION
                        + FUTURE_VERSION_OFFSET,
                RecognitionCommitRecord.CURRENT_RULES_VERSION,
                RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION
        );
        validateIndividualFutureDimension(
                checks,
                "Future rules version alone is write-blocked",
                RecognitionData.CURRENT_DATA_VERSION,
                RecognitionCommitRecord.CURRENT_RESULT_VERSION,
                RecognitionCommitRecord.CURRENT_RULES_VERSION
                        + FUTURE_VERSION_OFFSET,
                RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION
        );
        validateIndividualFutureDimension(
                checks,
                "Future reward profile alone is write-blocked",
                RecognitionData.CURRENT_DATA_VERSION,
                RecognitionCommitRecord.CURRENT_RESULT_VERSION,
                RecognitionCommitRecord.CURRENT_RULES_VERSION,
                RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION
                        + FUTURE_VERSION_OFFSET
        );

        RecognitionData futureRewardInLegacySchema =
                createVersionVariant(
                        1,
                        RecognitionCommitRecord.CURRENT_RESULT_VERSION,
                        RecognitionCommitRecord.CURRENT_RULES_VERSION,
                        RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION
                                + FUTURE_VERSION_OFFSET
                );

        add(
                checks,
                "Future semantic version prevents legacy reinterpretation",
                futureRewardInLegacySchema.getDataVersion() == 1
                        && futureRewardInLegacySchema
                        .isWriteBlockedByFutureVersion(),
                "stored schema="
                        + futureRewardInLegacySchema.getDataVersion()
        );

        RecognitionData futureUncommitted = new RecognitionData(
                RecognitionData.CURRENT_DATA_VERSION
                        + FUTURE_VERSION_OFFSET,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );

        boolean committed = futureUncommitted.commitNaming(
                new RecognitionPathSelection(
                        RecognitionPath.CHAOTIC_EVIL,
                        null,
                        true,
                        160.0D,
                        0.0D
                ),
                "the Forbidden Rewrite"
        );

        add(
                checks,
                "Future uncommitted schema rejects a new commitment",
                !committed
                        && !futureUncommitted.isNamingCommitted(),
                "committed=" + committed
        );

        add(
                checks,
                "Future committed result remains diagnosable",
                future.getCommittedResult().migrationState()
                        == RecognitionCommittedResult.MigrationState
                        .FUTURE_VERSION,
                "state="
                        + future.getCommittedResult().migrationState()
        );
    }

    private static void validateIndividualFutureDimension(
            List<Check> checks,
            String name,
            int dataVersion,
            int resultVersion,
            int rulesVersion,
            int rewardVersion
    ) {
        RecognitionData data = createVersionVariant(
                dataVersion,
                resultVersion,
                rulesVersion,
                rewardVersion
        );
        RecognitionData.PersistentState before =
                data.persistentStateForFixture();

        data.setCounter(FUTURE_COUNTER_KEY, 1_000);

        add(
                checks,
                name,
                data.isWriteBlockedByFutureVersion()
                        && before.equals(
                        data.persistentStateForFixture()
                ),
                "schema/result/rules/reward="
                        + dataVersion + "/"
                        + resultVersion + "/"
                        + rulesVersion + "/"
                        + rewardVersion
        );
    }

    private static RecognitionData createVersionVariant(
            int dataVersion,
            int resultVersion,
            int rulesVersion,
            int rewardVersion
    ) {
        return new RecognitionData(
                dataVersion,
                Map.of(
                        RecognitionStatKeys.RECOGNITION_RESULT_VERSION,
                        resultVersion,
                        RecognitionStatKeys.RECOGNITION_RULES_VERSION,
                        rulesVersion,
                        RecognitionStatKeys.REWARD_PROFILE_VERSION,
                        rewardVersion,
                        FUTURE_COUNTER_KEY,
                        731
                ),
                Map.of(),
                Map.of(
                        RecognitionStatKeys.NAMING_COMMITTED,
                        true,
                        RecognitionStatKeys.PURE_RECOGNITION,
                        true
                ),
                Map.of(
                        RecognitionStatKeys.PRIMARY_PATH,
                        RecognitionPath.CHAOTIC_EVIL.getId(),
                        RecognitionStatKeys.BESTOWED_TITLE,
                        FUTURE_TITLE,
                        RecognitionStatKeys.FROZEN_DISPLAY_NAME,
                        "Fixture " + FUTURE_TITLE,
                        RecognitionStatKeys.INCARNATION_ID,
                        "future-fixture-incarnation"
                ),
                Map.of()
        );
    }

    private static String fingerprint(
            RecognitionData.PersistentState state
    ) {
        if (state == null) {
            return "missing";
        }

        return String.format(
                "%08x",
                state.hashCode()
        );
    }

    private static void add(
            List<Check> checks,
            String name,
            boolean passed,
            String detail
    ) {
        checks.add(
                new Check(
                        name,
                        passed,
                        detail
                )
        );
    }

    public record FutureInspection(
            boolean exactMatch,
            boolean writeBlocked,
            String expectedFingerprint,
            String actualFingerprint,
            String detail
    ) {
        public boolean passed() {
            return exactMatch && writeBlocked;
        }
    }

    public record Check(
            String name,
            boolean passed,
            String detail
    ) {
    }

    public record ValidationReport(
            List<Check> checks
    ) {
        public ValidationReport {
            checks = checks == null
                    ? List.of()
                    : List.copyOf(checks);
        }

        public long passedChecks() {
            return checks.stream()
                    .filter(Check::passed)
                    .count();
        }

        public long failedChecks() {
            return checks.size() - passedChecks();
        }

        public boolean passed() {
            return failedChecks() == 0L;
        }
    }
}
