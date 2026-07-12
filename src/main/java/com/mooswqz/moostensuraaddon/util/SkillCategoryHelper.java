package com.mooswqz.moostensuraaddon.util;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.impl.SkillRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Comparator;
import java.util.Locale;

public final class SkillCategoryHelper {
    private static final TagKey<ManasSkill> UNIQUE_SKILLS = createTensuraSkillTag("unique_skills");
    private static final TagKey<ManasSkill> EXTRA_SKILLS = createTensuraSkillTag("extra_skills");
    private static final TagKey<ManasSkill> RESISTANCE_SKILLS = createTensuraSkillTag("resistance_skills");

    private SkillCategoryHelper() {
    }

    public static SkillCategory getCategory(ManasSkillInstance instance) {
        if (instance == null) {
            return SkillCategory.OTHER;
        }

        return getCategory(instance, instance.getSkillId(), instance.getDisplayName());
    }

    public static SkillCategory getCategory(ManasSkillInstance instance, ResourceLocation skillId, Component displayName) {
        /*
         * Prefer real Tensura/ManasCore tags. This is the important fix.
         * IDs such as "tensura:magic_sense" do not contain "extra", so text
         * matching alone cannot sort the list correctly.
         */
        if (isInTag(instance, UNIQUE_SKILLS)) {
            return SkillCategory.UNIQUE;
        }

        if (isInTag(instance, EXTRA_SKILLS)) {
            return SkillCategory.EXTRA;
        }

        if (isCommonSkillClass(instance)) {
            return SkillCategory.BASIC;
        }

        if (isInTag(instance, RESISTANCE_SKILLS)) {
            return SkillCategory.RESISTANCE;
        }

        /*
         * Fallback for custom/modded skills or if the client does not have
         * tag data available for some reason.
         */
        SkillCategory classCategory = getCategoryFromSkillClass(instance);

        if (classCategory != SkillCategory.OTHER) {
            return classCategory;
        }

        return getCategory(skillId, displayName);
    }

    public static SkillCategory getCategory(ResourceLocation skillId, Component displayName) {
        String idText = skillId == null ? "" : skillId.toString().toLowerCase(Locale.ROOT);
        String pathText = skillId == null ? "" : skillId.getPath().toLowerCase(Locale.ROOT);
        String nameText = displayName == null ? "" : displayName.getString().toLowerCase(Locale.ROOT);

        if (isUnique(idText, pathText, nameText)) {
            return SkillCategory.UNIQUE;
        }

        if (isExtra(idText, pathText, nameText)) {
            return SkillCategory.EXTRA;
        }

        if (isBasic(idText, pathText, nameText)) {
            return SkillCategory.BASIC;
        }

        if (isResistance(idText, pathText, nameText)) {
            return SkillCategory.RESISTANCE;
        }

        return SkillCategory.OTHER;
    }

    public static boolean isIntrinsic(ManasSkillInstance instance) {
        if (instance == null) {
            return false;
        }

        return isIntrinsic(instance, instance.getSkillId(), instance.getDisplayName());
    }

    public static boolean isIntrinsic(ManasSkillInstance instance, ResourceLocation skillId, Component displayName) {
        if (isIntrinsicSkillClass(instance)) {
            return true;
        }

        return isIntrinsic(skillId, displayName);
    }

    public static boolean isIntrinsic(ResourceLocation skillId, Component displayName) {
        String idText = skillId == null ? "" : skillId.toString().toLowerCase(Locale.ROOT);
        String pathText = skillId == null ? "" : skillId.getPath().toLowerCase(Locale.ROOT);
        String nameText = displayName == null ? "" : displayName.getString().toLowerCase(Locale.ROOT);

        return idText.contains("intrinsic")
                || pathText.contains("intrinsic")
                || nameText.contains("intrinsic");
    }

    public static Component getMasteryPrefix(boolean mastered) {
        if (!mastered) {
            return Component.empty();
        }

        return Component.literal("♛ ")
                .withStyle(ChatFormatting.GOLD);
    }

    public static Component getCategoryHeader(SkillCategory category) {
        if (category == null) {
            return Component.literal("Other")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        }

        return switch (category) {
            case UNIQUE -> Component.literal("Unique")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            case EXTRA -> Component.literal("Extra")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            case BASIC -> Component.literal("Basic")
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            case RESISTANCE -> Component.literal("Resistances")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            case OTHER -> Component.literal("Other")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        };
    }

    public static Comparator<SkillDisplayEntry> skillDisplaySorter() {
        return Comparator
                .comparingInt((SkillDisplayEntry entry) -> entry.category().sortOrder())
                .thenComparing(entry -> entry.displayName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.skillId() == null ? "" : entry.skillId().toString());
    }

    private static TagKey<ManasSkill> createTensuraSkillTag(String path) {
        return TagKey.create(
                SkillRegistry.KEY,
                ResourceLocation.fromNamespaceAndPath("tensura", path)
        );
    }

    private static boolean isInTag(ManasSkillInstance instance, TagKey<ManasSkill> tagKey) {
        if (instance == null || tagKey == null) {
            return false;
        }

        try {
            return instance.is(tagKey);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static SkillCategory getCategoryFromSkillClass(ManasSkillInstance instance) {
        String className = getSkillClassName(instance);

        if (className.isBlank()) {
            return SkillCategory.OTHER;
        }

        if (className.contains(".skill.unique.")) {
            return SkillCategory.UNIQUE;
        }

        if (className.contains(".skill.extra.")) {
            return SkillCategory.EXTRA;
        }

        if (className.contains(".skill.common.")) {
            return SkillCategory.BASIC;
        }

        if (className.contains(".skill.resistance.")) {
            return SkillCategory.RESISTANCE;
        }

        return SkillCategory.OTHER;
    }

    private static boolean isCommonSkillClass(ManasSkillInstance instance) {
        return getSkillClassName(instance).contains(".skill.common.");
    }

    private static boolean isIntrinsicSkillClass(ManasSkillInstance instance) {
        return getSkillClassName(instance).contains(".skill.intrinsic.");
    }

    private static String getSkillClassName(ManasSkillInstance instance) {
        if (instance == null || instance.getSkill() == null) {
            return "";
        }

        return instance.getSkill()
                .getClass()
                .getName()
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isUnique(String idText, String pathText, String nameText) {
        return idText.contains("unique")
                || pathText.contains("unique")
                || nameText.contains("unique");
    }

    private static boolean isExtra(String idText, String pathText, String nameText) {
        return idText.contains("extra")
                || pathText.contains("extra")
                || nameText.contains("extra");
    }

    private static boolean isBasic(String idText, String pathText, String nameText) {
        return idText.contains("basic")
                || idText.contains("common")
                || pathText.contains("basic")
                || pathText.contains("common")
                || nameText.contains("basic")
                || nameText.contains("common");
    }

    private static boolean isResistance(String idText, String pathText, String nameText) {
        return idText.contains("resistance")
                || idText.contains("resist")
                || pathText.contains("resistance")
                || pathText.contains("resist")
                || nameText.contains("resistance")
                || nameText.contains("resist");
    }

    public enum SkillCategory {
        UNIQUE(0),
        EXTRA(1),
        BASIC(2),
        RESISTANCE(3),
        OTHER(4);

        private final int sortOrder;

        SkillCategory(int sortOrder) {
            this.sortOrder = sortOrder;
        }

        public int sortOrder() {
            return sortOrder;
        }
    }

    public record SkillDisplayEntry(
            ResourceLocation skillId,
            Component displayName,
            SkillCategory category,
            boolean mastered
    ) {
        public Component getFormattedDisplayName() {
            return Component.empty()
                    .append(getMasteryPrefix(mastered))
                    .append(displayName == null ? Component.literal("Unknown Skill") : displayName.copy());
        }
    }
}