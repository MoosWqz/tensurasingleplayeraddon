package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionDisplayNameService;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionDisplayNameSyncService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class RecognitionDisplayNameEvents {

    private RecognitionDisplayNameEvents() {
    }

    /**
     * Server-authoritative display name used by death messages,
     * advancement announcements and other vanilla display-name consumers.
     */
    @SubscribeEvent
    public static void onNameFormat(
            PlayerEvent.NameFormat event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {

            return;
        }

        RecognitionDisplayNameService
                .getVisibleRecognition(
                        player
                )
                .ifPresent(display ->
                        event.setDisplayname(
                                RecognitionDisplayNameService
                                        .appendTitle(
                                                event.getDisplayname(),
                                                display.title(),
                                                display.rgbColor(),
                                                display.bold()
                                        )
                        )
                );
    }

    @SubscribeEvent
    public static void onTabListNameFormat(
            PlayerEvent.TabListNameFormat event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {

            return;
        }

        if (RecognitionDisplayNameService
                .getVisibleRecognition(
                        player
                )
                .isEmpty()) {

            return;
        }

        /*
         * getDisplayName() already contains the recognition title, RGB color
         * and pure-recognition bold state.
         */
        event.setDisplayName(
                player.getDisplayName()
        );
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {

            return;
        }

        player.refreshDisplayName();
        player.refreshTabListName();

        /*
         * Seed this client with all online recognized players, then broadcast
         * the connecting player's state to every connected client.
         */
        RecognitionDisplayNameSyncService
                .sendFullSnapshot(
                        player
                );

        RecognitionDisplayNameSyncService
                .broadcast(
                        player
                );
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer receiver)) {

            return;
        }

        if (!(event.getTarget()
                instanceof ServerPlayer subject)) {

            return;
        }

        RecognitionDisplayNameSyncService
                .sendToPlayer(
                        receiver,
                        subject
                );
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {

            return;
        }

        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(
                        player
                );
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {

            return;
        }

        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(
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

        RecognitionDisplayNameSyncService
                .sendClearToOtherPlayers(
                        player
                );
    }
}