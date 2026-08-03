package com.mooswqz.moostensuraaddon.skill;

import com.mooswqz.moostensuraaddon.util.AuthorityActionService;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class GranterSkill extends Skill {

    private static final ResourceLocation HERO_OF_THE_VILLAGE_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "textures/mob_effect/hero_of_the_village.png"
            );

    public GranterSkill() {
        super(SkillType.UNIQUE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return HERO_OF_THE_VILLAGE_ICON;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return GranterMode.count();
    }

    @Override
    public int nextMode(
            LivingEntity entity,
            ManasSkillInstance instance,
            int mode,
            boolean reverse
    ) {
        return GranterMode.fromId(mode)
                .next(reverse)
                .id();
    }

    @Override
    public String getModeId(
            ManasSkillInstance instance,
            int mode
    ) {
        return "granter."
                + GranterMode.fromId(mode).modeId();
    }

    @Override
    public Component getModeName(
            ManasSkillInstance instance,
            int mode
    ) {
        return switch (GranterMode.fromId(mode)) {
            case GRANT -> Component.translatable(
                    "skill.moostensuraaddon.mode.granter.grant"
            );
            case TAKE_BACK -> Component.translatable(
                    "skill.moostensuraaddon.mode.granter.take_back"
            );
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

        switch (GranterMode.fromId(mode)) {
            case GRANT -> AuthorityActionService.openGranterGrant(
                    player,
                    instance
            );
            case TAKE_BACK -> AuthorityActionService.openGranterTakeBack(
                    player,
                    instance
            );
        }
    }
}