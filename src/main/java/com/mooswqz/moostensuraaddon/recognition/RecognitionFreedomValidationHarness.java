package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Permanent debug-only validation companion for the datapack-driven Freedom
 * expansion.
 *
 * <p>The existing recognition validator remains the authority for refined
 * Pure-path and required-cross reachability. This companion adds the new
 * self-reliance source to an otherwise equivalent deterministic coarse grid,
 * validates its persistence semantics, and compares the practical Freedom
 * ceiling with Order. Every profile uses temporary {@link RecognitionData};
 * no player attachment is read or written.</p>
 */
public final class RecognitionFreedomValidationHarness {

    private static final double[] COARSE_LEVELS = {
            0.0D,
            1.0D / 3.0D,
            2.0D / 3.0D,
            1.0D
    };

    private static final int BASE_CHANNEL_COUNT = 6;
    private static final int BASE_PROFILES_PER_AWAKENING =
            1 << (BASE_CHANNEL_COUNT * 2);

    private static final int MAX_SYNTHETIC_ENTRIES =
            512;

    private static final Object CACHE_LOCK =
            new Object();

    private static volatile CachedReport cache;

    private RecognitionFreedomValidationHarness() {
    }

    public static Report validate() {
        RecognitionBalanceManager.State balanceState =
                RecognitionBalanceManager.getState();

        RecognitionIndependenceMilestoneManager.State milestoneState =
                RecognitionIndependenceMilestoneManager
                        .getState();

        CachedReport current = cache;

        if (current != null
                && current.matches(
                balanceState.revision(),
                milestoneState.revision(),
                milestoneState.fingerprint()
        )) {
            return current.report();
        }

        synchronized (CACHE_LOCK) {
            current = cache;

            if (current != null
                    && current.matches(
                    balanceState.revision(),
                    milestoneState.revision(),
                    milestoneState.fingerprint()
            )) {
                return current.report();
            }

            Report report = buildStableReport();

            cache = new CachedReport(
                    report.balanceRevision(),
                    report.milestoneRevision(),
                    report.milestoneFingerprint(),
                    report
            );

            return report;
        }
    }

    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            cache = null;
        }
    }

    private static Report buildStableReport() {
        for (int attempt = 0; attempt < 2; attempt++) {
            RecognitionBalanceManager.State balanceBefore =
                    RecognitionBalanceManager.getState();

            RecognitionIndependenceMilestoneManager.State milestonesBefore =
                    RecognitionIndependenceMilestoneManager
                            .getState();

            Report report = buildReport(
                    balanceBefore,
                    milestonesBefore
            );

            RecognitionBalanceManager.State balanceAfter =
                    RecognitionBalanceManager.getState();

            RecognitionIndependenceMilestoneManager.State milestonesAfter =
                    RecognitionIndependenceMilestoneManager
                            .getState();

            if (balanceBefore.revision()
                    == balanceAfter.revision()
                    && milestonesBefore.revision()
                    == milestonesAfter.revision()
                    && milestonesBefore.fingerprint()
                    .equals(
                            milestonesAfter.fingerprint()
                    )) {
                return report;
            }
        }

        return buildReport(
                RecognitionBalanceManager.getState(),
                RecognitionIndependenceMilestoneManager
                        .getState()
        );
    }

    private static Report buildReport(
            RecognitionBalanceManager.State balanceState,
            RecognitionIndependenceMilestoneManager.State milestoneState
    ) {
        RecognitionBalanceSnapshot balance =
                balanceState.snapshot();

        RecognitionBalanceValidationHarness.Report mainReport =
                RecognitionBalanceValidationHarness
                        .validate();

        List<Check> checks =
                new ArrayList<>();

        List<String> warnings =
                new ArrayList<>();

        RecognitionData empty =
                new RecognitionData();

        double emptyScore =
                RecognitionIndependenceMilestoneManager
                        .calculateScore(
                                empty
                        );

        checks.add(
                new Check(
                        "Empty self-reliance score",
                        approximatelyEqual(
                                emptyScore,
                                0.0D
                        ),
                        format(emptyScore)
                )
        );

        RecognitionData allMilestones =
                new RecognitionData();

        for (RecognitionIndependenceMilestoneManager.Milestone milestone :
                milestoneState.milestones()) {
            allMilestones.addUniqueValue(
                    RecognitionStatKeys
                            .INDEPENDENCE_MILESTONES,
                    milestone.id()
                            .toString()
            );
        }

        double allMilestoneScore =
                RecognitionIndependenceMilestoneManager
                        .calculateScore(
                                allMilestones
                        );

        checks.add(
                new Check(
                        "Configured milestone total",
                        approximatelyEqual(
                                allMilestoneScore,
                                milestoneState.maximumScore()
                        ),
                        format(allMilestoneScore)
                                + " / "
                                + format(
                                milestoneState.maximumScore()
                        )
                )
        );

        boolean duplicateSafe =
                true;

        if (!milestoneState.milestones()
                .isEmpty()) {
            RecognitionIndependenceMilestoneManager.Milestone first =
                    milestoneState.milestones()
                            .get(0);

            int before =
                    allMilestones.getUniqueValueCount(
                            RecognitionStatKeys
                                    .INDEPENDENCE_MILESTONES
                    );

            boolean addedAgain =
                    allMilestones.addUniqueValue(
                            RecognitionStatKeys
                                    .INDEPENDENCE_MILESTONES,
                            first.id()
                                    .toString()
                    );

            int after =
                    allMilestones.getUniqueValueCount(
                            RecognitionStatKeys
                                    .INDEPENDENCE_MILESTONES
                    );

            duplicateSafe =
                    !addedAgain
                            && before == after;
        }

        checks.add(
                new Check(
                        "Duplicate semantic IDs are ignored",
                        duplicateSafe,
                        duplicateSafe
                                ? "one stored value per milestone"
                                : "duplicate storage changed"
                )
        );

        RecognitionData inactiveOnly =
                new RecognitionData();

        inactiveOnly.addUniqueValue(
                RecognitionStatKeys
                        .INDEPENDENCE_MILESTONES,
                findInactiveMilestoneId(
                        milestoneState
                )
        );

        double inactiveScore =
                RecognitionIndependenceMilestoneManager
                        .calculateScore(
                                inactiveOnly
                        );

        checks.add(
                new Check(
                        "Inactive historical IDs do not score",
                        approximatelyEqual(
                                inactiveScore,
                                0.0D
                        ),
                        format(inactiveScore)
                )
        );

        RecognitionEvaluation independenceOnlyEvaluation =
                RecognitionPathEvaluator.evaluate(
                        allMilestones
                );

        checks.add(
                new Check(
                        "Self-reliance alone cannot establish recognition",
                        independenceOnlyEvaluation
                                .getSelection()
                                .isEmpty(),
                        selectionDescription(
                                independenceOnlyEvaluation
                        )
                )
        );

        double legacyFreedomCeiling =
                balance.freedom()
                        .soloMajorEnemyTypesDefeated()
                        .maximum()
                        + balance.freedom()
                        .discoveryMilestones()
                        .maximum();

        double configuredFreedomCeiling =
                legacyFreedomCeiling
                        + milestoneState.maximumScore();

        RecognitionEvaluation freedomCeilingEvaluation =
                RecognitionPathEvaluator.evaluate(
                        createFreedomCeilingData(
                                balance,
                                milestoneState
                        )
                );

        double evaluatedFreedomCeiling =
                freedomCeilingEvaluation
                        .getDimensions()
                        .freedom();

        checks.add(
                new Check(
                        "Production Freedom ceiling includes self-reliance",
                        approximatelyEqual(
                                evaluatedFreedomCeiling,
                                configuredFreedomCeiling
                        ),
                        format(evaluatedFreedomCeiling)
                                + " / expected "
                                + format(configuredFreedomCeiling)
                )
        );

        double orderCeiling =
                evaluateOrderCeiling(
                        balance
                );

        double freedomOrderGap =
                orderCeiling
                        - evaluatedFreedomCeiling;

        checks.add(
                new Check(
                        "Freedom remains near the Order ceiling",
                        freedomOrderGap <= 10.0D,
                        "Freedom "
                                + format(evaluatedFreedomCeiling)
                                + ", Order "
                                + format(orderCeiling)
                                + ", gap "
                                + format(freedomOrderGap)
                )
        );

        ExpandedGridResult expandedGrid =
                runExpandedCoarseGrid(
                        balance,
                        milestoneState
                );

        checks.add(
                new Check(
                        "Expanded coarse grid exercises self-reliance",
                        milestoneState.milestones().isEmpty()
                                || expandedGrid.freedomGainProfiles() > 0,
                        expandedGrid.freedomGainProfiles()
                                + " profile(s) gained Freedom"
                )
        );

        checks.add(
                new Check(
                        "Self-reliance creates new chaotic selections",
                        milestoneState.milestones().isEmpty()
                                || expandedGrid.newChaoticSelectionProfiles() > 0,
                        expandedGrid.newChaoticSelectionProfiles()
                                + " profile(s) gained a Chaotic selection"
                )
        );

        checks.add(
                new Check(
                        "All Chaotic Pure paths appear in the expanded grid",
                        expandedGrid.chaoticPurePaths()
                                == expandedGrid.totalChaoticPurePaths(),
                        expandedGrid.chaoticPurePaths()
                                + " / "
                                + expandedGrid.totalChaoticPurePaths()
                )
        );

        checks.add(
                new Check(
                        "Expanded grid contains Chaotic adjacent crossings",
                        expandedGrid.chaoticAdjacentPairs() > 0,
                        expandedGrid.chaoticAdjacentPairs()
                                + " / "
                                + expandedGrid.totalChaoticAdjacentPairs()
                )
        );

        checks.add(
                new Check(
                        "Refined Pure-path reachability remains complete",
                        mainReport.exactPurePaths()
                                == mainReport.totalPurePaths(),
                        mainReport.exactPurePaths()
                                + " / "
                                + mainReport.totalPurePaths()
                )
        );

        checks.add(
                new Check(
                        "Refined required crossings remain complete",
                        mainReport.exactAdjacentPairs()
                                == mainReport.totalAdjacentPairs(),
                        mainReport.exactAdjacentPairs()
                                + " / "
                                + mainReport.totalAdjacentPairs()
                )
        );

        if (milestoneState.milestones()
                .isEmpty()) {
            warnings.add(
                    "No independence milestones are currently configured; "
                            + "the expanded grid therefore matches the legacy Freedom model."
            );
        }

        if (milestoneState.maximumScore()
                > 30.0D) {
            warnings.add(
                    "Configured self-reliance contributes "
                            + format(
                            milestoneState.maximumScore()
                    )
                            + " points; review datapack balance before release."
            );
        }

        if (freedomOrderGap > 10.0D) {
            warnings.add(
                    "Freedom remains more than 10 points below Order at their practical ceilings."
            );
        }

        if (expandedGrid.exactAdjacentPairs()
                < expandedGrid.totalAdjacentPairs()) {
            warnings.add(
                    "The expanded observation grid directly selects "
                            + expandedGrid.exactAdjacentPairs()
                            + " of "
                            + expandedGrid.totalAdjacentPairs()
                            + " required adjacent crossings. This is an observational grid, not the targeted refined search; the permanent production validator remains authoritative."
            );
        }

        boolean passed =
                checks.stream()
                        .allMatch(
                                Check::passed
                        );

        return new Report(
                passed,
                balance.sourceId(),
                balanceState.revision(),
                milestoneState.revision(),
                milestoneState.fingerprint(),
                milestoneState.sourceFileCount(),
                milestoneState.milestones()
                        .size(),
                milestoneState.maximumScore(),
                legacyFreedomCeiling,
                evaluatedFreedomCeiling,
                orderCeiling,
                mainReport.exactPureCoarse(),
                expandedGrid.exactPurePaths(),
                mainReport.exactPurePaths(),
                mainReport.totalPurePaths(),
                mainReport.exactAdjacentCoarse(),
                expandedGrid.exactAdjacentPairs(),
                mainReport.exactAdjacentPairs(),
                mainReport.totalAdjacentPairs(),
                expandedGrid.expandedProfilesEvaluated(),
                expandedGrid.comparisonProfilesEvaluated(),
                expandedGrid.freedomGainProfiles(),
                expandedGrid.newChaoticSelectionProfiles(),
                expandedGrid.chaoticPurePaths(),
                expandedGrid.totalChaoticPurePaths(),
                expandedGrid.chaoticAdjacentPairs(),
                expandedGrid.totalChaoticAdjacentPairs(),
                checks,
                warnings
        );
    }

    private static String findInactiveMilestoneId(
            RecognitionIndependenceMilestoneManager.State state
    ) {
        int suffix = 0;

        while (suffix < Integer.MAX_VALUE) {
            String candidate =
                    "moostensuraaddon_validation:inactive_probe_"
                            + suffix;

            boolean configured = false;

            for (RecognitionIndependenceMilestoneManager.Milestone milestone :
                    state.milestones()) {

                if (milestone.id()
                        .toString()
                        .equals(candidate)) {

                    configured = true;
                    break;
                }
            }

            if (!configured) {
                return candidate;
            }

            suffix++;
        }

        throw new IllegalStateException(
                "Unable to reserve an inactive Freedom validation milestone ID."
        );
    }

    private static ExpandedGridResult runExpandedCoarseGrid(
            RecognitionBalanceSnapshot balance,
            RecognitionIndependenceMilestoneManager.State milestoneState
    ) {
        Set<RecognitionPath> purePaths =
                EnumSet.noneOf(
                        RecognitionPath.class
                );

        Set<OrderedPair> exactPairs =
                new LinkedHashSet<>();

        int expandedProfiles =
                0;

        int comparisonProfiles =
                0;

        int freedomGainProfiles =
                0;

        int newChaoticSelectionProfiles =
                0;

        for (Awakening awakening :
                Awakening.values()) {
            for (int code = 0;
                 code < BASE_PROFILES_PER_AWAKENING;
                 code++) {

                CoarseProfile baseProfile =
                        decodeBaseProfile(
                                awakening,
                                code
                        );

                RecognitionEvaluation legacyEvaluation =
                        RecognitionPathEvaluator.evaluate(
                                createCoarseData(
                                        baseProfile.withSelfReliance(
                                                0.0D
                                        ),
                                        balance,
                                        milestoneState
                                )
                        );

                comparisonProfiles++;

                for (double selfRelianceLevel :
                        COARSE_LEVELS) {
                    CoarseProfile expandedProfile =
                            baseProfile.withSelfReliance(
                                    selfRelianceLevel
                            );

                    RecognitionEvaluation expandedEvaluation =
                            selfRelianceLevel <= 0.0D
                                    ? legacyEvaluation
                                    : RecognitionPathEvaluator.evaluate(
                                    createCoarseData(
                                            expandedProfile,
                                            balance,
                                            milestoneState
                                    )
                            );

                    expandedProfiles++;

                    RecognitionPathSelection selection =
                            expandedEvaluation.getSelection()
                                    .orElse(null);

                    if (selection != null) {
                        if (selection.pure()) {
                            purePaths.add(
                                    selection.primaryPath()
                            );
                        } else if (selection.secondaryPath()
                                != null) {
                            exactPairs.add(
                                    new OrderedPair(
                                            selection.primaryPath(),
                                            selection.secondaryPath()
                                    )
                            );
                        }
                    }

                    if (selfRelianceLevel <= 0.0D) {
                        continue;
                    }

                    if (expandedEvaluation.getDimensions()
                            .freedom()
                            > legacyEvaluation.getDimensions()
                            .freedom() + 0.000_001D) {
                        freedomGainProfiles++;
                    }

                    if (containsChaoticPath(selection)
                            && !containsChaoticPath(
                            legacyEvaluation.getSelection()
                                    .orElse(null)
                    )) {
                        newChaoticSelectionProfiles++;
                    }
                }
            }
        }

        int adjacent =
                0;

        int totalAdjacent =
                0;

        int chaoticAdjacent =
                0;

        int totalChaoticAdjacent =
                0;

        for (RecognitionPath primary :
                RecognitionPath.values()) {
            for (RecognitionPath secondary :
                    RecognitionPath.values()) {
                if (primary == secondary) {
                    continue;
                }

                if (!RecognitionBalanceValidationHarness
                        .classifyPair(
                                primary,
                                secondary
                        )
                        .required()) {
                    continue;
                }

                totalAdjacent++;

                boolean chaoticPair =
                        primary.getTemperament()
                                == RecognitionPath.Temperament.CHAOTIC
                                || secondary.getTemperament()
                                == RecognitionPath.Temperament.CHAOTIC;

                if (chaoticPair) {
                    totalChaoticAdjacent++;
                }

                if (exactPairs.contains(
                        new OrderedPair(
                                primary,
                                secondary
                        )
                )) {
                    adjacent++;

                    if (chaoticPair) {
                        chaoticAdjacent++;
                    }
                }
            }
        }

        int chaoticPure =
                0;

        int totalChaoticPure =
                0;

        for (RecognitionPath path :
                RecognitionPath.values()) {
            if (path.getTemperament()
                    != RecognitionPath.Temperament.CHAOTIC) {
                continue;
            }

            totalChaoticPure++;

            if (purePaths.contains(path)) {
                chaoticPure++;
            }
        }

        return new ExpandedGridResult(
                expandedProfiles,
                comparisonProfiles,
                freedomGainProfiles,
                newChaoticSelectionProfiles,
                purePaths.size(),
                chaoticPure,
                totalChaoticPure,
                adjacent,
                totalAdjacent,
                chaoticAdjacent,
                totalChaoticAdjacent
        );
    }

    private static boolean containsChaoticPath(
            RecognitionPathSelection selection
    ) {
        if (selection == null) {
            return false;
        }

        if (selection.primaryPath()
                .getTemperament()
                == RecognitionPath.Temperament.CHAOTIC) {
            return true;
        }

        return selection.secondaryPath() != null
                && selection.secondaryPath()
                .getTemperament()
                == RecognitionPath.Temperament.CHAOTIC;
    }

    private static CoarseProfile decodeBaseProfile(
            Awakening awakening,
            int code
    ) {
        int value =
                code;

        double[] channels =
                new double[BASE_CHANNEL_COUNT];

        for (int index = 0;
             index < BASE_CHANNEL_COUNT;
             index++) {
            channels[index] =
                    COARSE_LEVELS[
                            value & 3
                            ];

            value >>>= 2;
        }

        return new CoarseProfile(
                awakening,
                channels[0],
                channels[1],
                channels[2],
                channels[3],
                channels[4],
                channels[5],
                0.0D
        );
    }

    private static RecognitionData createCoarseData(
            CoarseProfile profile,
            RecognitionBalanceSnapshot balance,
            RecognitionIndependenceMilestoneManager.State milestoneState
    ) {
        RecognitionData data =
                new RecognitionData();

        data.setFlag(
                RecognitionStatKeys.TRUE_HERO,
                profile.awakening()
                        == Awakening.TRUE_HERO
        );

        data.setFlag(
                RecognitionStatKeys.TRUE_DEMON_LORD,
                profile.awakening()
                        == Awakening.TRUE_DEMON_LORD
        );

        putCounter(
                data,
                RecognitionStatKeys.RAID_VICTORIES,
                balance.good()
                        .raidVictories(),
                profile.good()
        );

        putCounter(
                data,
                RecognitionStatKeys.VILLAGERS_CURED,
                balance.good()
                        .villagersCured(),
                profile.good()
        );

        putCounter(
                data,
                RecognitionStatKeys.CIVILIANS_DEFENDED,
                balance.good()
                        .civiliansDefended(),
                profile.good()
        );

        putCollection(
                data,
                RecognitionStatKeys
                        .MALEVOLENT_BOSS_TYPES_DEFEATED,
                "moostensuraaddon:synthetic_good_",
                entriesFor(
                        balance.good()
                                .malevolentBossTypesDefeated(),
                        profile.good()
                )
        );

        putCounter(
                data,
                RecognitionStatKeys.CIVILIAN_KILLS,
                balance.evil()
                        .civilianKills(),
                profile.evil()
        );

        putCounter(
                data,
                RecognitionStatKeys.PASSIVE_BABY_KILLS,
                balance.evil()
                        .passiveBabyKills(),
                profile.evil()
        );

        putCounter(
                data,
                RecognitionStatKeys.OWNED_COMPANION_KILLS,
                balance.evil()
                        .ownedCompanionKills(),
                profile.evil()
        );

        putCounter(
                data,
                RecognitionStatKeys.OWNED_SUBORDINATE_KILLS,
                balance.evil()
                        .ownedSubordinateKills(),
                profile.evil()
        );

        putCollection(
                data,
                RecognitionStatKeys
                        .BENEVOLENT_BOSS_TYPES_KILLED,
                "moostensuraaddon:synthetic_evil_",
                entriesFor(
                        balance.evil()
                                .benevolentBossTypesKilled(),
                        profile.evil()
                )
        );

        int subordinates =
                Math.max(
                        entriesFor(
                                balance.order()
                                        .subordinateRosterPrimary(),
                                profile.order()
                        ),
                        entriesFor(
                                balance.order()
                                        .subordinateRosterEstablished(),
                                profile.order()
                        )
                );

        data.setCounter(
                RecognitionStatKeys
                        .CURRENT_SUBORDINATES,
                subordinates
        );

        data.setCounter(
                RecognitionStatKeys
                        .HIGHEST_SUBORDINATES,
                subordinates
        );

        putCounter(
                data,
                RecognitionStatKeys
                        .SUBORDINATE_ASSISTED_MAJOR_VICTORIES,
                balance.order()
                        .subordinateAssistedMajorVictories(),
                profile.order()
        );

        putCollection(
                data,
                RecognitionStatKeys
                        .UNIQUE_SUBORDINATES_EMPOWERED,
                "moostensuraaddon:synthetic_subordinate_",
                entriesFor(
                        balance.order()
                                .uniqueSubordinatesEmpowered(),
                        profile.order()
                )
        );

        putCounter(
                data,
                RecognitionStatKeys.MASS_GRANTS_PERFORMED,
                balance.order()
                        .massGrantsPerformed(),
                profile.order()
        );

        putCounter(
                data,
                RecognitionStatKeys
                        .GLOBAL_TAKE_BACKS_PERFORMED,
                balance.order()
                        .globalTakeBacksPerformed(),
                profile.order()
        );

        int soloMajor =
                entriesFor(
                        balance.freedom()
                                .soloMajorEnemyTypesDefeated(),
                        profile.independence()
                );

        int masteryMajor =
                Math.max(
                        entriesFor(
                                balance.mastery()
                                        .majorEnemyTypesDefeated(),
                                profile.mastery()
                        ),
                        entriesFor(
                                balance.identityStrength()
                                        .majorEnemyTypes(),
                                profile.mastery()
                        )
                );

        int majorEnemies =
                Math.max(
                        soloMajor,
                        masteryMajor
                );

        putCollection(
                data,
                RecognitionStatKeys
                        .MAJOR_ENEMY_TYPES_DEFEATED,
                "moostensuraaddon:synthetic_major_",
                majorEnemies
        );

        putCollection(
                data,
                RecognitionStatKeys
                        .SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
                "moostensuraaddon:synthetic_major_",
                soloMajor
        );

        putMilestonesByLevel(
                data,
                milestoneState,
                profile.selfReliance()
        );

        int discoveryMilestones =
                Math.max(
                        entriesFor(
                                balance.freedom()
                                        .discoveryMilestones(),
                                profile.exploration()
                        ),
                        entriesFor(
                                balance.discovery()
                                        .milestones(),
                                profile.exploration()
                        )
                );

        putCollection(
                data,
                RecognitionStatKeys.DISCOVERY_MILESTONES,
                "moostensuraaddon:synthetic_discovery_",
                discoveryMilestones
        );

        int totalSkillEntries =
                balance.mastery()
                        .skillTiers()
                        .stream()
                        .mapToInt(
                                RecognitionBalanceSnapshot
                                        .SkillTier::entries
                        )
                        .sum();

        data.setCounter(
                RecognitionStatKeys.MASTERED_SKILLS,
                scaledCount(
                        totalSkillEntries,
                        profile.mastery()
                )
        );

        putCounter(
                data,
                RecognitionStatKeys
                        .MASTERED_SKILL_CATEGORIES,
                balance.mastery()
                        .masteredSkillCategories(),
                profile.mastery()
        );

        double ep =
                epForLevel(
                        balance.mastery()
                                .highestEp(),
                        profile.mastery()
                );

        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_EP,
                ep
        );

        data.setMeasurement(
                RecognitionStatKeys.CURRENT_EP,
                ep
        );

        return data;
    }

    private static RecognitionData createFreedomCeilingData(
            RecognitionBalanceSnapshot balance,
            RecognitionIndependenceMilestoneManager.State milestoneState
    ) {
        RecognitionData data =
                new RecognitionData();

        putCollection(
                data,
                RecognitionStatKeys
                        .SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
                "moostensuraaddon:ceiling_solo_",
                entriesFor(
                        balance.freedom()
                                .soloMajorEnemyTypesDefeated(),
                        1.0D
                )
        );

        putCollection(
                data,
                RecognitionStatKeys.DISCOVERY_MILESTONES,
                "moostensuraaddon:ceiling_discovery_",
                entriesFor(
                        balance.freedom()
                                .discoveryMilestones(),
                        1.0D
                )
        );

        for (RecognitionIndependenceMilestoneManager.Milestone milestone :
                milestoneState.milestones()) {
            data.addUniqueValue(
                    RecognitionStatKeys
                            .INDEPENDENCE_MILESTONES,
                    milestone.id()
                            .toString()
            );
        }

        return data;
    }

    private static double evaluateOrderCeiling(
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionData data =
                new RecognitionData();

        int subordinates =
                Math.max(
                        entriesFor(
                                balance.order()
                                        .subordinateRosterPrimary(),
                                1.0D
                        ),
                        entriesFor(
                                balance.order()
                                        .subordinateRosterEstablished(),
                                1.0D
                        )
                );

        data.setCounter(
                RecognitionStatKeys.CURRENT_SUBORDINATES,
                subordinates
        );

        data.setCounter(
                RecognitionStatKeys.HIGHEST_SUBORDINATES,
                subordinates
        );

        data.setCounter(
                RecognitionStatKeys
                        .SUBORDINATE_ASSISTED_MAJOR_VICTORIES,
                entriesFor(
                        balance.order()
                                .subordinateAssistedMajorVictories(),
                        1.0D
                )
        );

        putCollection(
                data,
                RecognitionStatKeys
                        .UNIQUE_SUBORDINATES_EMPOWERED,
                "moostensuraaddon:ceiling_empowered_",
                entriesFor(
                        balance.order()
                                .uniqueSubordinatesEmpowered(),
                        1.0D
                )
        );

        data.setCounter(
                RecognitionStatKeys.MASS_GRANTS_PERFORMED,
                entriesFor(
                        balance.order()
                                .massGrantsPerformed(),
                        1.0D
                )
        );

        data.setCounter(
                RecognitionStatKeys
                        .GLOBAL_TAKE_BACKS_PERFORMED,
                entriesFor(
                        balance.order()
                                .globalTakeBacksPerformed(),
                        1.0D
                )
        );

        return RecognitionPathEvaluator.evaluate(
                        data
                )
                .getDimensions()
                .order();
    }

    private static void putMilestonesByLevel(
            RecognitionData data,
            RecognitionIndependenceMilestoneManager.State state,
            double level
    ) {
        double target =
                state.maximumScore()
                        * clamp01(level);

        double earned =
                0.0D;

        for (RecognitionIndependenceMilestoneManager.Milestone milestone :
                state.milestones()) {
            if (earned >= target
                    && target < state.maximumScore()) {
                break;
            }

            data.addUniqueValue(
                    RecognitionStatKeys
                            .INDEPENDENCE_MILESTONES,
                    milestone.id()
                            .toString()
            );

            earned +=
                    milestone.points();
        }
    }

    private static void putCounter(
            RecognitionData data,
            String key,
            RecognitionBalanceSnapshot.Contribution contribution,
            double level
    ) {
        data.setCounter(
                key,
                entriesFor(
                        contribution,
                        level
                )
        );
    }

    private static void putCollection(
            RecognitionData data,
            String key,
            String prefix,
            int count
    ) {
        int safeCount =
                Math.min(
                        MAX_SYNTHETIC_ENTRIES,
                        Math.max(
                                0,
                                count
                        )
                );

        for (int index = 0;
             index < safeCount;
             index++) {
            data.addUniqueValue(
                    key,
                    prefix + index
            );
        }
    }

    private static int entriesFor(
            RecognitionBalanceSnapshot.Contribution contribution,
            double level
    ) {
        if (contribution == null
                || !Double.isFinite(
                contribution.pointsPerEntry()
        )
                || contribution.pointsPerEntry() <= 0.0D
                || !Double.isFinite(
                contribution.maximum()
        )
                || contribution.maximum() <= 0.0D) {
            return 0;
        }

        double target =
                contribution.maximum()
                        * clamp01(level);

        return Math.min(
                MAX_SYNTHETIC_ENTRIES,
                Math.max(
                        0,
                        (int) Math.ceil(
                                target
                                        / contribution.pointsPerEntry()
                        )
                )
        );
    }

    private static int scaledCount(
            int maximum,
            double level
    ) {
        return Math.max(
                0,
                (int) Math.round(
                        Math.max(
                                0,
                                maximum
                        )
                                * clamp01(level)
                )
        );
    }

    private static double epForLevel(
            RecognitionBalanceSnapshot.EpContribution contribution,
            double level
    ) {
        if (contribution == null
                || contribution.epPerUnit() <= 0.0D
                || contribution.pointsPerUnit() <= 0.0D
                || contribution.maximum() <= 0.0D) {
            return 0.0D;
        }

        double units =
                contribution.maximum()
                        / contribution.pointsPerUnit();

        return units
                * contribution.epPerUnit()
                * clamp01(level);
    }

    private static double clamp01(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        value
                )
        );
    }

    private static boolean approximatelyEqual(
            double first,
            double second
    ) {
        return Math.abs(
                first - second
        ) <= 0.000_001D;
    }

    private static String selectionDescription(
            RecognitionEvaluation evaluation
    ) {
        RecognitionPathSelection selection =
                evaluation.getSelection()
                        .orElse(null);

        if (selection == null) {
            return "none";
        }

        if (selection.pure()) {
            return "Pure "
                    + selection.primaryPath()
                    .getId();
        }

        return selection.primaryPath()
                .getId()
                + " / "
                + (
                selection.secondaryPath() == null
                        ? "none"
                        : selection.secondaryPath()
                        .getId()
        );
    }

    private static String format(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f",
                !Double.isFinite(value)
                        ? 0.0D
                        : value
        );
    }

    public record Check(
            String name,
            boolean passed,
            String detail
    ) {

        public Check {
            name = name == null
                    ? "Unnamed check"
                    : name.trim();

            detail = detail == null
                    ? ""
                    : detail.trim();
        }
    }

    public record Report(
            boolean passed,
            String balanceSource,
            long balanceRevision,
            long milestoneRevision,
            String milestoneFingerprint,
            int sourceFileCount,
            int configuredMilestones,
            double configuredMilestonePoints,
            double legacyFreedomCeiling,
            double expandedFreedomCeiling,
            double orderCeiling,
            int baselinePureCoarse,
            int observedExpandedPure,
            int refinedPurePaths,
            int totalPurePaths,
            int baselineAdjacentCoarse,
            int observedExpandedAdjacent,
            int refinedAdjacentPairs,
            int totalAdjacentPairs,
            int expandedProfilesEvaluated,
            int comparisonProfilesEvaluated,
            int freedomGainProfiles,
            int newChaoticSelectionProfiles,
            int chaoticPurePaths,
            int totalChaoticPurePaths,
            int chaoticAdjacentPairs,
            int totalChaoticAdjacentPairs,
            List<Check> checks,
            List<String> warnings
    ) {

        public Report {
            balanceSource =
                    balanceSource == null
                            ? ""
                            : balanceSource.trim();

            milestoneFingerprint =
                    milestoneFingerprint == null
                            ? ""
                            : milestoneFingerprint.trim();

            sourceFileCount =
                    Math.max(
                            0,
                            sourceFileCount
                    );

            configuredMilestones =
                    Math.max(
                            0,
                            configuredMilestones
                    );

            expandedProfilesEvaluated =
                    Math.max(
                            0,
                            expandedProfilesEvaluated
                    );

            comparisonProfilesEvaluated =
                    Math.max(
                            0,
                            comparisonProfilesEvaluated
                    );

            freedomGainProfiles =
                    Math.max(
                            0,
                            freedomGainProfiles
                    );

            newChaoticSelectionProfiles =
                    Math.max(
                            0,
                            newChaoticSelectionProfiles
                    );

            chaoticPurePaths =
                    Math.max(
                            0,
                            chaoticPurePaths
                    );

            totalChaoticPurePaths =
                    Math.max(
                            0,
                            totalChaoticPurePaths
                    );

            chaoticAdjacentPairs =
                    Math.max(
                            0,
                            chaoticAdjacentPairs
                    );

            totalChaoticAdjacentPairs =
                    Math.max(
                            0,
                            totalChaoticAdjacentPairs
                    );

            checks =
                    checks == null
                            ? List.of()
                            : List.copyOf(
                            checks
                    );

            warnings =
                    warnings == null
                            ? List.of()
                            : List.copyOf(
                            warnings
                    );
        }
    }

    private record CachedReport(
            long balanceRevision,
            long milestoneRevision,
            String milestoneFingerprint,
            Report report
    ) {

        private boolean matches(
                long currentBalanceRevision,
                long currentMilestoneRevision,
                String currentFingerprint
        ) {
            return balanceRevision
                    == currentBalanceRevision
                    && milestoneRevision
                    == currentMilestoneRevision
                    && milestoneFingerprint.equals(
                    currentFingerprint == null
                            ? ""
                            : currentFingerprint
            );
        }
    }

    private record ExpandedGridResult(
            int expandedProfilesEvaluated,
            int comparisonProfilesEvaluated,
            int freedomGainProfiles,
            int newChaoticSelectionProfiles,
            int exactPurePaths,
            int chaoticPurePaths,
            int totalChaoticPurePaths,
            int exactAdjacentPairs,
            int totalAdjacentPairs,
            int chaoticAdjacentPairs,
            int totalChaoticAdjacentPairs
    ) {
    }

    private record OrderedPair(
            RecognitionPath primary,
            RecognitionPath secondary
    ) {
    }

    private record CoarseProfile(
            Awakening awakening,
            double good,
            double evil,
            double order,
            double independence,
            double exploration,
            double mastery,
            double selfReliance
    ) {

        private CoarseProfile withSelfReliance(
                double value
        ) {
            return new CoarseProfile(
                    awakening,
                    good,
                    evil,
                    order,
                    independence,
                    exploration,
                    mastery,
                    clamp01(value)
            );
        }
    }

    private enum Awakening {
        NONE,
        TRUE_HERO,
        TRUE_DEMON_LORD
    }
}