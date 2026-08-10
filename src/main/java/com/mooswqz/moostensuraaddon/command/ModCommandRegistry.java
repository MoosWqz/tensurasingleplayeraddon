package com.mooswqz.moostensuraaddon.command;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class ModCommandRegistry {

    private ModCommandRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event
    ) {
        /*
         * Register the canonical player/admin command root first.
         */
        MoosTensuraCommand.register(
                event.getDispatcher()
        );

        /*
         * Attach permission-gated developer tooling below the canonical root.
         * No standalone development aliases are registered for release.
         */
        DebugCommand.attachToMoosTensuraRoot(
                event.getDispatcher()
        );

        /*
         * Legacy self-endowment remains available for compatibility while its
         * save/migration policy is audited separately. It is intentionally not
         * advertised by the normal guide/help surface.
         */
        GetNamedCommand.register(
                event.getDispatcher()
        );
    }
}
