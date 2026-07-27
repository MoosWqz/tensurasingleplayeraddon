package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only breakdown of every source currently contributing to Freedom.
 *
 * <p>The snapshot never synchronizes advancements, backfills deeds or mutates
 * player data. It is shared by the progress GUI and debug diagnostics so both
 * surfaces describe the exact same production scoring sources.</p>
 */
public record RecognitionFreedomProgressSnapshot(
        long balanceRevision,
        String balanceSource,
        long milestoneRevision,
        String milestoneFingerprint,
        String storedMilestoneFingerprint,
        boolean milestoneFingerprintCurrent,
        int milestoneSourceFileCount,
        int configuredMilestoneCount,
        int soloMajorVictoryCount,
        double soloMajorVictoryScore,
        double soloMajorVictoryMaximum,
        int discoveryMilestoneCount,
        double discoveryMilestoneScore,
        double discoveryMilestoneMaximum,
        int storedIndependenceMilestoneCount,
        int activeIndependenceMilestoneCount,
        int inactiveIndependenceMilestoneCount,
        double activeIndependenceScore,
        double activeIndependenceMaximum,
        double totalFreedomScore,
        double totalFreedomMaximum,
        List<String> activeMilestoneIds,
        List<String> inactiveMilestoneIds
) {

    public RecognitionFreedomProgressSnapshot {
        balanceSource = clean(balanceSource);
        milestoneFingerprint = clean(milestoneFingerprint);
        storedMilestoneFingerprint = clean(storedMilestoneFingerprint);

        balanceRevision = Math.max(0L, balanceRevision);
        milestoneRevision = Math.max(0L, milestoneRevision);
        milestoneSourceFileCount = Math.max(0, milestoneSourceFileCount);
        configuredMilestoneCount = Math.max(0, configuredMilestoneCount);
        soloMajorVictoryCount = Math.max(0, soloMajorVictoryCount);
        discoveryMilestoneCount = Math.max(0, discoveryMilestoneCount);
        storedIndependenceMilestoneCount = Math.max(
                0,
                storedIndependenceMilestoneCount
        );
        activeIndependenceMilestoneCount = Math.max(
                0,
                activeIndependenceMilestoneCount
        );
        inactiveIndependenceMilestoneCount = Math.max(
                0,
                inactiveIndependenceMilestoneCount
        );

        soloMajorVictoryScore = sanitizeScore(soloMajorVictoryScore);
        soloMajorVictoryMaximum = sanitizeScore(soloMajorVictoryMaximum);
        discoveryMilestoneScore = sanitizeScore(discoveryMilestoneScore);
        discoveryMilestoneMaximum = sanitizeScore(discoveryMilestoneMaximum);
        activeIndependenceScore = sanitizeScore(activeIndependenceScore);
        activeIndependenceMaximum = sanitizeScore(activeIndependenceMaximum);
        totalFreedomScore = sanitizeScore(totalFreedomScore);
        totalFreedomMaximum = sanitizeScore(totalFreedomMaximum);

        activeMilestoneIds = immutableSorted(activeMilestoneIds);
        inactiveMilestoneIds = immutableSorted(inactiveMilestoneIds);
    }

    public static RecognitionFreedomProgressSnapshot inspect(
            RecognitionData data
    ) {
        RecognitionEvaluation evaluation =
                RecognitionPathEvaluator.evaluate(data);

        return inspect(
                data,
                evaluation
        );
    }

    public static RecognitionFreedomProgressSnapshot inspect(
            RecognitionData data,
            RecognitionEvaluation evaluation
    ) {
        RecognitionData safeData =
                data == null
                        ? new RecognitionData()
                        : data;

        RecognitionEvaluation safeEvaluation =
                evaluation == null
                        ? RecognitionPathEvaluator.evaluate(safeData)
                        : evaluation;

        RecognitionBalanceSnapshot balance =
                safeEvaluation.getBalance();

        RecognitionBalanceSnapshot.Freedom freedom =
                balance.freedom();

        RecognitionIndependenceMilestoneManager.State milestoneState =
                RecognitionIndependenceMilestoneManager.getState();

        int soloCount =
                safeData.getUniqueValueCount(
                        RecognitionStatKeys
                                .SOLO_MAJOR_ENEMY_TYPES_DEFEATED
                );

        int discoveryCount =
                safeData.getUniqueValueCount(
                        RecognitionStatKeys.DISCOVERY_MILESTONES
                );

        List<String> storedMilestoneIds =
                safeData.getUniqueValues(
                        RecognitionStatKeys.INDEPENDENCE_MILESTONES
                );

        Set<String> configuredIds =
                new HashSet<>();

        List<String> activeIds =
                new ArrayList<>();

        for (RecognitionIndependenceMilestoneManager.Milestone milestone :
                milestoneState.milestones()) {

            String id = milestone.id().toString();
            configuredIds.add(id);

            if (safeData.containsUniqueValue(
                    RecognitionStatKeys.INDEPENDENCE_MILESTONES,
                    id
            )) {
                activeIds.add(id);
            }
        }

        List<String> inactiveIds =
                new ArrayList<>();

        for (String storedId : storedMilestoneIds) {
            if (storedId != null
                    && !storedId.isBlank()
                    && !configuredIds.contains(storedId)) {
                inactiveIds.add(storedId);
            }
        }

        double soloScore = contribution(
                soloCount,
                freedom.soloMajorEnemyTypesDefeated()
        );

        double discoveryScore = contribution(
                discoveryCount,
                freedom.discoveryMilestones()
        );

        double independenceScore =
                RecognitionIndependenceMilestoneManager
                        .calculateScore(safeData);

        String storedFingerprint =
                safeData.getString(
                        RecognitionStatKeys
                                .INDEPENDENCE_DEFINITION_FINGERPRINT
                );

        return new RecognitionFreedomProgressSnapshot(
                safeEvaluation.getBalanceRevision(),
                balance.sourceId(),
                milestoneState.revision(),
                milestoneState.fingerprint(),
                storedFingerprint,
                milestoneState.fingerprint()
                        .equals(storedFingerprint),
                milestoneState.sourceFileCount(),
                milestoneState.milestones().size(),
                soloCount,
                soloScore,
                freedom.soloMajorEnemyTypesDefeated().maximum(),
                discoveryCount,
                discoveryScore,
                freedom.discoveryMilestones().maximum(),
                storedMilestoneIds.size(),
                activeIds.size(),
                inactiveIds.size(),
                independenceScore,
                milestoneState.maximumScore(),
                safeEvaluation.getDimensions().freedom(),
                freedom.soloMajorEnemyTypesDefeated().maximum()
                        + freedom.discoveryMilestones().maximum()
                        + milestoneState.maximumScore(),
                activeIds,
                inactiveIds
        );
    }

    private static double contribution(
            int count,
            RecognitionBalanceSnapshot.Contribution contribution
    ) {
        if (count <= 0
                || contribution == null
                || contribution.pointsPerEntry() <= 0.0D
                || contribution.maximum() <= 0.0D) {
            return 0.0D;
        }

        return Math.min(
                contribution.maximum(),
                count * contribution.pointsPerEntry()
        );
    }

    private static List<String> immutableSorted(
            List<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }

        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    private static String clean(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static double sanitizeScore(
            double value
    ) {
        return !Double.isFinite(value) || value < 0.0D
                ? 0.0D
                : value;
    }
}