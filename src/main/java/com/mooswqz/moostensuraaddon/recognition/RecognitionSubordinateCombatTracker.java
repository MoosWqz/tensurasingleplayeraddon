package com.mooswqz.moostensuraaddon.recognition;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RecognitionSubordinateCombatTracker {

    private static final long PARTICIPATION_WINDOW_TICKS =
            20L * 120L;

    private static final Map<UUID, ParticipationRecord>
            ACTIVE_MAJOR_ENEMIES = new HashMap<>();

    private RecognitionSubordinateCombatTracker() {
    }

    public static synchronized void recordIncomingDamage(
            LivingEntity victim,
            DamageSource source
    ) {
        if (victim == null
                || source == null
                || victim.level().isClientSide()
                || !victim.getType().is(
                RecognitionEntityTags.MAJOR_ENEMIES
        )) {
            return;
        }

        ServerPlayer owner = resolveSubordinateOwner(source);

        if (owner == null) {
            return;
        }

        long gameTime = victim.level().getGameTime();

        ParticipationRecord existing =
                ACTIVE_MAJOR_ENEMIES.get(victim.getUUID());

        Set<UUID> participatingOwners = new HashSet<>();

        if (existing != null
                && existing.dimension().equals(
                victim.level().dimension()
        )
                && gameTime <= existing.expirationGameTime()) {
            participatingOwners.addAll(
                    existing.participatingOwners()
            );
        }

        participatingOwners.add(owner.getUUID());

        ACTIVE_MAJOR_ENEMIES.put(
                victim.getUUID(),
                new ParticipationRecord(
                        victim.level().dimension(),
                        Set.copyOf(participatingOwners),
                        gameTime + PARTICIPATION_WINDOW_TICKS
                )
        );
    }

    public static synchronized Set<UUID> consumeParticipants(
            LivingEntity victim
    ) {
        if (victim == null) {
            return Set.of();
        }

        ParticipationRecord record =
                ACTIVE_MAJOR_ENEMIES.remove(
                        victim.getUUID()
                );

        if (record == null) {
            return Set.of();
        }

        if (!record.dimension().equals(
                victim.level().dimension()
        )) {
            return Set.of();
        }

        if (victim.level().getGameTime()
                > record.expirationGameTime()) {
            return Set.of();
        }

        return record.participatingOwners();
    }

    public static synchronized void forget(
            LivingEntity entity
    ) {
        if (entity == null) {
            return;
        }

        ACTIVE_MAJOR_ENEMIES.remove(entity.getUUID());
    }

    public static synchronized void cleanup(
            long currentGameTime
    ) {
        ACTIVE_MAJOR_ENEMIES.entrySet().removeIf(
                entry -> currentGameTime
                        > entry.getValue()
                        .expirationGameTime()
        );
    }

    private static ServerPlayer resolveSubordinateOwner(
            DamageSource source
    ) {
        Entity causingEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        ServerPlayer owner =
                RecognitionSubordinateSupport.findOnlineOwner(
                        causingEntity
                );

        if (owner != null) {
            return owner;
        }

        return RecognitionSubordinateSupport.findOnlineOwner(
                directEntity
        );
    }

    private record ParticipationRecord(
            ResourceKey<Level> dimension,
            Set<UUID> participatingOwners,
            long expirationGameTime
    ) {

        private ParticipationRecord {
            participatingOwners =
                    participatingOwners == null
                            ? Set.of()
                            : Set.copyOf(participatingOwners);
        }
    }
}