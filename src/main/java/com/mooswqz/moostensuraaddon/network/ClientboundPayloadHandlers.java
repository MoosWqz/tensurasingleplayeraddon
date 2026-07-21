package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.client
        .ClientPayloadHandler;
import net.neoforged.neoforge.network.handling
        .IPayloadContext;

public final class ClientboundPayloadHandlers {

    private ClientboundPayloadHandlers() {
    }

    public static void handleOpenGranterScreen(
            OpenGranterScreenPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                ClientPayloadHandler.openGranterScreen(
                        payload
                )
        );
    }

    public static void handleOpenUltimateSubordinateSkillScreen(
            OpenUltimateSubordinateSkillScreenPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                ClientPayloadHandler
                        .openUltimateSubordinateSkillScreen(
                                payload
                        )
        );
    }

    public static void handleOpenSubordinateOverviewScreen(
            OpenSubordinateOverviewScreenPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                ClientPayloadHandler
                        .openSubordinateOverviewScreen(
                                payload
                        )
        );
    }

    public static void handleOpenUltimateConfirmationScreen(
            OpenUltimateConfirmationScreenPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                ClientPayloadHandler
                        .openUltimateConfirmationScreen(
                                payload
                        )
        );
    }

    public static void handleOpenRecognitionProgressScreen(
            OpenRecognitionProgressScreenPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                ClientPayloadHandler
                        .openRecognitionProgressScreen(
                                payload
                        )
        );
    }

    public static void handleRecognitionDisplayNameSync(
            SyncRecognitionDisplayNamePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                ClientPayloadHandler
                        .syncRecognitionDisplayName(
                                payload
                        )
        );
    }
}