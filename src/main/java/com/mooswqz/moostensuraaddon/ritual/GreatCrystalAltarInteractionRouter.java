package com.mooswqz.moostensuraaddon.ritual;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GreatCrystalAltarInteractionRouter {

    private GreatCrystalAltarInteractionRouter() {
    }

    public static void interact(
            ServerPlayer player,
            BlockPos altarPos
    ) {
        if (player == null || altarPos == null) {
            return;
        }

        /*
         * The altar supports only one active ritual per player.
         */
        if (RecognitionNamingRitualManager.isRitualActive(
                player
        )) {
            player.displayClientMessage(
                    Component.literal(
                                    "The crystal is already reading your soul."
                            )
                            .withStyle(ChatFormatting.AQUA),
                    true
            );

            return;
        }

        if (GreatSageRitualManager.isRitualActive(
                player
        )) {
            player.displayClientMessage(
                    Component.literal(
                                    "The Great Sage ritual is already in progress."
                            )
                            .withStyle(ChatFormatting.AQUA),
                    true
            );

            return;
        }

        /*
         * Naming always has priority while the player is unnamed.
         *
         * A previously committed but interrupted naming ritual is also sent
         * back through this route so its fixed result can be resumed.
         */
        if (RecognitionNamingRitualManager
                .shouldHandleNaming(player)) {

            RecognitionNamingRitualManager.tryStartRitual(
                    player,
                    altarPos
            );

            return;
        }

        /*
         * Once native naming/endowment is present, the existing altar
         * behavior continues unchanged.
         */
        GreatSageRitualManager.tryStartRitual(
                player,
                altarPos
        );
    }
}