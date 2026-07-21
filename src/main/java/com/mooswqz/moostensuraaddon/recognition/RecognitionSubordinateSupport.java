package com.mooswqz.moostensuraaddon.recognition;

import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

public final class RecognitionSubordinateSupport {

    private RecognitionSubordinateSupport() {
    }

    public static ServerPlayer findOnlineOwner(
            Entity candidate
    ) {
        LivingEntity actingEntity =
                resolveActingLivingEntity(candidate);

        if (actingEntity == null
                || actingEntity instanceof ServerPlayer
                || !(actingEntity.level()
                instanceof ServerLevel serverLevel)) {
            return null;
        }

        for (ServerPlayer possibleOwner :
                serverLevel.getServer()
                        .getPlayerList()
                        .getPlayers()) {

            if (isSubordinateOf(
                    possibleOwner,
                    actingEntity
            )) {
                return possibleOwner;
            }
        }

        return null;
    }

    public static boolean isSubordinateOf(
            ServerPlayer player,
            LivingEntity candidate
    ) {
        if (player == null
                || candidate == null
                || candidate == player) {
            return false;
        }

        try {
            return SubordinateHelper.isSubordinate(
                    player,
                    candidate
            );
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static LivingEntity resolveActingLivingEntity(
            Entity candidate
    ) {
        if (candidate instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        if (candidate instanceof Projectile projectile
                && projectile.getOwner()
                instanceof LivingEntity owner) {
            return owner;
        }

        return null;
    }
}