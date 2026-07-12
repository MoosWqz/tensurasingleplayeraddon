package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import com.mooswqz.moostensuraaddon.util.ActionbarHelper;
import com.mooswqz.moostensuraaddon.util.GranterActions;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public class GranterProgressionEvents {
    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final long NAMING_HINT_COOLDOWN_TICKS = 20L * 30L;

    private static final Map<UUID, Long> NAMING_HINT_COOLDOWNS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        tryAwakenGranter(player);
    }

    private static void tryAwakenGranter(ServerPlayer player) {
        if (!MoosTensuraConfig.GRANTER_AWAKENING_ENABLED.get()) {
            return;
        }

        /*
         * Reincarnation-friendly rule:
         * - If the player currently has Granter, do nothing.
         * - If the player currently has an evolved Granter Ultimate Skill, do nothing.
         * - If reincarnation removes both Granter and the evolved Ultimate Skill, the player can awaken Granter again.
         */
        if (hasGranter(player) || hasAnyEvolvedGranterSkill(player)) {
            return;
        }

        GranterProgressData progress = player.getData(AttachmentRegistry.GRANTER_PROGRESS_DATA);
        IExistence existence = TensuraStorages.getExistenceFrom(player);

        List<LivingEntity> nearbySubordinates = getNearbySubordinates(player);

        boolean hasGreatSage = GranterActions.hasGreatSage(player);
        boolean hasEnoughMasteredSkills = getMasteredSkillCount(player) >= MoosTensuraConfig.GRANTER_AWAKENING_REQUIRED_MASTERED_SKILLS.get();
        boolean hasEnoughSubordinates = nearbySubordinates.size() >= MoosTensuraConfig.GRANTER_AWAKENING_REQUIRED_SUBORDINATES.get()
                || progress.getRecognizedSubordinateCount() >= MoosTensuraConfig.GRANTER_AWAKENING_REQUIRED_SUBORDINATES.get();
        boolean hasEnoughEp = getEp(existence) >= MoosTensuraConfig.GRANTER_AWAKENING_REQUIRED_EP.get();
        boolean named = isNamed(existence);

        /*
         * Mystery-friendly hint:
         * If the player has everything else but is missing the named/endowed condition,
         * give a vague actionbar hint instead of a full debug checklist.
         */
        if (!named && hasGreatSage && hasEnoughMasteredSkills && hasEnoughSubordinates && hasEnoughEp) {
            sendNamingHint(player);
            return;
        }

        if (!named) {
            return;
        }

        if (!hasGreatSage) {
            return;
        }

        if (!hasEnoughMasteredSkills) {
            return;
        }

        if (!hasEnoughSubordinates) {
            return;
        }

        if (!hasEnoughEp) {
            return;
        }

        boolean learned = GranterActions.giveGranter(player);

        if (!learned) {
            return;
        }

        for (LivingEntity subordinate : nearbySubordinates) {
            progress.recognizeSubordinate(subordinate.getUUID());
        }

        progress.setAwakenedGranterNaturally(true);
        player.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, progress);

        player.sendSystemMessage(Component.translatable("moostensuraaddon.granter.awakened_naturally")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void sendNamingHint(ServerPlayer player) {
        long currentGameTime = player.level().getGameTime();
        long nextAllowedTime = NAMING_HINT_COOLDOWNS.getOrDefault(player.getUUID(), 0L);

        if (currentGameTime < nextAllowedTime) {
            return;
        }

        NAMING_HINT_COOLDOWNS.put(player.getUUID(), currentGameTime + NAMING_HINT_COOLDOWN_TICKS);

        ActionbarHelper.send(player, Component.translatable("moostensuraaddon.granter.hint.needs_name")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    private static boolean hasGranter(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.GRANTER.get().getRegistryName())
                .isPresent();
    }

    private static boolean hasAnyEvolvedGranterSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName())
                .isPresent()
                || SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName())
                .isPresent();
    }

    private static boolean isNamed(IExistence existence) {
        if (existence == null) {
            return false;
        }

        String name = existence.getName();
        return name != null && !name.isBlank();
    }

    private static double getEp(IExistence existence) {
        if (existence == null) {
            return 0.0D;
        }

        return existence.getEP();
    }

    private static int getMasteredSkillCount(ServerPlayer player) {
        return (int) SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .filter(instance -> instance.isMastered(player))
                .count();
    }

    private static List<LivingEntity> getNearbySubordinates(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(MoosTensuraConfig.GRANTER_AWAKENING_SCAN_RADIUS.get()),
                entity -> entity != player && SubordinateHelper.isSubordinate(player, entity)
        );
    }
}