package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.neoforge.network.event
        .RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration
        .PayloadRegistrar;

public final class NetworkRegistry {

    private NetworkRegistry() {
    }

    public static void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        PayloadRegistrar registrar =
                event.registrar(
                                MoosTensuraAddon.MODID
                        )
                        /*
                         * Version 3 added authoritative RGB and bold-state
                         * fields to the recognition display-name payload.
                         *
                         * Version 4 added the server-authoritative recognition
                         * progress-screen payload.
                         *
                         * Version 5 adds:
                         * - the protected client-to-server refresh request,
                         * - recognition guidance entries,
                         * - authorized debug details in the screen snapshot.
                         *
                         * Older clients must not decode the new structures.
                         */
                        .versioned("5");

        registrar.playToServer(
                SelectSkillPayload.TYPE,
                SelectSkillPayload.STREAM_CODEC,
                SelectSkillPayload::handle
        );

        registrar.playToServer(
                ExecuteUltimateSubordinateSkillPayload.TYPE,
                ExecuteUltimateSubordinateSkillPayload.STREAM_CODEC,
                ExecuteUltimateSubordinateSkillPayload::handle
        );

        registrar.playToServer(
                ExecuteUltimateConfirmationPayload.TYPE,
                ExecuteUltimateConfirmationPayload.STREAM_CODEC,
                ExecuteUltimateConfirmationPayload::handle
        );

        registrar.playToServer(
                RequestRecognitionProgressScreenPayload.TYPE,
                RequestRecognitionProgressScreenPayload.STREAM_CODEC,
                RequestRecognitionProgressScreenPayload::handle
        );

        registrar.playToClient(
                OpenGranterScreenPayload.TYPE,
                OpenGranterScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleOpenGranterScreen
        );

        registrar.playToClient(
                OpenUltimateSubordinateSkillScreenPayload.TYPE,
                OpenUltimateSubordinateSkillScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleOpenUltimateSubordinateSkillScreen
        );

        registrar.playToClient(
                OpenSubordinateOverviewScreenPayload.TYPE,
                OpenSubordinateOverviewScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleOpenSubordinateOverviewScreen
        );

        registrar.playToClient(
                OpenUltimateConfirmationScreenPayload.TYPE,
                OpenUltimateConfirmationScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleOpenUltimateConfirmationScreen
        );

        registrar.playToClient(
                OpenRecognitionProgressScreenPayload.TYPE,
                OpenRecognitionProgressScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleOpenRecognitionProgressScreen
        );

        registrar.playToClient(
                SyncRecognitionDisplayNamePayload.TYPE,
                SyncRecognitionDisplayNamePayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleRecognitionDisplayNameSync
        );
    }
}