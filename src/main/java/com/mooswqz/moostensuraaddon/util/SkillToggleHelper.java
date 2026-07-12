package com.mooswqz.moostensuraaddon.util;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import net.minecraft.world.entity.LivingEntity;

public final class SkillToggleHelper {
    private SkillToggleHelper() {
    }

    public static void autoToggleIfPossible(LivingEntity owner, ManasSkillInstance instance) {
        if (owner == null || instance == null) {
            return;
        }

        if (instance.isToggled()) {
            return;
        }

        if (!instance.canBeToggled(owner)) {
            return;
        }

        if (!instance.canInteractSkill(owner)) {
            return;
        }

        instance.setToggled(true);
        instance.onToggleOn(owner);

        Skills skills = SkillAPI.getSkillsFrom(owner);
        skills.checkAndMarkDirty(instance);
    }
}