package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionBalanceReloadListener;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionTitlePoolReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Registers the server-side recognition datapack reload listeners.
 *
 * This event is fired while server resources are initially constructed and
 * whenever the server performs a datapack reload.
 */
@EventBusSubscriber(
        modid = MoosTensuraAddon.MODID
)
public final class RecognitionDataReloadEvents {

    private RecognitionDataReloadEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(
            AddReloadListenerEvent event
    ) {
        /*
         * Balance is registered first so recognition evaluations performed
         * immediately after reload already observe the new immutable snapshot.
         */
        event.addListener(
                RecognitionBalanceReloadListener.INSTANCE
        );

        event.addListener(
                RecognitionTitlePoolReloadListener.INSTANCE
        );
    }
}