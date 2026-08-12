package com.mooswqz.moostensuraaddon.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class MoosTensuraConfig {
    public static final int CURRENT_CONFIG_VERSION = 4;

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue CONFIG_VERSION;
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;

    public static final ModConfigSpec.BooleanValue SELF_ENDOWMENT_ENABLED;
    public static final ModConfigSpec.IntValue SELF_ENDOWMENT_REQUIRED_LEVEL;
    public static final ModConfigSpec.DoubleValue SELF_ENDOWMENT_REQUIRED_EP;
    public static final ModConfigSpec.DoubleValue SELF_ENDOWMENT_REQUIRED_MAGICULES;
    public static final ModConfigSpec.IntValue SELF_ENDOWMENT_XP_LEVEL_COST;

    public static final ModConfigSpec.BooleanValue SAGE_UPGRADE_ENABLED;
    public static final ModConfigSpec.BooleanValue SAGE_UPGRADE_REQUIRE_SAGE;
    public static final ModConfigSpec.IntValue SAGE_UPGRADE_REQUIRED_XP_LEVEL;
    public static final ModConfigSpec.IntValue SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION;
    public static final ModConfigSpec.DoubleValue SAGE_UPGRADE_MAGICULE_COST;

    public static final ModConfigSpec.BooleanValue GREAT_SAGE_RITUAL_ENABLED;
    public static final ModConfigSpec.IntValue GREAT_SAGE_RITUAL_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue GREAT_SAGE_RITUAL_REQUIRED_EP;
    public static final ModConfigSpec.IntValue GREAT_SAGE_RITUAL_REQUIRED_MASTERED_SKILLS;
    public static final ModConfigSpec.BooleanValue GREAT_SAGE_RITUAL_REQUIRE_NAMED;

    public static final ModConfigSpec.BooleanValue GREAT_CRYSTAL_READY_PARTICLES_ENABLED;
    public static final ModConfigSpec.IntValue GREAT_CRYSTAL_READY_PARTICLE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue GREAT_CRYSTAL_READY_PARTICLE_RANGE;
    public static final ModConfigSpec.IntValue GREAT_CRYSTAL_READY_PARTICLE_VERTICAL_RANGE;

    public static final ModConfigSpec.BooleanValue SAGE_WHISPERS_ENABLED;
    public static final ModConfigSpec.IntValue SAGE_WHISPER_INITIAL_DELAY_TICKS;
    public static final ModConfigSpec.IntValue SAGE_WHISPER_MIN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SAGE_WHISPER_RANDOM_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue SAGE_WHISPER_CHANCE;

    public static final ModConfigSpec.BooleanValue GRANTER_AWAKENING_ENABLED;
    public static final ModConfigSpec.DoubleValue GRANTER_AWAKENING_REQUIRED_EP;
    public static final ModConfigSpec.IntValue GRANTER_AWAKENING_REQUIRED_MASTERED_SKILLS;
    public static final ModConfigSpec.IntValue GRANTER_AWAKENING_REQUIRED_SUBORDINATES;
    public static final ModConfigSpec.DoubleValue GRANTER_AWAKENING_SCAN_RADIUS;

    public static final ModConfigSpec.DoubleValue GRANTER_GRANT_MAGICULE_COST;
    public static final ModConfigSpec.IntValue GRANTER_GRANT_COOLDOWN_TICKS;

    public static final ModConfigSpec.DoubleValue ULTIMATE_EVOLUTION_REQUIRED_EP;
    public static final ModConfigSpec.DoubleValue ULTIMATE_EVOLUTION_REQUIRED_MAGICULES;
    public static final ModConfigSpec.IntValue ULTIMATE_EVOLUTION_REQUIRED_MASTERED_SKILLS;
    public static final ModConfigSpec.IntValue ULTIMATE_EVOLUTION_REQUIRED_SUCCESSFUL_GRANTS;
    public static final ModConfigSpec.IntValue ULTIMATE_EVOLUTION_REQUIRED_SUBORDINATES;
    public static final ModConfigSpec.DoubleValue ULTIMATE_EVOLUTION_SCAN_RADIUS;

    public static final ModConfigSpec.DoubleValue BENEVOLENT_MASS_GRANT_COST_PER_TARGET;
    public static final ModConfigSpec.DoubleValue ABSOLUTE_MASS_GRANT_COST_PER_TARGET;

    public static final ModConfigSpec.DoubleValue BENEVOLENT_GRANT_WITHOUT_MASTERY_BASE_COST;
    public static final ModConfigSpec.DoubleValue ABSOLUTE_GRANT_WITHOUT_MASTERY_BASE_COST;
    public static final ModConfigSpec.DoubleValue BENEVOLENT_GRANT_WITHOUT_MASTERY_EXTRA_COST;
    public static final ModConfigSpec.DoubleValue ABSOLUTE_GRANT_WITHOUT_MASTERY_EXTRA_COST;

    public static final ModConfigSpec.DoubleValue BORROW_COST_PER_SKILL;
    public static final ModConfigSpec.IntValue BORROW_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue BORROW_PERMANENT_BASE_CHANCE;
    public static final ModConfigSpec.DoubleValue BORROW_PERMANENT_BONUS_PER_PREVIOUS_BORROW;
    public static final ModConfigSpec.DoubleValue BORROW_PERMANENT_MAX_CHANCE;

    public static final ModConfigSpec.DoubleValue SEIZE_COST_PER_SKILL;
    public static final ModConfigSpec.DoubleValue SEIZE_DEATH_CHANCE_PER_SKILL;
    public static final ModConfigSpec.DoubleValue SEIZE_DEATH_CHANCE_MAX;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GLOBAL_SKILL_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GRANT_SKILL_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BORROW_SKILL_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SEIZE_SKILL_BLACKLIST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        CONFIG_VERSION = builder
                .comment(
                        "Internal config schema version for Moos Tensura Addon.",
                        "Do not edit unless you intentionally want the addon to re-apply migration defaults."
                )
                .defineInRange("configVersion", 0, 0, Integer.MAX_VALUE);

        builder.push("debug");

        DEBUG_MODE = builder
                .comment(
                        "Enables developer commands and diagnostic tools.",
                        "This setting is controlled by the server.",
                        "Keep this disabled during normal gameplay and in release server configurations.",
                        "Normal players cannot use debug tools even when this is enabled; command permission checks still apply."
                )
                .define(
                        "enabled",
                        false
                );

        builder.pop();

        builder.push("self_endowment");

        SELF_ENDOWMENT_ENABLED = builder
                .comment("Allows players to use the self-endowment system.")
                .define("enabled", true);

        SELF_ENDOWMENT_REQUIRED_LEVEL = builder
                .comment("Minimum vanilla XP level required before self-endowment is allowed.")
                .defineInRange("requiredLevel", 75, 0, 100000);

        SELF_ENDOWMENT_REQUIRED_EP = builder
                .comment("Minimum current EP required before self-endowment is allowed.")
                .defineInRange("requiredEp", 200_000.0D, 0.0D, Double.MAX_VALUE);

        SELF_ENDOWMENT_REQUIRED_MAGICULES = builder
                .comment("Current magicules consumed by self-endowment.")
                .defineInRange("requiredMagicules", 100_000.0D, 0.0D, Double.MAX_VALUE);

        SELF_ENDOWMENT_XP_LEVEL_COST = builder
                .comment("Raw XP equivalent of this level is consumed by self-endowment.")
                .defineInRange("xpLevelEquivalentCost", 50, 0, 100000);

        builder.pop();

        builder.push("sage_upgrade");

        SAGE_UPGRADE_ENABLED = builder
                .comment("Allows Sage to evolve into Great Sage through this addon.")
                .define("enabled", true);

        SAGE_UPGRADE_REQUIRE_SAGE = builder
                .comment("If true, the player must already have Sage before evolving it into Great Sage.")
                .define("requireSage", true);

        SAGE_UPGRADE_REQUIRED_XP_LEVEL = builder
                .comment("Minimum vanilla XP level equivalent required before Sage can evolve into Great Sage.")
                .defineInRange("requiredXpLevel", 45, 0, 100000);

        SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION = builder
                .comment("Amount of relative vanilla XP levels removed when Sage evolves into Great Sage. Example: 45 -> 20 when set to 25.")
                .defineInRange("relativeLevelDeduction", 25, 0, 100000);

        SAGE_UPGRADE_MAGICULE_COST = builder
                .comment("Current magicules consumed by Sage -> Great Sage.")
                .defineInRange("magiculeCost", 0.0D, 0.0D, Double.MAX_VALUE);

        builder.pop();

        builder.push("great_sage_ritual");

        GREAT_SAGE_RITUAL_ENABLED = builder
                .comment("Allows the Great Crystal Altar ritual path for Sage -> Great Sage.")
                .define("enabled", true);

        GREAT_SAGE_RITUAL_DURATION_TICKS = builder
                .comment("Duration of the Great Crystal Altar ritual in ticks. 20 ticks = 1 second. Costs and skill changes happen only when this finishes.")
                .defineInRange("durationTicks", 300, 40, 20 * 60);

        GREAT_SAGE_RITUAL_REQUIRED_EP = builder
                .comment("Current EP required to start the Great Crystal Altar ritual.")
                .defineInRange("requiredEp", 60_000.0D, 0.0D, Double.MAX_VALUE);

        GREAT_SAGE_RITUAL_REQUIRED_MASTERED_SKILLS = builder
                .comment("Amount of mastered skills required to start the Great Crystal Altar ritual.")
                .defineInRange("requiredMasteredSkills", 1, 0, 100000);

        GREAT_SAGE_RITUAL_REQUIRE_NAMED = builder
                .comment("If true, the player must be named/endowed before using the Great Crystal Altar ritual.")
                .define("requireNamed", false);

        builder.pop();

        builder.push("great_crystal_altar");

        GREAT_CRYSTAL_READY_PARTICLES_ENABLED = builder
                .comment("If true, Great Crystal Altars emit subtle particles when a nearby player is ready to evolve Sage into Great Sage.")
                .define("readyParticlesEnabled", true);

        GREAT_CRYSTAL_READY_PARTICLE_INTERVAL_TICKS = builder
                .comment("How often ready-particle checks happen. 20 ticks = 1 second.")
                .defineInRange("readyParticleIntervalTicks", 10, 5, 20 * 60);

        GREAT_CRYSTAL_READY_PARTICLE_RANGE = builder
                .comment("Horizontal block range for altar ready particles around a ritual-ready player.")
                .defineInRange("readyParticleRange", 8, 1, 64);

        GREAT_CRYSTAL_READY_PARTICLE_VERTICAL_RANGE = builder
                .comment("Vertical block range for altar ready particles around a ritual-ready player.")
                .defineInRange("readyParticleVerticalRange", 4, 1, 32);

        builder.pop();

        builder.push("sage_whispers");

        SAGE_WHISPERS_ENABLED = builder
                .comment("Allows Sage to occasionally give subtle actionbar hints before evolving into Great Sage.")
                .define("enabled", true);

        SAGE_WHISPER_INITIAL_DELAY_TICKS = builder
                .comment("Initial delay before Sage can whisper after the player has Sage. 20 ticks = 1 second.")
                .defineInRange("initialDelayTicks", 600, 20, Integer.MAX_VALUE);

        SAGE_WHISPER_MIN_INTERVAL_TICKS = builder
                .comment("Minimum time between Sage whispers. 20 ticks = 1 second.")
                .defineInRange("minIntervalTicks", 2400, 20, Integer.MAX_VALUE);

        SAGE_WHISPER_RANDOM_INTERVAL_TICKS = builder
                .comment("Random extra time added to the minimum whisper interval. 20 ticks = 1 second.")
                .defineInRange("randomIntervalTicks", 2400, 0, Integer.MAX_VALUE);

        SAGE_WHISPER_CHANCE = builder
                .comment("Chance for a whisper to actually appear when the cooldown finishes. 1.0 = always, 0.5 = 50%.")
                .defineInRange("chance", 0.75D, 0.0D, 1.0D);

        builder.pop();

        builder.push("granter_awakening");

        GRANTER_AWAKENING_ENABLED = builder
                .comment("Allows natural awakening of the Unique Skill Granter.")
                .define("enabled", true);

        GRANTER_AWAKENING_REQUIRED_EP = builder
                .comment("Current EP required for natural Granter awakening.")
                .defineInRange("requiredEp", 200_000.0D, 0.0D, Double.MAX_VALUE);

        GRANTER_AWAKENING_REQUIRED_MASTERED_SKILLS = builder
                .comment("Amount of mastered skills required for natural Granter awakening.")
                .defineInRange("requiredMasteredSkills", 5, 0, 100000);

        GRANTER_AWAKENING_REQUIRED_SUBORDINATES = builder
                .comment("Amount of nearby or recognized subordinates required for natural Granter awakening.")
                .defineInRange("requiredSubordinates", 5, 0, 100000);

        GRANTER_AWAKENING_SCAN_RADIUS = builder
                .comment("Radius used to scan for nearby subordinates for Granter awakening.")
                .defineInRange("scanRadius", 32.0D, 1.0D, 512.0D);

        builder.pop();

        builder.push("granter");

        GRANTER_GRANT_MAGICULE_COST = builder
                .comment("Current magicule cost for normal Granter skill granting.")
                .defineInRange("grantMagiculeCost", 200_000.0D, 0.0D, Double.MAX_VALUE);

        GRANTER_GRANT_COOLDOWN_TICKS = builder
                .comment("Cooldown in ticks for normal Granter skill granting. 20 ticks = 1 second.")
                .defineInRange("grantCooldownTicks", 6000, 0, Integer.MAX_VALUE);

        builder.pop();

        builder.push("ultimate_evolution");

        ULTIMATE_EVOLUTION_REQUIRED_EP = builder
                .comment("Current EP required for Granter to evolve into an Ultimate Skill.")
                .defineInRange("requiredEp", 1_000_000.0D, 0.0D, Double.MAX_VALUE);

        ULTIMATE_EVOLUTION_REQUIRED_MAGICULES = builder
                .comment("Current magicules consumed by Granter Ultimate evolution.")
                .defineInRange("requiredMagicules", 500_000.0D, 0.0D, Double.MAX_VALUE);

        ULTIMATE_EVOLUTION_REQUIRED_MASTERED_SKILLS = builder
                .comment("Amount of mastered skills required for Granter Ultimate evolution.")
                .defineInRange("requiredMasteredSkills", 10, 0, 100000);

        ULTIMATE_EVOLUTION_REQUIRED_SUCCESSFUL_GRANTS = builder
                .comment("Amount of successful Granter grants required for Granter Ultimate evolution.")
                .defineInRange("requiredSuccessfulGrants", 10, 0, 100000);

        ULTIMATE_EVOLUTION_REQUIRED_SUBORDINATES = builder
                .comment("Amount of nearby or recognized subordinates required for Granter Ultimate evolution.")
                .defineInRange("requiredSubordinates", 10, 0, 100000);

        ULTIMATE_EVOLUTION_SCAN_RADIUS = builder
                .comment("Radius used to scan for nearby subordinates for Granter Ultimate evolution.")
                .defineInRange("scanRadius", 32.0D, 1.0D, 512.0D);

        builder.pop();

        builder.push("ultimate_actions");

        BENEVOLENT_MASS_GRANT_COST_PER_TARGET = builder
                .comment("Current magicule cost per target for Benevolent Empowerment Mass Grant.")
                .defineInRange("benevolentMassGrantCostPerTarget", 75_000.0D, 0.0D, Double.MAX_VALUE);

        ABSOLUTE_MASS_GRANT_COST_PER_TARGET = builder
                .comment("Current magicule cost per target for Absolute Governance Mass Grant.")
                .defineInRange("absoluteMassGrantCostPerTarget", 100_000.0D, 0.0D, Double.MAX_VALUE);

        BENEVOLENT_GRANT_WITHOUT_MASTERY_BASE_COST = builder
                .comment("Base magicule cost for Benevolent Empowerment Grant Without Mastery.")
                .defineInRange("benevolentGrantWithoutMasteryBaseCost", 100_000.0D, 0.0D, Double.MAX_VALUE);

        ABSOLUTE_GRANT_WITHOUT_MASTERY_BASE_COST = builder
                .comment("Base magicule cost for Absolute Governance Grant Without Mastery.")
                .defineInRange("absoluteGrantWithoutMasteryBaseCost", 150_000.0D, 0.0D, Double.MAX_VALUE);

        BENEVOLENT_GRANT_WITHOUT_MASTERY_EXTRA_COST = builder
                .comment("Maximum extra cost added when a Benevolent Grant Without Mastery skill has 0 mastery.")
                .defineInRange("benevolentGrantWithoutMasteryExtraCost", 650_000.0D, 0.0D, Double.MAX_VALUE);

        ABSOLUTE_GRANT_WITHOUT_MASTERY_EXTRA_COST = builder
                .comment("Maximum extra cost added when an Absolute Grant Without Mastery skill has 0 mastery.")
                .defineInRange("absoluteGrantWithoutMasteryExtraCost", 350_000.0D, 0.0D, Double.MAX_VALUE);

        BORROW_COST_PER_SKILL = builder
                .comment("Current magicule cost per borrowed skill.")
                .defineInRange("borrowCostPerSkill", 150_000.0D, 0.0D, Double.MAX_VALUE);

        BORROW_DURATION_TICKS = builder
                .comment("Duration of temporary borrowed skills in ticks. 6000 ticks = 5 minutes.")
                .defineInRange("borrowDurationTicks", 6000, 1, Integer.MAX_VALUE);

        BORROW_PERMANENT_BASE_CHANCE = builder
                .comment("Base chance for a borrowed skill to become permanent. 0.02 = 2%.")
                .defineInRange("borrowPermanentBaseChance", 0.02D, 0.0D, 1.0D);

        BORROW_PERMANENT_BONUS_PER_PREVIOUS_BORROW = builder
                .comment("Extra permanence chance added for each previous time the same skill was borrowed. 0.01 = +1%.")
                .defineInRange("borrowPermanentBonusPerPreviousBorrow", 0.01D, 0.0D, 1.0D);

        BORROW_PERMANENT_MAX_CHANCE = builder
                .comment("Maximum permanence chance for repeatedly borrowed skills. 0.20 = 20%.")
                .defineInRange("borrowPermanentMaxChance", 0.20D, 0.0D, 1.0D);

        SEIZE_COST_PER_SKILL = builder
                .comment("Current magicule cost per seized skill.")
                .defineInRange("seizeCostPerSkill", 250_000.0D, 0.0D, Double.MAX_VALUE);

        SEIZE_DEATH_CHANCE_PER_SKILL = builder
                .comment("Chance added per seized skill that the subordinate dies from forced extraction. 0.01 = 1% per skill.")
                .defineInRange("seizeDeathChancePerSkill", 0.01D, 0.0D, 1.0D);

        SEIZE_DEATH_CHANCE_MAX = builder
                .comment("Maximum death chance from a single multi-skill Seize action. 0.25 = 25%.")
                .defineInRange("seizeDeathChanceMax", 0.25D, 0.0D, 1.0D);

        builder.pop();

        builder.push("compatibility");

        GLOBAL_SKILL_BLACKLIST = builder
                .comment(
                        "Skills listed here cannot be granted, borrowed, or seized by this addon.",
                        "Use full skill IDs, for example: \"tensura:creator\".",
                        "Example inactive entry you may add manually: \"tensura:example_skill\"."
                )
                .defineList("globalSkillBlacklist", List.of(
                        "moostensuraaddon:granter",
                        "moostensuraaddon:benevolent_empowerment",
                        "moostensuraaddon:absolute_governance",
                        "tensura:creator"
                ), value -> value instanceof String);

        GRANT_SKILL_BLACKLIST = builder
                .comment("Skills listed here cannot be granted.")
                .defineList("grantSkillBlacklist", List.of(), value -> value instanceof String);

        BORROW_SKILL_BLACKLIST = builder
                .comment("Skills listed here cannot be borrowed.")
                .defineList("borrowSkillBlacklist", List.of(), value -> value instanceof String);

        SEIZE_SKILL_BLACKLIST = builder
                .comment("Skills listed here cannot be seized.")
                .defineList("seizeSkillBlacklist", List.of(), value -> value instanceof String);

        builder.pop();

        SPEC = builder.build();
    }

    private MoosTensuraConfig() {
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    public static void migrateIfNeeded() {
        int version =
                CONFIG_VERSION.get();

        if (version >= CURRENT_CONFIG_VERSION) {
            return;
        }

        if (version < 2) {
            migrateToVersion2();
        }

        if (version < 3) {
            migrateToVersion3();
        }

        if (version < 4) {
            migrateToVersion4();
        }

        CONFIG_VERSION.set(
                CURRENT_CONFIG_VERSION
        );

        SPEC.save();
    }

    private static void migrateToVersion2() {
        if (SAGE_UPGRADE_REQUIRED_XP_LEVEL.get() > 50) {
            SAGE_UPGRADE_REQUIRED_XP_LEVEL.set(45);
        }

        if (SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION.get() > 25) {
            SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION.set(25);
        }

        GREAT_CRYSTAL_READY_PARTICLES_ENABLED.set(true);
        GREAT_CRYSTAL_READY_PARTICLE_INTERVAL_TICKS.set(10);
        GREAT_CRYSTAL_READY_PARTICLE_RANGE.set(8);
        GREAT_CRYSTAL_READY_PARTICLE_VERTICAL_RANGE.set(4);
    }

    private static void migrateToVersion3() {
        /*
         * Existing worlds must enter the new debug system safely.
         *
         * Debug mode always begins disabled during the migration. An operator
         * must deliberately enable it through the permission-protected,
         * confirmation-protected command.
         */
        DEBUG_MODE.set(false);
    }

    private static void migrateToVersion4() {
        /*
         * Lower only the former built-in ritual requirement. Server owners
         * who deliberately configured a different value keep their choice.
         */
        if (Double.compare(
                GREAT_SAGE_RITUAL_REQUIRED_EP.get(),
                100_000.0D
        ) == 0) {
            GREAT_SAGE_RITUAL_REQUIRED_EP.set(60_000.0D);
        }
    }

    public static void resetToAddonDefaults() {
        CONFIG_VERSION.set(
                CURRENT_CONFIG_VERSION
        );

        DEBUG_MODE.set(false);

        SELF_ENDOWMENT_ENABLED.set(true);
        SELF_ENDOWMENT_REQUIRED_LEVEL.set(75);
        SELF_ENDOWMENT_REQUIRED_EP.set(200_000.0D);
        SELF_ENDOWMENT_REQUIRED_MAGICULES.set(100_000.0D);
        SELF_ENDOWMENT_XP_LEVEL_COST.set(50);

        SAGE_UPGRADE_ENABLED.set(true);
        SAGE_UPGRADE_REQUIRE_SAGE.set(true);
        SAGE_UPGRADE_REQUIRED_XP_LEVEL.set(45);
        SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION.set(25);
        SAGE_UPGRADE_MAGICULE_COST.set(0.0D);

        GREAT_SAGE_RITUAL_ENABLED.set(true);
        GREAT_SAGE_RITUAL_DURATION_TICKS.set(300);
        GREAT_SAGE_RITUAL_REQUIRED_EP.set(60_000.0D);
        GREAT_SAGE_RITUAL_REQUIRED_MASTERED_SKILLS.set(1);
        GREAT_SAGE_RITUAL_REQUIRE_NAMED.set(false);

        GREAT_CRYSTAL_READY_PARTICLES_ENABLED.set(true);
        GREAT_CRYSTAL_READY_PARTICLE_INTERVAL_TICKS.set(10);
        GREAT_CRYSTAL_READY_PARTICLE_RANGE.set(8);
        GREAT_CRYSTAL_READY_PARTICLE_VERTICAL_RANGE.set(4);

        SAGE_WHISPERS_ENABLED.set(true);
        SAGE_WHISPER_INITIAL_DELAY_TICKS.set(600);
        SAGE_WHISPER_MIN_INTERVAL_TICKS.set(2400);
        SAGE_WHISPER_RANDOM_INTERVAL_TICKS.set(2400);
        SAGE_WHISPER_CHANCE.set(0.75D);

        GRANTER_AWAKENING_ENABLED.set(true);
        GRANTER_AWAKENING_REQUIRED_EP.set(200_000.0D);
        GRANTER_AWAKENING_REQUIRED_MASTERED_SKILLS.set(5);
        GRANTER_AWAKENING_REQUIRED_SUBORDINATES.set(5);
        GRANTER_AWAKENING_SCAN_RADIUS.set(32.0D);

        GRANTER_GRANT_MAGICULE_COST.set(200_000.0D);
        GRANTER_GRANT_COOLDOWN_TICKS.set(6000);

        ULTIMATE_EVOLUTION_REQUIRED_EP.set(1_000_000.0D);
        ULTIMATE_EVOLUTION_REQUIRED_MAGICULES.set(500_000.0D);
        ULTIMATE_EVOLUTION_REQUIRED_MASTERED_SKILLS.set(10);
        ULTIMATE_EVOLUTION_REQUIRED_SUCCESSFUL_GRANTS.set(10);
        ULTIMATE_EVOLUTION_REQUIRED_SUBORDINATES.set(10);
        ULTIMATE_EVOLUTION_SCAN_RADIUS.set(32.0D);

        BENEVOLENT_MASS_GRANT_COST_PER_TARGET.set(75_000.0D);
        ABSOLUTE_MASS_GRANT_COST_PER_TARGET.set(100_000.0D);

        BENEVOLENT_GRANT_WITHOUT_MASTERY_BASE_COST.set(100_000.0D);
        ABSOLUTE_GRANT_WITHOUT_MASTERY_BASE_COST.set(150_000.0D);
        BENEVOLENT_GRANT_WITHOUT_MASTERY_EXTRA_COST.set(650_000.0D);
        ABSOLUTE_GRANT_WITHOUT_MASTERY_EXTRA_COST.set(350_000.0D);

        BORROW_COST_PER_SKILL.set(150_000.0D);
        BORROW_DURATION_TICKS.set(6000);
        BORROW_PERMANENT_BASE_CHANCE.set(0.02D);
        BORROW_PERMANENT_BONUS_PER_PREVIOUS_BORROW.set(0.01D);
        BORROW_PERMANENT_MAX_CHANCE.set(0.20D);

        SEIZE_COST_PER_SKILL.set(250_000.0D);
        SEIZE_DEATH_CHANCE_PER_SKILL.set(0.01D);
        SEIZE_DEATH_CHANCE_MAX.set(0.25D);

        GLOBAL_SKILL_BLACKLIST.set(List.of(
                "moostensuraaddon:granter",
                "moostensuraaddon:benevolent_empowerment",
                "moostensuraaddon:absolute_governance",
                "tensura:creator"
        ));
        GRANT_SKILL_BLACKLIST.set(List.of());
        BORROW_SKILL_BLACKLIST.set(List.of());
        SEIZE_SKILL_BLACKLIST.set(List.of());

        SPEC.save();
    }

    public static boolean isSkillBlacklisted(ResourceLocation skillId, SkillAction action) {
        if (skillId == null) {
            return true;
        }

        String id = skillId.toString();

        if (containsSkill(GLOBAL_SKILL_BLACKLIST.get(), id)) {
            return true;
        }

        return switch (action) {
            case GRANT -> containsSkill(GRANT_SKILL_BLACKLIST.get(), id);
            case BORROW -> containsSkill(BORROW_SKILL_BLACKLIST.get(), id);
            case SEIZE -> containsSkill(SEIZE_SKILL_BLACKLIST.get(), id);
            case ANY -> false;
        };
    }

    private static boolean containsSkill(List<? extends String> entries, String skillId) {
        if (entries == null || skillId == null || skillId.isBlank()) {
            return false;
        }

        for (String entry : entries) {
            if (entry == null) {
                continue;
            }

            String cleaned = entry.trim();

            if (cleaned.isBlank()) {
                continue;
            }

            if (cleaned.equals(skillId)) {
                return true;
            }
        }

        return false;
    }

    public enum SkillAction {
        ANY,
        GRANT,
        BORROW,
        SEIZE
    }
}
