package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

/**
 * Converts already-confirmed gameplay deeds into the versioned identity
 * history introduced by Packet 6G.6A.
 *
 * <p>This class never changes normal recognition scores and never rewrites a
 * frozen committed contradiction modifier. It applies semantic momentum,
 * registers genuine reversals and then asks the separate history resolver to
 * update the evolving history modifier after the qualifying deed.</p>
 */
public final class RecognitionIdentityHistoryIntegration {

    private RecognitionIdentityHistoryIntegration() {
    }

    public static EventResult record(
            RecognitionData data,
            TrackedDeed deed,
            long overworldGameTime
    ) {
        if (deed == null) {
            return EventResult.rejected(
                    "A tracked deed is required."
            );
        }

        return recordWeighted(
                data,
                deed.id(),
                deed.axis(),
                deed.directionId(),
                deed.weight(),
                overworldGameTime
        );
    }

    public static EventResult recordOccurrences(
            RecognitionData data,
            TrackedDeed deed,
            int occurrences,
            long overworldGameTime
    ) {
        if (deed == null) {
            return EventResult.rejected(
                    "A tracked deed is required."
            );
        }

        int safeOccurrences = Math.max(
                0,
                occurrences
        );

        if (safeOccurrences <= 0) {
            return EventResult.rejected(
                    "At least one deed occurrence is required."
            );
        }

        double totalWeight = deed.weight()
                * (double) safeOccurrences;

        return recordWeighted(
                data,
                deed.id(),
                deed.axis(),
                deed.directionId(),
                totalWeight,
                overworldGameTime
        );
    }

    /**
     * Uses the active datapack milestone value as the Freedom-history weight.
     * Backfilled advancements deliberately do not call this method because
     * their chronological order is unknown.
     */
    public static EventResult recordIndependenceMilestone(
            RecognitionData data,
            double configuredPoints,
            long overworldGameTime
    ) {
        return recordWeighted(
                data,
                "independence_milestone",
                Axis.TEMPERAMENT,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id(),
                configuredPoints,
                overworldGameTime
        );
    }

    public static EventResult recordWeighted(
            RecognitionData data,
            String deedId,
            Axis axis,
            String directionId,
            double weight,
            long overworldGameTime
    ) {
        if (data == null) {
            return EventResult.rejected(
                    "Recognition data is required."
            );
        }

        if (axis == null) {
            return EventResult.rejected(
                    "A contradiction axis is required."
            );
        }

        String safeDeedId = clean(
                deedId
        );

        String safeDirectionId = clean(
                directionId
        );

        if (safeDeedId.isBlank()
                || !axis.supports(
                safeDirectionId
        )) {
            return EventResult.rejected(
                    "The deed direction is invalid for its axis."
            );
        }

        RecognitionIdentityHistorySnapshot before =
                RecognitionIdentityHistoryService.inspect(
                        data
                );

        RecognitionIdentityHistoryService.MutationResult mutation =
                axis == Axis.MORAL
                        ? recordMoralMutation(
                        data,
                        safeDirectionId,
                        weight,
                        overworldGameTime
                )
                        : recordTemperamentMutation(
                        data,
                        safeDirectionId,
                        weight,
                        overworldGameTime
                );

        if (!mutation.applied()) {
            return new EventResult(
                    false,
                    safeDeedId,
                    axis,
                    safeDirectionId,
                    0.0D,
                    false,
                    currentReversalCount(
                            data,
                            axis
                    ),
                    mutation.rejectionReason()
            );
        }

        RecognitionIdentityHistorySnapshot after =
                RecognitionIdentityHistoryService.inspect(
                        data
                );

        String establishedKey =
                axis == Axis.MORAL
                        ? RecognitionStatKeys
                          .MORAL_ESTABLISHED_DIRECTION
                        : RecognitionStatKeys
                          .TEMPERAMENT_ESTABLISHED_DIRECTION;

        String storedEstablished = normalizeDirection(
                data.getString(
                        establishedKey
                ),
                axis
        );

        if (storedEstablished.isBlank()) {
            storedEstablished = establishedDirection(
                    before,
                    axis
            );
        }

        String newlyEstablished = establishedDirection(
                after,
                axis
        );

        boolean reversalRegistered = false;
        int reversalCount = currentReversalCount(
                data,
                axis
        );

        if (!newlyEstablished.isBlank()) {
            if (storedEstablished.isBlank()) {
                data.setString(
                        establishedKey,
                        newlyEstablished
                );
            } else if (!storedEstablished.equals(
                    newlyEstablished
            )) {
                reversalCount =
                        axis == Axis.MORAL
                                ? RecognitionIdentityHistoryService
                                .registerMoralReversal(
                                        data
                                )
                                : RecognitionIdentityHistoryService
                                .registerTemperamentReversal(
                                        data
                                );

                data.setString(
                        establishedKey,
                        newlyEstablished
                );

                reversalRegistered = true;
            }
        }

        /*
         * Modifier resolution is deliberately event-driven. Passive GUI
         * inspection, login and the passage of time never call this write
         * path, so lazy decay alone cannot create or change a modifier.
         */
        RecognitionIdentityHistoryResolver
                .resolveAndStoreAfterDeed(data);

        return new EventResult(
                true,
                safeDeedId,
                axis,
                safeDirectionId,
                mutation.appliedWeight(),
                reversalRegistered,
                reversalCount,
                ""
        );
    }

    /**
     * Converts deltas from the existing authority counters into history deeds.
     *
     * <p>The first call only installs persisted baselines through
     * {@link RecognitionIdentityHistoryService#ensureCurrent(RecognitionData)}.
     * Existing worlds therefore do not receive fabricated retroactive
     * chronology. Later positive deltas are consumed exactly once.</p>
     */
    public static AuthoritySyncResult synchronizeAuthorityCounters(
            RecognitionData data,
            long overworldGameTime
    ) {
        if (data == null) {
            return AuthoritySyncResult.empty();
        }

        RecognitionIdentityHistoryService.MigrationResult migration =
                RecognitionIdentityHistoryService.ensureCurrent(
                        data
                );

        if (!migration.writable()) {
            return AuthoritySyncResult.empty();
        }

        int empoweredCurrent =
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .UNIQUE_SUBORDINATES_EMPOWERED
                );

        int massGrantsCurrent =
                data.getCounter(
                        RecognitionStatKeys
                                .MASS_GRANTS_PERFORMED
                );

        int takeBacksCurrent =
                data.getCounter(
                        RecognitionStatKeys
                                .GLOBAL_TAKE_BACKS_PERFORMED
                );

        int seizedCurrent =
                data.getCounter(
                        RecognitionStatKeys
                                .SKILLS_SEIZED
                );

        int empoweredDelta = positiveDelta(
                empoweredCurrent,
                data.getCounter(
                        RecognitionStatKeys
                                .HISTORY_OBSERVED_UNIQUE_SUBORDINATES_EMPOWERED
                )
        );

        int massGrantDelta = positiveDelta(
                massGrantsCurrent,
                data.getCounter(
                        RecognitionStatKeys
                                .HISTORY_OBSERVED_MASS_GRANTS_PERFORMED
                )
        );

        int takeBackDelta = positiveDelta(
                takeBacksCurrent,
                data.getCounter(
                        RecognitionStatKeys
                                .HISTORY_OBSERVED_GLOBAL_TAKE_BACKS_PERFORMED
                )
        );

        int seizedDelta = positiveDelta(
                seizedCurrent,
                data.getCounter(
                        RecognitionStatKeys
                                .HISTORY_OBSERVED_SKILLS_SEIZED
                )
        );

        int appliedGroups = 0;
        boolean moralReversal = false;
        boolean temperamentReversal = false;

        EventResult empoweredResult = recordDelta(
                data,
                TrackedDeed.UNIQUE_SUBORDINATE_EMPOWERED,
                empoweredDelta,
                overworldGameTime
        );

        if (empoweredResult.applied()) {
            appliedGroups++;
            temperamentReversal |=
                    empoweredResult.reversalRegistered();
        }

        EventResult massGrantResult = recordDelta(
                data,
                TrackedDeed.MASS_GRANT_PERFORMED,
                massGrantDelta,
                overworldGameTime
        );

        if (massGrantResult.applied()) {
            appliedGroups++;
            temperamentReversal |=
                    massGrantResult.reversalRegistered();
        }

        EventResult takeBackResult = recordDelta(
                data,
                TrackedDeed.GLOBAL_TAKE_BACK_PERFORMED,
                takeBackDelta,
                overworldGameTime
        );

        if (takeBackResult.applied()) {
            appliedGroups++;
            temperamentReversal |=
                    takeBackResult.reversalRegistered();
        }

        EventResult seizedResult = recordDelta(
                data,
                TrackedDeed.SKILL_SEIZED,
                seizedDelta,
                overworldGameTime
        );

        if (seizedResult.applied()) {
            appliedGroups++;
            moralReversal |=
                    seizedResult.reversalRegistered();
        }

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_UNIQUE_SUBORDINATES_EMPOWERED,
                empoweredCurrent
        );

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_MASS_GRANTS_PERFORMED,
                massGrantsCurrent
        );

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_GLOBAL_TAKE_BACKS_PERFORMED,
                takeBacksCurrent
        );

        data.setCounter(
                RecognitionStatKeys
                        .HISTORY_OBSERVED_SKILLS_SEIZED,
                seizedCurrent
        );

        return new AuthoritySyncResult(
                empoweredDelta,
                massGrantDelta,
                takeBackDelta,
                seizedDelta,
                appliedGroups,
                moralReversal,
                temperamentReversal
        );
    }

    private static EventResult recordDelta(
            RecognitionData data,
            TrackedDeed deed,
            int delta,
            long overworldGameTime
    ) {
        if (delta <= 0) {
            return EventResult.rejected(
                    "No positive counter delta."
            );
        }

        return recordOccurrences(
                data,
                deed,
                delta,
                overworldGameTime
        );
    }

    private static int positiveDelta(
            int current,
            int observed
    ) {
        return Math.max(
                0,
                Math.max(
                        0,
                        current
                ) - Math.max(
                        0,
                        observed
                )
        );
    }

    public static String establishedDirection(
            RecognitionIdentityHistorySnapshot snapshot,
            Axis axis
    ) {
        if (snapshot == null || axis == null) {
            return "";
        }

        return axis == Axis.MORAL
                ? RecognitionIdentityHistoryService
                .inferEstablishedDirection(
                        snapshot.goodMomentum(),
                        snapshot.evilMomentum(),
                        RecognitionIdentityHistoryService
                                .MoralDirection
                                .GOOD
                                .id(),
                        RecognitionIdentityHistoryService
                                .MoralDirection
                                .EVIL
                                .id()
                )
                : RecognitionIdentityHistoryService
                .inferEstablishedDirection(
                        snapshot.orderMomentum(),
                        snapshot.freedomMomentum(),
                        RecognitionIdentityHistoryService
                                .TemperamentDirection
                                .ORDER
                                .id(),
                        RecognitionIdentityHistoryService
                                .TemperamentDirection
                                .FREEDOM
                                .id()
                );
    }

    private static RecognitionIdentityHistoryService.MutationResult
    recordMoralMutation(
            RecognitionData data,
            String directionId,
            double weight,
            long overworldGameTime
    ) {
        RecognitionIdentityHistoryService.MoralDirection direction =
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id()
                        .equals(
                                directionId
                        )
                        ? RecognitionIdentityHistoryService
                          .MoralDirection
                          .GOOD
                        : RecognitionIdentityHistoryService
                          .MoralDirection
                          .EVIL;

        return RecognitionIdentityHistoryService.recordMoralDeed(
                data,
                direction,
                weight,
                overworldGameTime
        );
    }

    private static RecognitionIdentityHistoryService.MutationResult
    recordTemperamentMutation(
            RecognitionData data,
            String directionId,
            double weight,
            long overworldGameTime
    ) {
        RecognitionIdentityHistoryService.TemperamentDirection direction =
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id()
                        .equals(
                                directionId
                        )
                        ? RecognitionIdentityHistoryService
                          .TemperamentDirection
                          .ORDER
                        : RecognitionIdentityHistoryService
                          .TemperamentDirection
                          .FREEDOM;

        return RecognitionIdentityHistoryService.recordTemperamentDeed(
                data,
                direction,
                weight,
                overworldGameTime
        );
    }

    private static int currentReversalCount(
            RecognitionData data,
            Axis axis
    ) {
        if (data == null || axis == null) {
            return 0;
        }

        return data.getCounter(
                axis == Axis.MORAL
                        ? RecognitionStatKeys
                          .MORAL_REVERSAL_COUNT
                        : RecognitionStatKeys
                          .TEMPERAMENT_REVERSAL_COUNT
        );
    }

    private static String normalizeDirection(
            String rawDirection,
            Axis axis
    ) {
        String cleaned = clean(
                rawDirection
        );

        return axis != null
                && axis.supports(
                cleaned
        )
                ? cleaned
                : "";
    }

    private static String clean(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    public enum Axis {
        MORAL(
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id(),
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id()
        ),
        TEMPERAMENT(
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id(),
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id()
        );

        private final String firstDirectionId;
        private final String secondDirectionId;

        Axis(
                String firstDirectionId,
                String secondDirectionId
        ) {
            this.firstDirectionId = firstDirectionId;
            this.secondDirectionId = secondDirectionId;
        }

        public boolean supports(
                String directionId
        ) {
            return firstDirectionId.equals(
                    directionId
            ) || secondDirectionId.equals(
                    directionId
            );
        }
    }

    public enum TrackedDeed {
        RAID_VICTORY(
                "raid_victory",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id(),
                8.0D
        ),
        VILLAGER_CURED(
                "villager_cured",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id(),
                6.0D
        ),
        CIVILIAN_DEFENDED(
                "civilian_defended",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id(),
                5.0D
        ),
        MALEVOLENT_BOSS_DEFEATED(
                "malevolent_boss_defeated",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .GOOD
                        .id(),
                10.0D
        ),
        OWNED_SUBORDINATE_KILLED(
                "owned_subordinate_killed",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id(),
                12.0D
        ),
        OWNED_COMPANION_KILLED(
                "owned_companion_killed",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id(),
                10.0D
        ),
        CIVILIAN_KILLED(
                "civilian_killed",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id(),
                7.0D
        ),
        BENEVOLENT_BOSS_KILLED(
                "benevolent_boss_killed",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id(),
                12.0D
        ),
        PASSIVE_BABY_KILLED(
                "passive_baby_killed",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id(),
                2.0D
        ),
        UNIQUE_SUBORDINATE_EMPOWERED(
                "unique_subordinate_empowered",
                Axis.TEMPERAMENT,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id(),
                4.0D
        ),
        MASS_GRANT_PERFORMED(
                "mass_grant_performed",
                Axis.TEMPERAMENT,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id(),
                6.0D
        ),
        GLOBAL_TAKE_BACK_PERFORMED(
                "global_take_back_performed",
                Axis.TEMPERAMENT,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id(),
                5.0D
        ),
        SKILL_SEIZED(
                "skill_seized",
                Axis.MORAL,
                RecognitionIdentityHistoryService
                        .MoralDirection
                        .EVIL
                        .id(),
                8.0D
        ),
        SUBORDINATE_ASSISTED_MAJOR_VICTORY(
                "subordinate_assisted_major_victory",
                Axis.TEMPERAMENT,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .ORDER
                        .id(),
                6.0D
        ),
        SOLO_MAJOR_VICTORY(
                "solo_major_victory",
                Axis.TEMPERAMENT,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id(),
                8.0D
        ),
        DISCOVERY_MILESTONE(
                "discovery_milestone",
                Axis.TEMPERAMENT,
                RecognitionIdentityHistoryService
                        .TemperamentDirection
                        .FREEDOM
                        .id(),
                2.0D
        );

        private final String id;
        private final Axis axis;
        private final String directionId;
        private final double weight;

        TrackedDeed(
                String id,
                Axis axis,
                String directionId,
                double weight
        ) {
            this.id = id;
            this.axis = axis;
            this.directionId = directionId;
            this.weight = weight;
        }

        public String id() {
            return id;
        }

        public Axis axis() {
            return axis;
        }

        public String directionId() {
            return directionId;
        }

        public double weight() {
            return weight;
        }
    }

    public record AuthoritySyncResult(
            int empoweredSubordinateDelta,
            int massGrantDelta,
            int globalTakeBackDelta,
            int skillSeizedDelta,
            int appliedDeedGroups,
            boolean moralReversalRegistered,
            boolean temperamentReversalRegistered
    ) {

        public AuthoritySyncResult {
            empoweredSubordinateDelta = Math.max(
                    0,
                    empoweredSubordinateDelta
            );

            massGrantDelta = Math.max(
                    0,
                    massGrantDelta
            );

            globalTakeBackDelta = Math.max(
                    0,
                    globalTakeBackDelta
            );

            skillSeizedDelta = Math.max(
                    0,
                    skillSeizedDelta
            );

            appliedDeedGroups = Math.max(
                    0,
                    appliedDeedGroups
            );
        }

        public static AuthoritySyncResult empty() {
            return new AuthoritySyncResult(
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false
            );
        }

        public boolean changed() {
            return appliedDeedGroups > 0;
        }
    }

    public record EventResult(
            boolean applied,
            String deedId,
            Axis axis,
            String directionId,
            double appliedWeight,
            boolean reversalRegistered,
            int reversalCount,
            String rejectionReason
    ) {

        public EventResult {
            deedId = clean(
                    deedId
            );

            directionId = clean(
                    directionId
            );

            appliedWeight = applied
                    && Double.isFinite(
                    appliedWeight
            )
                    ? Math.max(
                    0.0D,
                    appliedWeight
            )
                    : 0.0D;

            reversalCount = Math.max(
                    0,
                    reversalCount
            );

            rejectionReason = rejectionReason == null
                    ? ""
                    : rejectionReason;
        }

        public static EventResult rejected(
                String reason
        ) {
            return new EventResult(
                    false,
                    "",
                    null,
                    "",
                    0.0D,
                    false,
                    0,
                    reason
            );
        }
    }
}