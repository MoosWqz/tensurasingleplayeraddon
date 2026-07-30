package com.mooswqz.moostensuraaddon.client.screen.skillui;

import java.util.List;

public final class UltimateConfirmationPolicy {

    private UltimateConfirmationPolicy() {
    }

    public static double totalCost(
            double costPerSkill,
            int selectedSkills
    ) {
        double safeCost = sanitizeCost(costPerSkill);
        int safeCount = Math.max(0, selectedSkills);
        double total = safeCost * safeCount;

        return Double.isFinite(total)
                ? Math.max(0.0D, total)
                : Double.MAX_VALUE;
    }

    public static double combinedSeizeRisk(
            double chancePerSkill,
            double maximumChance,
            int selectedSkills
    ) {
        double perSkill = clampChance(chancePerSkill);
        double maximum = clampChance(maximumChance);
        int safeCount = Math.max(0, selectedSkills);

        return Math.min(
                maximum,
                perSkill * safeCount
        );
    }

    public static ChanceRange chanceRange(
            List<Double> chances
    ) {
        if (chances == null || chances.isEmpty()) {
            return new ChanceRange(0.0D, 0.0D);
        }

        double minimum = 1.0D;
        double maximum = 0.0D;
        boolean found = false;

        for (Double chance : chances) {
            if (chance == null) {
                continue;
            }

            double safeChance = clampChance(chance);
            minimum = Math.min(minimum, safeChance);
            maximum = Math.max(maximum, safeChance);
            found = true;
        }

        return found
                ? new ChanceRange(minimum, maximum)
                : new ChanceRange(0.0D, 0.0D);
    }

    public static boolean affordable(
            double availableMagicules,
            double requiredMagicules
    ) {
        return sanitizeCost(availableMagicules)
                >= sanitizeCost(requiredMagicules);
    }

    public static int visibleSkillRows(
            int availableHeight,
            boolean compact
    ) {
        int rowHeight = compact ? 11 : 13;

        return Math.max(
                1,
                Math.min(
                        compact ? 5 : 10,
                        Math.max(1, availableHeight) / rowHeight
                )
        );
    }

    public static double sanitizeCost(
            double value
    ) {
        return Double.isFinite(value) && value > 0.0D
                ? value
                : 0.0D;
    }

    public static double clampChance(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(1.0D, value)
        );
    }

    public record ChanceRange(
            double minimum,
            double maximum
    ) {
        public ChanceRange {
            minimum = clampChance(minimum);
            maximum = Math.max(
                    minimum,
                    clampChance(maximum)
            );
        }
    }
}