package com.mooswqz.moostensuraaddon.util;

import java.util.Locale;
import java.util.Optional;

public enum AuthorityActionMode {

    GRANTER_GRANT(
            "granter_grant",
            1,
            false,
            false,
            false,
            false
    ),

    GRANTER_TAKE_BACK(
            "granter_take_back",
            1,
            false,
            false,
            true,
            false
    ),

    BENEVOLENT_BESTOW(
            "benevolent_bestow",
            32,
            true,
            false,
            false,
            false
    ),

    GOVERNANCE_INVEST(
            "governance_invest",
            32,
            false,
            true,
            false,
            false
    ),

    BENEVOLENT_MASS_GRANT(
            "benevolent_mass_grant",
            1,
            true,
            false,
            false,
            true
    ),

    GOVERNANCE_MASS_GRANT(
            "governance_mass_grant",
            1,
            false,
            true,
            false,
            true
    ),

    BENEVOLENT_TAKE_BACK(
            "benevolent_take_back",
            1,
            true,
            false,
            true,
            false
    ),

    GOVERNANCE_TAKE_BACK(
            "governance_take_back",
            1,
            false,
            true,
            true,
            false
    );

    private final String id;
    private final int selectionLimit;
    private final boolean benevolent;
    private final boolean governance;
    private final boolean takeBack;
    private final boolean massGrant;

    AuthorityActionMode(
            String id,
            int selectionLimit,
            boolean benevolent,
            boolean governance,
            boolean takeBack,
            boolean massGrant
    ) {
        this.id = id;
        this.selectionLimit = Math.max(1, selectionLimit);
        this.benevolent = benevolent;
        this.governance = governance;
        this.takeBack = takeBack;
        this.massGrant = massGrant;
    }

    public String id() {
        return id;
    }

    public int selectionLimit() {
        return selectionLimit;
    }

    public boolean benevolent() {
        return benevolent;
    }

    public boolean governance() {
        return governance;
    }

    public boolean granter() {
        return !benevolent && !governance;
    }

    public boolean takeBack() {
        return takeBack;
    }

    public boolean grant() {
        return !takeBack;
    }

    public boolean massGrant() {
        return massGrant;
    }

    public boolean directGrant() {
        return grant() && !massGrant;
    }

    public boolean supportsUnmasteredSkills() {
        return this == BENEVOLENT_BESTOW
                || this == GOVERNANCE_INVEST;
    }

    public boolean supportsAllEligibleToggle() {
        return this == BENEVOLENT_TAKE_BACK
                || this == GOVERNANCE_TAKE_BACK;
    }

    public String title() {
        return switch (this) {
            case GRANTER_GRANT -> "Grant Skill";
            case GRANTER_TAKE_BACK -> "Take Back Skill";
            case BENEVOLENT_BESTOW -> "Skill Bestowal";
            case GOVERNANCE_INVEST -> "Skill Investiture";
            case BENEVOLENT_MASS_GRANT,
                 GOVERNANCE_MASS_GRANT -> "Mass Grant";
            case BENEVOLENT_TAKE_BACK -> "Ranged Take Back";
            case GOVERNANCE_TAKE_BACK -> "Global Take Back";
        };
    }

    public String badge() {
        return switch (this) {
            case GRANTER_GRANT -> "GRANT";
            case GRANTER_TAKE_BACK -> "TAKE BACK";
            case BENEVOLENT_BESTOW -> "BESTOW";
            case GOVERNANCE_INVEST -> "INVEST";
            case BENEVOLENT_MASS_GRANT,
                 GOVERNANCE_MASS_GRANT -> "MASS GRANT";
            case BENEVOLENT_TAKE_BACK -> "RANGED RETURN";
            case GOVERNANCE_TAKE_BACK -> "GLOBAL RETURN";
        };
    }

    public String actionButtonLabel(int selectedCount) {
        int safeCount = Math.max(0, selectedCount);

        return switch (this) {
            case GRANTER_GRANT -> "Grant Skill";
            case GRANTER_TAKE_BACK -> "Take Back";
            case BENEVOLENT_BESTOW -> "Bestow " + safeCount
                    + (safeCount == 1 ? " Skill" : " Skills");
            case GOVERNANCE_INVEST -> "Invest " + safeCount
                    + (safeCount == 1 ? " Skill" : " Skills");
            case BENEVOLENT_MASS_GRANT,
                 GOVERNANCE_MASS_GRANT -> "Grant to All";
            case BENEVOLENT_TAKE_BACK,
                 GOVERNANCE_TAKE_BACK -> "Take Back";
        };
    }

    public String subtitle() {
        return switch (this) {
            case GRANTER_GRANT ->
                    "Choose one mastered skill and transfer it immediately.";
            case GRANTER_TAKE_BACK ->
                    "Choose one skill previously granted by you and reclaim it.";
            case BENEVOLENT_BESTOW ->
                    "Transfer one or several skills to one subordinate.";
            case GOVERNANCE_INVEST ->
                    "Invest one or several skills into one subordinate.";
            case BENEVOLENT_MASS_GRANT,
                 GOVERNANCE_MASS_GRANT ->
                    "Choose one mastered skill for every eligible recipient.";
            case BENEVOLENT_TAKE_BACK ->
                    "Reclaim one granted skill from a target or all nearby recipients.";
            case GOVERNANCE_TAKE_BACK ->
                    "Reclaim one granted skill from a target or every loaded recipient in range.";
        };
    }

    public static Optional<AuthorityActionMode> fromId(
            String rawId
    ) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }

        String cleaned = rawId.trim().toLowerCase(Locale.ROOT);

        for (AuthorityActionMode mode : values()) {
            if (mode.id.equals(cleaned)) {
                return Optional.of(mode);
            }
        }

        return Optional.empty();
    }
}