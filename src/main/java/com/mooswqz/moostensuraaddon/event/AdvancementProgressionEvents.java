package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.lifecycle.GranterAcquisitionTracker;
import com.mooswqz.moostensuraaddon.lifecycle.RecognitionNativeEndowmentService;
import com.mooswqz.moostensuraaddon.util.AddonAdvancementHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Low-frequency state repair for addon advancements and native endowment.
 */
@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class AdvancementProgressionEvents {

    private static final int SYNCHRONIZE_INTERVAL_TICKS = 20;

    private AdvancementProgressionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()
                || player.tickCount % SYNCHRONIZE_INTERVAL_TICKS != 0
                || AddonIncarnationState.isResetGuardActive(player)) {
            return;
        }

        GranterAcquisitionTracker.observe(player);
        RecognitionNativeEndowmentService.synchronize(player);
        AddonAdvancementHelper.awardStateBasedAdvancements(player);
    }
}