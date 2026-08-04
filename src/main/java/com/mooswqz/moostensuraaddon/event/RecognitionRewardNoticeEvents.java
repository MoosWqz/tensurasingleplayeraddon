package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition.RecognitionRewardNoticeService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class RecognitionRewardNoticeEvents {

    private RecognitionRewardNoticeEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecognitionRewardNoticeService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event
    ) {
        RecognitionRewardNoticeService.copyPersistentState(
                event.getOriginal(),
                event.getEntity()
        );
    }
}