package com.mooswqz.moostensuraaddon.recognition;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RecognitionSubordinateCombatTracker {

    private static final long PARTICIPATION_WINDOW_TICKS =
            20L * 120L;

    private static final int MAX_ACTIVE_MAJOR_ENEMIES = 4096;

    private static final RecognitionRuntimeCapTable<UUID, ParticipationRecord>
            ACTIVE_MAJOR_ENEMIES = new RecognitionRuntimeCapTable<>(
            MAX_ACTIVE_MAJOR_ENEMIES,
            ParticipationRecord::firstObservedGameTime
    );

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
                        existing == null
                                ? gameTime
                                : existing.firstObservedGameTime(),
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
        ACTIVE_MAJOR_ENEMIES.removeIf(
                entry -> currentGameTime
                        > entry.getValue()
                        .expirationGameTime()
        );
    }

    public static synchronized void forgetOwner(
            UUID ownerUuid
    ) {
        if (ownerUuid == null) {
            return;
        }

        for (var entry : ACTIVE_MAJOR_ENEMIES.snapshotEntries()) {
            ParticipationRecord record = entry.getValue();

            if (!record.participatingOwners().contains(ownerUuid)) {
                continue;
            }

            Set<UUID> remainingOwners = new HashSet<>(
                    record.participatingOwners()
            );
            remainingOwners.remove(ownerUuid);

            if (remainingOwners.isEmpty()) {
                ACTIVE_MAJOR_ENEMIES.remove(entry.getKey());
            } else {
                ACTIVE_MAJOR_ENEMIES.put(
                        entry.getKey(),
                        new ParticipationRecord(
                                record.dimension(),
                                remainingOwners,
                                record.firstObservedGameTime(),
                                record.expirationGameTime()
                        )
                );
            }
        }
    }

    public static synchronized void clearAll() {
        ACTIVE_MAJOR_ENEMIES.clear();
    }

    public static synchronized RuntimeSnapshot inspect(
            UUID selectedOwner
    ) {
        int selectedOwnerRecords = 0;

        if (selectedOwner != null) {
            for (var entry :
                    ACTIVE_MAJOR_ENEMIES.snapshotEntries()) {
                if (entry.getValue()
                        .participatingOwners()
                        .contains(selectedOwner)) {
                    selectedOwnerRecords++;
                }
            }
        }

        return new RuntimeSnapshot(
                ACTIVE_MAJOR_ENEMIES.size(),
                selectedOwnerRecords,
                MAX_ACTIVE_MAJOR_ENEMIES
        );
    }

    /** Installs one synthetic participant used only by the reset fixture. */
    public static synchronized void installResetFixture(
            ServerPlayer owner
    ) {
        if (owner == null) {
            return;
        }

        long gameTime = owner.level().getGameTime();

        ACTIVE_MAJOR_ENEMIES.put(
                UUID.randomUUID(),
                new ParticipationRecord(
                        owner.level().dimension(),
                        Set.of(owner.getUUID()),
                        gameTime,
                        gameTime + PARTICIPATION_WINDOW_TICKS
                )
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
            long firstObservedGameTime,
            long expirationGameTime
    ) {

        private ParticipationRecord {
            participatingOwners =
                    participatingOwners == null
                            ? Set.of()
                            : Set.copyOf(participatingOwners);
        }
    }

    public record RuntimeSnapshot(
            int activeMajorEnemies,
            int selectedOwnerRecords,
            int maximumActiveMajorEnemies
    ) {
    }
}
