package com.mooswqz.moostensuraaddon.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceManager;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCommitRecord;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCommittedResult;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPath;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPathSelection;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecognitionData {

    private static final int LEGACY_DATA_VERSION =
            1;

    public static final int CURRENT_DATA_VERSION =
            2;

    private static final String LEGACY_MIGRATION_SOURCE =
            "v1_to_v2";

    private static final Codec<Map<String, Integer>> INTEGER_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    private static final Codec<Map<String, Double>> DOUBLE_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);

    private static final Codec<Map<String, Boolean>> BOOLEAN_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.BOOL);

    private static final Codec<Map<String, String>> STRING_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);

    private static final Codec<Map<String, List<String>>> STRING_LIST_MAP_CODEC =
            Codec.unboundedMap(
                    Codec.STRING,
                    Codec.STRING.listOf()
            );

    public static final Codec<RecognitionData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT
                            .optionalFieldOf(
                                    "data_version",
                                    LEGACY_DATA_VERSION
                            )
                            .forGetter(
                                    RecognitionData::getDataVersion
                            ),

                    INTEGER_MAP_CODEC
                            .optionalFieldOf(
                                    "counters",
                                    Map.of()
                            )
                            .forGetter(
                                    RecognitionData::getCountersForCodec
                            ),

                    DOUBLE_MAP_CODEC
                            .optionalFieldOf(
                                    "measurements",
                                    Map.of()
                            )
                            .forGetter(
                                    RecognitionData::getMeasurementsForCodec
                            ),

                    BOOLEAN_MAP_CODEC
                            .optionalFieldOf(
                                    "flags",
                                    Map.of()
                            )
                            .forGetter(
                                    RecognitionData::getFlagsForCodec
                            ),

                    STRING_MAP_CODEC
                            .optionalFieldOf(
                                    "strings",
                                    Map.of()
                            )
                            .forGetter(
                                    RecognitionData::getStringsForCodec
                            ),

                    STRING_LIST_MAP_CODEC
                            .optionalFieldOf(
                                    "collections",
                                    Map.of()
                            )
                            .forGetter(
                                    RecognitionData::getCollectionsForCodec
                            )
            ).apply(
                    instance,
                    RecognitionData::new
            ));

    private int dataVersion;

    /**
     * Debug-fixture-only guard that preserves an artificial raw legacy
     * payload until it has passed through the real attachment codec once.
     *
     * <p>The field is deliberately not part of {@link #CODEC}. A normal
     * version-1 save therefore loads with this guard disabled and immediately
     * follows the production v1-to-v2 migration path.</p>
     */
    private final boolean preserveRawLegacyUntilCodecReload;

    private final Map<String, Integer> counters;
    private final Map<String, Double> measurements;
    private final Map<String, Boolean> flags;
    private final Map<String, String> strings;
    private final Map<String, List<String>> collections;

    public RecognitionData() {
        this(
                CURRENT_DATA_VERSION,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    public RecognitionData(
            int dataVersion,
            Map<String, Integer> counters,
            Map<String, Double> measurements,
            Map<String, Boolean> flags,
            Map<String, String> strings,
            Map<String, List<String>> collections
    ) {
        this(
                dataVersion,
                counters,
                measurements,
                flags,
                strings,
                collections,
                true
        );
    }

    RecognitionData(
            int dataVersion,
            Map<String, Integer> counters,
            Map<String, Double> measurements,
            Map<String, Boolean> flags,
            Map<String, String> strings,
            Map<String, List<String>> collections,
            boolean migrateOnLoad
    ) {
        this.dataVersion =
                Math.max(
                        LEGACY_DATA_VERSION,
                        dataVersion
                );

        this.preserveRawLegacyUntilCodecReload =
                !migrateOnLoad
                        && this.dataVersion
                        < CURRENT_DATA_VERSION;

        this.counters =
                sanitizeIntegerMap(
                        counters
                );

        this.measurements =
                sanitizeDoubleMap(
                        measurements
                );

        this.flags =
                sanitizeBooleanMap(
                        flags
                );

        this.strings =
                sanitizeStringMap(
                        strings
                );

        this.collections =
                sanitizeCollectionMap(
                        collections
                );

        if (migrateOnLoad
                && !isWriteBlockedByFutureVersion()) {
            migrateToCurrentVersion();
        }

        if (migrateOnLoad
                && !isWriteBlockedByFutureVersion()) {
            repairCurrentCommittedRecordDefaults();
        }
    }

    public int getDataVersion() {
        return dataVersion;
    }

    /**
     * Returns whether this payload was written by recognition semantics newer
     * than this build understands.
     *
     * <p>Unknown future schema, result, rules and reward-profile versions are
     * read-only. Older builds may inspect and re-save them, but must not
     * reinterpret, repair, reset or append progression to their maps.</p>
     */
    public boolean isWriteBlockedByFutureVersion() {
        return dataVersion > CURRENT_DATA_VERSION
                || getCounter(
                RecognitionStatKeys.RECOGNITION_RESULT_VERSION
        ) > RecognitionCommitRecord.CURRENT_RESULT_VERSION
                || getCounter(
                RecognitionStatKeys.RECOGNITION_RULES_VERSION
        ) > RecognitionCommitRecord.CURRENT_RULES_VERSION
                || getCounter(
                RecognitionStatKeys.REWARD_PROFILE_VERSION
                ) > RecognitionCommitRecord.CURRENT_REWARD_PROFILE_VERSION;
    }

    private boolean isMutationWriteBlocked() {
        return preserveRawLegacyUntilCodecReload
                || isWriteBlockedByFutureVersion();
    }

    public int getCounter(
            String key
    ) {
        if (!isValidKey(key)) {
            return 0;
        }

        return Math.max(
                0,
                counters.getOrDefault(
                        key,
                        0
                )
        );
    }

    public void setCounter(
            String key,
            int value
    ) {
        if (!isValidKey(key)
                || isMutationWriteBlocked()) {
            return;
        }

        counters.put(
                key,
                Math.max(
                        0,
                        value
                )
        );

        markCurrentVersion();
    }

    public void setCounterMaximum(
            String key,
            int value
    ) {
        if (!isValidKey(key)
                || isMutationWriteBlocked()) {
            return;
        }

        int safeValue =
                Math.max(
                        0,
                        value
                );

        if (safeValue > getCounter(key)) {
            counters.put(
                    key,
                    safeValue
            );

            markCurrentVersion();
        }
    }

    public int incrementCounter(
            String key
    ) {
        return addToCounter(
                key,
                1
        );
    }

    public int addToCounter(
            String key,
            int amount
    ) {
        if (!isValidKey(key)
                || amount == 0
                || isMutationWriteBlocked()) {
            return getCounter(key);
        }

        long result =
                (long) getCounter(key)
                        + amount;

        int safeResult;

        if (result <= 0L) {
            safeResult = 0;
        } else if (result
                >= Integer.MAX_VALUE) {
            safeResult =
                    Integer.MAX_VALUE;
        } else {
            safeResult =
                    (int) result;
        }

        counters.put(
                key,
                safeResult
        );

        markCurrentVersion();

        return safeResult;
    }

    public double getMeasurement(
            String key
    ) {
        if (!isValidKey(key)) {
            return 0.0D;
        }

        double value =
                measurements.getOrDefault(
                        key,
                        0.0D
                );

        if (!Double.isFinite(value)
                || value < 0.0D) {
            return 0.0D;
        }

        return value;
    }

    public void setMeasurement(
            String key,
            double value
    ) {
        if (!isValidKey(key)
                || isMutationWriteBlocked()) {
            return;
        }

        measurements.put(
                key,
                sanitizeMeasurement(
                        value
                )
        );

        markCurrentVersion();
    }

    public void setMeasurementMaximum(
            String key,
            double value
    ) {
        if (!isValidKey(key)
                || isMutationWriteBlocked()) {
            return;
        }

        double safeValue =
                sanitizeMeasurement(
                        value
                );

        if (safeValue > getMeasurement(key)) {
            measurements.put(
                    key,
                    safeValue
            );

            markCurrentVersion();
        }
    }

    public boolean getFlag(
            String key
    ) {
        if (!isValidKey(key)) {
            return false;
        }

        return flags.getOrDefault(
                key,
                false
        );
    }

    public void setFlag(
            String key,
            boolean value
    ) {
        if (!isValidKey(key)
                || isMutationWriteBlocked()) {
            return;
        }

        flags.put(
                key,
                value
        );

        markCurrentVersion();
    }

    public String getString(
            String key
    ) {
        if (!isValidKey(key)) {
            return "";
        }

        String value =
                strings.get(key);

        return value == null
                ? ""
                : value;
    }

    public void setString(
            String key,
            String value
    ) {
        if (!isValidKey(key)
                || isMutationWriteBlocked()) {
            return;
        }

        putStringDirect(
                key,
                value
        );

        markCurrentVersion();
    }

    public boolean addUniqueValue(
            String collectionKey,
            String value
    ) {
        if (!isValidKey(collectionKey)
                || value == null
                || value.isBlank()
                || isMutationWriteBlocked()) {
            return false;
        }

        String cleanedValue =
                value.trim();

        List<String> values =
                collections.computeIfAbsent(
                        collectionKey,
                        ignored ->
                                new ArrayList<>()
                );

        if (values.contains(
                cleanedValue
        )) {
            return false;
        }

        values.add(
                cleanedValue
        );

        markCurrentVersion();

        return true;
    }

    public boolean containsUniqueValue(
            String collectionKey,
            String value
    ) {
        if (!isValidKey(collectionKey)
                || value == null
                || value.isBlank()) {
            return false;
        }

        List<String> values =
                collections.get(
                        collectionKey
                );

        return values != null
                && values.contains(
                value.trim()
        );
    }

    public int getUniqueValueCount(
            String collectionKey
    ) {
        if (!isValidKey(collectionKey)) {
            return 0;
        }

        List<String> values =
                collections.get(
                        collectionKey
                );

        return values == null
                ? 0
                : values.size();
    }

    public List<String> getUniqueValues(
            String collectionKey
    ) {
        if (!isValidKey(collectionKey)) {
            return List.of();
        }

        List<String> values =
                collections.get(
                        collectionKey
                );

        if (values == null
                || values.isEmpty()) {
            return List.of();
        }

        return Collections.unmodifiableList(
                new ArrayList<>(
                        values
                )
        );
    }

    public boolean removeUniqueValue(
            String collectionKey,
            String value
    ) {
        if (!isValidKey(collectionKey)
                || value == null
                || value.isBlank()
                || isMutationWriteBlocked()) {
            return false;
        }

        List<String> values =
                collections.get(
                        collectionKey
                );

        if (values == null) {
            return false;
        }

        boolean removed =
                values.remove(
                        value.trim()
                );

        if (!removed) {
            return false;
        }

        if (values.isEmpty()) {
            collections.remove(
                    collectionKey
            );
        }

        markCurrentVersion();

        return true;
    }

    public boolean isNamingCommitted() {
        return getFlag(
                RecognitionStatKeys
                        .NAMING_COMMITTED
        );
    }

    public boolean isPureRecognition() {
        return getFlag(
                RecognitionStatKeys
                        .PURE_RECOGNITION
        );
    }

    public boolean isRevealPending() {
        return getFlag(
                RecognitionStatKeys
                        .REVEAL_PENDING
        );
    }

    public String getCommittedPrimaryPathId() {
        return getString(
                RecognitionStatKeys
                        .PRIMARY_PATH
        );
    }

    public String getCommittedSecondaryPathId() {
        return getString(
                RecognitionStatKeys
                        .SECONDARY_PATH
        );
    }

    public Optional<RecognitionPath>
    getCommittedPrimaryPath() {
        return RecognitionPath.byId(
                getCommittedPrimaryPathId()
        );
    }

    public Optional<RecognitionPath>
    getCommittedSecondaryPath() {
        return RecognitionPath.byId(
                getCommittedSecondaryPathId()
        );
    }

    public String getBestowedTitle() {
        return getString(
                RecognitionStatKeys
                        .BESTOWED_TITLE
        );
    }

    public String getFrozenDisplayName() {
        return getString(
                RecognitionStatKeys
                        .FROZEN_DISPLAY_NAME
        );
    }

    public String getContradictionModifier() {
        String modifier =
                getString(
                        RecognitionStatKeys
                                .CONTRADICTION_MODIFIER
                );

        return modifier.isBlank()
                ? RecognitionCommitRecord
                  .NO_CONTRADICTION
                : modifier;
    }

    public String getIncarnationId() {
        return getString(
                RecognitionStatKeys
                        .INCARNATION_ID
        );
    }

    public int getRecognitionResultVersion() {
        return getCounter(
                RecognitionStatKeys
                        .RECOGNITION_RESULT_VERSION
        );
    }

    public int getRecognitionRulesVersion() {
        return getCounter(
                RecognitionStatKeys
                        .RECOGNITION_RULES_VERSION
        );
    }

    public int getRewardProfileVersion() {
        return getCounter(
                RecognitionStatKeys
                        .REWARD_PROFILE_VERSION
        );
    }

    public String getBalanceSourceAtCommit() {
        return getString(
                RecognitionStatKeys
                        .BALANCE_SOURCE_AT_COMMIT
        );
    }

    public long getBalanceRevisionAtCommit() {
        return getStoredLong(
                RecognitionStatKeys
                        .BALANCE_REVISION_AT_COMMIT
        );
    }

    public long getCommitTimestampEpochMillis() {
        return getStoredLong(
                RecognitionStatKeys
                        .COMMIT_TIMESTAMP_EPOCH_MILLIS
        );
    }

    public String getRecognitionMigrationSource() {
        return getString(
                RecognitionStatKeys
                        .RECOGNITION_MIGRATION_SOURCE
        );
    }

    /**
     * Compatibility commitment route used by the current altar runtime.
     *
     * <p>It now captures every non-player-specific version and balance field.
     * Frozen display name and a missing incarnation ID are completed later by
     * {@link #completeCommittedPlayerMetadata(String, String)} when a player
     * context is available. The method signature remains unchanged, so older
     * altar call sites continue to compile.</p>
     */
    public boolean commitNaming(
            RecognitionPathSelection selection,
            String bestowedTitle
    ) {
        if (selection == null
                || bestowedTitle == null
                || bestowedTitle.isBlank()
                || isNamingCommitted()
                || isMutationWriteBlocked()) {
            return false;
        }

        RecognitionBalanceManager.State balanceState =
                RecognitionBalanceManager.getState();

        RecognitionCommitRecord record =
                RecognitionCommitRecord
                        .fromSelection(
                                selection,
                                bestowedTitle,
                                "",
                                balanceState
                                        .snapshot()
                                        .sourceId(),
                                balanceState
                                        .revision(),
                                System.currentTimeMillis(),
                                getIncarnationId()
                        );

        return commitNaming(
                record
        );
    }

    /**
     * Writes a fully prepared recognition result as one transaction.
     *
     * <p>The committed flag is written last. A defensive rollback restores all
     * maps and the previous data version if an unexpected runtime exception
     * occurs while applying the record.</p>
     */
    public boolean commitNaming(
            RecognitionCommitRecord record
    ) {
        if (record == null
                || isNamingCommitted()
                || isMutationWriteBlocked()) {
            return false;
        }

        Map<String, Integer> countersBackup =
                new LinkedHashMap<>(
                        counters
                );

        Map<String, Double> measurementsBackup =
                new LinkedHashMap<>(
                        measurements
                );

        Map<String, Boolean> flagsBackup =
                new LinkedHashMap<>(
                        flags
                );

        Map<String, String> stringsBackup =
                new LinkedHashMap<>(
                        strings
                );

        Map<String, List<String>> collectionsBackup =
                deepCopyCollections(
                        collections
                );

        int dataVersionBackup =
                dataVersion;

        try {
            counters.put(
                    RecognitionStatKeys
                            .RECOGNITION_RESULT_VERSION,
                    record.resultVersion()
            );

            counters.put(
                    RecognitionStatKeys
                            .RECOGNITION_RULES_VERSION,
                    record.rulesVersion()
            );

            counters.put(
                    RecognitionStatKeys
                            .REWARD_PROFILE_VERSION,
                    record.rewardProfileVersion()
            );

            measurements.put(
                    RecognitionStatKeys
                            .PRIMARY_SCORE_AT_COMMIT,
                    record.primaryScore()
            );

            measurements.put(
                    RecognitionStatKeys
                            .SECONDARY_SCORE_AT_COMMIT,
                    record.secondaryScore()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .PRIMARY_PATH,
                    record.primaryPathId()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .SECONDARY_PATH,
                    record.secondaryPathId()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .BESTOWED_TITLE,
                    record.bestowedTitle()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .FROZEN_DISPLAY_NAME,
                    record.frozenDisplayName()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .CONTRADICTION_MODIFIER,
                    record.contradictionModifier()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .BALANCE_SOURCE_AT_COMMIT,
                    record.balanceSource()
            );

            putStoredLongDirect(
                    RecognitionStatKeys
                            .BALANCE_REVISION_AT_COMMIT,
                    record.balanceRevision()
            );

            putStoredLongDirect(
                    RecognitionStatKeys
                            .COMMIT_TIMESTAMP_EPOCH_MILLIS,
                    record.committedAtEpochMillis()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .INCARNATION_ID,
                    record.incarnationId()
            );

            putStringDirect(
                    RecognitionStatKeys
                            .RECOGNITION_MIGRATION_SOURCE,
                    RecognitionCommitRecord
                            .NATIVE_MIGRATION_SOURCE
            );

            flags.put(
                    RecognitionStatKeys
                            .PURE_RECOGNITION,
                    record.pure()
            );

            flags.put(
                    RecognitionStatKeys
                            .REVEAL_PENDING,
                    true
            );

            /*
             * Published last so no observer can see a committed record before
             * the frozen fields have been written.
             */
            flags.put(
                    RecognitionStatKeys
                            .NAMING_COMMITTED,
                    true
            );

            markCurrentVersion();

            return true;
        } catch (RuntimeException exception) {
            restoreMap(
                    counters,
                    countersBackup
            );

            restoreMap(
                    measurements,
                    measurementsBackup
            );

            restoreMap(
                    flags,
                    flagsBackup
            );

            restoreMap(
                    strings,
                    stringsBackup
            );

            collections.clear();
            collections.putAll(
                    deepCopyCollections(
                            collectionsBackup
                    )
            );

            dataVersion =
                    dataVersionBackup;

            return false;
        }
    }

    /**
     * Fills only player-context fields that were unavailable to a legacy
     * commitment or v1 migration.
     *
     * <p>Existing non-blank values are never overwritten.</p>
     */
    public boolean completeCommittedPlayerMetadata(
            String frozenDisplayName,
            String incarnationId
    ) {
        if (!isNamingCommitted()
                || isMutationWriteBlocked()) {
            return false;
        }

        boolean changed =
                false;

        if (getFrozenDisplayName()
                .isBlank()
                && frozenDisplayName != null
                && !frozenDisplayName.isBlank()) {

            strings.put(
                    RecognitionStatKeys
                            .FROZEN_DISPLAY_NAME,
                    frozenDisplayName.trim()
            );

            changed = true;
        }

        if (getIncarnationId()
                .isBlank()
                && incarnationId != null
                && !incarnationId.isBlank()) {

            strings.put(
                    RecognitionStatKeys
                            .INCARNATION_ID,
                    incarnationId.trim()
            );

            changed = true;
        }

        if (changed) {
            markCurrentVersion();
        }

        return changed;
    }

    public RecognitionCommittedResult
    getCommittedResult() {
        boolean committed =
                isNamingCommitted();

        List<String> issues =
                new ArrayList<>();

        String primaryId =
                getCommittedPrimaryPathId();

        String secondaryId =
                getCommittedSecondaryPathId();

        if (committed) {
            if (primaryId.isBlank()) {
                issues.add(
                        "Missing committed primary path ID."
                );
            } else if (RecognitionPath.byId(
                    primaryId
            ).isEmpty()) {
                issues.add(
                        "Unknown committed primary path ID: "
                                + primaryId
                );
            }

            if (isPureRecognition()) {
                if (!secondaryId.isBlank()) {
                    issues.add(
                            "Pure recognition unexpectedly stores a secondary path ID."
                    );
                }
            } else {
                if (secondaryId.isBlank()) {
                    issues.add(
                            "Combined recognition is missing a secondary path ID."
                    );
                } else if (RecognitionPath.byId(
                        secondaryId
                ).isEmpty()) {
                    issues.add(
                            "Unknown committed secondary path ID: "
                                    + secondaryId
                    );
                }
            }

            if (getBestowedTitle().isBlank()) {
                issues.add(
                        "Missing frozen bestowed title."
                );
            }

            if (getRecognitionResultVersion()
                    < 1) {
                issues.add(
                        "Missing recognition result version."
                );
            }

            if (getRecognitionRulesVersion()
                    < 1) {
                issues.add(
                        "Missing recognition rules version."
                );
            }

            if (getRewardProfileVersion()
                    < 1) {
                issues.add(
                        "Missing reward profile version."
                );
            }

            if (getFrozenDisplayName().isBlank()) {
                issues.add(
                        "Frozen display name awaits player-context backfill."
                );
            }

            if (getIncarnationId().isBlank()) {
                issues.add(
                        "Incarnation ID awaits player-context backfill."
                );
            }
        }

        return new RecognitionCommittedResult(
                committed,
                dataVersion,
                getRecognitionResultVersion(),
                getRecognitionRulesVersion(),
                getRewardProfileVersion(),
                primaryId,
                secondaryId,
                isPureRecognition(),
                getContradictionModifier(),
                getBestowedTitle(),
                getFrozenDisplayName(),
                getBalanceSourceAtCommit(),
                getBalanceRevisionAtCommit(),
                getCommitTimestampEpochMillis(),
                getIncarnationId(),
                getMeasurement(
                        RecognitionStatKeys
                                .PRIMARY_SCORE_AT_COMMIT
                ),
                getMeasurement(
                        RecognitionStatKeys
                                .SECONDARY_SCORE_AT_COMMIT
                ),
                getRecognitionMigrationSource(),
                issues
        );
    }

    public void markRevealPresented() {
        setFlag(
                RecognitionStatKeys
                        .REVEAL_PENDING,
                false
        );
    }

    /**
     * Clears the complete frozen naming result while preserving this life's
     * deeds, progression counters, identity history and incarnation ID.
     *
     * <p>This is intentionally narrower than {@link #resetForNewIncarnation}
     * and exists for the protected administrator unname/retest route.</p>
     */
    public void clearNamingCommitPreservingLifeProgress() {
        if (isMutationWriteBlocked()) {
            return;
        }

        counters.remove(
                RecognitionStatKeys.RECOGNITION_RESULT_VERSION
        );
        counters.remove(
                RecognitionStatKeys.RECOGNITION_RULES_VERSION
        );
        counters.remove(
                RecognitionStatKeys.REWARD_PROFILE_VERSION
        );

        measurements.remove(
                RecognitionStatKeys.PRIMARY_SCORE_AT_COMMIT
        );
        measurements.remove(
                RecognitionStatKeys.SECONDARY_SCORE_AT_COMMIT
        );
        measurements.remove(
                RecognitionStatKeys.IDENTITY_STRENGTH_AT_COMMIT
        );
        measurements.remove(
                RecognitionStatKeys.IDENTITY_STRENGTH_MAXIMUM_AT_COMMIT
        );
        measurements.remove(
                RecognitionStatKeys.RECOGNITION_STRENGTH_REWARD
        );

        flags.remove(
                RecognitionStatKeys.NAMING_COMMITTED
        );
        flags.remove(
                RecognitionStatKeys.PURE_RECOGNITION
        );
        flags.remove(
                RecognitionStatKeys.REVEAL_PENDING
        );
        flags.remove(
                RecognitionStatKeys.RECOGNITION_REWARD_INITIALIZED
        );

        strings.remove(
                RecognitionStatKeys.PRIMARY_PATH
        );
        strings.remove(
                RecognitionStatKeys.SECONDARY_PATH
        );
        strings.remove(
                RecognitionStatKeys.BESTOWED_TITLE
        );
        strings.remove(
                RecognitionStatKeys.FROZEN_DISPLAY_NAME
        );
        strings.remove(
                RecognitionStatKeys.CONTRADICTION_MODIFIER
        );
        strings.remove(
                RecognitionStatKeys.BALANCE_SOURCE_AT_COMMIT
        );
        strings.remove(
                RecognitionStatKeys.BALANCE_REVISION_AT_COMMIT
        );
        strings.remove(
                RecognitionStatKeys.COMMIT_TIMESTAMP_EPOCH_MILLIS
        );
        strings.remove(
                RecognitionStatKeys.RECOGNITION_MIGRATION_SOURCE
        );
        strings.remove(
                RecognitionStatKeys.RECOGNITION_REWARD_MIGRATION_SOURCE
        );
        strings.remove(
                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER
        );

        dataVersion = CURRENT_DATA_VERSION;
    }

    public void resetForNewIncarnation(
            String incarnationId
    ) {
        if (isMutationWriteBlocked()) {
            return;
        }

        counters.clear();
        measurements.clear();
        flags.clear();
        strings.clear();
        collections.clear();

        dataVersion =
                CURRENT_DATA_VERSION;

        if (incarnationId != null
                && !incarnationId.isBlank()) {
            strings.put(
                    RecognitionStatKeys
                            .INCARNATION_ID,
                    incarnationId.trim()
            );
        }
    }

    private void migrateToCurrentVersion() {
        while (dataVersion
                < CURRENT_DATA_VERSION) {
            switch (dataVersion) {
                case 1 ->
                        migrateV1ToV2();

                default ->
                        throw new IllegalStateException(
                                "No recognition-data migration exists from version "
                                        + dataVersion
                                        + "."
                        );
            }
        }
    }

    private void migrateV1ToV2() {
        if (flags.getOrDefault(
                RecognitionStatKeys
                        .NAMING_COMMITTED,
                false
        )) {
            canonicalizeKnownStoredPath(
                    RecognitionStatKeys
                            .PRIMARY_PATH
            );

            canonicalizeKnownStoredPath(
                    RecognitionStatKeys
                            .SECONDARY_PATH
            );

            counters.putIfAbsent(
                    RecognitionStatKeys
                            .RECOGNITION_RESULT_VERSION,
                    RecognitionCommitRecord
                            .CURRENT_RESULT_VERSION
            );

            counters.putIfAbsent(
                    RecognitionStatKeys
                            .RECOGNITION_RULES_VERSION,
                    RecognitionCommitRecord
                            .CURRENT_RULES_VERSION
            );

            counters.putIfAbsent(
                    RecognitionStatKeys
                            .REWARD_PROFILE_VERSION,
                    RecognitionCommitRecord
                            .CURRENT_REWARD_PROFILE_VERSION
            );

            strings.putIfAbsent(
                    RecognitionStatKeys
                            .CONTRADICTION_MODIFIER,
                    RecognitionCommitRecord
                            .NO_CONTRADICTION
            );

            strings.putIfAbsent(
                    RecognitionStatKeys
                            .BALANCE_SOURCE_AT_COMMIT,
                    RecognitionCommitRecord
                            .UNKNOWN_BALANCE_SOURCE
            );

            strings.putIfAbsent(
                    RecognitionStatKeys
                            .BALANCE_REVISION_AT_COMMIT,
                    "0"
            );

            strings.putIfAbsent(
                    RecognitionStatKeys
                            .COMMIT_TIMESTAMP_EPOCH_MILLIS,
                    "0"
            );

            strings.putIfAbsent(
                    RecognitionStatKeys
                            .RECOGNITION_MIGRATION_SOURCE,
                    LEGACY_MIGRATION_SOURCE
            );

            measurements.putIfAbsent(
                    RecognitionStatKeys
                            .PRIMARY_SCORE_AT_COMMIT,
                    0.0D
            );

            measurements.putIfAbsent(
                    RecognitionStatKeys
                            .SECONDARY_SCORE_AT_COMMIT,
                    0.0D
            );
        }

        dataVersion = 2;
    }

    /**
     * Repairs absent v2 metadata without changing any frozen path or title.
     *
     * <p>This handles interrupted development builds and hand-edited saves.
     * It is intentionally not run for data from a future schema version.</p>
     */
    private void repairCurrentCommittedRecordDefaults() {
        if (!flags.getOrDefault(
                RecognitionStatKeys
                        .NAMING_COMMITTED,
                false
        )) {
            return;
        }

        canonicalizeKnownStoredPath(
                RecognitionStatKeys
                        .PRIMARY_PATH
        );

        canonicalizeKnownStoredPath(
                RecognitionStatKeys
                        .SECONDARY_PATH
        );

        counters.putIfAbsent(
                RecognitionStatKeys
                        .RECOGNITION_RESULT_VERSION,
                RecognitionCommitRecord
                        .CURRENT_RESULT_VERSION
        );

        counters.putIfAbsent(
                RecognitionStatKeys
                        .RECOGNITION_RULES_VERSION,
                RecognitionCommitRecord
                        .CURRENT_RULES_VERSION
        );

        counters.putIfAbsent(
                RecognitionStatKeys
                        .REWARD_PROFILE_VERSION,
                RecognitionCommitRecord
                        .CURRENT_REWARD_PROFILE_VERSION
        );

        strings.putIfAbsent(
                RecognitionStatKeys
                        .CONTRADICTION_MODIFIER,
                RecognitionCommitRecord
                        .NO_CONTRADICTION
        );

        strings.putIfAbsent(
                RecognitionStatKeys
                        .BALANCE_SOURCE_AT_COMMIT,
                RecognitionCommitRecord
                        .UNKNOWN_BALANCE_SOURCE
        );

        strings.putIfAbsent(
                RecognitionStatKeys
                        .BALANCE_REVISION_AT_COMMIT,
                "0"
        );

        strings.putIfAbsent(
                RecognitionStatKeys
                        .COMMIT_TIMESTAMP_EPOCH_MILLIS,
                "0"
        );

        strings.putIfAbsent(
                RecognitionStatKeys
                        .RECOGNITION_MIGRATION_SOURCE,
                dataVersion < CURRENT_DATA_VERSION
                        ? LEGACY_MIGRATION_SOURCE
                        : RecognitionCommitRecord
                          .NATIVE_MIGRATION_SOURCE
        );

        measurements.putIfAbsent(
                RecognitionStatKeys
                        .PRIMARY_SCORE_AT_COMMIT,
                0.0D
        );

        measurements.putIfAbsent(
                RecognitionStatKeys
                        .SECONDARY_SCORE_AT_COMMIT,
                0.0D
        );
    }

    private void canonicalizeKnownStoredPath(
            String key
    ) {
        String rawId =
                strings.get(key);

        if (rawId == null
                || rawId.isBlank()) {
            return;
        }

        String canonical =
                RecognitionPath
                        .canonicalizeStoredId(
                                rawId
                        );

        if (!canonical.isBlank()) {
            strings.put(
                    key,
                    canonical
            );
        }
    }

    private Map<String, Integer>
    getCountersForCodec() {
        return Map.copyOf(
                counters
        );
    }

    private Map<String, Double>
    getMeasurementsForCodec() {
        return Map.copyOf(
                measurements
        );
    }

    private Map<String, Boolean>
    getFlagsForCodec() {
        return Map.copyOf(
                flags
        );
    }

    private Map<String, String>
    getStringsForCodec() {
        return Map.copyOf(
                strings
        );
    }

    private Map<String, List<String>>
    getCollectionsForCodec() {
        Map<String, List<String>> result =
                new LinkedHashMap<>();

        collections.forEach(
                (key, values) ->
                        result.put(
                                key,
                                List.copyOf(
                                        values
                                )
                        )
        );

        return result;
    }

    PersistentState persistentStateForFixture() {
        return new PersistentState(
                dataVersion,
                Map.copyOf(counters),
                Map.copyOf(measurements),
                Map.copyOf(flags),
                Map.copyOf(strings),
                getCollectionsForCodec()
        );
    }

    record PersistentState(
            int dataVersion,
            Map<String, Integer> counters,
            Map<String, Double> measurements,
            Map<String, Boolean> flags,
            Map<String, String> strings,
            Map<String, List<String>> collections
    ) {
    }

    private void markCurrentVersion() {
        dataVersion =
                Math.max(
                        dataVersion,
                        CURRENT_DATA_VERSION
                );
    }

    private long getStoredLong(
            String key
    ) {
        String rawValue =
                getString(key);

        if (rawValue.isBlank()) {
            return 0L;
        }

        try {
            long value =
                    Long.parseLong(
                            rawValue
                    );

            return Math.max(
                    0L,
                    value
            );
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void putStoredLongDirect(
            String key,
            long value
    ) {
        strings.put(
                key,
                Long.toString(
                        Math.max(
                                0L,
                                value
                        )
                )
        );
    }

    private void putStringDirect(
            String key,
            String value
    ) {
        String safeValue =
                value == null
                        ? ""
                        : value.trim();

        if (safeValue.isEmpty()) {
            strings.remove(
                    key
            );
        } else {
            strings.put(
                    key,
                    safeValue
            );
        }
    }

    private static <K, V> void restoreMap(
            Map<K, V> target,
            Map<K, V> backup
    ) {
        target.clear();
        target.putAll(
                backup
        );
    }

    private static Map<String, List<String>>
    deepCopyCollections(
            Map<String, List<String>> source
    ) {
        Map<String, List<String>> result =
                new LinkedHashMap<>();

        if (source == null) {
            return result;
        }

        source.forEach(
                (key, values) ->
                        result.put(
                                key,
                                values == null
                                        ? new ArrayList<>()
                                        : new ArrayList<>(
                                        values
                                )
                        )
        );

        return result;
    }

    private static Map<String, Integer>
    sanitizeIntegerMap(
            Map<String, Integer> source
    ) {
        Map<String, Integer> result =
                new LinkedHashMap<>();

        if (source == null) {
            return result;
        }

        source.forEach(
                (key, value) -> {
                    if (isValidKey(key)
                            && value != null) {
                        result.put(
                                key,
                                Math.max(
                                        0,
                                        value
                                )
                        );
                    }
                }
        );

        return result;
    }

    private static Map<String, Double>
    sanitizeDoubleMap(
            Map<String, Double> source
    ) {
        Map<String, Double> result =
                new LinkedHashMap<>();

        if (source == null) {
            return result;
        }

        source.forEach(
                (key, value) -> {
                    if (isValidKey(key)
                            && value != null) {
                        result.put(
                                key,
                                sanitizeMeasurement(
                                        value
                                )
                        );
                    }
                }
        );

        return result;
    }

    private static Map<String, Boolean>
    sanitizeBooleanMap(
            Map<String, Boolean> source
    ) {
        Map<String, Boolean> result =
                new LinkedHashMap<>();

        if (source == null) {
            return result;
        }

        source.forEach(
                (key, value) -> {
                    if (isValidKey(key)
                            && value != null) {
                        result.put(
                                key,
                                value
                        );
                    }
                }
        );

        return result;
    }

    private static Map<String, String>
    sanitizeStringMap(
            Map<String, String> source
    ) {
        Map<String, String> result =
                new LinkedHashMap<>();

        if (source == null) {
            return result;
        }

        source.forEach(
                (key, value) -> {
                    if (!isValidKey(key)
                            || value == null
                            || value.isBlank()) {
                        return;
                    }

                    result.put(
                            key,
                            value.trim()
                    );
                }
        );

        return result;
    }

    private static Map<String, List<String>>
    sanitizeCollectionMap(
            Map<String, List<String>> source
    ) {
        Map<String, List<String>> result =
                new LinkedHashMap<>();

        if (source == null) {
            return result;
        }

        source.forEach(
                (key, values) -> {
                    if (!isValidKey(key)
                            || values == null) {
                        return;
                    }

                    List<String> cleanedValues =
                            new ArrayList<>();

                    for (String value : values) {
                        if (value == null
                                || value.isBlank()) {
                            continue;
                        }

                        String cleanedValue =
                                value.trim();

                        if (!cleanedValues.contains(
                                cleanedValue
                        )) {
                            cleanedValues.add(
                                    cleanedValue
                            );
                        }
                    }

                    if (!cleanedValues.isEmpty()) {
                        result.put(
                                key,
                                cleanedValues
                        );
                    }
                }
        );

        return result;
    }

    private static double sanitizeMeasurement(
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0D) {
            return 0.0D;
        }

        return value;
    }

    private static boolean isValidKey(
            String key
    ) {
        return key != null
                && !key.isBlank();
    }
}
