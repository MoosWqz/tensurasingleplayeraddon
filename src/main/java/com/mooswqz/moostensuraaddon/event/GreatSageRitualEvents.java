package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.ritual
        .GreatSageRitualManager;
import com.mooswqz.moostensuraaddon.ritual
        .RecognitionNamingRitualManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living
        .LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class GreatSageRitualEvents {

    private GreatSageRitualEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        /*
         * Only one manager can contain an active ritual for the player, but
         * both are ticked so each system remains independently testable.
         */
        RecognitionNamingRitualManager.tick(
                player
        );

        GreatSageRitualManager.tick(
                player
        );
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionNamingRitualManager
                .cancelForLogout(player);

        GreatSageRitualManager
                .cancelForLogout(player);
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionNamingRitualManager
                .cancelForDeath(player);

        GreatSageRitualManager
                .cancelForDeath(player);
    }
}