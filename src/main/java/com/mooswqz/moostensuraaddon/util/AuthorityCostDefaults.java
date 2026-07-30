package com.mooswqz.moostensuraaddon.util;

public final class AuthorityCostDefaults {

    public static final double GRANTER_GRANT = 50_000.0D;
    public static final double ULTIMATE_EVOLUTION = 250_000.0D;
    public static final double BENEVOLENT_MASS_GRANT = 10_000.0D;
    public static final double GOVERNANCE_MASS_GRANT = 15_000.0D;
    public static final double BENEVOLENT_DIRECT_BASE = 20_000.0D;
    public static final double GOVERNANCE_DIRECT_BASE = 25_000.0D;
    public static final double BENEVOLENT_DIRECT_EXTRA = 100_000.0D;
    public static final double GOVERNANCE_DIRECT_EXTRA = 75_000.0D;
    public static final double BORROW = 50_000.0D;
    public static final double SEIZE = 250_000.0D;

    public static final double OLD_GRANTER_GRANT = 200_000.0D;
    public static final double OLD_ULTIMATE_EVOLUTION = 500_000.0D;
    public static final double OLD_BENEVOLENT_MASS_GRANT = 75_000.0D;
    public static final double OLD_GOVERNANCE_MASS_GRANT = 100_000.0D;
    public static final double OLD_BENEVOLENT_DIRECT_BASE = 100_000.0D;
    public static final double OLD_GOVERNANCE_DIRECT_BASE = 150_000.0D;
    public static final double OLD_BENEVOLENT_DIRECT_EXTRA = 650_000.0D;
    public static final double OLD_GOVERNANCE_DIRECT_EXTRA = 350_000.0D;
    public static final double OLD_BORROW = 150_000.0D;
    public static final double OLD_SEIZE = 250_000.0D;

    private AuthorityCostDefaults() {
    }

    public static double migrateUntouchedDefault(
            double current,
            double oldDefault,
            double newDefault
    ) {
        if (!Double.isFinite(current)
                || !Double.isFinite(oldDefault)
                || !Double.isFinite(newDefault)) {
            return current;
        }

        return approximately(current, oldDefault)
                ? newDefault
                : current;
    }

    public static boolean approximately(
            double first,
            double second
    ) {
        return Double.isFinite(first)
                && Double.isFinite(second)
                && Math.abs(first - second) < 0.0001D;
    }
}