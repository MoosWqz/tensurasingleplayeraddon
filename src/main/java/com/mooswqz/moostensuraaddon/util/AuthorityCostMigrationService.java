package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;

public final class AuthorityCostMigrationService {

    private AuthorityCostMigrationService() {
    }

    public static boolean applyRecommendedDefaults() {
        boolean changed = false;

        changed |= migrate(
                MoosTensuraConfig.GRANTER_GRANT_MAGICULE_COST,
                AuthorityCostDefaults.OLD_GRANTER_GRANT,
                AuthorityCostDefaults.GRANTER_GRANT
        );
        changed |= migrate(
                MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_MAGICULES,
                AuthorityCostDefaults.OLD_ULTIMATE_EVOLUTION,
                AuthorityCostDefaults.ULTIMATE_EVOLUTION
        );
        changed |= migrate(
                MoosTensuraConfig.BENEVOLENT_MASS_GRANT_COST_PER_TARGET,
                AuthorityCostDefaults.OLD_BENEVOLENT_MASS_GRANT,
                AuthorityCostDefaults.BENEVOLENT_MASS_GRANT
        );
        changed |= migrate(
                MoosTensuraConfig.ABSOLUTE_MASS_GRANT_COST_PER_TARGET,
                AuthorityCostDefaults.OLD_GOVERNANCE_MASS_GRANT,
                AuthorityCostDefaults.GOVERNANCE_MASS_GRANT
        );
        changed |= migrate(
                MoosTensuraConfig.BENEVOLENT_GRANT_WITHOUT_MASTERY_BASE_COST,
                AuthorityCostDefaults.OLD_BENEVOLENT_DIRECT_BASE,
                AuthorityCostDefaults.BENEVOLENT_DIRECT_BASE
        );
        changed |= migrate(
                MoosTensuraConfig.ABSOLUTE_GRANT_WITHOUT_MASTERY_BASE_COST,
                AuthorityCostDefaults.OLD_GOVERNANCE_DIRECT_BASE,
                AuthorityCostDefaults.GOVERNANCE_DIRECT_BASE
        );
        changed |= migrate(
                MoosTensuraConfig.BENEVOLENT_GRANT_WITHOUT_MASTERY_EXTRA_COST,
                AuthorityCostDefaults.OLD_BENEVOLENT_DIRECT_EXTRA,
                AuthorityCostDefaults.BENEVOLENT_DIRECT_EXTRA
        );
        changed |= migrate(
                MoosTensuraConfig.ABSOLUTE_GRANT_WITHOUT_MASTERY_EXTRA_COST,
                AuthorityCostDefaults.OLD_GOVERNANCE_DIRECT_EXTRA,
                AuthorityCostDefaults.GOVERNANCE_DIRECT_EXTRA
        );
        changed |= migrate(
                MoosTensuraConfig.BORROW_COST_PER_SKILL,
                AuthorityCostDefaults.OLD_BORROW,
                AuthorityCostDefaults.BORROW
        );
        changed |= migrate(
                MoosTensuraConfig.SEIZE_COST_PER_SKILL,
                AuthorityCostDefaults.OLD_SEIZE,
                AuthorityCostDefaults.SEIZE
        );

        if (changed) {
            MoosTensuraConfig.SPEC.save();
        }

        return changed;
    }

    private static boolean migrate(
            net.neoforged.neoforge.common.ModConfigSpec.DoubleValue value,
            double oldDefault,
            double newDefault
    ) {
        if (value == null) {
            return false;
        }

        double current = value.get();
        double migrated = AuthorityCostDefaults.migrateUntouchedDefault(
                current,
                oldDefault,
                newDefault
        );

        if (AuthorityCostDefaults.approximately(current, migrated)) {
            return false;
        }

        value.set(migrated);
        return true;
    }
}