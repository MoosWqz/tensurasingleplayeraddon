package com.mooswqz.moostensuraaddon.recognition;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import java.util.WeakHashMap;

public final class CivilianDefenseTracker {

    private static final long TARGET_ONLY_WINDOW_TICKS =
            20L * 20L;

    private static final long DAMAGE_CONFIRMED_WINDOW_TICKS =
            20L * 45L;

    private static final long CLEANUP_INTERVAL_TICKS =
            20L * 10L;

    private static final int MAX_ACTIVE_AGGRESSORS = 4096;

    /**
     * Runtime encounter state is isolated by MinecraftServer instance.
     *
     * <p>This fixes two lifecycle problems from the original tracker:</p>
     * <ul>
     *     <li>a cleanup timestamp from one dimension could suppress cleanup
     *     for records created in another dimension;</li>
     *     <li>a stopped integrated server could leave a high game-time value
     *     behind for the next world loaded in the same JVM.</li>
     * </ul>
     *
     * <p>Weak keys are an additional safety net. ServerStoppedEvent also
     * removes the state explicitly.</p>
     */
    private static final Map<MinecraftServer, TrackerState>
            SERVER_STATES = new WeakHashMap<>();

    private CivilianDefenseTracker() {
    }

    public static boolean isCivilian(LivingEntity entity) {
        if (entity == null) {
            return false;
        }

        /*
         * This catches vanilla villagers, wandering traders and modded
         * entities that inherit Minecraft's normal villager foundation.
         */
        if (entity instanceof AbstractVillager) {
            return true;
        }

        return entity.getType().is(
                RecognitionEntityTags.CIVILIANS
        );
    }

    public static boolean isHostileToCivilians(
            LivingEntity entity
    ) {
        return entity != null
                && entity.getType().is(
                RecognitionEntityTags.HOSTILE_TO_CIVILIANS
        );
    }

    public static void recordTargetChange(
            LivingEntity aggressor,
            LivingEntity newTarget
    ) {
        if (aggressor == null
                || !(aggressor.level()
                instanceof ServerLevel serverLevel)) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();
        TrackerState state = stateFor(server);
        EntityKey aggressorKey = keyOf(aggressor);

        /*
         * Player behaviour is evaluated by direct deed tracking.
         * This encounter tracker is for hostile NPCs and creatures.
         */
        if (aggressor instanceof Player) {
            removeAggressorEverywhere(
                    state,
                    aggressor.getUUID()
            );
            return;
        }

        /*
         * The datapack decides which entity types are eligible civilian
         * threats. Real target/damage evidence is still required below, so
         * this does not introduce proximity-only morality.
         */
        if (!isHostileToCivilians(aggressor)) {
            removeAggressorEverywhere(
                    state,
                    aggressor.getUUID()
            );
            return;
        }

        if (!isCivilian(newTarget)
                || !(newTarget.level()
                instanceof ServerLevel targetLevel)
                || targetLevel.getServer() != server
                || !targetLevel.dimension().equals(
                serverLevel.dimension()
        )) {
            removeAggressorEverywhere(
                    state,
                    aggressor.getUUID()
            );
            return;
        }

        long gameTime = getServerGameTime(
                server,
                serverLevel
        );

        state.activeAggressors().put(
                aggressorKey,
                new AggressionRecord(
                        keyOf(newTarget),
                        gameTime,
                        gameTime + TARGET_ONLY_WINDOW_TICKS,
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
                || !(civilian.level()
                instanceof ServerLevel serverLevel)
                || !isCivilian(civilian)) {
            return;
        }

        LivingEntity aggressor = resolveAggressor(source);

        if (aggressor == null
                || aggressor == civilian
                || aggressor instanceof Player
                || !(aggressor.level()
                instanceof ServerLevel aggressorLevel)
                || aggressorLevel.getServer()
                != serverLevel.getServer()
                || !aggressorLevel.dimension().equals(
                serverLevel.dimension()
        )) {
            return;
        }

        MinecraftServer server = serverLevel.getServer();

        if (!isHostileToCivilians(aggressor)) {
            TrackerState state = existingState(server);

            if (state != null) {
                removeAggressorEverywhere(
                        state,
                        aggressor.getUUID()
                );
            }
            return;
        }

        long gameTime = getServerGameTime(
                server,
                serverLevel
        );

        stateFor(server).activeAggressors().put(
                keyOf(aggressor),
                new AggressionRecord(
                        keyOf(civilian),
                        gameTime,
                        gameTime + DAMAGE_CONFIRMED_WINDOW_TICKS,
                        true
                )
        );
    }

    /**
     * Returns true exactly once when a player defeats a recently observed
     * aggressor while the protected civilian is still alive.
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

        MinecraftServer server = serverLevel.getServer();

        if (responsiblePlayer.getServer() != server) {
            return false;
        }

        TrackerState state = existingState(server);

        if (state == null) {
            return false;
        }

        AggressionRecord record = state
                .activeAggressors()
                .remove(keyOf(defeatedAggressor));

        if (record == null) {
            return false;
        }

        long gameTime = getServerGameTime(
                server,
                serverLevel
        );

        if (gameTime > record.expirationGameTime()) {
            return false;
        }

        if (!record.civilianKey().dimension().equals(
                serverLevel.dimension()
        )) {
            return false;
        }

        Entity civilianEntity = serverLevel.getEntity(
                record.civilianKey().entityUuid()
        );

        return civilianEntity instanceof LivingEntity civilian
                && civilian.isAlive()
                && isCivilian(civilian);
    }

    public static void forget(LivingEntity entity) {
        if (entity == null
                || !(entity.level()
                instanceof ServerLevel serverLevel)) {
            return;
        }

        TrackerState state = existingState(
                serverLevel.getServer()
        );

        if (state == null) {
            return;
        }

        EntityKey entityKey = keyOf(entity);

        state.activeAggressors().remove(entityKey);
        state.activeAggressors().removeIf(
                entry -> entry.getValue()
                        .civilianKey()
                        .equals(entityKey)
        );
    }

    /**
     * Performs bounded cleanup using the overworld game time for the server.
     * The call is cheap and may be made by several players; only one cleanup
     * per server can pass the interval gate.
     */
    public static void cleanup(MinecraftServer server) {
        if (server == null) {
            return;
        }

        ServerLevel overworld = server.overworld();

        if (overworld == null) {
            return;
        }

        cleanup(
                server,
                overworld.getGameTime()
        );
    }

    static void cleanup(
            MinecraftServer server,
            long currentGameTime
    ) {
        if (server == null) {
            return;
        }

        TrackerState state = existingState(server);

        if (state == null) {
            return;
        }

        synchronized (state) {
            long lastCleanup = state.lastCleanupGameTime();

            if (!shouldRunCleanup(
                    lastCleanup,
                    currentGameTime
            )) {
                return;
            }

            /*
             * A lower clock means a world/server lifecycle changed. Treat it
             * as a new cleanup epoch instead of waiting for the old clock to
             * be reached again.
             */
            state.setLastCleanupGameTime(currentGameTime);

            state.activeAggressors().removeIf(
                    entry -> currentGameTime
                            > entry.getValue()
                            .expirationGameTime()
            );
        }
    }

    public static void clearServer(MinecraftServer server) {
        if (server == null) {
            return;
        }

        synchronized (SERVER_STATES) {
            SERVER_STATES.remove(server);
        }
    }

    public static void clearAll() {
        synchronized (SERVER_STATES) {
            SERVER_STATES.clear();
        }
    }

    public static RuntimeSnapshot inspect(
            MinecraftServer server
    ) {
        TrackerState state = existingState(server);

        if (state == null) {
            return new RuntimeSnapshot(
                    0,
                    0,
                    0L,
                    MAX_ACTIVE_AGGRESSORS,
                    serverStateCount()
            );
        }

        int damageConfirmed = 0;

        for (Map.Entry<EntityKey, AggressionRecord> entry :
                state.activeAggressors().snapshotEntries()) {
            if (entry.getValue().damageConfirmed()) {
                damageConfirmed++;
            }
        }

        return new RuntimeSnapshot(
                state.activeAggressors().size(),
                damageConfirmed,
                state.lastCleanupGameTime(),
                MAX_ACTIVE_AGGRESSORS,
                serverStateCount()
        );
    }

    static boolean shouldRunCleanup(
            long lastCleanupGameTime,
            long currentGameTime
    ) {
        return currentGameTime < lastCleanupGameTime
                || currentGameTime >= lastCleanupGameTime
                + CLEANUP_INTERVAL_TICKS;
    }

    public static int maximumActiveAggressors() {
        return MAX_ACTIVE_AGGRESSORS;
    }

    public static long cleanupIntervalTicks() {
        return CLEANUP_INTERVAL_TICKS;
    }

    public static int serverStateCount() {
        synchronized (SERVER_STATES) {
            return SERVER_STATES.size();
        }
    }

    private static TrackerState stateFor(
            MinecraftServer server
    ) {
        synchronized (SERVER_STATES) {
            return SERVER_STATES.computeIfAbsent(
                    server,
                    ignored -> new TrackerState()
            );
        }
    }

    private static TrackerState existingState(
            MinecraftServer server
    ) {
        if (server == null) {
            return null;
        }

        synchronized (SERVER_STATES) {
            return SERVER_STATES.get(server);
        }
    }

    private static void removeAggressorEverywhere(
            TrackerState state,
            UUID aggressorUuid
    ) {
        state.activeAggressors().removeIf(
                entry -> entry.getKey()
                        .entityUuid()
                        .equals(aggressorUuid)
        );
    }

    private static EntityKey keyOf(LivingEntity entity) {
        return new EntityKey(
                entity.level().dimension(),
                entity.getUUID()
        );
    }

    private static long getServerGameTime(
            MinecraftServer server,
            ServerLevel fallbackLevel
    ) {
        ServerLevel overworld = server.overworld();

        return overworld == null
                ? fallbackLevel.getGameTime()
                : overworld.getGameTime();
    }

    private static LivingEntity resolveAggressor(
            DamageSource source
    ) {
        if (source.getEntity() instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        if (source.getDirectEntity()
                instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        return null;
    }

    private static final class TrackerState {

        private final RecognitionRuntimeCapTable<EntityKey, AggressionRecord>
                activeAggressors = new RecognitionRuntimeCapTable<>(
                MAX_ACTIVE_AGGRESSORS,
                AggressionRecord::firstObservedGameTime
        );

        private volatile long lastCleanupGameTime;

        private RecognitionRuntimeCapTable<EntityKey, AggressionRecord>
        activeAggressors() {
            return activeAggressors;
        }

        private long lastCleanupGameTime() {
            return lastCleanupGameTime;
        }

        private void setLastCleanupGameTime(long value) {
            lastCleanupGameTime = Math.max(0L, value);
        }
    }

    private record EntityKey(
            ResourceKey<Level> dimension,
            UUID entityUuid
    ) {
    }

    private record AggressionRecord(
            EntityKey civilianKey,
            long firstObservedGameTime,
            long expirationGameTime,
            boolean damageConfirmed
    ) {
    }

    public record RuntimeSnapshot(
            int activeAggressors,
            int damageConfirmedAggressors,
            long lastCleanupGameTime,
            int maximumActiveAggressors,
            int trackedServerStates
    ) {
    }
}
