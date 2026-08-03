package com.mooswqz.moostensuraaddon.client;

import com.mooswqz.moostensuraaddon.client.screen.GranterSkillSelectionScreen;
import com.mooswqz.moostensuraaddon.client.screen.RecognitionProgressScreen;
import com.mooswqz.moostensuraaddon.client.screen.SubordinateOverviewScreen;
import com.mooswqz.moostensuraaddon.client.screen.UltimateConfirmationScreen;
import com.mooswqz.moostensuraaddon.client.screen.UltimateMultiGrantScreen;
import com.mooswqz.moostensuraaddon.client.screen.UltimateSubordinateSkillSelectionScreen;
import com.mooswqz.moostensuraaddon.network.OpenGranterScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenRecognitionProgressScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateConfirmationScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateMultiGrantScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;
import com.mooswqz.moostensuraaddon.network.RemoveSubordinateOverviewEntryPayload;
import com.mooswqz.moostensuraaddon.network.SyncRecognitionDisplayNamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void openGranterScreen() {
        openGranterScreen(
                OpenGranterScreenPayload.INSTANCE
        );
    }

    public static void openGranterScreen(
            OpenGranterScreenPayload payload
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(
                    new GranterSkillSelectionScreen()
            );
        }
    }

    public static void openUltimateMultiGrantScreen(
            OpenUltimateMultiGrantScreenPayload payload
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(
                    new UltimateMultiGrantScreen(payload)
            );
        }
    }

    public static void openUltimateSubordinateSkillScreen(
            OpenUltimateSubordinateSkillScreenPayload payload
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(
                    new UltimateSubordinateSkillSelectionScreen(
                            payload
                    )
            );
        }
    }

    public static void openSubordinateOverviewScreen(
            OpenSubordinateOverviewScreenPayload payload
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        if (minecraft.screen instanceof SubordinateOverviewScreen screen) {
            screen.applyPayload(payload);
            return;
        }

        minecraft.setScreen(
                new SubordinateOverviewScreen(payload)
        );
    }

    public static void removeSubordinateOverviewEntry(
            RemoveSubordinateOverviewEntryPayload payload
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (payload == null
                || !(minecraft.screen instanceof SubordinateOverviewScreen screen)) {
            return;
        }

        screen.removeTarget(payload.targetUuid());
    }

    public static void openUltimateConfirmationScreen(
            OpenUltimateConfirmationScreenPayload payload
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(
                    new UltimateConfirmationScreen(payload)
            );
        }
    }

    public static void openRecognitionProgressScreen(
            OpenRecognitionProgressScreenPayload payload
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(
                    new RecognitionProgressScreen(payload)
            );
        }
    }

    public static void syncRecognitionDisplayName(
            SyncRecognitionDisplayNamePayload payload
    ) {
        ClientRecognitionDisplayNameCache.apply(payload);

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        for (AbstractClientPlayer player :
                minecraft.level.players()) {
            if (!player.getUUID().equals(
                    payload.playerUuid()
            )) {
                continue;
            }

            player.refreshDisplayName();
            break;
        }
    }
}