package com.mooswqz.moostensuraaddon.ritual;

import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.util.GreatSageAwakeningHelper;
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
         * A committed answer must remain recoverable even when this update
         * changes the normal order of the altar flows. Resuming its reveal
         * cannot wait behind another ritual because the result is already
         * fixed for this incarnation.
         */
        if (RecognitionNamingRitualManager
                .hasPendingReveal(player)) {

            RecognitionNamingRitualManager.tryStartRitual(
                    player,
                    altarPos
            );

            return;
        }

        /*
         * When Sage is still present, its Great Sage evolution is the first
         * altar milestone. Soul Recognition follows on a later interaction.
         * Disabling either Great Sage progression or its altar ritual leaves
         * Soul Recognition available instead of deadlocking the altar.
         */
        if (shouldPrioritizeGreatSage(player)) {
            GreatSageRitualManager.tryStartRitual(
                    player,
                    altarPos
            );

            return;
        }

        /*
         * Native Tensura naming is compatible metadata, not a blocker: an
         * administrator-created or legacy native name can still be followed
         * by the altar recognition ritual.
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
         * A player who acquired Sage only after recognition can still use the
         * normal Great Sage altar flow here.
         */
        GreatSageRitualManager.tryStartRitual(
                player,
                altarPos
        );
    }

    private static boolean shouldPrioritizeGreatSage(
            ServerPlayer player
    ) {
        return MoosTensuraConfig.SAGE_UPGRADE_ENABLED.get()
                && MoosTensuraConfig.GREAT_SAGE_RITUAL_ENABLED.get()
                && !GreatSageAwakeningHelper.hasGreatSage(player)
                && GreatSageAwakeningHelper.findSage(player)
                .isPresent();
    }
}
