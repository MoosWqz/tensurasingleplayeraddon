package com.mooswqz.moostensuraaddon.skill;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SkillRegistry {
    public static final DeferredRegister<ManasSkill> SKILLS =
            DeferredRegister.create(SkillAPI.getSkillRegistryKey(), MoosTensuraAddon.MODID);

    public static final DeferredHolder<ManasSkill, GranterSkill> GRANTER =
            SKILLS.register("granter", GranterSkill::new);

    public static final DeferredHolder<ManasSkill, BenevolentEmpowermentSkill> BENEVOLENT_EMPOWERMENT =
            SKILLS.register("benevolent_empowerment", BenevolentEmpowermentSkill::new);

    public static final DeferredHolder<ManasSkill, AbsoluteGovernanceSkill> ABSOLUTE_GOVERNANCE =
            SKILLS.register("absolute_governance", AbsoluteGovernanceSkill::new);
}