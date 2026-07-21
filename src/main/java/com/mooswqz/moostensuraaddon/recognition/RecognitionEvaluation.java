package com.mooswqz.moostensuraaddon.recognition;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable result of one recognition evaluation.
 *
 * <p>Rules version 2 retains the existing raw, identity and final path maps,
 * while also exposing the axis components used by component-aware
 * qualification.</p>
 */
public final class RecognitionEvaluation {

    private final RecognitionDimensions dimensions;
    private final RecognitionPathComponents components;

    /*
     * Scores before identity strength is applied.
     *
     * These represent paths the player has genuinely lived toward after the
     * component evidence gate is applied.
     */
    private final Map<RecognitionPath, Double> rawPathScores;

    /*
     * Identity resonance assigned to each component-supported path.
     */
    private final Map<RecognitionPath, Double> identityBoosts;

    /*
     * Raw affinity + identity resonance.
     */
    private final Map<RecognitionPath, Double> pathScores;

    private final Optional<RecognitionPathSelection> selection;
    private final RecognitionBalanceSnapshot balance;
    private final long balanceRevision;

    public RecognitionEvaluation(
            RecognitionDimensions dimensions,
            RecognitionPathComponents components,
            Map<RecognitionPath, Double> rawPathScores,
            Map<RecognitionPath, Double> identityBoosts,
            Map<RecognitionPath, Double> pathScores,
            Optional<RecognitionPathSelection> selection,
            RecognitionBalanceSnapshot balance,
            long balanceRevision
    ) {
        this.dimensions =
                dimensions == null
                        ? emptyDimensions()
                        : dimensions;

        this.balance =
                balance == null
                        ? RecognitionBalanceSnapshot
                        .createDefaults()
                        : balance;

        this.components =
                components == null
                        ? RecognitionPathComponents
                        .calculate(
                                this.dimensions,
                                this.balance
                        )
                        : components;

        this.rawPathScores =
                sanitizeScores(
                        rawPathScores
                );

        this.identityBoosts =
                sanitizeScores(
                        identityBoosts
                );

        this.pathScores =
                sanitizeScores(
                        pathScores
                );

        this.selection =
                selection == null
                        ? Optional.empty()
                        : selection;

        this.balanceRevision =
                Math.max(
                        0L,
                        balanceRevision
                );
    }

    /**
     * Compatibility constructor for Packet 6F/6G.2 call sites.
     */
    public RecognitionEvaluation(
            RecognitionDimensions dimensions,
            Map<RecognitionPath, Double> rawPathScores,
            Map<RecognitionPath, Double> identityBoosts,
            Map<RecognitionPath, Double> pathScores,
            Optional<RecognitionPathSelection> selection,
            RecognitionBalanceSnapshot balance,
            long balanceRevision
    ) {
        this(
                dimensions,
                RecognitionPathComponents.calculate(
                        dimensions,
                        balance
                ),
                rawPathScores,
                identityBoosts,
                pathScores,
                selection,
                balance,
                balanceRevision
        );
    }

    /**
     * Compatibility constructor for older call sites without a balance
     * snapshot.
     */
    public RecognitionEvaluation(
            RecognitionDimensions dimensions,
            Map<RecognitionPath, Double> rawPathScores,
            Map<RecognitionPath, Double> identityBoosts,
            Map<RecognitionPath, Double> pathScores,
            Optional<RecognitionPathSelection> selection
    ) {
        this(
                dimensions,
                RecognitionPathComponents.calculate(
                        dimensions,
                        RecognitionBalanceSnapshot
                                .createDefaults()
                ),
                rawPathScores,
                identityBoosts,
                pathScores,
                selection,
                RecognitionBalanceSnapshot
                        .createDefaults(),
                0L
        );
    }

    /**
     * Compatibility constructor for the original score-only model.
     */
    public RecognitionEvaluation(
            RecognitionDimensions dimensions,
            Map<RecognitionPath, Double> pathScores,
            Optional<RecognitionPathSelection> selection
    ) {
        this(
                dimensions,
                RecognitionPathComponents.calculate(
                        dimensions,
                        RecognitionBalanceSnapshot
                                .createDefaults()
                ),
                pathScores,
                createEmptyScoreMap(),
                pathScores,
                selection,
                RecognitionBalanceSnapshot
                        .createDefaults(),
                0L
        );
    }

    public RecognitionDimensions getDimensions() {
        return dimensions;
    }

    public RecognitionPathComponents getComponents() {
        return components;
    }

    public Map<RecognitionPath, Double> getRawPathScores() {
        return rawPathScores;
    }

    public double getRawPathScore(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return rawPathScores.getOrDefault(
                path,
                0.0D
        );
    }

    public Map<RecognitionPath, Double> getIdentityBoosts() {
        return identityBoosts;
    }

    public double getIdentityBoost(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return identityBoosts.getOrDefault(
                path,
                0.0D
        );
    }

    public Map<RecognitionPath, Double> getPathScores() {
        return pathScores;
    }

    public double getPathScore(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return pathScores.getOrDefault(
                path,
                0.0D
        );
    }

    public Optional<RecognitionPathSelection> getSelection() {
        return selection;
    }

    public RecognitionBalanceSnapshot getBalance() {
        return balance;
    }

    public long getBalanceRevision() {
        return balanceRevision;
    }

    public RecognitionComponentQualificationRules
    getComponentQualificationRules() {
        return RecognitionComponentQualificationRules
                .from(
                        balance.selection()
                );
    }

    private static RecognitionDimensions emptyDimensions() {
        return new RecognitionDimensions(
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
    }

    private static Map<RecognitionPath, Double> sanitizeScores(
            Map<RecognitionPath, Double> source
    ) {
        EnumMap<RecognitionPath, Double> result =
                new EnumMap<>(
                        RecognitionPath.class
                );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            double score = 0.0D;

            if (source != null) {
                Double rawScore =
                        source.get(path);

                if (rawScore != null
                        && Double.isFinite(rawScore)
                        && rawScore > 0.0D) {

                    score = rawScore;
                }
            }

            result.put(
                    path,
                    score
            );
        }

        return Collections.unmodifiableMap(
                result
        );
    }

    private static Map<RecognitionPath, Double>
    createEmptyScoreMap() {
        EnumMap<RecognitionPath, Double> result =
                new EnumMap<>(
                        RecognitionPath.class
                );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            result.put(
                    path,
                    0.0D
            );
        }

        return result;
    }
}