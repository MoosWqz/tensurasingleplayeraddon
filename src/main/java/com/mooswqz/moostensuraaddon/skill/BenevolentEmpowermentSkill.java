package com.mooswqz.moostensuraaddon.skill;

import com.mooswqz.moostensuraaddon.util.UltimateSkillActions;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class BenevolentEmpowermentSkill extends Skill {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/mob_effect/absorption.png"
    );

    public static final int MODE_COUNT = 6;

    public static final int MODE_CHOOSE_SKILL = 0;
    public static final int MODE_MASS_GRANT = 1;
    public static final int MODE_RANGED_TAKE_BACK = 2;
    public static final int MODE_BORROW_SKILL = 3;
    public static final int MODE_GRANT_WITHOUT_MASTERY = 4;
    public static final int MODE_RANGED_SKILL_VIEW = 5;

    public BenevolentEmpowermentSkill() {
        super(SkillType.ULTIMATE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ICON;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_COUNT;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int currentMode, boolean reverse) {
        return getNextMode(currentMode, reverse);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (normalizeMode(mode)) {
            case MODE_CHOOSE_SKILL -> "choose_skill";
            case MODE_MASS_GRANT -> "mass_grant";
            case MODE_RANGED_TAKE_BACK -> "ranged_take_back";
            case MODE_BORROW_SKILL -> "borrow_skill";
            case MODE_GRANT_WITHOUT_MASTERY -> "grant_without_mastery";
            case MODE_RANGED_SKILL_VIEW -> "ranged_skill_view";
            default -> "choose_skill";
        };
    }

    @Override
    public Component getModeName(ManasSkillInstance instance, int mode) {
        return switch (normalizeMode(mode)) {
            case MODE_CHOOSE_SKILL -> Component.translatable("moostensuraaddon.skill.mode.benevolent_empowerment.choose_skill");
            case MODE_MASS_GRANT -> Component.translatable("moostensuraaddon.skill.mode.benevolent_empowerment.mass_grant");
            case MODE_RANGED_TAKE_BACK -> Component.translatable("moostensuraaddon.skill.mode.benevolent_empowerment.ranged_take_back");
            case MODE_BORROW_SKILL -> Component.translatable("moostensuraaddon.skill.mode.benevolent_empowerment.borrow_skill");
            case MODE_GRANT_WITHOUT_MASTERY -> Component.translatable("moostensuraaddon.skill.mode.benevolent_empowerment.grant_without_mastery");
            case MODE_RANGED_SKILL_VIEW -> Component.translatable("moostensuraaddon.skill.mode.benevolent_empowerment.ranged_skill_view");
            default -> Component.translatable("moostensuraaddon.skill.mode.benevolent_empowerment.choose_skill");
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        switch (normalizeMode(mode)) {
            case MODE_CHOOSE_SKILL -> UltimateSkillActions.chooseSkill(player);
            case MODE_MASS_GRANT -> UltimateSkillActions.massGrant(player, instance, true);
            case MODE_RANGED_TAKE_BACK -> UltimateSkillActions.rangedTakeBack(player, instance, true);
            case MODE_BORROW_SKILL -> UltimateSkillActions.openBorrowSkillSelection(player);
            case MODE_GRANT_WITHOUT_MASTERY -> UltimateSkillActions.grantWithoutMastery(player, instance, true);
            case MODE_RANGED_SKILL_VIEW -> UltimateSkillActions.rangedSkillView(player, true);
            default -> UltimateSkillActions.chooseSkill(player);
        }
    }

    private static int getNextMode(int currentMode, boolean reverse) {
        int normalizedCurrentMode = normalizeMode(currentMode);
        int nextMode = reverse ? normalizedCurrentMode - 1 : normalizedCurrentMode + 1;

        if (nextMode < 0) {
            return MODE_COUNT - 1;
        }

        if (nextMode >= MODE_COUNT) {
            return 0;
        }

        return nextMode;
    }

    private static int normalizeMode(int mode) {
        if (mode < 0 || mode >= MODE_COUNT) {
            return MODE_CHOOSE_SKILL;
        }

        return mode;
    }
}