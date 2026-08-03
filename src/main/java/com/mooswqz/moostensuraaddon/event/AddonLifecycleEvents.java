package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.lifecycle.AddonPlayerDataResetService;
import com.mooswqz.moostensuraaddon.lifecycle.GranterAcquisitionTracker;
import com.mooswqz.moostensuraaddon.lifecycle.RecognitionNativeEndowmentService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Connects actual player lifecycle events to the bounded incarnation state.
 */
@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class AddonLifecycleEvents {

    private static final ResourceLocation CHARACTER_RESET_SCROLL =
            ResourceLocation.fromNamespaceAndPath(
                    "tensura",
                    "character_reset_scroll"
            );

    private AddonLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onFinishedUsingItem(
            LivingEntityUseItemEvent.Finish event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                event.getItem().getItem()
        );

        if (!CHARACTER_RESET_SCROLL.equals(itemId)) {
            return;
        }

        AddonPlayerDataResetService.resetForNewIncarnation(
                player,
                AddonPlayerDataResetService.ResetReason.CHARACTER_RESET
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        AddonIncarnationState.load(player);
        GranterAcquisitionTracker.initializeOnLogin(player);
        RecognitionNativeEndowmentService.synchronize(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event
    ) {
        AddonIncarnationState.copyPersistentState(
                event.getOriginal(),
                event.getEntity()
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        RecognitionProgressScreenService.clear(player.getUUID());
        SubordinateOverviewService.forget(player.getUUID());
    }
}