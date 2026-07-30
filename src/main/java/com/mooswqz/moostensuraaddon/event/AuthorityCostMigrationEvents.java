package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.AuthorityCostMigrationService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class AuthorityCostMigrationEvents {

    private AuthorityCostMigrationEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(
            ServerStartingEvent event
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();
    }
}