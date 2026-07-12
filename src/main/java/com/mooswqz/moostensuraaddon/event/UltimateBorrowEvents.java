package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.ActionbarHelper;
import com.mooswqz.moostensuraaddon.util.AddonDataCleaner;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public class UltimateBorrowEvents {
    private static final int BORROWED_SKILL_CHECK_INTERVAL_TICKS = 20;
    private static final int FULL_DATA_CLEANUP_INTERVAL_TICKS = 200;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (player.tickCount % BORROWED_SKILL_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        AddonDataCleaner.CleanupResult result = AddonDataCleaner.cleanupPlayer(player);

        if (result.expiredBorrowedSkills() > 0) {
            ActionbarHelper.send(player, Component.translatable(
                    "moostensuraaddon.ultimate.benevolent.borrow.expired_chat",
                    result.expiredBorrowedSkills()
            ).withStyle(ChatFormatting.YELLOW));
        }

        if (player.tickCount % FULL_DATA_CLEANUP_INTERVAL_TICKS == 0) {
            AddonDataCleaner.cleanupNearbyGrantedData(player);
        }
    }
}