package com.mooswqz.moostensuraaddon.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class ActionbarHelper {
    private ActionbarHelper() {
    }

    public static void send(ServerPlayer player, Component message) {
        if (player == null || message == null) {
            return;
        }

        player.displayClientMessage(message, true);
    }

    public static void send(ServerPlayer player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }

        send(player, Component.literal(message));
    }

    public static void sendIfPlayer(LivingEntity entity, Component message) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        send(player, message);
    }

    public static void sendIfPlayer(LivingEntity entity, String message) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        send(player, message);
    }
}