package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.ritual.GreatSageRitualManager;
import com.mooswqz.moostensuraaddon.util.ActionbarHelper;
import com.mooswqz.moostensuraaddon.util.GreatSageAwakeningHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public class SageWhisperEvents {
    private static final int CHECK_STEP_TICKS = 20;
    private static final Map<UUID, Integer> WHISPER_COOLDOWNS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (player.tickCount % CHECK_STEP_TICKS != 0) {
            return;
        }

        tickWhispers(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WHISPER_COOLDOWNS.remove(player.getUUID());
        }
    }

    private static void tickWhispers(ServerPlayer player) {
        if (!MoosTensuraConfig.SAGE_WHISPERS_ENABLED.get()) {
            WHISPER_COOLDOWNS.remove(player.getUUID());
            return;
        }

        if (GreatSageRitualManager.isRitualActive(player)) {
            resetCooldown(player);
            return;
        }

        if (GreatSageAwakeningHelper.hasGreatSage(player)) {
            WHISPER_COOLDOWNS.remove(player.getUUID());
            return;
        }

        if (GreatSageAwakeningHelper.findSage(player).isEmpty()) {
            WHISPER_COOLDOWNS.remove(player.getUUID());
            return;
        }

        UUID playerId = player.getUUID();
        int cooldown = WHISPER_COOLDOWNS.getOrDefault(playerId, initialCooldown());

        cooldown -= CHECK_STEP_TICKS;

        if (cooldown > 0) {
            WHISPER_COOLDOWNS.put(playerId, cooldown);
            return;
        }

        resetCooldown(player);

        double chance = MoosTensuraConfig.SAGE_WHISPER_CHANCE.get();

        if (chance <= 0.0D) {
            return;
        }

        if (chance < 1.0D && player.getRandom().nextDouble() > chance) {
            return;
        }

        GreatSageAwakeningHelper.RequirementCheck check = GreatSageAwakeningHelper.checkAltarRequirements(player);
        Component whisper = getWhisperFor(player, check);

        if (whisper != null) {
            ActionbarHelper.send(player, whisper.copy().withStyle(ChatFormatting.AQUA));
        }
    }

    private static Component getWhisperFor(ServerPlayer player, GreatSageAwakeningHelper.RequirementCheck check) {
        if (check == null) {
            return randomGenericWhisper(player);
        }

        if (check.successful()) {
            return randomReadyWhisper(player);
        }

        return switch (check.failureReason()) {
            case NOT_ENOUGH_EP -> randomEpWhisper(player);
            case NOT_ENOUGH_XP -> randomExperienceWhisper(player);
            case NOT_ENOUGH_MAGICULES -> randomMagiculeWhisper(player);
            case NOT_ENOUGH_MASTERED_SKILLS -> randomMasteryWhisper(player);
            case NOT_NAMED -> randomSoulAnchorWhisper(player);
            case DISABLED -> null;
            case ALREADY_HAS_GREAT_SAGE -> null;
            case NO_SAGE -> null;
            case NO_EXISTENCE -> randomGenericWhisper(player);
            case LEARN_FAILED -> randomGenericWhisper(player);
            case NONE -> randomGenericWhisper(player);
        };
    }

    private static Component randomGenericWhisper(ServerPlayer player) {
        return switch (player.getRandom().nextInt(4)) {
            case 0 -> Component.translatable("moostensuraaddon.sage_whisper.generic_0");
            case 1 -> Component.translatable("moostensuraaddon.sage_whisper.generic_1");
            case 2 -> Component.translatable("moostensuraaddon.sage_whisper.generic_2");
            default -> Component.translatable("moostensuraaddon.sage_whisper.generic_3");
        };
    }

    private static Component randomReadyWhisper(ServerPlayer player) {
        return switch (player.getRandom().nextInt(4)) {
            case 0 -> Component.translatable("moostensuraaddon.sage_whisper.ready_0");
            case 1 -> Component.translatable("moostensuraaddon.sage_whisper.ready_1");
            case 2 -> Component.translatable("moostensuraaddon.sage_whisper.ready_2");
            default -> Component.translatable("moostensuraaddon.sage_whisper.ready_3");
        };
    }

    private static Component randomEpWhisper(ServerPlayer player) {
        return switch (player.getRandom().nextInt(3)) {
            case 0 -> Component.translatable("moostensuraaddon.sage_whisper.ep_0");
            case 1 -> Component.translatable("moostensuraaddon.sage_whisper.ep_1");
            default -> Component.translatable("moostensuraaddon.sage_whisper.ep_2");
        };
    }

    private static Component randomExperienceWhisper(ServerPlayer player) {
        return switch (player.getRandom().nextInt(3)) {
            case 0 -> Component.translatable("moostensuraaddon.sage_whisper.experience_0");
            case 1 -> Component.translatable("moostensuraaddon.sage_whisper.experience_1");
            default -> Component.translatable("moostensuraaddon.sage_whisper.experience_2");
        };
    }

    private static Component randomMagiculeWhisper(ServerPlayer player) {
        return switch (player.getRandom().nextInt(3)) {
            case 0 -> Component.translatable("moostensuraaddon.sage_whisper.magicules_0");
            case 1 -> Component.translatable("moostensuraaddon.sage_whisper.magicules_1");
            default -> Component.translatable("moostensuraaddon.sage_whisper.magicules_2");
        };
    }

    private static Component randomMasteryWhisper(ServerPlayer player) {
        return switch (player.getRandom().nextInt(3)) {
            case 0 -> Component.translatable("moostensuraaddon.sage_whisper.mastery_0");
            case 1 -> Component.translatable("moostensuraaddon.sage_whisper.mastery_1");
            default -> Component.translatable("moostensuraaddon.sage_whisper.mastery_2");
        };
    }

    private static Component randomSoulAnchorWhisper(ServerPlayer player) {
        return switch (player.getRandom().nextInt(3)) {
            case 0 -> Component.translatable("moostensuraaddon.sage_whisper.soul_anchor_0");
            case 1 -> Component.translatable("moostensuraaddon.sage_whisper.soul_anchor_1");
            default -> Component.translatable("moostensuraaddon.sage_whisper.soul_anchor_2");
        };
    }

    private static int initialCooldown() {
        return Math.max(20, MoosTensuraConfig.SAGE_WHISPER_INITIAL_DELAY_TICKS.get());
    }

    private static void resetCooldown(ServerPlayer player) {
        int min = Math.max(20, MoosTensuraConfig.SAGE_WHISPER_MIN_INTERVAL_TICKS.get());
        int randomExtra = Math.max(0, MoosTensuraConfig.SAGE_WHISPER_RANDOM_INTERVAL_TICKS.get());
        int cooldown = min;

        if (randomExtra > 0) {
            cooldown += player.getRandom().nextInt(randomExtra + 1);
        }

        WHISPER_COOLDOWNS.put(player.getUUID(), cooldown);
    }
}