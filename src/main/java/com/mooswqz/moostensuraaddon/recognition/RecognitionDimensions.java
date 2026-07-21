package com.mooswqz.moostensuraaddon.recognition;

public record RecognitionDimensions(
        double good,
        double evil,
        double order,
        double freedom,
        double mastery,
        double discovery,
        double identityStrength
) {

    public RecognitionDimensions {
        good = sanitize(good);
        evil = sanitize(evil);
        order = sanitize(order);
        freedom = sanitize(freedom);
        mastery = sanitize(mastery);
        discovery = sanitize(discovery);
        identityStrength = sanitize(identityStrength);
    }

    public double getGoodResonance() {
        return Math.max(0.0D, good - evil * 0.75D);
    }

    public double getEvilResonance() {
        return Math.max(0.0D, evil - good * 0.75D);
    }

    public double getLawfulResonance() {
        return Math.max(0.0D, order - freedom * 0.75D);
    }

    public double getChaoticResonance() {
        return Math.max(0.0D, freedom - order * 0.75D);
    }

    public double getMoralBalanceFactor() {
        return calculateBalanceFactor(good, evil);
    }

    public double getBehaviourBalanceFactor() {
        return calculateBalanceFactor(order, freedom);
    }

    private static double calculateBalanceFactor(
            double first,
            double second
    ) {
        double total = first + second;

        if (total <= 0.0D) {
            return 1.0D;
        }

        double difference = Math.abs(first - second);

        return clamp(
                1.0D - difference / (total + 10.0D),
                0.0D,
                1.0D
        );
    }

    private static double sanitize(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return 0.0D;
        }

        return value;
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }
}