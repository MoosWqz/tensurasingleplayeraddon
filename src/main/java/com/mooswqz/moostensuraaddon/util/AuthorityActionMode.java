package com.mooswqz.moostensuraaddon.util;

import net.minecraft.network.chat.Component;

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

    public Component titleComponent() {
        return Component.translatable(
                "screen.moostensuraaddon.skill_ui.action."
                        + id
                        + ".title"
        );
    }

    public String title() {
        return titleComponent().getString();
    }

    public Component badgeComponent() {
        return Component.translatable(
                "screen.moostensuraaddon.skill_ui.action."
                        + id
                        + ".badge"
        );
    }

    public String badge() {
        return badgeComponent().getString();
    }

    public Component actionButtonComponent(
            int selectedCount
    ) {
        int safeCount = Math.max(0, selectedCount);
        String suffix = switch (this) {
            case BENEVOLENT_BESTOW,
                 GOVERNANCE_INVEST -> safeCount == 1
                    ? ".button_one"
                    : ".button_many";
            default -> ".button";
        };

        return Component.translatable(
                "screen.moostensuraaddon.skill_ui.action."
                        + id
                        + suffix,
                safeCount
        );
    }

    public String actionButtonLabel(
            int selectedCount
    ) {
        return actionButtonComponent(selectedCount).getString();
    }

    public Component subtitleComponent() {
        return Component.translatable(
                "screen.moostensuraaddon.skill_ui.action."
                        + id
                        + ".subtitle"
        );
    }

    public String subtitle() {
        return subtitleComponent().getString();
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