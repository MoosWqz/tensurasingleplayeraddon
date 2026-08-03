package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import com.mooswqz.moostensuraaddon.util.GranterActions;
import com.mooswqz.moostensuraaddon.util.SkillCategoryHelper;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts the player's learned skills into the shared presentation model.
 */
public final class GranterSkillUiEntryFactory {

    private GranterSkillUiEntryFactory() {
    }

    public static BuildResult build(
            LocalPlayer player
    ) {
        if (player == null) {
            return BuildResult.empty();
        }

        var skillStorage = SkillAPI.getSkillsFrom(player);

        Optional<ManasSkillInstance> benevolent =
                skillStorage.getSkill(
                        SkillRegistry.BENEVOLENT_EMPOWERMENT
                                .get()
                                .getRegistryName()
                );
        Optional<ManasSkillInstance> governance =
                skillStorage.getSkill(
                        SkillRegistry.ABSOLUTE_GOVERNANCE
                                .get()
                                .getRegistryName()
                );
        Optional<ManasSkillInstance> granter =
                skillStorage.getSkill(
                        SkillRegistry.GRANTER
                                .get()
                                .getRegistryName()
                );

        boolean evolvedAuthority = benevolent.isPresent()
                || governance.isPresent();

        ManasSkillInstance authorityInstance = benevolent
                .orElseGet(() -> governance
                        .orElseGet(() -> granter
                                .orElse(null)));

        Optional<String> currentSelection = authorityInstance == null
                ? Optional.empty()
                : GranterActions
                .getSelectedSkillId(authorityInstance)
                .map(ResourceLocation::toString);

        Component authorityName;

        if (benevolent.isPresent()) {
            authorityName = SkillUiText.component(
                    "authority.benevolent"
            );
        } else if (governance.isPresent()) {
            authorityName = SkillUiText.component(
                    "authority.governance"
            );
        } else {
            authorityName = SkillUiText.component("authority.granter");
        }

        List<SkillUiEntry> entries = new ArrayList<>();

        for (ManasSkillInstance instance :
                skillStorage.getLearnedSkills()) {
            if (instance == null) {
                continue;
            }

            ResourceLocation skillId = instance.getSkillId();

            if (skillId == null) {
                continue;
            }

            Component displayName = instance.getDisplayName();
            boolean grantable = GranterActions
                    .isGrantableSkill(skillId);
            boolean intrinsic = SkillCategoryHelper
                    .isIntrinsic(
                            instance,
                            skillId,
                            displayName
                    );
            boolean mastered = instance.isMastered(player);

            if (!shouldIncludeForSelection(
                    grantable,
                    intrinsic,
                    mastered,
                    evolvedAuthority
            )) {
                continue;
            }

            SkillUiCategory category = toUiCategory(
                    SkillCategoryHelper.getCategory(
                            instance,
                            skillId,
                            displayName
                    )
            );

            List<Component> details = new ArrayList<>();

            if (mastered) {
                details.add(
                        SkillUiText.component(
                                "details.eligible_mastery"
                        )
                );
            } else {
                details.add(
                        SkillUiText.component(
                                "details.ultimate_mastery_bypass"
                        )
                );
            }

            if (currentSelection
                    .filter(skillId.toString()::equals)
                    .isPresent()) {
                details.add(
                        SkillUiText.component(
                                "details.current_authority_selection"
                        )
                );
            }

            entries.add(
                    new SkillUiEntry(
                            skillId.toString(),
                            displayName,
                            category,
                            true,
                            mastered,
                            Component.empty(),
                            Component.empty(),
                            details,
                            category.defaultAccentColor()
                    )
            );
        }

        return new BuildResult(
                List.copyOf(entries),
                currentSelection,
                evolvedAuthority,
                authorityName
        );
    }

    public static boolean shouldIncludeForSelection(
            boolean grantable,
            boolean intrinsic,
            boolean mastered,
            boolean evolvedAuthority
    ) {
        return grantable
                && !intrinsic
                && (mastered || evolvedAuthority);
    }

    public static SkillUiCategory toUiCategory(
            SkillCategoryHelper.SkillCategory category
    ) {
        if (category == null) {
            return SkillUiCategory.OTHER;
        }

        return switch (category) {
            case UNIQUE -> SkillUiCategory.UNIQUE;
            case EXTRA -> SkillUiCategory.EXTRA;
            case BASIC -> SkillUiCategory.BASIC;
            case RESISTANCE -> SkillUiCategory.RESISTANCE;
            case OTHER -> SkillUiCategory.OTHER;
        };
    }

    public record BuildResult(
            List<SkillUiEntry> entries,
            Optional<String> currentSelection,
            boolean evolvedAuthority,
            Component authorityName
    ) {

        public BuildResult {
            entries = entries == null
                    ? List.of()
                    : List.copyOf(entries);
            currentSelection = currentSelection == null
                    ? Optional.empty()
                    : currentSelection;
            authorityName = authorityName == null
                    ? SkillUiText.component("authority.granter")
                    : authorityName;
        }

        public static BuildResult empty() {
            return new BuildResult(
                    List.of(),
                    Optional.empty(),
                    false,
                    SkillUiText.component("authority.granter")
            );
        }
    }
}