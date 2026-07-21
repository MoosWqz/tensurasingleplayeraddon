package com.mooswqz.moostensuraaddon.recognition;

/**
 * Immutable axis components used by component-aware recognition selection.
 *
 * <p>Neutral morality is active evidence: moral balance and meaningful
 * discovery. Neutral behaviour is split into active balance and passive
 * posture. Only {@link #activeNeutralBehaviour()} is allowed to establish or
 * Pure-qualify a path. Passive posture remains available to diagnostics and
 * the progress screen as a low-level hint.</p>
 */
public record RecognitionPathComponents(
        double good,
        double neutralMorality,
        double evil,
        double lawful,
        double activeNeutralBehaviour,
        double passiveNeutralPosture,
        double displayNeutralBehaviour,
        double chaotic,
        double neutralMoralBalance,
        double neutralMoralDiscovery
) {

    public RecognitionPathComponents {
        good = sanitize(good);
        neutralMorality = sanitize(neutralMorality);
        evil = sanitize(evil);
        lawful = sanitize(lawful);
        activeNeutralBehaviour =
                sanitize(activeNeutralBehaviour);
        passiveNeutralPosture =
                sanitize(passiveNeutralPosture);
        displayNeutralBehaviour =
                Math.max(
                        activeNeutralBehaviour,
                        sanitize(displayNeutralBehaviour)
                );
        chaotic = sanitize(chaotic);
        neutralMoralBalance =
                sanitize(neutralMoralBalance);
        neutralMoralDiscovery =
                sanitize(neutralMoralDiscovery);
    }

    public static RecognitionPathComponents calculate(
            RecognitionDimensions dimensions,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionDimensions safeDimensions =
                dimensions == null
                        ? new RecognitionDimensions(
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D
                )
                        : dimensions;

        RecognitionBalanceSnapshot safeBalance =
                balance == null
                        ? RecognitionBalanceSnapshot
                        .createDefaults()
                        : balance;

        RecognitionBalanceSnapshot.NeutralMorality morality =
                safeBalance.neutrality()
                        .morality();

        RecognitionBalanceSnapshot.NeutralBehaviour behaviour =
                safeBalance.neutrality()
                        .behaviour();

        double neutralMoralBalance =
                Math.min(
                        safeDimensions.good(),
                        safeDimensions.evil()
                ) * morality.balanceWeight();

        double moralVolume =
                safeDimensions.good()
                        + safeDimensions.evil();

        double moralQuietness =
                1.0D / (
                        1.0D
                                + moralVolume
                                / morality.moralVolumeDivisor()
                );

        double neutralMoralDiscovery =
                safeDimensions.discovery()
                        * morality.discoveryWeight()
                        * moralQuietness;

        double neutralMorality =
                Math.min(
                        morality.maximum(),
                        sanitize(
                                neutralMoralBalance
                                        + neutralMoralDiscovery
                        )
                );

        double activeNeutralBehaviour =
                Math.min(
                        behaviour.maximum(),
                        sanitize(
                                Math.min(
                                        safeDimensions.order(),
                                        safeDimensions.freedom()
                                ) * behaviour.balanceWeight()
                        )
                );

        double behaviourVolume =
                safeDimensions.order()
                        + safeDimensions.freedom();

        double passiveNeutralPosture =
                Math.min(
                        behaviour.maximum(),
                        sanitize(
                                behaviour.postureBase()
                                        / (
                                        1.0D
                                                + behaviourVolume
                                                / behaviour
                                                .behaviourVolumeDivisor()
                                )
                        )
                );

        double displayNeutralBehaviour =
                Math.min(
                        behaviour.maximum(),
                        sanitize(
                                activeNeutralBehaviour
                                        + passiveNeutralPosture
                        )
                );

        return new RecognitionPathComponents(
                safeDimensions.getGoodResonance(),
                neutralMorality,
                safeDimensions.getEvilResonance(),
                safeDimensions.getLawfulResonance(),
                activeNeutralBehaviour,
                passiveNeutralPosture,
                displayNeutralBehaviour,
                safeDimensions.getChaoticResonance(),
                neutralMoralBalance,
                neutralMoralDiscovery
        );
    }

    public static RecognitionPathComponents empty() {
        return calculate(
                null,
                null
        );
    }

    public double moralityFor(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return switch (path.getMorality()) {
            case GOOD -> good;
            case NEUTRAL -> neutralMorality;
            case EVIL -> evil;
        };
    }

    public double temperamentFor(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return switch (path.getTemperament()) {
            case LAWFUL -> lawful;
            case NEUTRAL -> activeNeutralBehaviour;
            case CHAOTIC -> chaotic;
        };
    }

    public double displayTemperamentFor(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return switch (path.getTemperament()) {
            case LAWFUL -> lawful;
            case NEUTRAL -> displayNeutralBehaviour;
            case CHAOTIC -> chaotic;
        };
    }

    public double strongestCompetingMorality(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return switch (path.getMorality()) {
            case GOOD ->
                    Math.max(
                            neutralMorality,
                            evil
                    );

            case NEUTRAL ->
                    Math.max(
                            good,
                            evil
                    );

            case EVIL ->
                    Math.max(
                            good,
                            neutralMorality
                    );
        };
    }

    public double strongestCompetingTemperament(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0.0D;
        }

        return switch (path.getTemperament()) {
            case LAWFUL ->
                    Math.max(
                            activeNeutralBehaviour,
                            chaotic
                    );

            case NEUTRAL ->
                    Math.max(
                            lawful,
                            chaotic
                    );

            case CHAOTIC ->
                    Math.max(
                            lawful,
                            activeNeutralBehaviour
                    );
        };
    }

    public boolean hasEstablishedEvidence(
            RecognitionPath path,
            RecognitionComponentQualificationRules rules
    ) {
        RecognitionComponentQualificationRules safeRules =
                rules == null
                        ? RecognitionComponentQualificationRules
                        .defaults()
                        : rules;

        return moralityFor(path)
                >= safeRules.establishedComponentMinimum()
                && temperamentFor(path)
                >= safeRules.establishedComponentMinimum();
    }

    public boolean hasPureEvidence(
            RecognitionPath path,
            RecognitionComponentQualificationRules rules
    ) {
        RecognitionComponentQualificationRules safeRules =
                rules == null
                        ? RecognitionComponentQualificationRules
                        .defaults()
                        : rules;

        double morality =
                moralityFor(path);

        double temperament =
                temperamentFor(path);

        if (morality
                < safeRules.pureComponentMinimum()
                || temperament
                < safeRules.pureComponentMinimum()) {

            return false;
        }

        return reachesDominance(
                morality,
                strongestCompetingMorality(path),
                safeRules.moralityDominanceRatio()
        ) && reachesDominance(
                temperament,
                strongestCompetingTemperament(path),
                safeRules.temperamentDominanceRatio()
        );
    }

    public double moralityDominance(
            RecognitionPath path
    ) {
        return dominance(
                moralityFor(path),
                strongestCompetingMorality(path)
        );
    }

    public double temperamentDominance(
            RecognitionPath path
    ) {
        return dominance(
                temperamentFor(path),
                strongestCompetingTemperament(path)
        );
    }

    private static boolean reachesDominance(
            double target,
            double competitor,
            double ratio
    ) {
        if (target <= 0.0D) {
            return false;
        }

        if (competitor <= 0.0D) {
            return true;
        }

        return target
                >= competitor
                * Math.max(
                1.0D,
                ratio
        );
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

    private static double sanitize(
            double value
    ) {
        return !Double.isFinite(value)
                || value < 0.0D
                ? 0.0D
                : value;
    }
}
