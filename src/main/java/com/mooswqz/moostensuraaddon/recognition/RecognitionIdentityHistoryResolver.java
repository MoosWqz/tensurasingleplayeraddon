package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the evolving identity-history modifier without changing the frozen
 * recognition result.
 *
 * <p>The resolver is intentionally read-only. The only write entry point is
 * {@link #resolveAndStoreAfterDeed(RecognitionData)}, which is called by the
 * event integration after a qualifying deed has already been applied. Passive
 * inspection, login, GUI opening and lazy time passage therefore cannot create
 * or change a modifier.</p>
 */
public final class RecognitionIdentityHistoryResolver {

    /**
     * A prior opposite commitment must have been more than a brief spike before
     * it can support a lasting modifier.
     */
    public static final double OPPOSING_HISTORY_MINIMUM = 28.0D;

    /**
     * Both live sides must remain clearly established before an axis is treated
     * as unresolved rather than merely close.
     */
    public static final double CONTESTED_SIDE_MINIMUM =
            RecognitionIdentityHistoryService.ESTABLISHED_DIRECTION_MINIMUM;

    /**
     * Returning to the original direction is only "Reconciled" after at least
     * two genuine direction changes on that axis.
     */
    public static final int RECONCILED_REVERSAL_MINIMUM = 2;

    private RecognitionIdentityHistoryResolver() {
    }

    /**
     * Computes the currently implied modifier without mutating any data.
     */
    public static RecognitionIdentityHistoryResolution resolve(
            RecognitionData data
    ) {
        if (data == null) {
            return resolution(
                    RecognitionIdentityHistoryModifier.NONE,
                    false,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "Recognition data is unavailable.",
                    "No history resolution can be performed.",
                    List.of("RecognitionData was null.")
            );
        }

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        Optional<RecognitionPath> primaryOptional =
                data.getCommittedPrimaryPath();

        RecognitionPath primaryPath =
                primaryOptional.orElse(null);

        RecognitionPath secondaryPath =
                data.getCommittedSecondaryPath()
                        .orElse(null);

        String originalMoral = originalMoralDirection(
                primaryPath,
                secondaryPath
        );

        String originalTemperament = originalTemperamentDirection(
                primaryPath,
                secondaryPath
        );

        String currentMoral =
                RecognitionIdentityHistoryIntegration.establishedDirection(
                        snapshot,
                        RecognitionIdentityHistoryIntegration.Axis.MORAL
                );

        String currentTemperament =
                RecognitionIdentityHistoryIntegration.establishedDirection(
                        snapshot,
                        RecognitionIdentityHistoryIntegration.Axis.TEMPERAMENT
                );

        String storedMoral = normalizeMoralDirection(
                data.getString(
                        RecognitionStatKeys.MORAL_ESTABLISHED_DIRECTION
                )
        );

        String storedTemperament = normalizeTemperamentDirection(
                data.getString(
                        RecognitionStatKeys.TEMPERAMENT_ESTABLISHED_DIRECTION
                )
        );

        boolean moralContested = isContested(
                snapshot.goodMomentum(),
                snapshot.evilMomentum()
        )
                && snapshot.moralReversalCount() > 0
                && snapshot.highestGoodCommitment()
                >= OPPOSING_HISTORY_MINIMUM
                && snapshot.highestEvilCommitment()
                >= OPPOSING_HISTORY_MINIMUM;

        boolean temperamentContested = isContested(
                snapshot.orderMomentum(),
                snapshot.freedomMomentum()
        )
                && snapshot.temperamentReversalCount() > 0
                && snapshot.highestOrderCommitment()
                >= OPPOSING_HISTORY_MINIMUM
                && snapshot.highestFreedomCommitment()
                >= OPPOSING_HISTORY_MINIMUM;

        boolean committedIdentityAvailable =
                data.isNamingCommitted()
                        && primaryPath != null;

        if (!committedIdentityAvailable) {
            return resolution(
                    RecognitionIdentityHistoryModifier.NONE,
                    false,
                    originalMoral,
                    originalTemperament,
                    currentMoral,
                    currentTemperament,
                    storedMoral,
                    storedTemperament,
                    false,
                    false,
                    false,
                    false,
                    moralContested,
                    temperamentContested,
                    "Commit recognition before later deeds can become a legacy modifier.",
                    "A frozen recognition identity is required as the historical reference point.",
                    baseEvidence(
                            snapshot,
                            originalMoral,
                            originalTemperament,
                            currentMoral,
                            currentTemperament
                    )
            );
        }

        if (snapshot.futureVersion()) {
            RecognitionIdentityHistoryModifier preserved =
                    snapshot.knownModifier()
                            .orElse(
                                    RecognitionIdentityHistoryModifier.NONE
                            );

            return resolution(
                    preserved,
                    true,
                    originalMoral,
                    originalTemperament,
                    currentMoral,
                    currentTemperament,
                    storedMoral,
                    storedTemperament,
                    false,
                    false,
                    false,
                    false,
                    moralContested,
                    temperamentContested,
                    playerFacingSummary(
                            preserved,
                            true
                    ),
                    "A future identity-history version is preserved without reinterpretation.",
                    baseEvidence(
                            snapshot,
                            originalMoral,
                            originalTemperament,
                            currentMoral,
                            currentTemperament
                    )
            );
        }

        boolean moralContradiction = isOppositeEstablishedDirection(
                originalMoral,
                currentMoral
        )
                && snapshot.moralReversalCount() > 0
                && historicalPeakForMoral(
                snapshot,
                originalMoral
        ) >= OPPOSING_HISTORY_MINIMUM
                && currentMomentumForMoral(
                snapshot,
                currentMoral
        ) >= RecognitionIdentityHistoryService
                .ESTABLISHED_DIRECTION_MINIMUM;

        boolean originalTemperamentContradiction =
                isOppositeEstablishedDirection(
                        originalTemperament,
                        currentTemperament
                )
                        && snapshot.temperamentReversalCount() > 0
                        && historicalPeakForTemperament(
                        snapshot,
                        originalTemperament
                ) >= OPPOSING_HISTORY_MINIMUM
                        && currentMomentumForTemperament(
                        snapshot,
                        currentTemperament
                ) >= RecognitionIdentityHistoryService
                        .ESTABLISHED_DIRECTION_MINIMUM;

        boolean neutralTemperamentContradiction =
                originalTemperament.isBlank()
                        && snapshot.temperamentReversalCount() > 0
                        && !currentTemperament.isBlank()
                        && oppositeHistoricalPeakForTemperament(
                        snapshot,
                        currentTemperament
                ) >= OPPOSING_HISTORY_MINIMUM
                        && currentMomentumForTemperament(
                        snapshot,
                        currentTemperament
                ) >= RecognitionIdentityHistoryService
                        .ESTABLISHED_DIRECTION_MINIMUM;

        boolean temperamentContradiction =
                originalTemperamentContradiction
                        || neutralTemperamentContradiction;

        boolean moralReturned =
                !originalMoral.isBlank()
                        && originalMoral.equals(currentMoral)
                        && snapshot.moralReversalCount()
                        >= RECONCILED_REVERSAL_MINIMUM
                        && oppositeHistoricalPeakForMoral(
                        snapshot,
                        originalMoral
                ) >= OPPOSING_HISTORY_MINIMUM;

        boolean temperamentReturned =
                !originalTemperament.isBlank()
                        && originalTemperament.equals(
                        currentTemperament
                )
                        && snapshot.temperamentReversalCount()
                        >= RECONCILED_REVERSAL_MINIMUM
                        && oppositeHistoricalPeakForTemperament(
                        snapshot,
                        originalTemperament
                ) >= OPPOSING_HISTORY_MINIMUM;

        boolean fractured =
                moralContradiction
                        && temperamentContradiction
                        || moralContradiction
                        && temperamentContested
                        || temperamentContradiction
                        && moralContested
                        || moralContested
                        && temperamentContested;

        RecognitionIdentityHistoryModifier modifier;
        String reason;

        if (fractured) {
            modifier = RecognitionIdentityHistoryModifier.FRACTURED;
            reason = "More than one part of the frozen identity is actively contradicted or unresolved.";
        } else if (!moralContradiction
                && !temperamentContradiction
                && !moralContested
                && !temperamentContested
                && (moralReturned || temperamentReturned)) {
            modifier = RecognitionIdentityHistoryModifier.RECONCILED;
            reason = "After genuine reversals, the currently established direction again matches the frozen identity.";
        } else if (moralContradiction) {
            modifier = originalMoral.equals(
                    RecognitionIdentityHistoryService
                            .MoralDirection
                            .GOOD
                            .id()
            )
                    ? RecognitionIdentityHistoryModifier.FALLEN
                    : RecognitionIdentityHistoryModifier.REDEEMED;

            reason = modifier == RecognitionIdentityHistoryModifier.FALLEN
                    ? "A Good frozen identity is now opposed by clearly established Evil history."
                    : "An Evil frozen identity is now opposed by clearly established Good history.";
        } else if (temperamentContradiction) {
            if (currentTemperament.equals(
                    RecognitionIdentityHistoryService
                            .TemperamentDirection
                            .FREEDOM
                            .id()
            )) {
                modifier = originalTemperament.equals(
                        RecognitionIdentityHistoryService
                                .TemperamentDirection
                                .ORDER
                                .id()
                )
                        ? RecognitionIdentityHistoryModifier.OATHBREAKER
                        : RecognitionIdentityHistoryModifier.DEFIANT;
            } else {
                modifier = RecognitionIdentityHistoryModifier
                        .CROWNED_OR_BOUND;
            }

            reason = switch (modifier) {
                case OATHBREAKER ->
                        "A Lawful frozen identity is now opposed by clearly established Freedom history.";
                case DEFIANT ->
                        "A previously experienced Order commitment has been replaced by established Freedom without a Lawful identity to betray.";
                case CROWNED_OR_BOUND ->
                        "A Freedom-oriented or neutral frozen identity has been replaced by established Order after meaningful Freedom history.";
                default -> "A temperament contradiction was established.";
            };
        } else {
            modifier = RecognitionIdentityHistoryModifier.NONE;
            reason = unresolvedReason(
                    originalMoral,
                    originalTemperament,
                    currentMoral,
                    currentTemperament,
                    moralContested,
                    temperamentContested,
                    snapshot
            );
        }

        List<String> evidence = baseEvidence(
                snapshot,
                originalMoral,
                originalTemperament,
                currentMoral,
                currentTemperament
        );

        evidence.add(
                "Moral contradiction=" + moralContradiction
                        + ", returned=" + moralReturned
                        + ", contested=" + moralContested
        );

        evidence.add(
                "Temperament contradiction=" + temperamentContradiction
                        + ", returned=" + temperamentReturned
                        + ", contested=" + temperamentContested
        );

        return resolution(
                modifier,
                true,
                originalMoral,
                originalTemperament,
                currentMoral,
                currentTemperament,
                storedMoral,
                storedTemperament,
                moralContradiction,
                temperamentContradiction,
                moralReturned,
                temperamentReturned,
                moralContested,
                temperamentContested,
                playerFacingSummary(
                        modifier,
                        true
                ),
                reason,
                evidence
        );
    }

    /**
     * Persists a known resolved modifier after a qualifying deed.
     *
     * <p>Unknown modifier IDs and future history versions are deliberately
     * preserved. Repeated calls with the same result are idempotent.</p>
     */
    public static StoreResult resolveAndStoreAfterDeed(
            RecognitionData data
    ) {
        if (data == null) {
            return StoreResult.rejected(
                    "Recognition data is unavailable."
            );
        }

        RecognitionIdentityHistoryService.MigrationResult migration =
                RecognitionIdentityHistoryService.ensureCurrent(data);

        if (!migration.writable()) {
            return StoreResult.rejected(
                    migration
                            == RecognitionIdentityHistoryService
                            .MigrationResult
                            .FUTURE_VERSION_PRESERVED
                            ? "Future history version preserved."
                            : "Identity history is not writable."
            );
        }

        String rawStoredId = data.getString(
                RecognitionStatKeys.IDENTITY_HISTORY_MODIFIER
        );

        Optional<RecognitionIdentityHistoryModifier> knownStored =
                RecognitionIdentityHistoryModifier.byId(
                        rawStoredId
                );

        if (!rawStoredId.isBlank()
                && knownStored.isEmpty()) {
            return new StoreResult(
                    false,
                    true,
                    rawStoredId,
                    rawStoredId,
                    RecognitionIdentityHistoryModifier.NONE,
                    "Unknown future modifier ID preserved."
            );
        }

        RecognitionIdentityHistoryResolution resolution = resolve(data);

        RecognitionIdentityHistoryModifier previous =
                knownStored.orElse(
                        RecognitionIdentityHistoryModifier.NONE
                );

        RecognitionIdentityHistoryModifier resolved =
                resolution.modifier();

        if (previous == resolved) {
            return new StoreResult(
                    false,
                    false,
                    previous.id(),
                    resolved.id(),
                    resolved,
                    "Resolved modifier is already current."
            );
        }

        boolean stored = RecognitionIdentityHistoryService
                .setResolvedModifier(
                        data,
                        resolved
                );

        return new StoreResult(
                stored,
                false,
                previous.id(),
                stored ? resolved.id() : previous.id(),
                resolved,
                stored
                        ? "Resolved modifier updated after a qualifying deed."
                        : "Resolved modifier could not be stored."
        );
    }

    public static String playerFacingSummary(
            RecognitionIdentityHistoryModifier modifier,
            boolean committedIdentityAvailable
    ) {
        RecognitionIdentityHistoryModifier safeModifier =
                modifier == null
                        ? RecognitionIdentityHistoryModifier.NONE
                        : modifier;

        if (!committedIdentityAvailable) {
            return "Your later deeds can form a legacy only after recognition is committed.";
        }

        return switch (safeModifier) {
            case NONE ->
                    "Your later deeds have not formed a lasting contradiction with your recognized identity.";
            case FALLEN ->
                    "Your history records a sustained turn from a Good recognition toward Evil deeds.";
            case REDEEMED ->
                    "Your history records a sustained turn from an Evil recognition toward Good deeds.";
            case OATHBREAKER ->
                    "Your history records a sustained break from Lawful recognition toward personal freedom.";
            case CROWNED_OR_BOUND ->
                    "Your history records a sustained turn from freedom toward hierarchy and command.";
            case DEFIANT ->
                    "After experiencing authority, you established independence without a Lawful recognition to betray.";
            case FRACTURED ->
                    "Strong contradictions now pull across more than one part of your recognized identity.";
            case RECONCILED ->
                    "After genuine reversals, your deeds have re-established the direction of your recognized identity.";
        };
    }

    public static int colorFor(
            RecognitionIdentityHistoryModifier modifier
    ) {
        RecognitionIdentityHistoryModifier safeModifier =
                modifier == null
                        ? RecognitionIdentityHistoryModifier.NONE
                        : modifier;

        return switch (safeModifier) {
            case NONE -> 0x8B98A5;
            case FALLEN -> 0xD9644F;
            case REDEEMED -> 0x71E0B8;
            case OATHBREAKER -> 0x7F86FF;
            case CROWNED_OR_BOUND -> 0x73D66E;
            case DEFIANT -> 0xA98CFF;
            case FRACTURED -> 0xC03E86;
            case RECONCILED -> 0x5DD9E8;
        };
    }

    public static int guidanceProgressFor(
            RecognitionIdentityHistoryModifier modifier
    ) {
        return modifier == null
                || modifier == RecognitionIdentityHistoryModifier.NONE
                ? 0
                : 100;
    }

    private static RecognitionIdentityHistoryResolution resolution(
            RecognitionIdentityHistoryModifier modifier,
            boolean committedIdentityAvailable,
            String originalMoral,
            String originalTemperament,
            String currentMoral,
            String currentTemperament,
            String storedMoral,
            String storedTemperament,
            boolean moralContradiction,
            boolean temperamentContradiction,
            boolean moralReturned,
            boolean temperamentReturned,
            boolean moralContested,
            boolean temperamentContested,
            String summary,
            String reason,
            List<String> evidence
    ) {
        RecognitionIdentityHistoryModifier safeModifier =
                modifier == null
                        ? RecognitionIdentityHistoryModifier.NONE
                        : modifier;

        return new RecognitionIdentityHistoryResolution(
                safeModifier,
                committedIdentityAvailable,
                originalMoral,
                originalTemperament,
                currentMoral,
                currentTemperament,
                storedMoral,
                storedTemperament,
                moralContradiction,
                temperamentContradiction,
                moralReturned,
                temperamentReturned,
                moralContested,
                temperamentContested,
                guidanceProgressFor(safeModifier),
                colorFor(safeModifier),
                summary,
                reason,
                evidence
        );
    }

    private static List<String> baseEvidence(
            RecognitionIdentityHistorySnapshot snapshot,
            String originalMoral,
            String originalTemperament,
            String currentMoral,
            String currentTemperament
    ) {
        List<String> evidence = new ArrayList<>();

        evidence.add(
                "Original axes: moral="
                        + displayDirection(originalMoral)
                        + ", temperament="
                        + displayDirection(originalTemperament)
        );

        evidence.add(
                "Current clear axes: moral="
                        + displayDirection(currentMoral)
                        + ", temperament="
                        + displayDirection(currentTemperament)
        );

        evidence.add(
                "Momentum: good=" + format(snapshot.goodMomentum())
                        + ", evil=" + format(snapshot.evilMomentum())
                        + ", order=" + format(snapshot.orderMomentum())
                        + ", freedom=" + format(snapshot.freedomMomentum())
        );

        evidence.add(
                "Historical peaks: good="
                        + format(snapshot.highestGoodCommitment())
                        + ", evil="
                        + format(snapshot.highestEvilCommitment())
                        + ", order="
                        + format(snapshot.highestOrderCommitment())
                        + ", freedom="
                        + format(snapshot.highestFreedomCommitment())
        );

        evidence.add(
                "Reversals: moral="
                        + snapshot.moralReversalCount()
                        + ", temperament="
                        + snapshot.temperamentReversalCount()
        );

        return evidence;
    }

    private static String unresolvedReason(
            String originalMoral,
            String originalTemperament,
            String currentMoral,
            String currentTemperament,
            boolean moralContested,
            boolean temperamentContested,
            RecognitionIdentityHistorySnapshot snapshot
    ) {
        if (moralContested || temperamentContested) {
            return "Opposing commitments are visible, but they do not currently satisfy the combined Fractured rule.";
        }

        if (snapshot.moralReversalCount() <= 0
                && snapshot.temperamentReversalCount() <= 0) {
            return "No genuine established-direction reversal has been recorded.";
        }

        if (currentMoral.isBlank()
                && currentTemperament.isBlank()) {
            return "Neither history axis currently has a clearly established direction.";
        }

        if (originalMoral.isBlank()
                && originalTemperament.isBlank()) {
            return "The frozen recognition is neutral on both axes, so no opposing identity direction exists.";
        }

        return "The current history does not meet a modifier's evidence, reversal or dominance requirements.";
    }

    private static String originalMoralDirection(
            RecognitionPath primary,
            RecognitionPath secondary
    ) {
        String primaryDirection = moralDirection(primary);

        if (!primaryDirection.isBlank()) {
            return primaryDirection;
        }

        return moralDirection(secondary);
    }

    private static String originalTemperamentDirection(
            RecognitionPath primary,
            RecognitionPath secondary
    ) {
        String primaryDirection = temperamentDirection(primary);

        if (!primaryDirection.isBlank()) {
            return primaryDirection;
        }

        return temperamentDirection(secondary);
    }

    private static String moralDirection(
            RecognitionPath path
    ) {
        if (path == null) {
            return "";
        }

        return switch (path.getMorality()) {
            case GOOD -> RecognitionIdentityHistoryService
                    .MoralDirection
                    .GOOD
                    .id();
            case EVIL -> RecognitionIdentityHistoryService
                    .MoralDirection
                    .EVIL
                    .id();
            case NEUTRAL -> "";
        };
    }

    private static String temperamentDirection(
            RecognitionPath path
    ) {
        if (path == null) {
            return "";
        }

        return switch (path.getTemperament()) {
            case LAWFUL -> RecognitionIdentityHistoryService
                    .TemperamentDirection
                    .ORDER
                    .id();
            case CHAOTIC -> RecognitionIdentityHistoryService
                    .TemperamentDirection
                    .FREEDOM
                    .id();
            case NEUTRAL -> "";
        };
    }

    private static boolean isOppositeEstablishedDirection(
            String original,
            String current
    ) {
        return !original.isBlank()
                && !current.isBlank()
                && !original.equals(current);
    }

    private static boolean isContested(
            double first,
            double second
    ) {
        double safeFirst = sanitize(first);
        double safeSecond = sanitize(second);

        return safeFirst >= CONTESTED_SIDE_MINIMUM
                && safeSecond >= CONTESTED_SIDE_MINIMUM
                && Math.abs(safeFirst - safeSecond)
                < RecognitionIdentityHistoryService
                .ESTABLISHED_DIRECTION_MARGIN;
    }

    private static double historicalPeakForMoral(
            RecognitionIdentityHistorySnapshot snapshot,
            String direction
    ) {
        return RecognitionIdentityHistoryService
                .MoralDirection
                .GOOD
                .id()
                .equals(direction)
                ? snapshot.highestGoodCommitment()
                : RecognitionIdentityHistoryService
                .MoralDirection
                .EVIL
                .id()
                .equals(direction)
                  ? snapshot.highestEvilCommitment()
                  : 0.0D;
    }

    private static double oppositeHistoricalPeakForMoral(
            RecognitionIdentityHistorySnapshot snapshot,
            String direction
    ) {
        return RecognitionIdentityHistoryService
                .MoralDirection
                .GOOD
                .id()
                .equals(direction)
                ? snapshot.highestEvilCommitment()
                : RecognitionIdentityHistoryService
                .MoralDirection
                .EVIL
                .id()
                .equals(direction)
                  ? snapshot.highestGoodCommitment()
                  : 0.0D;
    }

    private static double currentMomentumForMoral(
            RecognitionIdentityHistorySnapshot snapshot,
            String direction
    ) {
        return RecognitionIdentityHistoryService
                .MoralDirection
                .GOOD
                .id()
                .equals(direction)
                ? snapshot.goodMomentum()
                : RecognitionIdentityHistoryService
                .MoralDirection
                .EVIL
                .id()
                .equals(direction)
                  ? snapshot.evilMomentum()
                  : 0.0D;
    }

    private static double historicalPeakForTemperament(
            RecognitionIdentityHistorySnapshot snapshot,
            String direction
    ) {
        return RecognitionIdentityHistoryService
                .TemperamentDirection
                .ORDER
                .id()
                .equals(direction)
                ? snapshot.highestOrderCommitment()
                : RecognitionIdentityHistoryService
                .TemperamentDirection
                .FREEDOM
                .id()
                .equals(direction)
                  ? snapshot.highestFreedomCommitment()
                  : 0.0D;
    }

    private static double oppositeHistoricalPeakForTemperament(
            RecognitionIdentityHistorySnapshot snapshot,
            String direction
    ) {
        return RecognitionIdentityHistoryService
                .TemperamentDirection
                .ORDER
                .id()
                .equals(direction)
                ? snapshot.highestFreedomCommitment()
                : RecognitionIdentityHistoryService
                .TemperamentDirection
                .FREEDOM
                .id()
                .equals(direction)
                  ? snapshot.highestOrderCommitment()
                  : 0.0D;
    }

    private static double currentMomentumForTemperament(
            RecognitionIdentityHistorySnapshot snapshot,
            String direction
    ) {
        return RecognitionIdentityHistoryService
                .TemperamentDirection
                .ORDER
                .id()
                .equals(direction)
                ? snapshot.orderMomentum()
                : RecognitionIdentityHistoryService
                .TemperamentDirection
                .FREEDOM
                .id()
                .equals(direction)
                  ? snapshot.freedomMomentum()
                  : 0.0D;
    }

    private static String normalizeMoralDirection(
            String value
    ) {
        String clean = clean(value);

        return clean.equals(
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
        ) || clean.equals(
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        )
                ? clean
                : "";
    }

    private static String normalizeTemperamentDirection(
            String value
    ) {
        String clean = clean(value);

        return clean.equals(
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id()
        ) || clean.equals(
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
        )
                ? clean
                : "";
    }

    private static String displayDirection(
            String value
    ) {
        String clean = clean(value);
        return clean.isBlank() ? "neutral/none" : clean;
    }

    private static String clean(
            String value
    ) {
        return value == null
                ? ""
                : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static double sanitize(
            double value
    ) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0.0D;
        }

        return Math.min(
                RecognitionIdentityHistoryService.MAXIMUM_MOMENTUM,
                value
        );
    }

    private static String format(
            double value
    ) {
        return String.format(
                java.util.Locale.US,
                "%.1f",
                sanitize(value)
        );
    }

    public record StoreResult(
            boolean changed,
            boolean unknownModifierPreserved,
            String previousModifierId,
            String storedModifierId,
            RecognitionIdentityHistoryModifier resolvedModifier,
            String detail
    ) {

        public StoreResult {
            previousModifierId = previousModifierId == null
                    ? ""
                    : previousModifierId.trim();

            storedModifierId = storedModifierId == null
                    ? ""
                    : storedModifierId.trim();

            resolvedModifier = resolvedModifier == null
                    ? RecognitionIdentityHistoryModifier.NONE
                    : resolvedModifier;

            detail = detail == null
                    ? ""
                    : detail.trim();
        }

        public static StoreResult rejected(
                String detail
        ) {
            return new StoreResult(
                    false,
                    false,
                    "",
                    "",
                    RecognitionIdentityHistoryModifier.NONE,
                    detail
            );
        }
    }
}