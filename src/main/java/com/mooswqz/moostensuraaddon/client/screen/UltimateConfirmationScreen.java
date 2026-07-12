package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.network.ExecuteUltimateConfirmationPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateConfirmationScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

public class UltimateConfirmationScreen extends Screen {
    private static final int BUTTON_WIDTH = 125;
    private static final int BUTTON_HEIGHT = 20;

    private final OpenUltimateConfirmationScreenPayload payload;

    public UltimateConfirmationScreen(OpenUltimateConfirmationScreenPayload payload) {
        super(Component.literal(payload.massGrant() ? "Confirm Mass Grant" : "Confirm Ranged Take Back"));
        this.payload = payload;
    }

    public static void open(OpenUltimateConfirmationScreenPayload payload) {
        Minecraft.getInstance().setScreen(new UltimateConfirmationScreen(payload));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonY = Math.min(this.height - 42, 154);

        addRenderableWidget(Button.builder(
                Component.literal(payload.massGrant() ? "Confirm Grant" : "Confirm Take Back")
                        .withStyle(payload.massGrant() ? ChatFormatting.GREEN : ChatFormatting.GOLD),
                button -> confirm()
        ).pos(centerX - 130, buttonY).size(BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = payload.affectedTargets() > 0;

        addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                button -> onClose()
        ).pos(centerX + 5, buttonY).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new ExecuteUltimateConfirmationPayload(
                payload.massGrant(),
                payload.benevolent()
        ));

        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        guiGraphics.drawCenteredString(
                this.font,
                this.title,
                centerX,
                24,
                0xFFFFFF
        );

        guiGraphics.drawCenteredString(
                this.font,
                getPathLine(),
                centerX,
                44,
                payload.benevolent() ? 0xFFDD55 : 0xAA55FF
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal("Skill: " + payload.selectedSkillName())
                        .withStyle(ChatFormatting.AQUA),
                centerX,
                66,
                0xFFFFFF
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal("Affected targets: " + payload.affectedTargets())
                        .withStyle(ChatFormatting.GRAY),
                centerX,
                84,
                0xA0A0A0
        );

        guiGraphics.drawCenteredString(
                this.font,
                getCostLine(),
                centerX,
                102,
                payload.massGrant() ? 0xA0A0A0 : 0x707070
        );

        guiGraphics.drawCenteredString(
                this.font,
                getWarningLine(),
                centerX,
                124,
                payload.massGrant() ? 0x55FF55 : 0xFFAA55
        );
    }

    private Component getPathLine() {
        String path = payload.benevolent()
                ? "Benevolent Empowerment"
                : "Absolute Governance";

        return Component.literal(path);
    }

    private Component getCostLine() {
        if (!payload.massGrant()) {
            return Component.literal("Cost: none").withStyle(ChatFormatting.DARK_GRAY);
        }

        return Component.literal("Total cost: " + formatNumber(payload.totalCost()) + " magicules")
                .withStyle(ChatFormatting.GRAY);
    }

    private Component getWarningLine() {
        if (payload.massGrant()) {
            return Component.literal("This will grant the selected skill to all valid nearby subordinates.");
        }

        return Component.literal("This will remove this granted skill from all affected nearby subordinates.");
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}