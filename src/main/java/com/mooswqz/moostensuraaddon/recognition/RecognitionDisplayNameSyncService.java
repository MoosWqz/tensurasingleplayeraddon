package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.network
        .SyncRecognitionDisplayNamePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

public final class RecognitionDisplayNameSyncService {

    private RecognitionDisplayNameSyncService() {
    }

    public static SyncRecognitionDisplayNamePayload createPayload(
            ServerPlayer subject
    ) {
        if (subject == null) {
            throw new IllegalArgumentException(
                    "A subject player is required."
            );
        }

        Optional<
                RecognitionDisplayNameService
                        .VisibleRecognitionName
                > visibleRecognition =
                RecognitionDisplayNameService
                        .getVisibleRecognition(
                                subject
                        );

        if (visibleRecognition.isEmpty()) {
            return SyncRecognitionDisplayNamePayload
                    .clear(
                            subject.getUUID()
                    );
        }

        RecognitionDisplayNameService
                .VisibleRecognitionName display =
                visibleRecognition.get();

        return new SyncRecognitionDisplayNamePayload(
                subject.getUUID(),
                true,
                display.title(),
                display.rgbColor(),
                display.bold()
        );
    }

    public static void sendToPlayer(
            ServerPlayer receiver,
            ServerPlayer subject
    ) {
        if (receiver == null || subject == null) {
            return;
        }

        PacketDistributor.sendToPlayer(
                receiver,
                createPayload(
                        subject
                )
        );
    }

    /**
     * Seeds a connecting client with every currently online recognition name.
     */
    public static void sendFullSnapshot(
            ServerPlayer receiver
    ) {
        if (receiver == null) {
            return;
        }

        MinecraftServer server =
                receiver.getServer();

        if (server == null) {
            return;
        }

        for (ServerPlayer subject :
                server.getPlayerList()
                        .getPlayers()) {

            sendToPlayer(
                    receiver,
                    subject
            );
        }
    }

    /**
     * Broadcasts one player's current recognition-name state.
     */
    public static void broadcast(
            ServerPlayer subject
    ) {
        if (subject == null) {
            return;
        }

        MinecraftServer server =
                subject.getServer();

        if (server == null) {
            return;
        }

        SyncRecognitionDisplayNamePayload payload =
                createPayload(
                        subject
                );

        for (ServerPlayer receiver :
                server.getPlayerList()
                        .getPlayers()) {

            PacketDistributor.sendToPlayer(
                    receiver,
                    payload
            );
        }
    }

    /**
     * Refreshes server display-name caches, updates the tab list and sends
     * the latest server-authoritative nametag state.
     */
    public static void refreshAndBroadcast(
            ServerPlayer subject
    ) {
        if (subject == null) {
            return;
        }

        subject.refreshDisplayName();
        subject.refreshTabListName();

        broadcast(
                subject
        );
    }

    public static void sendClearToOtherPlayers(
            ServerPlayer subject
    ) {
        if (subject == null) {
            return;
        }

        MinecraftServer server =
                subject.getServer();

        if (server == null) {
            return;
        }

        SyncRecognitionDisplayNamePayload payload =
                SyncRecognitionDisplayNamePayload
                        .clear(
                                subject.getUUID()
                        );

        for (ServerPlayer receiver :
                server.getPlayerList()
                        .getPlayers()) {

            if (receiver.getUUID()
                    .equals(
                            subject.getUUID()
                    )) {

                continue;
            }

            PacketDistributor.sendToPlayer(
                    receiver,
                    payload
            );
        }
    }
}