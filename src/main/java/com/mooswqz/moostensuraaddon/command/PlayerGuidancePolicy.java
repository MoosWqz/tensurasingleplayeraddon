package com.mooswqz.moostensuraaddon.command;

/**
 * Pure progression-stage selection for the player-facing guide.
 */
public final class PlayerGuidancePolicy {

    private PlayerGuidancePolicy() {
    }

    public static Stage resolve(
            boolean resetGuardActive,
            boolean namedOrRecognized,
            boolean hasSage,
            boolean hasGreatSage,
            boolean hasGranter,
            boolean hasBenevolentEmpowerment,
            boolean hasAbsoluteGovernance
    ) {
        if (resetGuardActive) {
            return Stage.INCARNATION_REBUILDING;
        }

        if (hasAbsoluteGovernance) {
            return Stage.ABSOLUTE_GOVERNANCE;
        }

        if (hasBenevolentEmpowerment) {
            return Stage.BENEVOLENT_EMPOWERMENT;
        }

        if (hasGranter) {
            return Stage.EVOLVE_AUTHORITY;
        }

        if (!namedOrRecognized) {
            return Stage.FORM_IDENTITY;
        }

        if (hasGreatSage) {
            return Stage.AWAKEN_GRANTER;
        }

        if (hasSage) {
            return Stage.AWAKEN_GREAT_SAGE;
        }

        return Stage.SEEK_SAGE;
    }

    public enum Stage {
        INCARNATION_REBUILDING("incarnation_rebuilding"),
        FORM_IDENTITY("form_identity"),
        SEEK_SAGE("seek_sage"),
        AWAKEN_GREAT_SAGE("awaken_great_sage"),
        AWAKEN_GRANTER("awaken_granter"),
        EVOLVE_AUTHORITY("evolve_authority"),
        BENEVOLENT_EMPOWERMENT("benevolent_empowerment"),
        ABSOLUTE_GOVERNANCE("absolute_governance");

        private final String id;

        Stage(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}