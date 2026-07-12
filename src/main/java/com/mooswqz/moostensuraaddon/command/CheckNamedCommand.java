package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import com.mooswqz.moostensuraaddon.util.GranterActions;
import com.mooswqz.moostensuraaddon.util.TensuraPlayerStateHelper;
import com.mooswqz.moostensuraaddon.util.XpUtils;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Locale;

public class CheckNamedCommand {
    private static final int REQUIRED_GRANTER_MASTERED_SKILLS = 5;
    private static final int REQUIRED_GRANTER_SUBORDINATES = 5;
    private static final double REQUIRED_GRANTER_EP = 200_000.0D;
    private static final double SUBORDINATE_SCAN_RADIUS = 32.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("checknamed")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    IExistence existence = TensuraStorages.getExistenceFrom(player);

                    if (existence == null) {
                        player.sendSystemMessage(Component.translatable("moostensuraaddon.command.checknamed.failed")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    GranterProgressData progress = player.getData(AttachmentRegistry.GRANTER_PROGRESS_DATA);

                    String storedName = TensuraPlayerStateHelper.getStoredTensuraName(player).orElse(null);
                    boolean named = TensuraPlayerStateHelper.isNamedOrEndowed(player);

                    boolean hasGranter = hasGranter(player);
                    boolean hasBenevolentEmpowerment = hasBenevolentEmpowerment(player);
                    boolean hasAbsoluteGovernance = hasAbsoluteGovernance(player);
                    boolean hasAnyEvolvedGranterSkill = hasBenevolentEmpowerment || hasAbsoluteGovernance;

                    boolean hasGreatSage = GranterActions.hasGreatSage(player);

                    int masteredSkillCount = getMasteredSkillCount(player);
                    double ep = existence.getEP();
                    double magicules = existence.getMagicule();

                    List<LivingEntity> nearbyLivingEntities = getNearbyLivingEntities(player);
                    List<LivingEntity> nearbySubordinates = getNearbySubordinates(player);

                    int nearbyLivingCount = nearbyLivingEntities.size();
                    int nearbySubordinateCount = nearbySubordinates.size();
                    int recognizedSubordinateCount = progress.getRecognizedSubordinateCount();

                    boolean epOk = ep >= REQUIRED_GRANTER_EP;
                    boolean masteredSkillsOk = masteredSkillCount >= REQUIRED_GRANTER_MASTERED_SKILLS;
                    boolean nearbySubordinatesOk = nearbySubordinateCount >= REQUIRED_GRANTER_SUBORDINATES;
                    boolean recognizedSubordinatesOk = recognizedSubordinateCount >= REQUIRED_GRANTER_SUBORDINATES;
                    boolean subordinateRequirementOk = nearbySubordinatesOk || recognizedSubordinatesOk;

                    boolean canNaturallyAwakenGranter =
                            !hasGranter
                                    && !hasAnyEvolvedGranterSkill
                                    && !progress.hasAwakenedGranterNaturally()
                                    && named
                                    && hasGreatSage
                                    && masteredSkillsOk
                                    && subordinateRequirementOk
                                    && epOk;

                    player.sendSystemMessage(Component.translatable("moostensuraaddon.command.checknamed.header")
                            .withStyle(ChatFormatting.GOLD));

                    player.sendSystemMessage(Component.literal("Identity / Existence")
                            .withStyle(ChatFormatting.YELLOW));

                    sendBooleanLine(player, "Named/endowed", named);
                    sendInfoLine(player, "Stored Tensura name", safeName(storedName));
                    sendInfoLine(player, "Minecraft name", player.getGameProfile().getName());
                    sendInfoLine(player, "Display name", player.getDisplayName().getString());
                    sendInfoLine(player, "Custom name", getCustomName(player));
                    sendInfoLine(player, "EP", formatNumber(ep));
                    sendInfoLine(player, "Magicules", formatNumber(magicules));
                    sendInfoLine(player, "Level", String.valueOf(player.experienceLevel));
                    sendInfoLine(player, "Raw XP", String.valueOf(XpUtils.getTotalXp(player)));
                    sendInfoLine(player, "Mastered skills", String.valueOf(masteredSkillCount));

                    player.sendSystemMessage(Component.literal("Granter Awakening Debug")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));

                    sendBooleanLine(player, "Has Granter", hasGranter);
                    sendBooleanLine(player, "Has Benevolent Empowerment", hasBenevolentEmpowerment);
                    sendBooleanLine(player, "Has Absolute Governance", hasAbsoluteGovernance);
                    sendBooleanLine(player, "Already awakened Granter naturally", progress.hasAwakenedGranterNaturally());

                    sendRequirementLine(player, "Named/endowed", named, "required");
                    sendRequirementLine(player, "Great Sage", hasGreatSage, "required");
                    sendRequirementLine(player, "EP", epOk, formatNumber(ep) + " / " + formatNumber(REQUIRED_GRANTER_EP));
                    sendRequirementLine(player, "Mastered skills", masteredSkillsOk, masteredSkillCount + " / " + REQUIRED_GRANTER_MASTERED_SKILLS);
                    sendRequirementLine(player, "Nearby subordinates", nearbySubordinatesOk, nearbySubordinateCount + " / " + REQUIRED_GRANTER_SUBORDINATES);
                    sendRequirementLine(player, "Recognized subordinates", recognizedSubordinatesOk, recognizedSubordinateCount + " / " + REQUIRED_GRANTER_SUBORDINATES);
                    sendRequirementLine(player, "Subordinate requirement", subordinateRequirementOk, "nearby OR recognized");

                    sendInfoLine(player, "Nearby living entities in " + formatNumber(SUBORDINATE_SCAN_RADIUS) + " blocks", String.valueOf(nearbyLivingCount));
                    sendInfoLine(player, "Detected subordinate names", getEntityNamePreview(nearbySubordinates));

                    sendBooleanLine(player, "Can naturally awaken Granter now", canNaturallyAwakenGranter);

                    if (!canNaturallyAwakenGranter) {
                        player.sendSystemMessage(Component.literal("If nearby living entities is high but nearby subordinates is 0, SubordinateHelper is probably not recognizing your named mobs as subordinates.")
                                .withStyle(ChatFormatting.GRAY));
                    }

                    return 1;
                })
        );
    }

    private static boolean hasGranter(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.GRANTER.get().getRegistryName())
                .isPresent();
    }

    private static boolean hasBenevolentEmpowerment(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName())
                .isPresent();
    }

    private static boolean hasAbsoluteGovernance(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName())
                .isPresent();
    }

    private static int getMasteredSkillCount(ServerPlayer player) {
        return (int) SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .filter(instance -> instance.isMastered(player))
                .count();
    }

    private static List<LivingEntity> getNearbyLivingEntities(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(SUBORDINATE_SCAN_RADIUS),
                entity -> entity != player
        );
    }

    private static List<LivingEntity> getNearbySubordinates(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(SUBORDINATE_SCAN_RADIUS),
                entity -> entity != player && SubordinateHelper.isSubordinate(player, entity)
        );
    }

    private static void sendBooleanLine(ServerPlayer player, String label, boolean value) {
        player.sendSystemMessage(Component.literal(" - " + label + ": " + value)
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static void sendRequirementLine(ServerPlayer player, String label, boolean passed, String details) {
        player.sendSystemMessage(Component.literal(" - " + label + ": " + (passed ? "PASS" : "FAIL") + " (" + details + ")")
                .withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static void sendInfoLine(ServerPlayer player, String label, String value) {
        player.sendSystemMessage(Component.literal(" - " + label + ": " + value)
                .withStyle(ChatFormatting.GRAY));
    }

    private static String getEntityNamePreview(List<LivingEntity> entities) {
        if (entities.isEmpty()) {
            return "<none>";
        }

        StringBuilder builder = new StringBuilder();

        int maxShown = Math.min(entities.size(), 5);

        for (int i = 0; i < maxShown; i++) {
            if (i > 0) {
                builder.append(", ");
            }

            builder.append(entities.get(i).getDisplayName().getString());
        }

        if (entities.size() > maxShown) {
            builder.append(" +").append(entities.size() - maxShown).append(" more");
        }

        return builder.toString();
    }

    private static String getCustomName(ServerPlayer player) {
        Component customName = player.getCustomName();
        return customName == null ? "<none>" : customName.getString();
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "<none>" : name;
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }
}