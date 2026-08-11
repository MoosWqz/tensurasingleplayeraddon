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

        ServerPlayer directPlayer =
                causingEntity instanceof ServerPlayer causingPlayer
                        ? causingPlayer
                        : directEntity instanceof ServerPlayer directPlayerActor
                        ? directPlayerActor
                        : null;

        ServerPlayer projectileOwner =
                resolveProjectileOwner(directEntity);

        if (projectileOwner == null) {
            projectileOwner = resolveProjectileOwner(
                    causingEntity
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

        ServerPlayer tameOwner =
                resolveTamableOwner(causingEntity);

        if (tameOwner == null) {
            tameOwner = resolveTamableOwner(directEntity);
        }

        RecognitionAttributionPolicy.ActorKind actorKind =
                RecognitionAttributionPolicy.classifyActor(
                        projectileOwner != null,
                        directPlayer != null,
                        subordinateOwner != null,
                        tameOwner != null
                );

        ServerPlayer creditedPlayer = switch (actorKind) {
            case PLAYER_PROJECTILE -> projectileOwner;
            case DIRECT_PLAYER -> directPlayer;
            case TENSURA_SUBORDINATE -> subordinateOwner;
            case OWNED_COMPANION -> tameOwner;
            case NONE -> null;
        };

        if (creditedPlayer == null) {
            return Optional.empty();
        }

        CombatCreditType creditType = switch (actorKind) {
            case PLAYER_PROJECTILE ->
                    CombatCreditType.PLAYER_PROJECTILE;
            case TENSURA_SUBORDINATE ->
                    CombatCreditType.TENSURA_SUBORDINATE;
            case OWNED_COMPANION ->
                    CombatCreditType.OWNED_COMPANION;
            case NONE, DIRECT_PLAYER ->
                    CombatCreditType.DIRECT_PLAYER;
        };

        return Optional.of(
                new CombatCredit(
                        creditedPlayer,
                        creditType
                )
        );
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
