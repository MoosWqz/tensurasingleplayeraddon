package com.mooswqz.moostensuraaddon.skill;

import com.mooswqz.moostensuraaddon.util.AuthorityActionService;
import com.mooswqz.moostensuraaddon.util.UltimateSkillActions;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class AbsoluteGovernanceSkill extends Skill {

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "textures/mob_effect/bad_omen.png"
            );

    public static final int MODE_COUNT = 5;
    public static final int MODE_INVEST = 0;
    public static final int MODE_MASS_GRANT = 1;
    public static final int MODE_GLOBAL_TAKE_BACK = 2;
    public static final int MODE_SEIZE_SKILL = 3;
    public static final int MODE_RANGED_SKILL_VIEW = 4;

    public AbsoluteGovernanceSkill() {
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
    public int nextMode(
            LivingEntity entity,
            ManasSkillInstance instance,
            int mode,
            boolean reverse
    ) {
        int current = normalizeMode(mode);
        int next = reverse ? current - 1 : current + 1;

        if (next < 0) {
            return MODE_COUNT - 1;
        }

        return next >= MODE_COUNT ? 0 : next;
    }

    @Override
    public String getModeId(
            ManasSkillInstance instance,
            int mode
    ) {
        return switch (normalizeMode(mode)) {
            case MODE_INVEST -> "invest";
            case MODE_MASS_GRANT -> "mass_grant";
            case MODE_GLOBAL_TAKE_BACK -> "global_take_back";
            case MODE_SEIZE_SKILL -> "seize_skill";
            case MODE_RANGED_SKILL_VIEW -> "ranged_skill_view";
            default -> "invest";
        };
    }

    @Override
    public Component getModeName(
            ManasSkillInstance instance,
            int mode
    ) {
        return switch (normalizeMode(mode)) {
            case MODE_INVEST -> Component.literal("Invest");
            case MODE_MASS_GRANT -> Component.literal("Mass Grant");
            case MODE_GLOBAL_TAKE_BACK -> Component.literal("Global Take Back");
            case MODE_SEIZE_SKILL -> Component.literal("Seize");
            case MODE_RANGED_SKILL_VIEW -> Component.literal("List Skills");
            default -> Component.literal("Invest");
        };
    }

    @Override
    public void onPressed(
            ManasSkillInstance instance,
            LivingEntity entity,
            int slot,
            int mode
    ) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        switch (normalizeMode(mode)) {
            case MODE_INVEST ->
                    AuthorityActionService.openUltimateBestow(player, false);
            case MODE_MASS_GRANT ->
                    AuthorityActionService.openUltimateMassGrant(player, false);
            case MODE_GLOBAL_TAKE_BACK ->
                    AuthorityActionService.openUltimateTakeBack(player, false);
            case MODE_SEIZE_SKILL ->
                    UltimateSkillActions.openSeizeSkillSelection(player);
            case MODE_RANGED_SKILL_VIEW ->
                    UltimateSkillActions.rangedSkillView(player, false);
            default ->
                    AuthorityActionService.openUltimateBestow(player, false);
        }
    }

    private static int normalizeMode(int mode) {
        return mode >= 0 && mode < MODE_COUNT
                ? mode
                : MODE_INVEST;
    }
}