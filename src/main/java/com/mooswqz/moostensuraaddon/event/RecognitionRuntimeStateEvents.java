package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.command.RecognitionAttributionDebugCommand;
import com.mooswqz.moostensuraaddon.recognition.CivilianDefenseTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatCreditTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionSubordinateCombatTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Explicit lifecycle cleanup for runtime-only recognition state.
 *
 * <p>None of the state cleared here is persistent player progression. The
 * event subscriber only removes encounter windows, UI rate-limit state and
 * short-lived cached payloads.</p>
 */
@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class RecognitionRuntimeStateEvents {

    private RecognitionRuntimeStateEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(
            ServerStartingEvent event
    ) {
        clearRuntimeState();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            RecognitionProgressScreenService.clear(
                    player
            );
            RecognitionAttributionDebugCommand.forgetResetFixture(
                    player.getUUID()
            );
        }
    }

    @SubscribeEvent
    public static void onServerStopped(
            ServerStoppedEvent event
    ) {
        CivilianDefenseTracker.clearServer(
                event.getServer()
        );
        RecognitionCombatCreditTracker.clearServer(
                event.getServer()
        );

        /*
         * Clear every remaining weak-key entry as an extra defence for dev
         * environments that repeatedly start integrated servers in one JVM.
         */
        clearRuntimeState();
    }

    private static void clearRuntimeState() {
        CivilianDefenseTracker.clearAll();
        RecognitionCombatCreditTracker.clearAll();
        RecognitionSubordinateCombatTracker.clearAll();
        RecognitionProgressScreenService.clearAll();
        RecognitionAttributionDebugCommand.clearResetFixtures();
    }
}
