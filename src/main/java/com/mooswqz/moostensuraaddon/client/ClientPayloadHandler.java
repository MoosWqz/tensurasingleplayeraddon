package com.mooswqz.moostensuraaddon.client;

import com.mooswqz.moostensuraaddon.client.screen.GranterSkillSelectionScreen;
import com.mooswqz.moostensuraaddon.client.screen.SubordinateOverviewScreen;
import com.mooswqz.moostensuraaddon.client.screen.UltimateConfirmationScreen;
import com.mooswqz.moostensuraaddon.client.screen.UltimateSubordinateSkillSelectionScreen;
import com.mooswqz.moostensuraaddon.network.OpenGranterScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateConfirmationScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientPayloadHandler {
    public static void openGranterScreen() {
        openGranterScreen(OpenGranterScreenPayload.INSTANCE);
    }

    public static void openGranterScreen(OpenGranterScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(new GranterSkillSelectionScreen());
        }
    }

    public static void openUltimateSubordinateSkillScreen(OpenUltimateSubordinateSkillScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(new UltimateSubordinateSkillSelectionScreen(payload));
        }
    }

    public static void openSubordinateOverviewScreen(OpenSubordinateOverviewScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(new SubordinateOverviewScreen(payload));
        }
    }

    public static void openUltimateConfirmationScreen(OpenUltimateConfirmationScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.setScreen(new UltimateConfirmationScreen(payload));
        }
    }
}