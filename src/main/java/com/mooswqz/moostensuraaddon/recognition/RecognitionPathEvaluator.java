package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts stored recognition deeds into dimensions, path affinities and a
 * final recognition selection.
 *
 * Rules version 2 uses active axis components for qualification. Passive
 * Neutral posture remains diagnostic-only and cannot establish a path.
 *
 * All balancing values come from one immutable datapack snapshot captured at
 * the beginning of evaluate(). No JSON parsing or resource access occurs here.
 */
public final class RecognitionPathEvaluator {

    /*
     * Compatibility constants for older call sites and external integrations.
     *
     * Runtime code inside the addon uses RecognitionEvaluation#getBalance()
     * instead, so datapack reloads can change the active values safely.
     */
    public static final double DEFAULT_ESTABLISHED_THRESHOLD =
            35.0D;

    public static final double DEFAULT_PURE_THRESHOLD =
            70.0D;

    public static final double DEFAULT_DOMINANCE_RATIO =
            2.0D;

    public static final double DEFAULT_RAW_PURE_THRESHOLD =
            35.0D;

    public static final double DEFAULT_RAW_DOMINANCE_RATIO =
            1.6D;

    public static final double MIN_DIRECTIONAL_MORALITY_EVIDENCE =
            6.0D;

    private RecognitionPathEvaluator() {
    }

    public static RecognitionEvaluation evaluate(
            RecognitionData rawData
    ) {
        RecognitionData data =
                rawData == null
                        ? new RecognitionData()
                        : rawData;

        RecognitionBalanceManager.State balanceState =
                RecognitionBalanceManager.getState();

        RecognitionBalanceSnapshot balance =
                balanceState.snapshot();

        RecognitionDimensions dimensions =
                calculateDimensions(
                        data,
                        balance
                );

        RecognitionPathComponents components =
                RecognitionPathComponents.calculate(
                        dimensions,
                        balance
                );

        /*
         * Every path must have meaningful evidence on both defining axes
         * before it can consume identity resonance or become selectable.
         *
         * Passive Neutral posture is intentionally absent from this gate.
         *
         * This prevents Order or Freedom from independently establishing a
         * Good/Evil path when the player has no supporting morality evidence.
         */
        Map<RecognitionPath, Double> rawPathScores =
                applyComponentEvidenceGate(
                        components,
                        calculateRawPathScores(
                                data,
                                components,
                                dimensions,
                                balance
                        ),
                        balance
                );

        /*
         * Component-incomplete paths cannot consume the universal or focused
         * identity pool.
         */
        Map<RecognitionPath, Double> identityBoosts =
                applyComponentEvidenceGate(
                        components,
                        calculateIdentityBoosts(
                                rawPathScores,
                                dimensions.identityStrength(),
                                balance
                        ),
                        balance
                );

        Map<RecognitionPath, Double> pathScores =
                combineScores(
                        rawPathScores,
                        identityBoosts
                );

        RecognitionBalanceSnapshot.Selection selectionBalance =
                balance.selection();

        Optional<RecognitionPathSelection> selection =
                RecognitionPathSelector
                        .selectWithComponentPureGate(
                                pathScores,
                                rawPathScores,
                                components,
                                selectionBalance
                        );

        return new RecognitionEvaluation(
                dimensions,
                components,
                rawPathScores,
                identityBoosts,
                pathScores,
                selection,
                balance,
                balanceState.revision()
        );
    }

    private static RecognitionDimensions calculateDimensions(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        double good =
                calculateGood(
                        data,
                        balance
                );

        double evil =
                calculateEvil(
                        data,
                        balance
                );

        double order =
                calculateOrder(
                        data,
                        balance
                );

        double freedom =
                calculateFreedom(
                        data,
                        balance
                );

        double mastery =
                calculateMastery(
                        data,
                        balance
                );

        double discovery =
                calculateDiscovery(
                        data,
                        balance
                );

        int distinctMajorEnemies =
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .MAJOR_ENEMY_TYPES_DEFEATED
                );

        RecognitionBalanceSnapshot.IdentityStrength identityBalance =
                balance.identityStrength();

        double identityStrength =
                Math.min(
                        identityBalance.maximum(),
                        mastery
                                * identityBalance.masteryWeight()
                                + discovery
                                * identityBalance.discoveryWeight()
                                + contribution(
                                distinctMajorEnemies,
                                identityBalance.majorEnemyTypes()
                        )
                );

        return new RecognitionDimensions(
                good,
                evil,
                order,
                freedom,
                mastery,
                discovery,
                sanitizeScore(
                        identityStrength
                )
        );
    }

    private static double calculateGood(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Good good =
                balance.good();

        double points =
                data.getFlag(
                        RecognitionStatKeys.TRUE_HERO
                )
                        ? good.trueHeroModifier()
                        : 0.0D;

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.RAID_VICTORIES
                ),
                good.raidVictories()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.VILLAGERS_CURED
                ),
                good.villagersCured()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.CIVILIANS_DEFENDED
                ),
                good.civiliansDefended()
        );

        points += contribution(
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .MALEVOLENT_BOSS_TYPES_DEFEATED
                ),
                good.malevolentBossTypesDefeated()
        );

        return sanitizeScore(points);
    }

    private static double calculateEvil(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Evil evil =
                balance.evil();

        double points =
                data.getFlag(
                        RecognitionStatKeys.TRUE_DEMON_LORD
                )
                        ? evil.trueDemonLordModifier()
                        : 0.0D;

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.CIVILIAN_KILLS
                ),
                evil.civilianKills()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.PASSIVE_BABY_KILLS
                ),
                evil.passiveBabyKills()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.OWNED_COMPANION_KILLS
                ),
                evil.ownedCompanionKills()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.OWNED_SUBORDINATE_KILLS
                ),
                evil.ownedSubordinateKills()
        );

        points += contribution(
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .BENEVOLENT_BOSS_TYPES_KILLED
                ),
                evil.benevolentBossTypesKilled()
        );

        return sanitizeScore(points);
    }

    private static double calculateOrder(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Order order =
                balance.order();

        int recognizedSubordinates =
                Math.max(
                        data.getCounter(
                                RecognitionStatKeys
                                        .CURRENT_SUBORDINATES
                        ),
                        data.getCounter(
                                RecognitionStatKeys
                                        .HIGHEST_SUBORDINATES
                        )
                );

        double points =
                contribution(
                        recognizedSubordinates,
                        order.subordinateRosterPrimary()
                );

        points += contribution(
                recognizedSubordinates,
                order.subordinateRosterEstablished()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys
                                .SUBORDINATE_ASSISTED_MAJOR_VICTORIES
                ),
                order.subordinateAssistedMajorVictories()
        );

        points += contribution(
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .UNIQUE_SUBORDINATES_EMPOWERED
                ),
                order.uniqueSubordinatesEmpowered()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys.MASS_GRANTS_PERFORMED
                ),
                order.massGrantsPerformed()
        );

        points += contribution(
                data.getCounter(
                        RecognitionStatKeys
                                .GLOBAL_TAKE_BACKS_PERFORMED
                ),
                order.globalTakeBacksPerformed()
        );

        return sanitizeScore(points);
    }

    private static double calculateFreedom(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Freedom freedom =
                balance.freedom();

        double points =
                contribution(
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .SOLO_MAJOR_ENEMY_TYPES_DEFEATED
                        ),
                        freedom.soloMajorEnemyTypesDefeated()
                );

        points += contribution(
                data.getUniqueValueCount(
                        RecognitionStatKeys.DISCOVERY_MILESTONES
                ),
                freedom.discoveryMilestones()
        );

        return sanitizeScore(points);
    }

    private static double calculateMastery(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Mastery mastery =
                balance.mastery();

        int masteredSkills =
                data.getCounter(
                        RecognitionStatKeys.MASTERED_SKILLS
                );

        int masteredCategories =
                data.getCounter(
                        RecognitionStatKeys
                                .MASTERED_SKILL_CATEGORIES
                );

        int distinctMajorEnemies =
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .MAJOR_ENEMY_TYPES_DEFEATED
                );

        double highestEp =
                data.getMeasurement(
                        RecognitionStatKeys.HIGHEST_EP
                );

        double points =
                calculateSkillMasteryContribution(
                        masteredSkills,
                        mastery
                );

        points += contribution(
                masteredCategories,
                mastery.masteredSkillCategories()
        );

        points += epContribution(
                highestEp,
                mastery.highestEp()
        );

        points += contribution(
                distinctMajorEnemies,
                mastery.majorEnemyTypesDefeated()
        );

        return sanitizeScore(points);
    }

    private static double calculateDiscovery(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        return contribution(
                data.getUniqueValueCount(
                        RecognitionStatKeys.DISCOVERY_MILESTONES
                ),
                balance.discovery().milestones()
        );
    }

    private static Map<RecognitionPath, Double>
    calculateRawPathScores(
            RecognitionData data,
            RecognitionPathComponents components,
            RecognitionDimensions dimensions,
            RecognitionBalanceSnapshot balance
    ) {
        EnumMap<RecognitionPath, Double> scores =
                new EnumMap<>(
                        RecognitionPath.class
                );

        RecognitionBalanceSnapshot.RawPaths rawBalance =
                balance.rawPaths();

        double good =
                components.good();

        double evil =
                components.evil();

        double lawful =
                components.lawful();

        double chaotic =
                components.chaotic();

        double neutralMorality =
                components.neutralMorality();

        /*
         * Rules version 2 uses only actively demonstrated balance between
         * Order and Freedom. Passive posture remains display-only.
         */
        double neutralBehaviour =
                components.activeNeutralBehaviour();

        double darkHeroContradiction =
                data.getFlag(
                        RecognitionStatKeys.TRUE_DEMON_LORD
                ) && good > evil
                        ? Math.min(
                        rawBalance.contradictionMaximum(),
                        good
                        * rawBalance.contradictionFactor()
                )
                        : 0.0D;

        double fallenHeroContradiction =
                data.getFlag(
                        RecognitionStatKeys.TRUE_HERO
                ) && evil > good
                        ? Math.min(
                        rawBalance.contradictionMaximum(),
                        evil
                        * rawBalance.contradictionFactor()
                )
                        : 0.0D;

        scores.put(
                RecognitionPath.LAWFUL_GOOD,
                combineRaw(
                        good,
                        lawful,
                        rawBalance
                )
        );

        scores.put(
                RecognitionPath.NEUTRAL_GOOD,
                combineRaw(
                        good,
                        neutralBehaviour,
                        rawBalance
                )
        );

        scores.put(
                RecognitionPath.CHAOTIC_GOOD,
                combineRaw(
                        good,
                        chaotic,
                        rawBalance
                ) + darkHeroContradiction
        );

        scores.put(
                RecognitionPath.LAWFUL_NEUTRAL,
                combineRaw(
                        neutralMorality,
                        lawful,
                        rawBalance
                )
        );

        double trueNeutralDiscoverySupport =
                Math.min(
                        rawBalance
                                .trueNeutralDiscoveryMaximum(),
                        dimensions.discovery()
                                * rawBalance
                                .trueNeutralDiscoveryFactor()
                );

        scores.put(
                RecognitionPath.TRUE_NEUTRAL,
                combineRaw(
                        neutralMorality,
                        neutralBehaviour,
                        rawBalance
                ) + trueNeutralDiscoverySupport
        );

        double chaoticNeutralDiscoverySupport =
                Math.min(
                        rawBalance
                                .chaoticNeutralDiscoveryMaximum(),
                        dimensions.discovery()
                                * rawBalance
                                .chaoticNeutralDiscoveryFactor()
                );

        scores.put(
                RecognitionPath.CHAOTIC_NEUTRAL,
                combineRaw(
                        neutralMorality,
                        chaotic,
                        rawBalance
                ) + chaoticNeutralDiscoverySupport
        );

        scores.put(
                RecognitionPath.LAWFUL_EVIL,
                combineRaw(
                        evil,
                        lawful,
                        rawBalance
                )
        );

        scores.put(
                RecognitionPath.NEUTRAL_EVIL,
                combineRaw(
                        evil,
                        neutralBehaviour,
                        rawBalance
                )
        );

        scores.put(
                RecognitionPath.CHAOTIC_EVIL,
                combineRaw(
                        evil,
                        chaotic,
                        rawBalance
                ) + fallenHeroContradiction
        );

        return scores;
    }

    /**
     * Removes paths that do not have meaningful active evidence on both axes.
     *
     * <p>This gate is applied before and after identity distribution. Identity
     * can strengthen an established direction, but it cannot fabricate a
     * missing morality or temperament component.</p>
     */
    private static Map<RecognitionPath, Double>
    applyComponentEvidenceGate(
            RecognitionPathComponents components,
            Map<RecognitionPath, Double> scores,
            RecognitionBalanceSnapshot balance
    ) {
        EnumMap<RecognitionPath, Double> gatedScores =
                new EnumMap<>(
                        RecognitionPath.class
                );

        RecognitionComponentQualificationRules rules =
                RecognitionComponentQualificationRules
                        .from(
                                balance == null
                                        ? null
                                        : balance.selection()
                        );

        RecognitionPathComponents safeComponents =
                components == null
                        ? RecognitionPathComponents.empty()
                        : components;

        for (RecognitionPath path :
                RecognitionPath.values()) {

            double score =
                    getScore(
                            scores,
                            path
                    );

            if (!safeComponents.hasEstablishedEvidence(
                    path,
                    rules
            )) {
                score = 0.0D;
            }

            gatedScores.put(
                    path,
                    score
            );
        }

        return gatedScores;
    }

    private static Map<RecognitionPath, Double>
    calculateIdentityBoosts(
            Map<RecognitionPath, Double> rawScores,
            double identityStrength,
            RecognitionBalanceSnapshot balance
    ) {
        EnumMap<RecognitionPath, Double> boosts =
                new EnumMap<>(
                        RecognitionPath.class
                );

        RecognitionBalanceSnapshot.IdentityDistribution
                identityBalance =
                balance.identityDistribution();

        double safeIdentity =
                sanitizeScore(
                        identityStrength
                );

        double universalBoost =
                safeIdentity
                        * identityBalance.universalShare();

        for (RecognitionPath path :
                RecognitionPath.values()) {

            boosts.put(
                    path,
                    universalBoost
            );
        }

        double focusedPool =
                safeIdentity
                        * identityBalance.focusedShare();

        if (focusedPool <= 0.0D) {
            return boosts;
        }

        List<RecognitionPath> rankedPaths =
                rankPaths(
                        rawScores
                );

        if (rankedPaths.isEmpty()) {
            return boosts;
        }

        RecognitionPath primaryPath =
                rankedPaths.get(0);

        RecognitionPath secondaryPath =
                rankedPaths.size() > 1
                        ? rankedPaths.get(1)
                        : null;

        double primaryRaw =
                getScore(
                        rawScores,
                        primaryPath
                );

        double secondaryRaw =
                getScore(
                        rawScores,
                        secondaryPath
                );

        if (primaryRaw
                < identityBalance.minimumFocusAffinity()) {

            return boosts;
        }

        boolean secondaryQualifies =
                secondaryPath != null
                        && secondaryRaw
                        >= identityBalance.minimumFocusAffinity()
                        && secondaryRaw
                        >= primaryRaw
                        * identityBalance.secondaryFocusRatio();

        if (!secondaryQualifies) {
            boosts.put(
                    primaryPath,
                    boosts.get(primaryPath)
                            + focusedPool
            );

            return boosts;
        }

        double combinedRaw =
                primaryRaw
                        + secondaryRaw;

        if (combinedRaw <= 0.0D) {
            return boosts;
        }

        boosts.put(
                primaryPath,
                boosts.get(primaryPath)
                        + focusedPool
                        * primaryRaw
                        / combinedRaw
        );

        boosts.put(
                secondaryPath,
                boosts.get(secondaryPath)
                        + focusedPool
                        * secondaryRaw
                        / combinedRaw
        );

        return boosts;
    }

    private static Map<RecognitionPath, Double> combineScores(
            Map<RecognitionPath, Double> rawScores,
            Map<RecognitionPath, Double> identityBoosts
    ) {
        EnumMap<RecognitionPath, Double> result =
                new EnumMap<>(
                        RecognitionPath.class
                );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            result.put(
                    path,
                    sanitizeScore(
                            getScore(
                                    rawScores,
                                    path
                            ) + getScore(
                                    identityBoosts,
                                    path
                            )
                    )
            );
        }

        return result;
    }

    private static List<RecognitionPath> rankPaths(
            Map<RecognitionPath, Double> scores
    ) {
        List<RecognitionPath> rankedPaths =
                new ArrayList<>(
                        List.of(
                                RecognitionPath.values()
                        )
                );

        rankedPaths.sort((first, second) -> {
            int scoreComparison =
                    Double.compare(
                            getScore(
                                    scores,
                                    second
                            ),
                            getScore(
                                    scores,
                                    first
                            )
                    );

            if (scoreComparison != 0) {
                return scoreComparison;
            }

            return Integer.compare(
                    first.ordinal(),
                    second.ordinal()
            );
        });

        return rankedPaths;
    }

    private static double combineRaw(
            double moralComponent,
            double temperamentComponent,
            RecognitionBalanceSnapshot.RawPaths rawBalance
    ) {
        double score =
                moralComponent
                        * rawBalance.moralWeight()
                        + temperamentComponent
                        * rawBalance.temperamentWeight()
                        + Math.min(
                        moralComponent,
                        temperamentComponent
                ) * rawBalance.overlapWeight();

        return sanitizeScore(score);
    }

    private static double calculateSkillMasteryContribution(
            int masteredSkills,
            RecognitionBalanceSnapshot.Mastery mastery
    ) {
        int remainingSkills =
                Math.max(
                        0,
                        masteredSkills
                );

        double contribution =
                0.0D;

        for (RecognitionBalanceSnapshot.SkillTier tier :
                mastery.skillTiers()) {

            if (remainingSkills <= 0) {
                break;
            }

            int appliedEntries =
                    Math.min(
                            remainingSkills,
                            tier.entries()
                    );

            contribution +=
                    appliedEntries
                            * tier.pointsPerEntry();

            remainingSkills -=
                    appliedEntries;
        }

        return Math.min(
                mastery.skillMaximum(),
                sanitizeScore(
                        contribution
                )
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
                count
                        * contribution.pointsPerEntry()
        );
    }

    private static double epContribution(
            double ep,
            RecognitionBalanceSnapshot.EpContribution contribution
    ) {
        if (!Double.isFinite(ep)
                || ep <= 0.0D
                || contribution == null
                || contribution.epPerUnit() <= 0.0D
                || contribution.pointsPerUnit() <= 0.0D
                || contribution.maximum() <= 0.0D) {

            return 0.0D;
        }

        return Math.min(
                contribution.maximum(),
                ep
                        / contribution.epPerUnit()
                        * contribution.pointsPerUnit()
        );
    }

    private static double getScore(
            Map<RecognitionPath, Double> scores,
            RecognitionPath path
    ) {
        if (scores == null
                || path == null) {

            return 0.0D;
        }

        Double score =
                scores.get(path);

        return score == null
                ? 0.0D
                : sanitizeScore(
                score
        );
    }

    private static double sanitizeScore(
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0D) {

            return 0.0D;
        }

        return value;
    }
}