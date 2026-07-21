package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Permanent debug-only, non-persistent recognition balance validator.
 *
 * <p>The validator first evaluates the original deterministic four-step grid.
 * Missing Pure paths and required adjacent cross-path combinations are then
 * refined locally down to 1/24 intensity increments. Every temporary profile
 * is passed through the production evaluator and is never attached to a
 * player or written to disk.</p>
 */
public final class RecognitionBalanceValidationHarness {

    private static final double[] COARSE_LEVELS = {
            0.0D,
            1.0D / 3.0D,
            2.0D / 3.0D,
            1.0D
    };

    private static final int CHANNEL_COUNT = 6;
    private static final int PROFILES_PER_AWAKENING =
            1 << (CHANNEL_COUNT * 2);

    private static final int REFINEMENT_DENOMINATOR = 24;
    private static final int[] REFINEMENT_STEPS = {
            4,
            2,
            1
    };

    private static final int COARSE_SEEDS_PER_AWAKENING = 8;
    private static final int REFINEMENT_BEAM_SIZE = 16;
    private static final int MAX_COLLECTION_ENTRIES = 512;

    private static final int TOTAL_PURE_PATHS =
            RecognitionPath.values().length;

    private static final int TOTAL_CROSS_PAIRS =
            RecognitionPath.values().length
                    * (RecognitionPath.values().length - 1);

    private static final Object CACHE_LOCK = new Object();
    private static volatile SearchCache cache;

    private RecognitionBalanceValidationHarness() {
    }

    public static List<String> pathIds() {
        List<String> ids =
                new ArrayList<>(
                        RecognitionPath.values().length
                );

        for (RecognitionPath path : RecognitionPath.values()) {
            ids.add(path.getId());
        }

        return List.copyOf(ids);
    }

    public static Report validate() {
        SearchCache search = getSearch();

        List<PathResult> paths =
                new ArrayList<>(
                        RecognitionPath.values().length
                );

        int identityHeavy = 0;

        for (RecognitionPath path : RecognitionPath.values()) {
            Result result = pureResult(
                    path,
                    search
            );

            double raw =
                    result.evaluation()
                            .getRawPathScore(path);

            double identity =
                    result.evaluation()
                            .getIdentityBoost(path);

            boolean heavy =
                    identity > raw
                            || identity
                            >= result.evaluation()
                            .getPathScore(path) * 0.50D;

            if (heavy && identity > 0.0D) {
                identityHeavy++;
            }

            paths.add(
                    new PathResult(
                            path,
                            result,
                            heavy && identity > 0.0D
                    )
            );
        }

        List<String> warnings =
                new ArrayList<>(
                        search.warnings()
                );

        if (search.exactPurePaths()
                < TOTAL_PURE_PATHS) {
            warnings.add(
                    "Only "
                            + search.exactPurePaths()
                            + " of "
                            + TOTAL_PURE_PATHS
                            + " Pure paths were found after refinement."
            );
        }

        if (search.exactAdjacentPairs()
                < search.totalAdjacentPairs()) {
            warnings.add(
                    search.exactAdjacentPairs()
                            + " of "
                            + search.totalAdjacentPairs()
                            + " required ordered adjacent crosses were found."
            );
        }

        if (identityHeavy > 0) {
            warnings.add(
                    identityHeavy
                            + " best Pure simulation(s) receive at least half "
                            + "their target score from identity resonance or "
                            + "more identity than raw affinity."
            );
        }

        return new Report(
                search.revision(),
                search.source(),
                search.coarseProfilesEvaluated(),
                search.refinedProfilesEvaluated(),
                search.exactPureCoarse(),
                search.exactPurePaths(),
                TOTAL_PURE_PATHS,
                search.exactAdjacentCoarse(),
                search.exactAdjacentPairs(),
                search.totalAdjacentPairs(),
                search.exactCrossPairs(),
                TOTAL_CROSS_PAIRS,
                paths,
                search.crossClassSummaries(),
                warnings
        );
    }

    public static Result simulatePure(
            RecognitionPath path
    ) {
        if (path == null) {
            throw new IllegalArgumentException(
                    "A recognition path is required."
            );
        }

        return pureResult(
                path,
                getSearch()
        );
    }

    public static Result simulateCross(
            RecognitionPath primary,
            RecognitionPath secondary
    ) {
        if (primary == null || secondary == null) {
            throw new IllegalArgumentException(
                    "Both recognition paths are required."
            );
        }

        if (primary == secondary) {
            throw new IllegalArgumentException(
                    "Primary and secondary paths must differ."
            );
        }

        SearchCache search = getSearch();
        CrossKey key = new CrossKey(
                primary,
                secondary
        );

        Candidate candidate =
                search.crossCandidates()
                        .get(key);

        if (candidate == null) {
            candidate = emptyCandidate();
        }

        CrossClass pairClass = classifyPair(
                primary,
                secondary
        );

        return new Result(
                Mode.CROSS,
                primary,
                secondary,
                candidate.exact(),
                candidate.stage(),
                pairClass,
                candidate.profile(),
                candidate.evaluation(),
                componentSnapshot(
                        candidate.evaluation()
                ),
                crossBlockers(
                        primary,
                        secondary,
                        candidate.evaluation()
                ),
                search.revision(),
                search.source(),
                search.coarseProfilesEvaluated(),
                search.refinedProfilesEvaluated()
        );
    }

    public static CrossClass classifyPair(
            RecognitionPath first,
            RecognitionPath second
    ) {
        if (first == null
                || second == null
                || first == second) {
            return CrossClass.INVALID;
        }

        int moralityDistance =
                Math.abs(
                        moralityIndex(
                                first.getMorality()
                        ) - moralityIndex(
                                second.getMorality()
                        )
                );

        int temperamentDistance =
                Math.abs(
                        temperamentIndex(
                                first.getTemperament()
                        ) - temperamentIndex(
                                second.getTemperament()
                        )
                );

        if (moralityDistance + temperamentDistance == 1) {
            return CrossClass.ADJACENT_REQUIRED;
        }

        if (moralityDistance == 1
                && temperamentDistance == 1) {
            return CrossClass.DIAGONAL_OPTIONAL;
        }

        if ((moralityDistance == 2
                && temperamentDistance == 0)
                || (moralityDistance == 0
                && temperamentDistance == 2)) {
            return CrossClass.OPPOSING_AXIS;
        }

        if (moralityDistance == 2
                && temperamentDistance == 2) {
            return CrossClass.FULL_CONTRADICTION;
        }

        return CrossClass.DISTANT_OPTIONAL;
    }

    private static Result pureResult(
            RecognitionPath path,
            SearchCache search
    ) {
        Candidate candidate =
                search.pureCandidates()
                        .get(path);

        if (candidate == null) {
            candidate = emptyCandidate();
        }

        return new Result(
                Mode.PURE,
                path,
                null,
                candidate.exact(),
                candidate.stage(),
                CrossClass.INVALID,
                candidate.profile(),
                candidate.evaluation(),
                componentSnapshot(
                        candidate.evaluation()
                ),
                pureBlockers(
                        path,
                        candidate.evaluation()
                ),
                search.revision(),
                search.source(),
                search.coarseProfilesEvaluated(),
                search.refinedProfilesEvaluated()
        );
    }

    private static SearchCache getSearch() {
        long revision =
                RecognitionBalanceManager
                        .getRevision();

        SearchCache current = cache;

        if (current != null
                && current.revision() == revision) {
            return current;
        }

        synchronized (CACHE_LOCK) {
            current = cache;

            if (current != null
                    && current.revision() == revision) {
                return current;
            }

            current = buildStableSearch();
            cache = current;
            return current;
        }
    }

    private static SearchCache buildStableSearch() {
        for (int attempt = 0; attempt < 2; attempt++) {
            RecognitionBalanceManager.State before =
                    RecognitionBalanceManager
                            .getState();

            SearchCache built =
                    buildSearch(
                            before.revision(),
                            before.snapshot()
                    );

            if (RecognitionBalanceManager
                    .getRevision() == before.revision()) {
                return built;
            }
        }

        RecognitionBalanceManager.State state =
                RecognitionBalanceManager
                        .getState();

        return buildSearch(
                state.revision(),
                state.snapshot()
        );
    }

    private static SearchCache buildSearch(
            long revision,
            RecognitionBalanceSnapshot balance
    ) {
        Map<PureSeedKey, List<Candidate>> pureSeeds =
                new HashMap<>();

        Map<CrossSeedKey, List<Candidate>> crossSeeds =
                new HashMap<>();

        EvaluationCache evaluations =
                new EvaluationCache(balance);

        for (Awakening awakening : Awakening.values()) {
            for (int code = 0;
                 code < PROFILES_PER_AWAKENING;
                 code++) {

                Profile profile =
                        decodeProfile(
                                awakening,
                                code
                        );

                EvaluatedProfile evaluated =
                        evaluations.evaluate(
                                profile,
                                SearchStage.COARSE
                        );

                for (RecognitionPath path :
                        RecognitionPath.values()) {

                    Candidate candidate =
                            createPureCandidate(
                                    path,
                                    evaluated,
                                    SearchStage.COARSE
                            );

                    keepTop(
                            pureSeeds,
                            new PureSeedKey(
                                    path,
                                    awakening
                            ),
                            candidate,
                            COARSE_SEEDS_PER_AWAKENING
                    );
                }

                for (RecognitionPath primary :
                        RecognitionPath.values()) {

                    for (RecognitionPath secondary :
                            RecognitionPath.values()) {

                        if (primary == secondary) {
                            continue;
                        }

                        Candidate candidate =
                                createCrossCandidate(
                                        primary,
                                        secondary,
                                        evaluated,
                                        SearchStage.COARSE
                                );

                        keepTop(
                                crossSeeds,
                                new CrossSeedKey(
                                        primary,
                                        secondary,
                                        awakening
                                ),
                                candidate,
                                COARSE_SEEDS_PER_AWAKENING
                        );
                    }
                }
            }
        }

        EnumMap<RecognitionPath, Candidate> pureCandidates =
                new EnumMap<>(
                        RecognitionPath.class
                );

        Map<CrossKey, Candidate> crossCandidates =
                new HashMap<>();

        int exactPureCoarse = 0;

        for (RecognitionPath path : RecognitionPath.values()) {
            List<Candidate> seeds = collectPureSeeds(
                    pureSeeds,
                    path
            );

            Candidate coarse = bestCandidate(seeds);

            if (coarse.exact()) {
                exactPureCoarse++;
                pureCandidates.put(
                        path,
                        coarse
                );
            } else {
                pureCandidates.put(
                        path,
                        refinePure(
                                path,
                                seeds,
                                evaluations
                        )
                );
            }
        }

        int exactAdjacentCoarse = 0;
        int totalAdjacent = 0;

        for (RecognitionPath primary : RecognitionPath.values()) {
            for (RecognitionPath secondary : RecognitionPath.values()) {
                if (primary == secondary) {
                    continue;
                }

                CrossKey key = new CrossKey(
                        primary,
                        secondary
                );

                List<Candidate> seeds = collectCrossSeeds(
                        crossSeeds,
                        primary,
                        secondary
                );

                Candidate coarse = bestCandidate(seeds);
                CrossClass pairClass = classifyPair(
                        primary,
                        secondary
                );

                if (pairClass.required()) {
                    totalAdjacent++;

                    if (coarse.exact()) {
                        exactAdjacentCoarse++;
                        crossCandidates.put(
                                key,
                                coarse
                        );
                    } else {
                        crossCandidates.put(
                                key,
                                refineCross(
                                        primary,
                                        secondary,
                                        seeds,
                                        evaluations
                                )
                        );
                    }
                } else {
                    crossCandidates.put(
                            key,
                            coarse
                    );
                }
            }
        }

        int exactPure = countExact(
                pureCandidates.values()
        );

        int exactAdjacent = 0;
        int exactCross = 0;

        EnumMap<CrossClass, MutableCrossSummary> summaries =
                new EnumMap<>(
                        CrossClass.class
                );

        for (CrossClass pairClass : CrossClass.values()) {
            if (pairClass != CrossClass.INVALID) {
                summaries.put(
                        pairClass,
                        new MutableCrossSummary()
                );
            }
        }

        for (Map.Entry<CrossKey, Candidate> entry :
                crossCandidates.entrySet()) {

            CrossClass pairClass = classifyPair(
                    entry.getKey().primary(),
                    entry.getKey().secondary()
            );

            MutableCrossSummary summary =
                    summaries.get(pairClass);

            summary.total++;

            if (entry.getValue().exact()) {
                summary.exact++;
                exactCross++;

                if (pairClass.required()) {
                    exactAdjacent++;
                }
            }
        }

        List<CrossClassSummary> summaryList =
                new ArrayList<>();

        for (CrossClass pairClass : CrossClass.values()) {
            if (pairClass == CrossClass.INVALID) {
                continue;
            }

            MutableCrossSummary summary =
                    summaries.get(pairClass);

            summaryList.add(
                    new CrossClassSummary(
                            pairClass,
                            summary.exact,
                            summary.total
                    )
            );
        }

        return new SearchCache(
                revision,
                balance.sourceId(),
                evaluations.coarseCount(),
                evaluations.refinedCount(),
                exactPureCoarse,
                exactPure,
                exactAdjacentCoarse,
                exactAdjacent,
                totalAdjacent,
                exactCross,
                Map.copyOf(
                        pureCandidates
                ),
                Map.copyOf(
                        crossCandidates
                ),
                List.copyOf(
                        summaryList
                ),
                globalWarnings(
                        balance,
                        evaluations.capHit()
                )
        );
    }

    private static Profile decodeProfile(
            Awakening awakening,
            int code
    ) {
        int[] values = new int[CHANNEL_COUNT];
        int remaining = code;

        for (int index = 0;
             index < CHANNEL_COUNT;
             index++) {

            values[index] = remaining & 3;
            remaining >>= 2;
        }

        return new Profile(
                awakening,
                COARSE_LEVELS[values[0]],
                COARSE_LEVELS[values[1]],
                COARSE_LEVELS[values[2]],
                COARSE_LEVELS[values[3]],
                COARSE_LEVELS[values[4]],
                COARSE_LEVELS[values[5]],
                false
        );
    }

    private static Candidate refinePure(
            RecognitionPath path,
            List<Candidate> seeds,
            EvaluationCache evaluations
    ) {
        List<Candidate> expandedSeeds =
                new ArrayList<>(seeds);

        /*
         * Pure paths receive a small path-shaped anchor search before the
         * local 1/24 beam refinement. This avoids a coarse-grid miss becoming
         * trapped around a high-score but low-dominance profile.
         */
        for (Profile profile : pureAnchorProfiles(path)) {
            EvaluatedProfile evaluated =
                    evaluations.evaluate(
                            profile,
                            SearchStage.REFINED
                    );

            expandedSeeds.add(
                    createPureCandidate(
                            path,
                            evaluated,
                            SearchStage.REFINED
                    )
            );
        }

        return refine(
                topCandidates(
                        expandedSeeds,
                        REFINEMENT_BEAM_SIZE
                ),
                evaluations,
                (evaluated, stage) ->
                        createPureCandidate(
                                path,
                                evaluated,
                                stage
                        )
        );
    }

    private static Set<Profile> pureAnchorProfiles(
            RecognitionPath path
    ) {
        Set<Profile> profiles =
                new LinkedHashSet<>();

        double[] quarterLevels = {
                0.0D,
                0.25D,
                0.50D,
                0.75D,
                1.0D
        };

        double[] eighthLevels = {
                0.0D,
                0.125D,
                0.25D,
                0.375D,
                0.50D,
                0.625D,
                0.75D,
                0.875D,
                1.0D
        };

        double[] directionalMoralLevels =
                path.getTemperament()
                        == RecognitionPath.Temperament.NEUTRAL
                        ? new double[]{
                        0.125D,
                        0.25D,
                        0.375D,
                        0.50D,
                        0.625D,
                        0.75D,
                        0.875D,
                        1.0D
                }
                        : new double[]{
                        0.25D,
                        0.50D,
                        0.75D,
                        1.0D
                };

        double[] temperamentLevels =
                path.getTemperament()
                        == RecognitionPath.Temperament.NEUTRAL
                        ? eighthLevels
                        : quarterLevels;

        double[] explorationLevels =
                path.getTemperament()
                        == RecognitionPath.Temperament.NEUTRAL
                        ? new double[]{
                        0.0D,
                        0.125D,
                        0.25D,
                        0.50D,
                        1.0D
                }
                        : quarterLevels;

        double[] masteryLevels =
                path.getTemperament()
                        == RecognitionPath.Temperament.NEUTRAL
                        ? new double[]{
                        0.50D,
                        0.75D,
                        1.0D
                }
                        : new double[]{
                        0.25D,
                        0.50D,
                        0.75D,
                        1.0D
                };

        double[] firstMoralLevels = switch (
                path.getMorality()
                ) {
            case GOOD -> directionalMoralLevels;
            case EVIL -> new double[]{0.0D};
            case NEUTRAL -> temperamentLevels;
        };

        double[] secondMoralLevels = switch (
                path.getMorality()
                ) {
            case GOOD -> new double[]{0.0D};
            case EVIL -> directionalMoralLevels;
            case NEUTRAL -> temperamentLevels;
        };

        for (Awakening awakening : Awakening.values()) {
            for (double good : firstMoralLevels) {
                for (double evil : secondMoralLevels) {
                    if (!matchesMoralAnchor(
                            path,
                            good,
                            evil
                    )) {
                        continue;
                    }

                    for (double order : temperamentLevels) {
                        for (double independence : temperamentLevels) {
                            if (!matchesTemperamentAnchor(
                                    path,
                                    order,
                                    independence
                            )) {
                                continue;
                            }

                            for (double exploration :
                                    explorationLevels) {

                                for (double mastery :
                                        masteryLevels) {

                                    profiles.add(
                                            new Profile(
                                                    awakening,
                                                    good,
                                                    evil,
                                                    order,
                                                    independence,
                                                    exploration,
                                                    mastery,
                                                    false
                                            )
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }

        return profiles;
    }

    private static boolean matchesMoralAnchor(
            RecognitionPath path,
            double good,
            double evil
    ) {
        return switch (path.getMorality()) {
            case GOOD -> good > 0.0D
                    && evil == 0.0D;

            case EVIL -> evil > 0.0D
                    && good == 0.0D;

            case NEUTRAL -> good == evil;
        };
    }

    private static boolean matchesTemperamentAnchor(
            RecognitionPath path,
            double order,
            double independence
    ) {
        return switch (path.getTemperament()) {
            case LAWFUL -> order > 0.0D
                    && independence == 0.0D;

            case CHAOTIC -> independence > 0.0D
                    && order == 0.0D;

            case NEUTRAL -> true;
        };
    }

    private static Candidate refineCross(
            RecognitionPath primary,
            RecognitionPath secondary,
            List<Candidate> seeds,
            EvaluationCache evaluations
    ) {
        return refine(
                seeds,
                evaluations,
                (evaluated, stage) ->
                        createCrossCandidate(
                                primary,
                                secondary,
                                evaluated,
                                stage
                        )
        );
    }

    private static Candidate refine(
            List<Candidate> coarseSeeds,
            EvaluationCache evaluations,
            CandidateFactory factory
    ) {
        List<Candidate> beam = topCandidates(
                coarseSeeds,
                REFINEMENT_BEAM_SIZE
        );

        Candidate best = bestCandidate(beam);

        for (int stepUnits : REFINEMENT_STEPS) {
            Map<ProfileKey, Candidate> generated =
                    new HashMap<>();

            for (Candidate seed : beam) {
                for (Profile profile : neighborhood(
                        seed.profile(),
                        stepUnits
                )) {
                    EvaluatedProfile evaluated =
                            evaluations.evaluate(
                                    profile,
                                    SearchStage.REFINED
                            );

                    Candidate candidate =
                            factory.create(
                                    evaluated,
                                    SearchStage.REFINED
                            );

                    Candidate current =
                            generated.get(
                                    profile.key()
                            );

                    if (current == null
                            || isBetter(
                            candidate,
                            current
                    )) {
                        generated.put(
                                profile.key(),
                                candidate
                        );
                    }
                }
            }

            beam = topCandidates(
                    generated.values(),
                    REFINEMENT_BEAM_SIZE
            );

            Candidate roundBest = bestCandidate(beam);

            if (isBetter(
                    roundBest,
                    best
            )) {
                best = roundBest;
            }
        }

        return best;
    }

    private static Set<Profile> neighborhood(
            Profile profile,
            int stepUnits
    ) {
        Set<Profile> profiles =
                new LinkedHashSet<>();

        profiles.add(profile.quantized());

        for (int first = 0;
             first < CHANNEL_COUNT;
             first++) {

            profiles.add(
                    profile.adjustUnits(
                            first,
                            stepUnits
                    )
            );

            profiles.add(
                    profile.adjustUnits(
                            first,
                            -stepUnits
                    )
            );
        }

        for (int first = 0;
             first < CHANNEL_COUNT;
             first++) {

            for (int second = first + 1;
                 second < CHANNEL_COUNT;
                 second++) {

                for (int firstDirection : new int[]{-1, 1}) {
                    for (int secondDirection : new int[]{-1, 1}) {
                        profiles.add(
                                profile.adjustTwoUnits(
                                        first,
                                        firstDirection
                                                * stepUnits,
                                        second,
                                        secondDirection
                                                * stepUnits
                                )
                        );
                    }
                }
            }
        }

        return profiles;
    }

    private static Candidate createPureCandidate(
            RecognitionPath path,
            EvaluatedProfile evaluated,
            SearchStage stage
    ) {
        RecognitionEvaluation evaluation =
                evaluated.evaluation();

        return new Candidate(
                evaluated.profile(),
                evaluation,
                isExactPure(
                        path,
                        evaluation
                ),
                pureFitness(
                        path,
                        evaluated.profile(),
                        evaluation
                ),
                stage
        );
    }

    private static Candidate createCrossCandidate(
            RecognitionPath primary,
            RecognitionPath secondary,
            EvaluatedProfile evaluated,
            SearchStage stage
    ) {
        RecognitionEvaluation evaluation =
                evaluated.evaluation();

        return new Candidate(
                evaluated.profile(),
                evaluation,
                isExactCross(
                        primary,
                        secondary,
                        evaluation
                ),
                crossFitness(
                        primary,
                        secondary,
                        evaluated.profile(),
                        evaluation
                ),
                stage
        );
    }

    private static List<Candidate> collectPureSeeds(
            Map<PureSeedKey, List<Candidate>> seedMap,
            RecognitionPath path
    ) {
        List<Candidate> result =
                new ArrayList<>();

        for (Awakening awakening : Awakening.values()) {
            result.addAll(
                    seedMap.getOrDefault(
                            new PureSeedKey(
                                    path,
                                    awakening
                            ),
                            List.of()
                    )
            );
        }

        return topCandidates(
                result,
                COARSE_SEEDS_PER_AWAKENING
                        * Awakening.values().length
        );
    }

    private static List<Candidate> collectCrossSeeds(
            Map<CrossSeedKey, List<Candidate>> seedMap,
            RecognitionPath primary,
            RecognitionPath secondary
    ) {
        List<Candidate> result =
                new ArrayList<>();

        for (Awakening awakening : Awakening.values()) {
            result.addAll(
                    seedMap.getOrDefault(
                            new CrossSeedKey(
                                    primary,
                                    secondary,
                                    awakening
                            ),
                            List.of()
                    )
            );
        }

        return topCandidates(
                result,
                COARSE_SEEDS_PER_AWAKENING
                        * Awakening.values().length
        );
    }

    private static <K> void keepTop(
            Map<K, List<Candidate>> target,
            K key,
            Candidate candidate,
            int limit
    ) {
        List<Candidate> values =
                new ArrayList<>(
                        target.getOrDefault(
                                key,
                                List.of()
                        )
                );

        values.removeIf(existing ->
                existing.profile()
                        .key()
                        .equals(
                                candidate.profile()
                                        .key()
                        )
        );

        values.add(candidate);

        target.put(
                key,
                topCandidates(
                        values,
                        limit
                )
        );
    }

    private static List<Candidate> topCandidates(
            Iterable<Candidate> candidates,
            int limit
    ) {
        List<Candidate> result =
                new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (candidate != null) {
                result.add(candidate);
            }
        }

        result.sort(
                candidateComparator()
        );

        if (result.size() > limit) {
            return List.copyOf(
                    result.subList(
                            0,
                            limit
                    )
            );
        }

        return List.copyOf(result);
    }

    private static Candidate bestCandidate(
            List<Candidate> candidates
    ) {
        if (candidates == null
                || candidates.isEmpty()) {
            return emptyCandidate();
        }

        return candidates.get(0);
    }

    private static boolean isBetter(
            Candidate candidate,
            Candidate current
    ) {
        if (candidate == null) {
            return false;
        }

        if (current == null) {
            return true;
        }

        return candidateComparator()
                .compare(
                        candidate,
                        current
                ) < 0;
    }

    private static Comparator<Candidate> candidateComparator() {
        return Comparator
                .comparingDouble(
                        Candidate::fitness
                )
                .reversed()
                .thenComparingDouble(candidate ->
                        candidate.profile()
                                .totalIntensity()
                )
                .thenComparing(candidate ->
                        candidate.profile()
                                .key()
                                .sortKey()
                );
    }

    private static double pureFitness(
            RecognitionPath path,
            Profile profile,
            RecognitionEvaluation evaluation
    ) {
        RecognitionBalanceSnapshot.Selection settings =
                evaluation.getBalance()
                        .selection();

        double finalScore =
                evaluation.getPathScore(path);

        double rawScore =
                evaluation.getRawPathScore(path);

        double finalCompetitor =
                strongestCompetitor(
                        evaluation.getPathScores(),
                        path
                );

        double rawCompetitor =
                strongestCompetitor(
                        evaluation.getRawPathScores(),
                        path
                );

        boolean exact =
                isExactPure(
                        path,
                        evaluation
                );

        double fitness =
                exact
                        ? 1_000_000_000.0D
                        : 0.0D;

        fitness +=
                rank(
                        evaluation.getPathScores(),
                        path
                ) == 0
                        ? 1_000_000.0D
                        : -50_000.0D
                          * rank(
                        evaluation.getPathScores(),
                        path
                );

        fitness += cappedRatio(
                finalScore,
                settings.pureThreshold()
        ) * 20_000.0D;

        fitness += cappedRatio(
                rawScore,
                settings.rawPureThreshold()
        ) * 15_000.0D;

        fitness += cappedRatio(
                dominance(
                        finalScore,
                        finalCompetitor
                ),
                settings.dominanceRatio()
        ) * 100_000.0D;

        fitness += cappedRatio(
                dominance(
                        rawScore,
                        rawCompetitor
                ),
                settings.rawDominanceRatio()
        ) * 80_000.0D;

        if (exact) {
            fitness -=
                    profile.totalIntensity()
                            * 1_000.0D;
        }

        return fitness;
    }

    private static double crossFitness(
            RecognitionPath primary,
            RecognitionPath secondary,
            Profile profile,
            RecognitionEvaluation evaluation
    ) {
        double threshold =
                evaluation.getBalance()
                        .selection()
                        .establishedThreshold();

        boolean exact =
                isExactCross(
                        primary,
                        secondary,
                        evaluation
                );

        int primaryRank =
                rank(
                        evaluation.getPathScores(),
                        primary
                );

        int secondaryRank =
                rank(
                        evaluation.getPathScores(),
                        secondary
                );

        double fitness =
                exact
                        ? 1_000_000_000.0D
                        : 0.0D;

        fitness +=
                primaryRank == 0
                        ? 600_000.0D
                        : -primaryRank * 40_000.0D;

        fitness +=
                secondaryRank == 1
                        ? 400_000.0D
                        : -Math.abs(
                        secondaryRank - 1
                ) * 30_000.0D;

        fitness += cappedRatio(
                evaluation.getPathScore(primary),
                threshold
        ) * 15_000.0D;

        fitness += cappedRatio(
                evaluation.getPathScore(secondary),
                threshold
        ) * 15_000.0D;

        if (exact) {
            fitness -=
                    profile.totalIntensity()
                            * 1_000.0D;
        }

        return fitness;
    }

    private static List<String> pureBlockers(
            RecognitionPath path,
            RecognitionEvaluation evaluation
    ) {
        List<String> blockers =
                new ArrayList<>();

        RecognitionBalanceSnapshot.Selection settings =
                evaluation.getBalance()
                        .selection();

        int pathRank =
                rank(
                        evaluation.getPathScores(),
                        path
                );

        if (pathRank != 0) {
            blockers.add(
                    "Requested path ranks #"
                            + (pathRank + 1)
                            + " instead of #1."
            );
        }

        double finalScore =
                evaluation.getPathScore(path);

        if (finalScore
                < settings.pureThreshold()) {
            blockers.add(
                    "Final score "
                            + format(finalScore)
                            + " is below "
                            + format(
                            settings.pureThreshold()
                    )
                            + "."
            );
        }

        double rawScore =
                evaluation.getRawPathScore(path);

        if (rawScore
                < settings.rawPureThreshold()) {
            blockers.add(
                    "Raw score "
                            + format(rawScore)
                            + " is below "
                            + format(
                            settings.rawPureThreshold()
                    )
                            + "."
            );
        }

        double finalDominance =
                dominance(
                        finalScore,
                        strongestCompetitor(
                                evaluation.getPathScores(),
                                path
                        )
                );

        if (finalDominance
                < settings.dominanceRatio()) {
            blockers.add(
                    "Final dominance "
                            + format(finalDominance)
                            + "x is below "
                            + format(
                            settings.dominanceRatio()
                    )
                            + "x."
            );
        }

        double rawDominance =
                dominance(
                        rawScore,
                        strongestCompetitor(
                                evaluation.getRawPathScores(),
                                path
                        )
                );

        if (rawDominance
                < settings.rawDominanceRatio()) {
            blockers.add(
                    "Raw dominance "
                            + format(rawDominance)
                            + "x is below "
                            + format(
                            settings.rawDominanceRatio()
                    )
                            + "x."
            );
        }

        if (!isExactPure(
                path,
                evaluation
        )
                && blockers.isEmpty()) {
            blockers.add(
                    "Selector returned "
                            + selectionDescription(
                            evaluation
                    )
                            + "."
            );
        }

        return List.copyOf(blockers);
    }

    private static List<String> crossBlockers(
            RecognitionPath primary,
            RecognitionPath secondary,
            RecognitionEvaluation evaluation
    ) {
        List<String> blockers =
                new ArrayList<>();

        double threshold =
                evaluation.getBalance()
                        .selection()
                        .establishedThreshold();

        int primaryRank =
                rank(
                        evaluation.getPathScores(),
                        primary
                );

        int secondaryRank =
                rank(
                        evaluation.getPathScores(),
                        secondary
                );

        if (primaryRank != 0) {
            blockers.add(
                    "Requested primary ranks #"
                            + (primaryRank + 1)
                            + "."
            );
        }

        if (secondaryRank != 1) {
            blockers.add(
                    "Requested secondary ranks #"
                            + (secondaryRank + 1)
                            + "."
            );
        }

        if (evaluation.getPathScore(primary)
                < threshold) {
            blockers.add(
                    "Primary score is below the Established threshold."
            );
        }

        if (evaluation.getPathScore(secondary)
                < threshold) {
            blockers.add(
                    "Secondary score is below the Established threshold."
            );
        }

        if (!isExactCross(
                primary,
                secondary,
                evaluation
        )
                && blockers.isEmpty()) {
            blockers.add(
                    "Selector returned "
                            + selectionDescription(
                            evaluation
                    )
                            + "."
            );
        }

        return List.copyOf(blockers);
    }

    private static List<String> globalWarnings(
            RecognitionBalanceSnapshot balance,
            boolean capHit
    ) {
        List<String> warnings =
                new ArrayList<>();

        RecognitionEvaluation empty =
                RecognitionPathEvaluator.evaluate(
                        new RecognitionData()
                );

        if (empty.getSelection().isPresent()) {
            warnings.add(
                    "An empty deed profile already produces "
                            + selectionDescription(empty)
                            + "."
            );
        }

        double neutralEmpty =
                Math.max(
                        empty.getPathScore(
                                RecognitionPath.LAWFUL_NEUTRAL
                        ),
                        Math.max(
                                empty.getPathScore(
                                        RecognitionPath.TRUE_NEUTRAL
                                ),
                                empty.getPathScore(
                                        RecognitionPath.CHAOTIC_NEUTRAL
                                )
                        )
                );

        if (neutralEmpty
                >= balance.selection()
                .establishedThreshold() * 0.80D) {
            warnings.add(
                    "An empty deed profile reaches "
                            + format(neutralEmpty)
                            + " Neutral score, at least 80% of Established."
            );
        }

        double identityShare =
                balance.identityDistribution()
                        .universalShare()
                        + balance.identityDistribution()
                        .focusedShare();

        if (identityShare > 0.85D) {
            warnings.add(
                    "Identity distribution converts "
                            + format(
                            identityShare * 100.0D
                    )
                            + "% of identity strength into path score."
            );
        }

        if (capHit) {
            warnings.add(
                    "A synthetic collection reached the "
                            + MAX_COLLECTION_ENTRIES
                            + "-entry safety cap; extreme datapack values may "
                            + "be underrepresented."
            );
        }

        warnings.add(
                "Only Pure paths and required adjacent crosses receive the "
                        + "1/24 refinement pass; optional and contradictory "
                        + "pairs remain coarse-grid observations."
        );

        warnings.add(
                "Refinement uses deterministic path-shaped anchors followed by a local "
                        + "beam search; it is not a formal mathematical proof "
                        + "over every continuous profile."
        );

        return List.copyOf(warnings);
    }

    private static SyntheticData createData(
            Profile profile,
            RecognitionBalanceSnapshot balance
    ) {
        Map<String, Integer> counters =
                new LinkedHashMap<>();

        Map<String, Double> measurements =
                new LinkedHashMap<>();

        Map<String, Boolean> flags =
                new LinkedHashMap<>();

        Map<String, List<String>> collections =
                new LinkedHashMap<>();

        flags.put(
                RecognitionStatKeys.TRUE_HERO,
                profile.awakening()
                        == Awakening.TRUE_HERO
        );

        flags.put(
                RecognitionStatKeys.TRUE_DEMON_LORD,
                profile.awakening()
                        == Awakening.TRUE_DEMON_LORD
        );

        putCounter(
                counters,
                RecognitionStatKeys.RAID_VICTORIES,
                balance.good().raidVictories(),
                profile.good()
        );

        putCounter(
                counters,
                RecognitionStatKeys.VILLAGERS_CURED,
                balance.good().villagersCured(),
                profile.good()
        );

        putCounter(
                counters,
                RecognitionStatKeys.CIVILIANS_DEFENDED,
                balance.good().civiliansDefended(),
                profile.good()
        );

        Count goodBosses =
                count(
                        balance.good()
                                .malevolentBossTypesDefeated(),
                        profile.good(),
                        true
                );

        putCollection(
                collections,
                RecognitionStatKeys
                        .MALEVOLENT_BOSS_TYPES_DEFEATED,
                "synthetic:malevolent_",
                goodBosses.value()
        );

        putCounter(
                counters,
                RecognitionStatKeys.CIVILIAN_KILLS,
                balance.evil().civilianKills(),
                profile.evil()
        );

        putCounter(
                counters,
                RecognitionStatKeys.PASSIVE_BABY_KILLS,
                balance.evil().passiveBabyKills(),
                profile.evil()
        );

        putCounter(
                counters,
                RecognitionStatKeys.OWNED_COMPANION_KILLS,
                balance.evil().ownedCompanionKills(),
                profile.evil()
        );

        putCounter(
                counters,
                RecognitionStatKeys.OWNED_SUBORDINATE_KILLS,
                balance.evil().ownedSubordinateKills(),
                profile.evil()
        );

        Count evilBosses =
                count(
                        balance.evil()
                                .benevolentBossTypesKilled(),
                        profile.evil(),
                        true
                );

        putCollection(
                collections,
                RecognitionStatKeys
                        .BENEVOLENT_BOSS_TYPES_KILLED,
                "synthetic:benevolent_",
                evilBosses.value()
        );

        int subordinates =
                Math.max(
                        count(
                                balance.order()
                                        .subordinateRosterPrimary(),
                                profile.order(),
                                false
                        ).value(),
                        count(
                                balance.order()
                                        .subordinateRosterEstablished(),
                                profile.order(),
                                false
                        ).value()
                );

        counters.put(
                RecognitionStatKeys.CURRENT_SUBORDINATES,
                subordinates
        );

        counters.put(
                RecognitionStatKeys.HIGHEST_SUBORDINATES,
                subordinates
        );

        putCounter(
                counters,
                RecognitionStatKeys
                        .SUBORDINATE_ASSISTED_MAJOR_VICTORIES,
                balance.order()
                        .subordinateAssistedMajorVictories(),
                profile.order()
        );

        Count empowered =
                count(
                        balance.order()
                                .uniqueSubordinatesEmpowered(),
                        profile.order(),
                        true
                );

        putCollection(
                collections,
                RecognitionStatKeys
                        .UNIQUE_SUBORDINATES_EMPOWERED,
                "synthetic:subordinate_",
                empowered.value()
        );

        putCounter(
                counters,
                RecognitionStatKeys.MASS_GRANTS_PERFORMED,
                balance.order().massGrantsPerformed(),
                profile.order()
        );

        putCounter(
                counters,
                RecognitionStatKeys
                        .GLOBAL_TAKE_BACKS_PERFORMED,
                balance.order().globalTakeBacksPerformed(),
                profile.order()
        );

        Count solo =
                count(
                        balance.freedom()
                                .soloMajorEnemyTypesDefeated(),
                        profile.independence(),
                        true
                );

        Count masteryMajor =
                count(
                        balance.mastery()
                                .majorEnemyTypesDefeated(),
                        profile.mastery(),
                        true
                );

        Count identityMajor =
                count(
                        balance.identityStrength()
                                .majorEnemyTypes(),
                        profile.mastery(),
                        true
                );

        int majorCount =
                Math.max(
                        solo.value(),
                        Math.max(
                                masteryMajor.value(),
                                identityMajor.value()
                        )
                );

        putCollection(
                collections,
                RecognitionStatKeys
                        .MAJOR_ENEMY_TYPES_DEFEATED,
                "synthetic:major_",
                majorCount
        );

        putCollection(
                collections,
                RecognitionStatKeys
                        .SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
                "synthetic:major_",
                Math.min(
                        solo.value(),
                        majorCount
                )
        );

        Count freedomDiscovery =
                count(
                        balance.freedom()
                                .discoveryMilestones(),
                        profile.exploration(),
                        true
                );

        Count discovery =
                count(
                        balance.discovery().milestones(),
                        profile.exploration(),
                        true
                );

        int milestoneCount =
                Math.max(
                        freedomDiscovery.value(),
                        discovery.value()
                );

        putCollection(
                collections,
                RecognitionStatKeys.DISCOVERY_MILESTONES,
                "synthetic:discovery_",
                milestoneCount
        );

        counters.put(
                RecognitionStatKeys.MASTERED_SKILLS,
                skillCount(
                        balance.mastery(),
                        profile.mastery()
                )
        );

        putCounter(
                counters,
                RecognitionStatKeys
                        .MASTERED_SKILL_CATEGORIES,
                balance.mastery()
                        .masteredSkillCategories(),
                profile.mastery()
        );

        double ep =
                epValue(
                        balance.mastery().highestEp(),
                        profile.mastery()
                );

        measurements.put(
                RecognitionStatKeys.HIGHEST_EP,
                ep
        );

        measurements.put(
                RecognitionStatKeys.CURRENT_EP,
                ep
        );

        boolean capHit =
                goodBosses.capped()
                        || evilBosses.capped()
                        || empowered.capped()
                        || solo.capped()
                        || masteryMajor.capped()
                        || identityMajor.capped()
                        || freedomDiscovery.capped()
                        || discovery.capped();

        return new SyntheticData(
                new RecognitionData(
                        RecognitionData.CURRENT_DATA_VERSION,
                        counters,
                        measurements,
                        flags,
                        Map.of(),
                        collections
                ),
                capHit
        );
    }

    private static void putCounter(
            Map<String, Integer> counters,
            String key,
            RecognitionBalanceSnapshot.Contribution contribution,
            double intensity
    ) {
        counters.put(
                key,
                count(
                        contribution,
                        intensity,
                        false
                ).value()
        );
    }

    private static Count count(
            RecognitionBalanceSnapshot.Contribution contribution,
            double intensity,
            boolean collection
    ) {
        if (contribution == null
                || contribution.pointsPerEntry() <= 0.0D
                || contribution.maximum() <= 0.0D
                || intensity <= 0.0D) {
            return new Count(0, false);
        }

        double raw =
                Math.ceil(
                        contribution.maximum()
                                * clamp01(intensity)
                                / contribution.pointsPerEntry()
                );

        long value =
                !Double.isFinite(raw)
                        || raw >= Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : Math.max(
                        0L,
                        (long) raw
                );

        if (collection
                && value > MAX_COLLECTION_ENTRIES) {
            return new Count(
                    MAX_COLLECTION_ENTRIES,
                    true
            );
        }

        return new Count(
                (int) value,
                false
        );
    }

    private static int skillCount(
            RecognitionBalanceSnapshot.Mastery mastery,
            double intensity
    ) {
        if (mastery == null || intensity <= 0.0D) {
            return 0;
        }

        double target =
                mastery.skillMaximum()
                        * clamp01(intensity);

        double points = 0.0D;
        long entries = 0L;

        for (RecognitionBalanceSnapshot.SkillTier tier :
                mastery.skillTiers()) {

            if (points >= target) {
                break;
            }

            if (tier.entries() <= 0
                    || tier.pointsPerEntry() <= 0.0D) {
                continue;
            }

            int needed =
                    (int) Math.min(
                            tier.entries(),
                            Math.ceil(
                                    (target - points)
                                            / tier.pointsPerEntry()
                            )
                    );

            entries += needed;
            points +=
                    needed
                            * tier.pointsPerEntry();
        }

        return (int) Math.min(
                Integer.MAX_VALUE,
                entries
        );
    }

    private static double epValue(
            RecognitionBalanceSnapshot.EpContribution contribution,
            double intensity
    ) {
        if (contribution == null
                || contribution.epPerUnit() <= 0.0D
                || contribution.pointsPerUnit() <= 0.0D
                || contribution.maximum() <= 0.0D
                || intensity <= 0.0D) {
            return 0.0D;
        }

        double result =
                contribution.maximum()
                        * clamp01(intensity)
                        / contribution.pointsPerUnit()
                        * contribution.epPerUnit();

        return Double.isFinite(result)
                && result >= 0.0D
                ? result
                : Double.MAX_VALUE;
    }

    private static void putCollection(
            Map<String, List<String>> collections,
            String key,
            String prefix,
            int count
    ) {
        if (count <= 0) {
            return;
        }

        List<String> values =
                new ArrayList<>(count);

        for (int index = 0;
             index < count;
             index++) {
            values.add(
                    prefix + index
            );
        }

        collections.put(
                key,
                List.copyOf(values)
        );
    }


    private static boolean isExactPure(
            RecognitionPath path,
            RecognitionEvaluation evaluation
    ) {
        if (evaluation.getSelection().isEmpty()) {
            return false;
        }

        RecognitionPathSelection selection =
                evaluation.getSelection()
                        .orElseThrow();

        return selection.pure()
                && selection.primaryPath() == path;
    }

    private static boolean isExactCross(
            RecognitionPath primary,
            RecognitionPath secondary,
            RecognitionEvaluation evaluation
    ) {
        if (evaluation.getSelection().isEmpty()) {
            return false;
        }

        RecognitionPathSelection selection =
                evaluation.getSelection()
                        .orElseThrow();

        return !selection.pure()
                && selection.primaryPath() == primary
                && selection.secondaryPath() == secondary;
    }

    private static ComponentSnapshot componentSnapshot(
            RecognitionEvaluation evaluation
    ) {
        RecognitionDimensions dimensions =
                evaluation.getDimensions();

        RecognitionBalanceSnapshot balance =
                evaluation.getBalance();

        RecognitionBalanceSnapshot.NeutralMorality morality =
                balance.neutrality()
                        .morality();

        RecognitionBalanceSnapshot.NeutralBehaviour behaviour =
                balance.neutrality()
                        .behaviour();

        double neutralMoralBalance =
                Math.min(
                        dimensions.good(),
                        dimensions.evil()
                ) * morality.balanceWeight();

        double moralVolume =
                dimensions.good()
                        + dimensions.evil();

        double moralQuietness =
                1.0D / (
                        1.0D
                                + moralVolume
                                / morality.moralVolumeDivisor()
                );

        double neutralMoralDiscovery =
                dimensions.discovery()
                        * morality.discoveryWeight()
                        * moralQuietness;

        double neutralMorality =
                Math.min(
                        morality.maximum(),
                        sanitizeScore(
                                neutralMoralBalance
                                        + neutralMoralDiscovery
                        )
                );

        double activeNeutralBehaviour =
                Math.min(
                        dimensions.order(),
                        dimensions.freedom()
                ) * behaviour.balanceWeight();

        double behaviourVolume =
                dimensions.order()
                        + dimensions.freedom();

        double passiveNeutralPosture =
                behaviour.postureBase()
                        / (
                        1.0D
                                + behaviourVolume
                                / behaviour.behaviourVolumeDivisor()
                );

        double neutralBehaviour =
                Math.min(
                        behaviour.maximum(),
                        sanitizeScore(
                                activeNeutralBehaviour
                                        + passiveNeutralPosture
                        )
                );

        return new ComponentSnapshot(
                dimensions.getGoodResonance(),
                neutralMorality,
                dimensions.getEvilResonance(),
                dimensions.getLawfulResonance(),
                neutralBehaviour,
                dimensions.getChaoticResonance(),
                neutralMoralBalance,
                neutralMoralDiscovery,
                activeNeutralBehaviour,
                passiveNeutralPosture
        );
    }

    private static int rank(
            Map<RecognitionPath, Double> scores,
            RecognitionPath path
    ) {
        List<RecognitionPath> ranked =
                new ArrayList<>(
                        List.of(
                                RecognitionPath.values()
                        )
                );

        ranked.sort((first, second) -> {
            int comparison =
                    Double.compare(
                            score(scores, second),
                            score(scores, first)
                    );

            return comparison != 0
                    ? comparison
                    : Integer.compare(
                    first.ordinal(),
                    second.ordinal()
            );
        });

        return ranked.indexOf(path);
    }

    private static double strongestCompetitor(
            Map<RecognitionPath, Double> scores,
            RecognitionPath path
    ) {
        double strongest = 0.0D;

        for (RecognitionPath candidate :
                RecognitionPath.values()) {

            if (candidate != path) {
                strongest =
                        Math.max(
                                strongest,
                                score(
                                        scores,
                                        candidate
                                )
                        );
            }
        }

        return strongest;
    }

    private static double score(
            Map<RecognitionPath, Double> scores,
            RecognitionPath path
    ) {
        Double value =
                scores == null
                        ? null
                        : scores.get(path);

        return value == null
                || !Double.isFinite(value)
                || value < 0.0D
                ? 0.0D
                : value;
    }

    private static double dominance(
            double target,
            double competitor
    ) {
        if (target <= 0.0D) {
            return 0.0D;
        }

        return competitor <= 0.0D
                ? Double.POSITIVE_INFINITY
                : target / competitor;
    }

    private static double cappedRatio(
            double value,
            double requirement
    ) {
        if (value <= 0.0D) {
            return 0.0D;
        }

        if (requirement <= 0.0D
                || Double.isInfinite(value)) {
            return 4.0D;
        }

        return Math.min(
                4.0D,
                value / requirement
        );
    }

    private static String selectionDescription(
            RecognitionEvaluation evaluation
    ) {
        if (evaluation.getSelection().isEmpty()) {
            return "no selection";
        }

        RecognitionPathSelection selection =
                evaluation.getSelection()
                        .orElseThrow();

        return selection.pure()
                ? "Pure "
                  + selection.primaryPath()
                .getId()
                : selection.primaryPath()
                .getId()
                  + " / "
                  + selection.secondaryPath()
                .getId();
    }

    private static Candidate emptyCandidate() {
        return new Candidate(
                new Profile(
                        Awakening.NONE,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        false
                ),
                RecognitionPathEvaluator.evaluate(
                        new RecognitionData()
                ),
                false,
                Double.NEGATIVE_INFINITY,
                SearchStage.NOT_FOUND
        );
    }

    private static int countExact(
            Iterable<Candidate> candidates
    ) {
        int count = 0;

        for (Candidate candidate : candidates) {
            if (candidate != null
                    && candidate.exact()) {
                count++;
            }
        }

        return count;
    }

    private static int moralityIndex(
            RecognitionPath.Morality morality
    ) {
        return switch (morality) {
            case GOOD -> 0;
            case NEUTRAL -> 1;
            case EVIL -> 2;
        };
    }

    private static int temperamentIndex(
            RecognitionPath.Temperament temperament
    ) {
        return switch (temperament) {
            case LAWFUL -> 0;
            case NEUTRAL -> 1;
            case CHAOTIC -> 2;
        };
    }

    private static double clamp01(
            double value
    ) {
        return !Double.isFinite(value)
                ? 0.0D
                : Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        value
                )
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

    private static String format(
            double value
    ) {
        if (Double.isInfinite(value)) {
            return "infinite";
        }

        return String.format(
                Locale.US,
                "%.2f",
                Double.isFinite(value)
                        ? value
                        : 0.0D
        );
    }

    public enum Mode {
        PURE,
        CROSS
    }

    public enum SearchStage {
        COARSE("coarse grid"),
        REFINED("refined search"),
        NOT_FOUND("not found");

        private final String displayName;

        SearchStage(
                String displayName
        ) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum CrossClass {
        ADJACENT_REQUIRED(
                "adjacent — required",
                true,
                false
        ),
        DIAGONAL_OPTIONAL(
                "diagonal — optional",
                false,
                false
        ),
        DISTANT_OPTIONAL(
                "distant — optional",
                false,
                false
        ),
        OPPOSING_AXIS(
                "opposing axis — contradiction",
                false,
                true
        ),
        FULL_CONTRADICTION(
                "full contradiction",
                false,
                true
        ),
        INVALID(
                "not applicable",
                false,
                false
        );

        private final String displayName;
        private final boolean required;
        private final boolean contradictory;

        CrossClass(
                String displayName,
                boolean required,
                boolean contradictory
        ) {
            this.displayName = displayName;
            this.required = required;
            this.contradictory = contradictory;
        }

        public String displayName() {
            return displayName;
        }

        public boolean required() {
            return required;
        }

        public boolean contradictory() {
            return contradictory;
        }
    }

    public enum Awakening {
        NONE("none"),
        TRUE_HERO("TH"),
        TRUE_DEMON_LORD("TDL");

        private final String displayName;

        Awakening(
                String displayName
        ) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record Profile(
            Awakening awakening,
            double good,
            double evil,
            double order,
            double independence,
            double exploration,
            double mastery,
            boolean collectionCapHit
    ) {

        public Profile {
            awakening =
                    awakening == null
                            ? Awakening.NONE
                            : awakening;

            good = clamp01(good);
            evil = clamp01(evil);
            order = clamp01(order);
            independence = clamp01(independence);
            exploration = clamp01(exploration);
            mastery = clamp01(mastery);
        }

        public Profile withCapHit(
                boolean capHit
        ) {
            return new Profile(
                    awakening,
                    good,
                    evil,
                    order,
                    independence,
                    exploration,
                    mastery,
                    capHit
            );
        }

        public Profile quantized() {
            return new Profile(
                    awakening,
                    fromUnits(toUnits(good)),
                    fromUnits(toUnits(evil)),
                    fromUnits(toUnits(order)),
                    fromUnits(toUnits(independence)),
                    fromUnits(toUnits(exploration)),
                    fromUnits(toUnits(mastery)),
                    collectionCapHit
            );
        }

        public Profile adjustUnits(
                int channel,
                int deltaUnits
        ) {
            int[] units = channelUnits();
            units[channel] = clampUnits(
                    units[channel] + deltaUnits
            );

            return fromChannelUnits(
                    units
            );
        }

        public Profile adjustTwoUnits(
                int firstChannel,
                int firstDelta,
                int secondChannel,
                int secondDelta
        ) {
            int[] units = channelUnits();

            units[firstChannel] = clampUnits(
                    units[firstChannel]
                            + firstDelta
            );

            units[secondChannel] = clampUnits(
                    units[secondChannel]
                            + secondDelta
            );

            return fromChannelUnits(
                    units
            );
        }

        public ProfileKey key() {
            int[] units = channelUnits();

            return new ProfileKey(
                    awakening,
                    units[0],
                    units[1],
                    units[2],
                    units[3],
                    units[4],
                    units[5]
            );
        }

        public double totalIntensity() {
            return good
                    + evil
                    + order
                    + independence
                    + exploration
                    + mastery
                    + (
                    awakening == Awakening.NONE
                            ? 0.0D
                            : 0.25D
            );
        }

        public String describe() {
            return "awakening="
                    + awakening.displayName()
                    + ", good="
                    + percent(good)
                    + ", evil="
                    + percent(evil)
                    + ", order="
                    + percent(order)
                    + ", independence="
                    + percent(independence)
                    + ", exploration="
                    + percent(exploration)
                    + ", mastery="
                    + percent(mastery)
                    + (
                    collectionCapHit
                            ? ", collection-cap-hit"
                            : ""
            );
        }

        private int[] channelUnits() {
            return new int[]{
                    toUnits(good),
                    toUnits(evil),
                    toUnits(order),
                    toUnits(independence),
                    toUnits(exploration),
                    toUnits(mastery)
            };
        }

        private Profile fromChannelUnits(
                int[] units
        ) {
            return new Profile(
                    awakening,
                    fromUnits(units[0]),
                    fromUnits(units[1]),
                    fromUnits(units[2]),
                    fromUnits(units[3]),
                    fromUnits(units[4]),
                    fromUnits(units[5]),
                    collectionCapHit
            );
        }

        private static int toUnits(
                double value
        ) {
            return clampUnits(
                    (int) Math.round(
                            clamp01(value)
                                    * REFINEMENT_DENOMINATOR
                    )
            );
        }

        private static double fromUnits(
                int units
        ) {
            return clampUnits(units)
                    / (double) REFINEMENT_DENOMINATOR;
        }

        private static int clampUnits(
                int units
        ) {
            return Math.max(
                    0,
                    Math.min(
                            REFINEMENT_DENOMINATOR,
                            units
                    )
            );
        }

        private static String percent(
                double value
        ) {
            return Math.round(
                    clamp01(value) * 100.0D
            ) + "%";
        }
    }

    public record ComponentSnapshot(
            double good,
            double neutralMorality,
            double evil,
            double lawful,
            double neutralBehaviour,
            double chaotic,
            double neutralMoralBalance,
            double neutralMoralDiscovery,
            double activeNeutralBehaviour,
            double passiveNeutralPosture
    ) {

        public String moralitySummary() {
            return "good "
                    + format(good)
                    + ", neutral "
                    + format(neutralMorality)
                    + ", evil "
                    + format(evil);
        }

        public String temperamentSummary() {
            return "lawful "
                    + format(lawful)
                    + ", neutral "
                    + format(neutralBehaviour)
                    + ", chaotic "
                    + format(chaotic);
        }

        public String neutralBreakdown() {
            return "moral balance "
                    + format(neutralMoralBalance)
                    + " + discovery "
                    + format(neutralMoralDiscovery)
                    + "; behaviour active "
                    + format(activeNeutralBehaviour)
                    + " + passive "
                    + format(passiveNeutralPosture);
        }
    }

    public record Result(
            Mode mode,
            RecognitionPath requestedPrimary,
            RecognitionPath requestedSecondary,
            boolean exact,
            SearchStage searchStage,
            CrossClass pairClass,
            Profile profile,
            RecognitionEvaluation evaluation,
            ComponentSnapshot components,
            List<String> blockers,
            long balanceRevision,
            String balanceSource,
            int coarseProfilesEvaluated,
            int refinedProfilesEvaluated
    ) {

        public Result {
            if (mode == null
                    || requestedPrimary == null
                    || profile == null
                    || evaluation == null
                    || components == null) {
                throw new IllegalArgumentException(
                        "A complete simulation result is required."
                );
            }

            searchStage =
                    searchStage == null
                            ? SearchStage.NOT_FOUND
                            : searchStage;

            pairClass =
                    pairClass == null
                            ? CrossClass.INVALID
                            : pairClass;

            blockers =
                    blockers == null
                            ? List.of()
                            : List.copyOf(blockers);

            balanceSource =
                    balanceSource == null
                            ? ""
                            : balanceSource;
        }

        public int evaluatedProfiles() {
            return coarseProfilesEvaluated
                    + refinedProfilesEvaluated;
        }

        public String actualSelection() {
            return selectionDescription(
                    evaluation
            );
        }

        public String diagnosis() {
            if (exact) {
                return searchStage == SearchStage.REFINED
                        ? "Reachable after refined search."
                        : "Reachable on the coarse grid.";
            }

            if (mode == Mode.CROSS
                    && pairClass.contradictory()) {
                return "Contradictory matrix pairing; recorded for diagnostics, "
                        + "not required as an ordinary hybrid.";
            }

            if (blockers.isEmpty()) {
                return "No exact result was found within the current search range.";
            }

            return blockers.get(0);
        }
    }

    public record PathResult(
            RecognitionPath path,
            Result result,
            boolean identityHeavy
    ) {
    }

    public record CrossClassSummary(
            CrossClass pairClass,
            int exactPairs,
            int totalPairs
    ) {
    }

    public record Report(
            long balanceRevision,
            String balanceSource,
            int coarseProfilesEvaluated,
            int refinedProfilesEvaluated,
            int exactPureCoarse,
            int exactPurePaths,
            int totalPurePaths,
            int exactAdjacentCoarse,
            int exactAdjacentPairs,
            int totalAdjacentPairs,
            int exactCrossPairs,
            int totalCrossPairs,
            List<PathResult> paths,
            List<CrossClassSummary> crossClassSummaries,
            List<String> warnings
    ) {

        public Report {
            paths =
                    paths == null
                            ? List.of()
                            : List.copyOf(paths);

            crossClassSummaries =
                    crossClassSummaries == null
                            ? List.of()
                            : List.copyOf(
                            crossClassSummaries
                    );

            warnings =
                    warnings == null
                            ? List.of()
                            : List.copyOf(warnings);

            balanceSource =
                    balanceSource == null
                            ? ""
                            : balanceSource;
        }

        public int evaluatedProfiles() {
            return coarseProfilesEvaluated
                    + refinedProfilesEvaluated;
        }
    }

    private record SyntheticData(
            RecognitionData data,
            boolean capHit
    ) {
    }

    private record Count(
            int value,
            boolean capped
    ) {
    }

    private record EvaluatedProfile(
            Profile profile,
            RecognitionEvaluation evaluation
    ) {
    }

    private record Candidate(
            Profile profile,
            RecognitionEvaluation evaluation,
            boolean exact,
            double fitness,
            SearchStage stage
    ) {
    }

    private record ProfileKey(
            Awakening awakening,
            int good,
            int evil,
            int order,
            int independence,
            int exploration,
            int mastery
    ) {

        public String sortKey() {
            return awakening.ordinal()
                    + ":"
                    + good
                    + ":"
                    + evil
                    + ":"
                    + order
                    + ":"
                    + independence
                    + ":"
                    + exploration
                    + ":"
                    + mastery;
        }
    }

    private record PureSeedKey(
            RecognitionPath path,
            Awakening awakening
    ) {
    }

    private record CrossSeedKey(
            RecognitionPath primary,
            RecognitionPath secondary,
            Awakening awakening
    ) {
    }

    private record CrossKey(
            RecognitionPath primary,
            RecognitionPath secondary
    ) {
    }

    private record SearchCache(
            long revision,
            String source,
            int coarseProfilesEvaluated,
            int refinedProfilesEvaluated,
            int exactPureCoarse,
            int exactPurePaths,
            int exactAdjacentCoarse,
            int exactAdjacentPairs,
            int totalAdjacentPairs,
            int exactCrossPairs,
            Map<RecognitionPath, Candidate> pureCandidates,
            Map<CrossKey, Candidate> crossCandidates,
            List<CrossClassSummary> crossClassSummaries,
            List<String> warnings
    ) {

        public SearchCache {
            source =
                    source == null
                            ? ""
                            : source;

            pureCandidates =
                    pureCandidates == null
                            ? Map.of()
                            : Map.copyOf(
                            pureCandidates
                    );

            crossCandidates =
                    crossCandidates == null
                            ? Map.of()
                            : Map.copyOf(
                            crossCandidates
                    );

            crossClassSummaries =
                    crossClassSummaries == null
                            ? List.of()
                            : List.copyOf(
                            crossClassSummaries
                    );

            warnings =
                    warnings == null
                            ? List.of()
                            : List.copyOf(warnings);
        }
    }

    private static final class MutableCrossSummary {
        private int exact;
        private int total;
    }

    private static final class EvaluationCache {

        private final RecognitionBalanceSnapshot balance;
        private final Map<ProfileKey, EvaluatedProfile> evaluations =
                new HashMap<>();

        private int coarseCount;
        private int refinedCount;
        private boolean capHit;

        private EvaluationCache(
                RecognitionBalanceSnapshot balance
        ) {
            this.balance = balance;
        }

        private EvaluatedProfile evaluate(
                Profile rawProfile,
                SearchStage stage
        ) {
            Profile profile = rawProfile.quantized();
            ProfileKey key = profile.key();

            EvaluatedProfile existing =
                    evaluations.get(key);

            if (existing != null) {
                return existing;
            }

            SyntheticData synthetic =
                    createData(
                            profile,
                            balance
                    );

            profile = profile.withCapHit(
                    synthetic.capHit()
            );

            capHit |= synthetic.capHit();

            EvaluatedProfile evaluated =
                    new EvaluatedProfile(
                            profile,
                            RecognitionPathEvaluator.evaluate(
                                    synthetic.data()
                            )
                    );

            evaluations.put(
                    key,
                    evaluated
            );

            if (stage == SearchStage.COARSE) {
                coarseCount++;
            } else {
                refinedCount++;
            }

            return evaluated;
        }

        private int coarseCount() {
            return coarseCount;
        }

        private int refinedCount() {
            return refinedCount;
        }

        private boolean capHit() {
            return capHit;
        }
    }

    @FunctionalInterface
    private interface CandidateFactory {
        Candidate create(
                EvaluatedProfile evaluated,
                SearchStage stage
        );
    }
}