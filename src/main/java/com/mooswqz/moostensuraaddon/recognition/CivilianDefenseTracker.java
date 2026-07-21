package com.mooswqz.moostensuraaddon.recognition;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven attribution for protecting civilians.
 *
 * <p>The tracker never scans the world. It only records an encounter when a
 * datapack-classified hostile targets or damages a civilian, and consumes that
 * record when the aggressor dies.</p>
 */
public final class CivilianDefenseTracker {

    private static final long TARGET_ONLY_WINDOW_TICKS =
            20L * 20L;

    private static final long DAMAGE_CONFIRMED_WINDOW_TICKS =
            20L * 45L;

    private static final long CLEANUP_INTERVAL_TICKS =
            20L * 10L;

    /**
     * Hard safety ceiling for pathological mob swarms or malicious automation.
     *
     * <p>Existing encounters may still be refreshed while the map is full,
     * but new encounter IDs are ignored until expired entries are cleaned.</p>
     */
    private static final int MAX_ACTIVE_AGGRESSORS =
            4096;

    private static final Map<UUID, AggressionRecord>
            ACTIVE_AGGRESSORS = new ConcurrentHashMap<>();

    private static long lastCleanupGameTime;

    private CivilianDefenseTracker() {
    }

    /**
     * Vanilla villager-derived entities remain a safe fallback, while
     * datapacks can extend the protected population with the civilians tag.
     *
     * <p>The ignored tag always wins, including for subclasses of
     * {@link AbstractVillager}.</p>
     */
    public static boolean isCivilian(
            LivingEntity entity
    ) {
        if (entity == null
                || RecognitionEntityTags.isIgnored(
                entity.getType()
        )) {
            return false;
        }

        if (entity instanceof AbstractVillager) {
            return true;
        }

        return RecognitionEntityTags.isTaggedCivilian(
                entity.getType()
        );
    }

    public static void recordTargetChange(
            LivingEntity aggressor,
            LivingEntity newTarget
    ) {
        if (aggressor == null
                || aggressor.level().isClientSide()) {
            return;
        }

        UUID aggressorUuid =
                aggressor.getUUID();

        /*
         * Player behaviour is evaluated by direct deed tracking.
         * This encounter tracker is only for explicitly classified NPCs and
         * creatures.
         */
        if (!isHostileToCivilians(aggressor)) {
            ACTIVE_AGGRESSORS.remove(
                    aggressorUuid
            );
            return;
        }

        if (!isCivilian(newTarget)) {
            /*
             * A confirmed attack remains valid for its longer protection
             * window even if the mob temporarily loses or changes its target.
             * A target-only observation is discarded immediately.
             */
            ACTIVE_AGGRESSORS.computeIfPresent(
                    aggressorUuid,
                    (ignored, existing) ->
                            existing.damageConfirmed()
                                    ? existing
                                    : null
            );
            return;
        }

        long gameTime =
                aggressor.level().getGameTime();

        storeRecord(
                aggressorUuid,
                new AggressionRecord(
                        aggressor.level().dimension(),
                        newTarget.getUUID(),
                        gameTime,
                        gameTime
                                + TARGET_ONLY_WINDOW_TICKS,
                        false
                )
        );
    }

    public static void recordCivilianDamage(
            LivingEntity civilian,
            DamageSource source
    ) {
        if (civilian == null
                || source == null
                || civilian.level().isClientSide()
                || !isCivilian(civilian)) {
            return;
        }

        LivingEntity aggressor =
                resolveAggressor(source);

        if (aggressor == null
                || aggressor == civilian
                || !isHostileToCivilians(
                aggressor
        )) {
            return;
        }

        long gameTime =
                civilian.level().getGameTime();

        storeRecord(
                aggressor.getUUID(),
                new AggressionRecord(
                        civilian.level().dimension(),
                        civilian.getUUID(),
                        gameTime,
                        gameTime
                                + DAMAGE_CONFIRMED_WINDOW_TICKS,
                        true
                )
        );
    }

    /**
     * Returns true exactly once when a player defeats a recently observed
     * hostile while the protected civilian is still alive.
     */
    public static boolean consumeDefense(
            LivingEntity defeatedAggressor,
            ServerPlayer responsiblePlayer
    ) {
        if (defeatedAggressor == null
                || responsiblePlayer == null
                || !(defeatedAggressor.level()
                instanceof ServerLevel serverLevel)) {
            return false;
        }

        AggressionRecord record =
                ACTIVE_AGGRESSORS.remove(
                        defeatedAggressor.getUUID()
                );

        if (record == null
                || !isHostileToCivilians(
                defeatedAggressor
        )) {
            return false;
        }

        ResourceKey<Level> defeatedDimension =
                defeatedAggressor.level().dimension();

        if (!record.dimension().equals(
                defeatedDimension
        )
                || !record.dimension().equals(
                responsiblePlayer
                        .level()
                        .dimension()
        )) {
            return false;
        }

        long gameTime =
                defeatedAggressor
                        .level()
                        .getGameTime();

        if (gameTime
                < record.firstObservedGameTime()
                || gameTime
                > record.expirationGameTime()) {
            return false;
        }

        Entity civilianEntity =
                serverLevel.getEntity(
                        record.civilianUuid()
                );

        if (!(civilianEntity
                instanceof LivingEntity civilian)
                || !civilian.isAlive()
                || !isCivilian(civilian)) {
            return false;
        }

        return true;
    }

    public static void forget(
            LivingEntity entity
    ) {
        if (entity == null) {
            return;
        }

        ACTIVE_AGGRESSORS.remove(
                entity.getUUID()
        );

        UUID entityUuid =
                entity.getUUID();

        ACTIVE_AGGRESSORS
                .entrySet()
                .removeIf(
                        entry -> entry
                                .getValue()
                                .civilianUuid()
                                .equals(entityUuid)
                );
    }

    public static void cleanup(
            long currentGameTime
    ) {
        /*
         * Static fields can survive switching integrated-server worlds in the
         * same client process. A lower game time therefore means the old
         * world's transient records must be discarded.
         */
        if (currentGameTime
                < lastCleanupGameTime) {
            ACTIVE_AGGRESSORS.clear();
            lastCleanupGameTime =
                    currentGameTime;
            return;
        }

        if (currentGameTime
                < lastCleanupGameTime
                + CLEANUP_INTERVAL_TICKS) {
            return;
        }

        lastCleanupGameTime =
                currentGameTime;

        ACTIVE_AGGRESSORS
                .entrySet()
                .removeIf(
                        entry -> {
                            AggressionRecord record =
                                    entry.getValue();

                            return currentGameTime
                                    < record
                                    .firstObservedGameTime()
                                    || currentGameTime
                                    > record
                                    .expirationGameTime();
                        }
                );
    }

    private static boolean isHostileToCivilians(
            LivingEntity entity
    ) {
        if (entity == null
                || entity instanceof Player
                || isCivilian(entity)) {
            return false;
        }

        return RecognitionEntityTags
                .isTaggedHostileToCivilians(
                        entity.getType()
                );
    }

    private static void storeRecord(
            UUID aggressorUuid,
            AggressionRecord incoming
    ) {
        if (aggressorUuid == null
                || incoming == null) {
            return;
        }

        /*
         * Once the safety ceiling has been reached, already tracked encounters
         * may still be updated. Entirely new encounter IDs wait until cleanup
         * has freed capacity.
         */
        if (!ACTIVE_AGGRESSORS.containsKey(
                aggressorUuid
        )
                && ACTIVE_AGGRESSORS.size()
                >= MAX_ACTIVE_AGGRESSORS) {
            return;
        }

        ACTIVE_AGGRESSORS.merge(
                aggressorUuid,
                incoming,
                CivilianDefenseTracker::mergeRecords
        );
    }

    private static AggressionRecord mergeRecords(
            AggressionRecord existing,
            AggressionRecord incoming
    ) {
        boolean sameEncounter =
                existing.dimension().equals(
                        incoming.dimension()
                )
                        && existing
                        .civilianUuid()
                        .equals(
                                incoming.civilianUuid()
                        );

        if (!sameEncounter) {
            return incoming;
        }

        /*
         * Repeated target changes must not shorten a damage-confirmed
         * encounter. The earliest observation and longest expiry are retained.
         */
        return new AggressionRecord(
                existing.dimension(),
                existing.civilianUuid(),
                Math.min(
                        existing.firstObservedGameTime(),
                        incoming.firstObservedGameTime()
                ),
                Math.max(
                        existing.expirationGameTime(),
                        incoming.expirationGameTime()
                ),
                existing.damageConfirmed()
                        || incoming.damageConfirmed()
        );
    }

    private static LivingEntity resolveAggressor(
            DamageSource source
    ) {
        if (source.getEntity()
                instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        if (source.getDirectEntity()
                instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        return null;
    }

    private record AggressionRecord(
            ResourceKey<Level> dimension,
            UUID civilianUuid,
            long firstObservedGameTime,
            long expirationGameTime,
            boolean damageConfirmed
    ) {
    }
}