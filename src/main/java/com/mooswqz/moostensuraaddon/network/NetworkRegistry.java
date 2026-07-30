package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkRegistry {

    private NetworkRegistry() {
    }

    public static void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        PayloadRegistrar registrar = event.registrar(
                        MoosTensuraAddon.MODID
                )
                .versioned("9");

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
                ExecuteUltimateMultiGrantPayload.TYPE,
                ExecuteUltimateMultiGrantPayload.STREAM_CODEC,
                ExecuteUltimateMultiGrantPayload::handle
        );

        registrar.playToServer(
                RequestSubordinateOverviewPayload.TYPE,
                RequestSubordinateOverviewPayload.STREAM_CODEC,
                RequestSubordinateOverviewPayload::handle
        );

        registrar.playToClient(
                OpenGranterScreenPayload.TYPE,
                OpenGranterScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleOpenGranterScreen
        );

        registrar.playToClient(
                OpenUltimateMultiGrantScreenPayload.TYPE,
                OpenUltimateMultiGrantScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers
                        ::handleOpenUltimateMultiGrantScreen
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