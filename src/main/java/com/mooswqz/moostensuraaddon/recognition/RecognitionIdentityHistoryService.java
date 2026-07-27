package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.List;

/**
 * Versioned persistence and mutation rules for contradiction history.
 *
 * <p>This service deliberately does not influence recognition scoring or
 * committed results. Event integration feeds qualifying deeds into these
 * APIs, while {@link RecognitionIdentityHistoryResolver} interprets the
 * resulting history only after a qualifying deed.</p>
 *
 * <p>Decay is lazy: no tick handler exists. A moral deed decays the stored Good
 * and Evil momenta before applying the new deed. A temperament deed does the
 * same for Order and Freedom.</p>
 */
public final class RecognitionIdentityHistoryService {

    public static final int CURRENT_HISTORY_VERSION = 2;

    public static final double MAXIMUM_MOMENTUM = 100.0D;
    public static final double MAXIMUM_DEED_WEIGHT = 25.0D;

    public static final double ESTABLISHED_DIRECTION_MINIMUM = 20.0D;
    public static final double ESTABLISHED_DIRECTION_MARGIN = 8.0D;

    public static final long DECAY_GRACE_TICKS = 24_000L;
    public static final long DECAY_INTERVAL_TICKS = 24_000L;
    public static final double DECAY_PER_INTERVAL = 4.0D;

    public static final String MIGRATION_SOURCE_NATIVE =
            "identity_history_native_v2";

    public static final String MIGRATION_SOURCE_UNVERSIONED =
            "identity_history_v0_to_v2";

    public static final String MIGRATION_SOURCE_V1 =
            "identity_history_v1_to_v2";

    private RecognitionIdentityHistoryService() {
    }

    public static RecognitionIdentityHistorySnapshot inspect(
            RecognitionData data
    ) {
        if (data == null) {
            return invalidSnapshot();
        }

        int version = data.getCounter(
                RecognitionStatKeys.IDENTITY_HISTORY_VERSION
        );

        MigrationState migrationState = determineMigrationState(
                data,
                version
        );

        double goodMomentum = getMomentum(
                data,
                RecognitionStatKeys.GOOD_MOMENTUM
        );

        double evilMomentum = getMomentum(
                data,
                RecognitionStatKeys.EVIL_MOMENTUM
        );

        double orderMomentum = getMomentum(
                data,
                RecognitionStatKeys.ORDER_MOMENTUM
        );

        double freedomMomentum = getMomentum(
                data,
                RecognitionStatKeys.FREEDOM_MOMENTUM
        );

        double highestGood = getMomentum(
                data,
                RecognitionStatKeys.HIGHEST_GOOD_COMMITMENT
        );

        double highestEvil = getMomentum(
                data,
                RecognitionStatKeys.HIGHEST_EVIL_COMMITMENT
        );

        double highestOrder = getMomentum(
                data,
                RecognitionStatKeys.HIGHEST_ORDER_COMMITMENT
        );

        double highestFreedom = getMomentum(
                data,
                RecognitionStatKeys.HIGHEST_FREEDOM_COMMITMENT
        );

        List<String> issues = new ArrayList<>();

        if (highestGood + 1.0E-9D < goodMomentum) {
            issues.add("Highest Good commitment is below current Good momentum.");
        }

        if (highestEvil + 1.0E-9D < evilMomentum) {
            issues.add("Highest Evil commitment is below current Evil momentum.");
        }

        if (highestOrder + 1.0E-9D < orderMomentum) {
            issues.add("Highest Order commitment is below current Order momentum.");
        }

        if (highestFreedom + 1.0E-9D < freedomMomentum) {
            issues.add("Highest Freedom commitment is below current Freedom momentum.");
        }

        String modifierId = RecognitionIdentityHistoryModifier
                .canonicalOrPreserved(
                        data.getString(
                                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER
                        )
                );

        return new RecognitionIdentityHistorySnapshot(
                version,
                CURRENT_HISTORY_VERSION,
                migrationState,
                goodMomentum,
                evilMomentum,
                orderMomentum,
                freedomMomentum,
                highestGood,
                highestEvil,
                highestOrder,
                highestFreedom,
                data.getCounter(
                        RecognitionStatKeys.MORAL_REVERSAL_COUNT
                ),
                data.getCounter(
                        RecognitionStatKeys.TEMPERAMENT_REVERSAL_COUNT
                ),
                getStoredGameTime(
                        data,
                        RecognitionStatKeys.LAST_GOOD_DEED_GAME_TIME
                ),
                getStoredGameTime(
                        data,
                        RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME
                ),
                getStoredGameTime(
                        data,
                        RecognitionStatKeys.LAST_ORDER_DEED_GAME_TIME
                ),
                getStoredGameTime(
                        data,
                        RecognitionStatKeys.LAST_FREEDOM_DEED_GAME_TIME
                ),
                modifierId,
                data.getString(
                        RecognitionStatKeys.IDENTITY_HISTORY_MIGRATION_SOURCE
                ),
                issues
        );
    }

    /**
     * Initializes or repairs only the independent history namespace.
     * Unknown future versions and unknown modifier IDs are preserved.
     */
    public static MigrationResult ensureCurrent(
            RecognitionData data
    ) {
        if (data == null) {
            return MigrationResult.INVALID_DATA;
        }

        int storedVersion = data.getCounter(
                RecognitionStatKeys.IDENTITY_HISTORY_VERSION
        );

        if (storedVersion > CURRENT_HISTORY_VERSION) {
            return MigrationResult.FUTURE_VERSION_PRESERVED;
        }

        boolean changed = false;
        boolean hadUnversionedPayload = storedVersion == 0
                && hasAnyHistoryPayload(data);

        MigrationResult versionResult;

        if (storedVersion == 0) {
            data.setCounter(
                    RecognitionStatKeys.IDENTITY_HISTORY_VERSION,
                    CURRENT_HISTORY_VERSION
            );

            data.setString(
                    RecognitionStatKeys.IDENTITY_HISTORY_MIGRATION_SOURCE,
                    hadUnversionedPayload
                            ? MIGRATION_SOURCE_UNVERSIONED
                            : MIGRATION_SOURCE_NATIVE
            );

            versionResult = hadUnversionedPayload
                    ? MigrationResult.MIGRATED_FROM_UNVERSIONED
                    : MigrationResult.INITIALIZED_NATIVE;

            changed = true;
        } else if (storedVersion == 1) {
            migrateDecayAnchorsFromV1(data);
            repairEstablishedDirections(data);

            data.setCounter(
                    RecognitionStatKeys.IDENTITY_HISTORY_VERSION,
                    CURRENT_HISTORY_VERSION
            );

            data.setString(
                    RecognitionStatKeys.IDENTITY_HISTORY_MIGRATION_SOURCE,
                    MIGRATION_SOURCE_V1
            );

            versionResult = MigrationResult.MIGRATED_FROM_V1;
            changed = true;
        } else {
            versionResult = MigrationResult.ALREADY_CURRENT;
        }

        String rawModifier = data.getString(
                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER
        );

        if (rawModifier.isBlank()) {
            data.setString(
                    RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER,
                    RecognitionIdentityHistoryModifier.NONE.id()
            );

            changed = true;
        } else {
            String canonical = RecognitionIdentityHistoryModifier
                    .canonicalOrPreserved(rawModifier);

            if (!canonical.equals(rawModifier)) {
                data.setString(
                        RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER,
                        canonical
                );

                changed = true;
            }
        }

        changed |= repairPeak(
                data,
                RecognitionStatKeys.GOOD_MOMENTUM,
                RecognitionStatKeys.HIGHEST_GOOD_COMMITMENT
        );

        changed |= repairPeak(
                data,
                RecognitionStatKeys.EVIL_MOMENTUM,
                RecognitionStatKeys.HIGHEST_EVIL_COMMITMENT
        );

        changed |= repairPeak(
                data,
                RecognitionStatKeys.ORDER_MOMENTUM,
                RecognitionStatKeys.HIGHEST_ORDER_COMMITMENT
        );

        changed |= repairPeak(
                data,
                RecognitionStatKeys.FREEDOM_MOMENTUM,
                RecognitionStatKeys.HIGHEST_FREEDOM_COMMITMENT
        );

        changed |= repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_GOOD_DEED_GAME_TIME,
                RecognitionStatKeys.GOOD_DECAY_ANCHOR_GAME_TIME
        );

        changed |= repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME,
                RecognitionStatKeys.EVIL_DECAY_ANCHOR_GAME_TIME
        );

        changed |= repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_ORDER_DEED_GAME_TIME,
                RecognitionStatKeys.ORDER_DECAY_ANCHOR_GAME_TIME
        );

        changed |= repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_FREEDOM_DEED_GAME_TIME,
                RecognitionStatKeys.FREEDOM_DECAY_ANCHOR_GAME_TIME
        );

        changed |= repairEstablishedDirections(data);
        changed |= repairAuthorityBaselines(data);

        if (versionResult != MigrationResult.ALREADY_CURRENT) {
            return versionResult;
        }

        return changed
                ? MigrationResult.REPAIRED_CURRENT
                : MigrationResult.ALREADY_CURRENT;
    }

    public static MutationResult recordMoralDeed(
            RecognitionData data,
            MoralDirection direction,
            double weight,
            long overworldGameTime
    ) {
        if (direction == null) {
            return MutationResult.rejected("A moral direction is required.");
        }

        return recordDeed(
                data,
                direction == MoralDirection.GOOD
                        ? RecognitionStatKeys.GOOD_MOMENTUM
                        : RecognitionStatKeys.EVIL_MOMENTUM,
                direction == MoralDirection.GOOD
                        ? RecognitionStatKeys.HIGHEST_GOOD_COMMITMENT
                        : RecognitionStatKeys.HIGHEST_EVIL_COMMITMENT,
                direction == MoralDirection.GOOD
                        ? RecognitionStatKeys.LAST_GOOD_DEED_GAME_TIME
                        : RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME,
                direction == MoralDirection.GOOD
                        ? RecognitionStatKeys.GOOD_DECAY_ANCHOR_GAME_TIME
                        : RecognitionStatKeys.EVIL_DECAY_ANCHOR_GAME_TIME,
                RecognitionStatKeys.GOOD_MOMENTUM,
                RecognitionStatKeys.LAST_GOOD_DEED_GAME_TIME,
                RecognitionStatKeys.GOOD_DECAY_ANCHOR_GAME_TIME,
                RecognitionStatKeys.EVIL_MOMENTUM,
                RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME,
                RecognitionStatKeys.EVIL_DECAY_ANCHOR_GAME_TIME,
                weight,
                overworldGameTime,
                direction.id()
        );
    }

    public static MutationResult recordTemperamentDeed(
            RecognitionData data,
            TemperamentDirection direction,
            double weight,
            long overworldGameTime
    ) {
        if (direction == null) {
            return MutationResult.rejected("A temperament direction is required.");
        }

        return recordDeed(
                data,
                direction == TemperamentDirection.ORDER
                        ? RecognitionStatKeys.ORDER_MOMENTUM
                        : RecognitionStatKeys.FREEDOM_MOMENTUM,
                direction == TemperamentDirection.ORDER
                        ? RecognitionStatKeys.HIGHEST_ORDER_COMMITMENT
                        : RecognitionStatKeys.HIGHEST_FREEDOM_COMMITMENT,
                direction == TemperamentDirection.ORDER
                        ? RecognitionStatKeys.LAST_ORDER_DEED_GAME_TIME
                        : RecognitionStatKeys.LAST_FREEDOM_DEED_GAME_TIME,
                direction == TemperamentDirection.ORDER
                        ? RecognitionStatKeys.ORDER_DECAY_ANCHOR_GAME_TIME
                        : RecognitionStatKeys.FREEDOM_DECAY_ANCHOR_GAME_TIME,
                RecognitionStatKeys.ORDER_MOMENTUM,
                RecognitionStatKeys.LAST_ORDER_DEED_GAME_TIME,
                RecognitionStatKeys.ORDER_DECAY_ANCHOR_GAME_TIME,
                RecognitionStatKeys.FREEDOM_MOMENTUM,
                RecognitionStatKeys.LAST_FREEDOM_DEED_GAME_TIME,
                RecognitionStatKeys.FREEDOM_DECAY_ANCHOR_GAME_TIME,
                weight,
                overworldGameTime,
                direction.id()
        );
    }

    public static int registerMoralReversal(
            RecognitionData data
    ) {
        return incrementVersionedCounter(
                data,
                RecognitionStatKeys.MORAL_REVERSAL_COUNT
        );
    }

    public static int registerTemperamentReversal(
            RecognitionData data
    ) {
        return incrementVersionedCounter(
                data,
                RecognitionStatKeys.TEMPERAMENT_REVERSAL_COUNT
        );
    }

    public static boolean setResolvedModifier(
            RecognitionData data,
            RecognitionIdentityHistoryModifier modifier
    ) {
        if (data == null || modifier == null) {
            return false;
        }

        MigrationResult migration = ensureCurrent(data);

        if (!migration.writable()) {
            return false;
        }

        data.setString(
                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER,
                modifier.id()
        );

        return true;
    }

    public static double calculateDecayedMomentum(
            double storedMomentum,
            long lastEventGameTime,
            long currentGameTime
    ) {
        double safeMomentum = sanitizeMomentum(storedMomentum);

        if (safeMomentum <= 0.0D
                || lastEventGameTime <= 0L
                || currentGameTime <= lastEventGameTime) {
            return safeMomentum;
        }

        long elapsed = currentGameTime - lastEventGameTime;

        if (elapsed <= DECAY_GRACE_TICKS) {
            return safeMomentum;
        }

        long decayingTicks = elapsed - DECAY_GRACE_TICKS;
        double intervals = (double) decayingTicks
                / (double) DECAY_INTERVAL_TICKS;

        double decayed = safeMomentum
                - intervals * DECAY_PER_INTERVAL;

        return sanitizeMomentum(decayed);
    }

    private static MutationResult recordDeed(
            RecognitionData data,
            String targetMomentumKey,
            String targetPeakKey,
            String targetTimestampKey,
            String targetDecayAnchorKey,
            String firstMomentumKey,
            String firstTimestampKey,
            String firstDecayAnchorKey,
            String secondMomentumKey,
            String secondTimestampKey,
            String secondDecayAnchorKey,
            double weight,
            long gameTime,
            String semanticDirection
    ) {
        if (data == null) {
            return MutationResult.rejected("Recognition data is required.");
        }

        MigrationResult migration = ensureCurrent(data);

        if (!migration.writable()) {
            return MutationResult.rejected(
                    migration == MigrationResult.FUTURE_VERSION_PRESERVED
                            ? "Future history version preserved without mutation."
                            : "History data could not be initialized."
            );
        }

        double safeWeight = sanitizeDeedWeight(weight);

        if (safeWeight <= 0.0D) {
            return MutationResult.rejected("Deed weight must be positive.");
        }

        long safeGameTime = Math.max(0L, gameTime);

        AxisDecay firstDecay = decayStoredSide(
                data,
                firstMomentumKey,
                firstTimestampKey,
                firstDecayAnchorKey,
                safeGameTime
        );

        AxisDecay secondDecay = decayStoredSide(
                data,
                secondMomentumKey,
                secondTimestampKey,
                secondDecayAnchorKey,
                safeGameTime
        );

        double before = getMomentum(data, targetMomentumKey);
        double after = sanitizeMomentum(before + safeWeight);

        data.setMeasurement(
                targetMomentumKey,
                after
        );

        data.setMeasurementMaximum(
                targetPeakKey,
                after
        );

        setStoredGameTime(
                data,
                targetTimestampKey,
                safeGameTime
        );

        /*
         * A deed resets that direction's decay grace and incremental anchor.
         * The last-deed timestamp remains semantically accurate, while the
         * separate anchor prevents repeated lazy evaluations from subtracting
         * the same elapsed period twice.
         */
        setStoredGameTime(
                data,
                targetDecayAnchorKey,
                safeGameTime
        );

        return new MutationResult(
                true,
                semanticDirection,
                safeWeight,
                before,
                after,
                firstDecay.decayedAmount()
                        + secondDecay.decayedAmount(),
                safeGameTime,
                ""
        );
    }

    private static AxisDecay decayStoredSide(
            RecognitionData data,
            String momentumKey,
            String lastDeedTimestampKey,
            String decayAnchorKey,
            long currentGameTime
    ) {
        double before = getMomentum(data, momentumKey);
        long lastDeedTime = getStoredGameTime(
                data,
                lastDeedTimestampKey
        );

        long decayAnchor = getStoredGameTime(
                data,
                decayAnchorKey
        );

        if (currentGameTime <= 0L) {
            return new AxisDecay(before, before);
        }

        if (lastDeedTime > currentGameTime) {
            setStoredGameTime(
                    data,
                    lastDeedTimestampKey,
                    currentGameTime
            );

            setStoredGameTime(
                    data,
                    decayAnchorKey,
                    currentGameTime
            );

            return new AxisDecay(before, before);
        }

        if (decayAnchor > currentGameTime) {
            setStoredGameTime(
                    data,
                    decayAnchorKey,
                    currentGameTime
            );

            return new AxisDecay(before, before);
        }

        if (before <= 0.0D || lastDeedTime <= 0L) {
            setStoredGameTime(
                    data,
                    decayAnchorKey,
                    currentGameTime
            );

            return new AxisDecay(before, before);
        }

        long graceEnd = safeAdd(
                lastDeedTime,
                DECAY_GRACE_TICKS
        );

        long decayStart = Math.max(
                decayAnchor,
                graceEnd
        );

        double after = before;

        if (currentGameTime > decayStart) {
            long elapsedDecayTicks =
                    currentGameTime - decayStart;

            double intervals =
                    (double) elapsedDecayTicks
                            / (double) DECAY_INTERVAL_TICKS;

            after = sanitizeMomentum(
                    before
                            - intervals
                            * DECAY_PER_INTERVAL
            );

            if (Math.abs(after - before) > 1.0E-9D) {
                data.setMeasurement(
                        momentumKey,
                        after
                );
            }
        }

        setStoredGameTime(
                data,
                decayAnchorKey,
                currentGameTime
        );

        return new AxisDecay(before, after);
    }

    private static void migrateDecayAnchorsFromV1(
            RecognitionData data
    ) {
        repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_GOOD_DEED_GAME_TIME,
                RecognitionStatKeys.GOOD_DECAY_ANCHOR_GAME_TIME
        );

        repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME,
                RecognitionStatKeys.EVIL_DECAY_ANCHOR_GAME_TIME
        );

        repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_ORDER_DEED_GAME_TIME,
                RecognitionStatKeys.ORDER_DECAY_ANCHOR_GAME_TIME
        );

        repairDecayAnchor(
                data,
                RecognitionStatKeys.LAST_FREEDOM_DEED_GAME_TIME,
                RecognitionStatKeys.FREEDOM_DECAY_ANCHOR_GAME_TIME
        );
    }

    private static boolean repairDecayAnchor(
            RecognitionData data,
            String lastDeedKey,
            String decayAnchorKey
    ) {
        long lastDeedTime = getStoredGameTime(
                data,
                lastDeedKey
        );

        long decayAnchor = getStoredGameTime(
                data,
                decayAnchorKey
        );

        if (decayAnchor > 0L || lastDeedTime <= 0L) {
            return false;
        }

        setStoredGameTime(
                data,
                decayAnchorKey,
                lastDeedTime
        );

        return true;
    }

    private static boolean repairEstablishedDirections(
            RecognitionData data
    ) {
        boolean changed = false;

        String moralDirection = data.getString(
                RecognitionStatKeys.MORAL_ESTABLISHED_DIRECTION
        );

        if (moralDirection.isBlank()) {
            String inferred = inferEstablishedDirection(
                    getMomentum(
                            data,
                            RecognitionStatKeys.GOOD_MOMENTUM
                    ),
                    getMomentum(
                            data,
                            RecognitionStatKeys.EVIL_MOMENTUM
                    ),
                    MoralDirection.GOOD.id(),
                    MoralDirection.EVIL.id()
            );

            if (!inferred.isBlank()) {
                data.setString(
                        RecognitionStatKeys.MORAL_ESTABLISHED_DIRECTION,
                        inferred
                );

                changed = true;
            }
        }

        String temperamentDirection = data.getString(
                RecognitionStatKeys.TEMPERAMENT_ESTABLISHED_DIRECTION
        );

        if (temperamentDirection.isBlank()) {
            String inferred = inferEstablishedDirection(
                    getMomentum(
                            data,
                            RecognitionStatKeys.ORDER_MOMENTUM
                    ),
                    getMomentum(
                            data,
                            RecognitionStatKeys.FREEDOM_MOMENTUM
                    ),
                    TemperamentDirection.ORDER.id(),
                    TemperamentDirection.FREEDOM.id()
            );

            if (!inferred.isBlank()) {
                data.setString(
                        RecognitionStatKeys.TEMPERAMENT_ESTABLISHED_DIRECTION,
                        inferred
                );

                changed = true;
            }
        }

        return changed;
    }

    public static String inferEstablishedDirection(
            double firstMomentum,
            double secondMomentum,
            String firstDirectionId,
            String secondDirectionId
    ) {
        double first = sanitizeMomentum(firstMomentum);
        double second = sanitizeMomentum(secondMomentum);

        String safeFirstId =
                firstDirectionId == null
                        ? ""
                        : firstDirectionId.trim();

        String safeSecondId =
                secondDirectionId == null
                        ? ""
                        : secondDirectionId.trim();

        if (!safeFirstId.isBlank()
                && first >= ESTABLISHED_DIRECTION_MINIMUM
                && first >= second + ESTABLISHED_DIRECTION_MARGIN) {
            return safeFirstId;
        }

        if (!safeSecondId.isBlank()
                && second >= ESTABLISHED_DIRECTION_MINIMUM
                && second >= first + ESTABLISHED_DIRECTION_MARGIN) {
            return safeSecondId;
        }

        return "";
    }

    private static boolean repairAuthorityBaselines(
            RecognitionData data
    ) {
        if (data.getFlag(
                RecognitionStatKeys
                        .IDENTITY_HISTORY_AUTHORITY_BASELINE_INITIALIZED
        )) {
            return false;
        }

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_UNIQUE_SUBORDINATES_EMPOWERED,
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .UNIQUE_SUBORDINATES_EMPOWERED
                )
        );

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_MASS_GRANTS_PERFORMED,
                data.getCounter(
                        RecognitionStatKeys
                                .MASS_GRANTS_PERFORMED
                )
        );

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_GLOBAL_TAKE_BACKS_PERFORMED,
                data.getCounter(
                        RecognitionStatKeys
                                .GLOBAL_TAKE_BACKS_PERFORMED
                )
        );

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_SKILLS_SEIZED,
                data.getCounter(
                        RecognitionStatKeys
                                .SKILLS_SEIZED
                )
        );

        data.setFlag(
                RecognitionStatKeys
                        .IDENTITY_HISTORY_AUTHORITY_BASELINE_INITIALIZED,
                true
        );

        return true;
    }

    private static long safeAdd(
            long first,
            long second
    ) {
        long safeFirst = Math.max(0L, first);
        long safeSecond = Math.max(0L, second);

        if (safeFirst > Long.MAX_VALUE - safeSecond) {
            return Long.MAX_VALUE;
        }

        return safeFirst + safeSecond;
    }

    private static int incrementVersionedCounter(
            RecognitionData data,
            String key
    ) {
        if (data == null) {
            return 0;
        }

        MigrationResult migration = ensureCurrent(data);

        if (!migration.writable()) {
            return data.getCounter(key);
        }

        return data.incrementCounter(key);
    }

    private static boolean repairPeak(
            RecognitionData data,
            String momentumKey,
            String peakKey
    ) {
        double momentum = getMomentum(data, momentumKey);
        double peak = getMomentum(data, peakKey);

        if (peak + 1.0E-9D >= momentum) {
            return false;
        }

        data.setMeasurement(peakKey, momentum);
        return true;
    }

    private static MigrationState determineMigrationState(
            RecognitionData data,
            int storedVersion
    ) {
        if (storedVersion > CURRENT_HISTORY_VERSION) {
            return MigrationState.FUTURE_VERSION;
        }

        if (storedVersion == CURRENT_HISTORY_VERSION) {
            return MigrationState.CURRENT;
        }

        if (storedVersion > 0) {
            return MigrationState.LEGACY_VERSION;
        }

        if (hasAnyHistoryPayload(data)) {
            return MigrationState.UNVERSIONED_PAYLOAD;
        }

        return MigrationState.UNINITIALIZED;
    }

    private static boolean hasAnyHistoryPayload(
            RecognitionData data
    ) {
        return getMomentum(data, RecognitionStatKeys.GOOD_MOMENTUM) > 0.0D
                || getMomentum(data, RecognitionStatKeys.EVIL_MOMENTUM) > 0.0D
                || getMomentum(data, RecognitionStatKeys.ORDER_MOMENTUM) > 0.0D
                || getMomentum(data, RecognitionStatKeys.FREEDOM_MOMENTUM) > 0.0D
                || getMomentum(data, RecognitionStatKeys.HIGHEST_GOOD_COMMITMENT) > 0.0D
                || getMomentum(data, RecognitionStatKeys.HIGHEST_EVIL_COMMITMENT) > 0.0D
                || getMomentum(data, RecognitionStatKeys.HIGHEST_ORDER_COMMITMENT) > 0.0D
                || getMomentum(data, RecognitionStatKeys.HIGHEST_FREEDOM_COMMITMENT) > 0.0D
                || data.getCounter(RecognitionStatKeys.MORAL_REVERSAL_COUNT) > 0
                || data.getCounter(RecognitionStatKeys.TEMPERAMENT_REVERSAL_COUNT) > 0
                || getStoredGameTime(data, RecognitionStatKeys.LAST_GOOD_DEED_GAME_TIME) > 0L
                || getStoredGameTime(data, RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME) > 0L
                || getStoredGameTime(data, RecognitionStatKeys.LAST_ORDER_DEED_GAME_TIME) > 0L
                || getStoredGameTime(data, RecognitionStatKeys.LAST_FREEDOM_DEED_GAME_TIME) > 0L
                || !data.getString(RecognitionStatKeys.MORAL_ESTABLISHED_DIRECTION).isBlank()
                || !data.getString(RecognitionStatKeys.TEMPERAMENT_ESTABLISHED_DIRECTION).isBlank()
                || !data.getString(RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER).isBlank()
                || !data.getString(RecognitionStatKeys.IDENTITY_HISTORY_MIGRATION_SOURCE).isBlank();
    }

    private static RecognitionIdentityHistorySnapshot invalidSnapshot() {
        return new RecognitionIdentityHistorySnapshot(
                0,
                CURRENT_HISTORY_VERSION,
                MigrationState.INVALID_DATA,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0,
                0,
                0L,
                0L,
                0L,
                0L,
                RecognitionIdentityHistoryModifier.NONE.id(),
                "",
                List.of("Recognition data is unavailable.")
        );
    }

    private static double getMomentum(
            RecognitionData data,
            String key
    ) {
        return sanitizeMomentum(data.getMeasurement(key));
    }

    private static double sanitizeMomentum(
            double value
    ) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0.0D;
        }

        return Math.min(MAXIMUM_MOMENTUM, value);
    }

    private static double sanitizeDeedWeight(
            double value
    ) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0.0D;
        }

        return Math.min(MAXIMUM_DEED_WEIGHT, value);
    }

    private static long getStoredGameTime(
            RecognitionData data,
            String key
    ) {
        String raw = data.getString(key);

        if (raw.isBlank()) {
            return 0L;
        }

        try {
            return Math.max(0L, Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static void setStoredGameTime(
            RecognitionData data,
            String key,
            long value
    ) {
        data.setString(
                key,
                Long.toString(Math.max(0L, value))
        );
    }

    public enum MoralDirection {
        GOOD("good"),
        EVIL("evil");

        private final String id;

        MoralDirection(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public enum TemperamentDirection {
        ORDER("order"),
        FREEDOM("freedom");

        private final String id;

        TemperamentDirection(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public enum MigrationState {
        UNINITIALIZED("uninitialized"),
        UNVERSIONED_PAYLOAD("unversioned payload"),
        LEGACY_VERSION("legacy history version"),
        CURRENT("current"),
        FUTURE_VERSION("future version preserved"),
        INVALID_DATA("invalid data");

        private final String displayName;

        MigrationState(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum MigrationResult {
        INVALID_DATA(false),
        FUTURE_VERSION_PRESERVED(false),
        ALREADY_CURRENT(true),
        REPAIRED_CURRENT(true),
        INITIALIZED_NATIVE(true),
        MIGRATED_FROM_UNVERSIONED(true),
        MIGRATED_FROM_V1(true);

        private final boolean writable;

        MigrationResult(boolean writable) {
            this.writable = writable;
        }

        public boolean writable() {
            return writable;
        }
    }

    public record MutationResult(
            boolean applied,
            String semanticDirection,
            double appliedWeight,
            double momentumBefore,
            double momentumAfter,
            double totalDecayApplied,
            long gameTime,
            String rejectionReason
    ) {

        public MutationResult {
            semanticDirection = semanticDirection == null
                    ? ""
                    : semanticDirection;

            appliedWeight = applied
                    ? sanitizeDeedWeight(appliedWeight)
                    : 0.0D;

            momentumBefore = sanitizeMomentum(momentumBefore);
            momentumAfter = sanitizeMomentum(momentumAfter);
            totalDecayApplied = Math.max(0.0D, totalDecayApplied);
            gameTime = Math.max(0L, gameTime);
            rejectionReason = rejectionReason == null
                    ? ""
                    : rejectionReason;
        }

        public static MutationResult rejected(
                String reason
        ) {
            return new MutationResult(
                    false,
                    "",
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0L,
                    reason
            );
        }
    }

    private record AxisDecay(
            double before,
            double after
    ) {
        private double decayedAmount() {
            return Math.max(0.0D, before - after);
        }
    }
}