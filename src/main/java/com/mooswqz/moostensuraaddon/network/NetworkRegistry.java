package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkRegistry {
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MoosTensuraAddon.MODID)
                .versioned("1");

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

        registrar.playToClient(
                OpenGranterScreenPayload.TYPE,
                OpenGranterScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers::handleOpenGranterScreen
        );

        registrar.playToClient(
                OpenUltimateSubordinateSkillScreenPayload.TYPE,
                OpenUltimateSubordinateSkillScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers::handleOpenUltimateSubordinateSkillScreen
        );

        registrar.playToClient(
                OpenSubordinateOverviewScreenPayload.TYPE,
                OpenSubordinateOverviewScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers::handleOpenSubordinateOverviewScreen
        );

        registrar.playToClient(
                OpenUltimateConfirmationScreenPayload.TYPE,
                OpenUltimateConfirmationScreenPayload.STREAM_CODEC,
                ClientboundPayloadHandlers::handleOpenUltimateConfirmationScreen
        );
    }
}