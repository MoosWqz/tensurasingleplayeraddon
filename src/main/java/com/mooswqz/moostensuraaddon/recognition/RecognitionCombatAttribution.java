package com.mooswqz.moostensuraaddon.recognition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Optional;
import java.util.UUID;

public final class RecognitionCombatAttribution {

    private RecognitionCombatAttribution() {
    }

    public static Optional<CombatCredit> resolve(
            DamageSource damageSource
    ) {
        if (damageSource == null) {
            return Optional.empty();
        }

        Entity causingEntity = damageSource.getEntity();
        Entity directEntity = damageSource.getDirectEntity();

        if (causingEntity instanceof ServerPlayer player) {
            CombatCreditType type =
                    directEntity instanceof Projectile
                            ? CombatCreditType.PLAYER_PROJECTILE
                            : CombatCreditType.DIRECT_PLAYER;

            return Optional.of(
                    new CombatCredit(player, type)
            );
        }

        ServerPlayer projectileOwner =
                resolveProjectileOwner(directEntity);

        if (projectileOwner != null) {
            return Optional.of(
                    new CombatCredit(
                            projectileOwner,
                            CombatCreditType.PLAYER_PROJECTILE
                    )
            );
        }

        projectileOwner =
                resolveProjectileOwner(causingEntity);

        if (projectileOwner != null) {
            return Optional.of(
                    new CombatCredit(
                            projectileOwner,
                            CombatCreditType.PLAYER_PROJECTILE
                    )
            );
        }

        /*
         * Tensura subordinates take precedence over generic tame ownership.
         */
        ServerPlayer subordinateOwner =
                RecognitionSubordinateSupport.findOnlineOwner(
                        causingEntity
                );

        if (subordinateOwner == null) {
            subordinateOwner =
                    RecognitionSubordinateSupport.findOnlineOwner(
                            directEntity
                    );
        }

        if (subordinateOwner != null) {
            return Optional.of(
                    new CombatCredit(
                            subordinateOwner,
                            CombatCreditType.TENSURA_SUBORDINATE
                    )
            );
        }

        ServerPlayer tameOwner =
                resolveTamableOwner(causingEntity);

        if (tameOwner == null) {
            tameOwner = resolveTamableOwner(directEntity);
        }

        if (tameOwner != null) {
            return Optional.of(
                    new CombatCredit(
                            tameOwner,
                            CombatCreditType.OWNED_COMPANION
                    )
            );
        }

        if (directEntity instanceof ServerPlayer player) {
            return Optional.of(
                    new CombatCredit(
                            player,
                            CombatCreditType.DIRECT_PLAYER
                    )
            );
        }

        return Optional.empty();
    }

    private static ServerPlayer resolveProjectileOwner(
            Entity candidate
    ) {
        if (!(candidate instanceof Projectile projectile)) {
            return null;
        }

        return projectile.getOwner()
                instanceof ServerPlayer player
                ? player
                : null;
    }

    private static ServerPlayer resolveTamableOwner(
            Entity candidate
    ) {
        if (!(candidate instanceof TamableAnimal tamableAnimal)) {
            return null;
        }

        UUID ownerUuid = tamableAnimal.getOwnerUUID();

        if (ownerUuid == null
                || !(tamableAnimal.level()
                instanceof ServerLevel serverLevel)) {
            return null;
        }

        return serverLevel
                .getServer()
                .getPlayerList()
                .getPlayer(ownerUuid);
    }

    public record CombatCredit(
            ServerPlayer player,
            CombatCreditType type
    ) {

        public CombatCredit {
            if (player == null) {
                throw new IllegalArgumentException(
                        "Combat credit requires a player."
                );
            }

            if (type == null) {
                type = CombatCreditType.DIRECT_PLAYER;
            }
        }

        public boolean isSoloPlayerAction() {
            return type == CombatCreditType.DIRECT_PLAYER
                    || type == CombatCreditType.PLAYER_PROJECTILE;
        }

        public boolean isOwnedCompanionAction() {
            return type == CombatCreditType.OWNED_COMPANION;
        }

        public boolean isTensuraSubordinateAction() {
            return type == CombatCreditType.TENSURA_SUBORDINATE;
        }
    }

    public enum CombatCreditType {
        DIRECT_PLAYER,
        PLAYER_PROJECTILE,
        OWNED_COMPANION,
        TENSURA_SUBORDINATE
    }
}