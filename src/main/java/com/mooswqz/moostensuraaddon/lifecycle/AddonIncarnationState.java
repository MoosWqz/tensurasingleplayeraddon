package com.mooswqz.moostensuraaddon.lifecycle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Small, bounded, player-persistent state for incarnation lifecycle guards,
 * native endowment retries and Granter acquisition transitions.
 */
public final class AddonIncarnationState {

    private static final String ROOT_KEY =
            "moostensuraaddon_lifecycle";

    private static final String REVISION_KEY = "revision";
    private static final String LIFE_TOKEN_KEY = "life_token";
    private static final String RESET_SEQUENCE_KEY = "reset_sequence";
    private static final String LAST_RESET_REASON_KEY = "last_reset_reason";
    private static final String LAST_RESET_EPOCH_KEY = "last_reset_epoch";
    private static final String RESET_GUARD_UNTIL_KEY = "reset_guard_until";
    private static final String RESET_GUARD_UNTIL_GAME_TIME_KEY =
            "reset_guard_until_game_time";

    private static final String ENDOWMENT_INCARNATION_KEY =
            "native_endowment_incarnation";
    private static final String ENDOWMENT_ATTEMPTS_KEY =
            "native_endowment_attempts";
    private static final String ENDOWMENT_NEXT_ATTEMPT_KEY =
            "native_endowment_next_attempt";

    private static final String AUTHORITY_OBSERVATION_INITIALIZED_KEY =
            "authority_observation_initialized";
    private static final String AUTHORITY_LAST_OWNED_KEY =
            "authority_last_owned";
    private static final String AUTHORITY_ACQUIRED_THIS_LIFE_KEY =
            "authority_acquired_this_life";

    private final ServerPlayer player;
    private final CompoundTag lifecycleTag;

    private AddonIncarnationState(
            ServerPlayer player,
            CompoundTag lifecycleTag
    ) {
        this.player = player;
        this.lifecycleTag = lifecycleTag;
        migrate();
    }

    public static AddonIncarnationState load(
            ServerPlayer player
    ) {
        if (player == null) {
            throw new IllegalArgumentException(
                    "A server player is required."
            );
        }

        CompoundTag persisted = getPersistedPlayerTag(player);
        CompoundTag lifecycle = persisted.getCompound(ROOT_KEY);

        return new AddonIncarnationState(
                player,
                lifecycle
        );
    }

    public static Snapshot inspect(
            ServerPlayer player
    ) {
        AddonIncarnationState state = load(player);
        long now = System.currentTimeMillis();

        return new Snapshot(
                state.getRevision(),
                state.getLifeToken(),
                state.getResetSequence(),
                state.getLastResetReason(),
                state.getLastResetEpochMillis(),
                state.getResetGuardUntilEpochMillis(),
                state.getResetGuardUntilGameTime(),
                state.isResetGuardActive(now),
                state.getNativeEndowmentIncarnation(),
                state.getNativeEndowmentAttempts(),
                state.getNativeEndowmentNextAttemptEpochMillis(),
                state.isAuthorityObservationInitialized(),
                state.wasAuthorityLastOwned(),
                state.isAuthorityAcquiredThisLife()
        );
    }

    public static boolean isResetGuardActive(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        CompoundTag persisted = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);

        if (!persisted.contains(ROOT_KEY)) {
            return false;
        }

        CompoundTag lifecycle = persisted.getCompound(ROOT_KEY);
        long guardUntilEpoch = lifecycle.getLong(
                RESET_GUARD_UNTIL_KEY
        );
        long guardUntilGameTime = lifecycle.getLong(
                RESET_GUARD_UNTIL_GAME_TIME_KEY
        );
        long currentGameTime = player.serverLevel().getGameTime();

        return AddonLifecyclePolicy.isGuardActive(
                guardUntilEpoch,
                System.currentTimeMillis()
        ) || guardUntilGameTime > currentGameTime;
    }

    public static void copyPersistentState(
            Player original,
            Player clone
    ) {
        if (original == null || clone == null) {
            return;
        }

        CompoundTag originalPersisted = original.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);

        if (!originalPersisted.contains(ROOT_KEY)) {
            return;
        }

        CompoundTag cloneRoot = clone.getPersistentData();
        CompoundTag clonePersisted = cloneRoot.getCompound(
                Player.PERSISTED_NBT_TAG
        );
        clonePersisted.put(
                ROOT_KEY,
                originalPersisted.getCompound(ROOT_KEY).copy()
        );
        cloneRoot.put(
                Player.PERSISTED_NBT_TAG,
                clonePersisted
        );
    }

    public void beginNewIncarnation(
            String reason,
            long guardDurationMillis,
            long nowEpochMillis
    ) {
        long now = Math.max(0L, nowEpochMillis);
        int nextSequence = Math.max(0, getResetSequence()) + 1;

        lifecycleTag.putInt(
                REVISION_KEY,
                AddonLifecyclePolicy.CURRENT_STATE_REVISION
        );
        lifecycleTag.putString(
                LIFE_TOKEN_KEY,
                UUID.randomUUID().toString()
        );
        lifecycleTag.putInt(
                RESET_SEQUENCE_KEY,
                nextSequence
        );
        lifecycleTag.putString(
                LAST_RESET_REASON_KEY,
                clean(reason)
        );
        lifecycleTag.putLong(
                LAST_RESET_EPOCH_KEY,
                now
        );
        long safeGuardDuration = Math.max(0L, guardDurationMillis);
        lifecycleTag.putLong(
                RESET_GUARD_UNTIL_KEY,
                now + safeGuardDuration
        );
        lifecycleTag.putLong(
                RESET_GUARD_UNTIL_GAME_TIME_KEY,
                player.serverLevel().getGameTime()
                        + Math.max(0L, safeGuardDuration / 50L)
        );

        lifecycleTag.remove(ENDOWMENT_INCARNATION_KEY);
        lifecycleTag.putInt(ENDOWMENT_ATTEMPTS_KEY, 0);
        lifecycleTag.putLong(ENDOWMENT_NEXT_ATTEMPT_KEY, 0L);

        lifecycleTag.putBoolean(
                AUTHORITY_OBSERVATION_INITIALIZED_KEY,
                false
        );
        lifecycleTag.putBoolean(
                AUTHORITY_LAST_OWNED_KEY,
                false
        );
        lifecycleTag.putBoolean(
                AUTHORITY_ACQUIRED_THIS_LIFE_KEY,
                false
        );

        save();
    }

    public boolean isDuplicateReset(
            String requestedReason,
            long nowEpochMillis
    ) {
        return AddonLifecyclePolicy.shouldSuppressDuplicateReset(
                getLastResetReason(),
                getLastResetEpochMillis(),
                clean(requestedReason),
                nowEpochMillis
        );
    }

    public boolean isResetGuardActive(
            long nowEpochMillis
    ) {
        return AddonLifecyclePolicy.isGuardActive(
                getResetGuardUntilEpochMillis(),
                nowEpochMillis
        ) || getResetGuardUntilGameTime()
                > player.serverLevel().getGameTime();
    }

    public void markNativeEndowmentApplied(
            String incarnationId
    ) {
        lifecycleTag.putString(
                ENDOWMENT_INCARNATION_KEY,
                clean(incarnationId)
        );
        lifecycleTag.putInt(ENDOWMENT_ATTEMPTS_KEY, 0);
        lifecycleTag.putLong(ENDOWMENT_NEXT_ATTEMPT_KEY, 0L);
        save();
    }

    public void recordNativeEndowmentFailure(
            long nowEpochMillis
    ) {
        int attempts = Math.min(
                1_000,
                Math.max(0, getNativeEndowmentAttempts()) + 1
        );
        lifecycleTag.putInt(
                ENDOWMENT_ATTEMPTS_KEY,
                attempts
        );
        lifecycleTag.putLong(
                ENDOWMENT_NEXT_ATTEMPT_KEY,
                AddonLifecyclePolicy.nextEndowmentAttemptEpochMillis(
                        nowEpochMillis,
                        attempts
                )
        );
        save();
    }

    public void updateAuthorityObservation(
            GranterAcquisitionPolicy.Observation observation
    ) {
        if (observation == null) {
            return;
        }

        boolean changed = isAuthorityObservationInitialized()
                != observation.initialized()
                || wasAuthorityLastOwned()
                != observation.lastOwnedAuthority()
                || isAuthorityAcquiredThisLife()
                != observation.acquisitionConfirmedThisLife();

        if (!changed) {
            return;
        }

        lifecycleTag.putBoolean(
                AUTHORITY_OBSERVATION_INITIALIZED_KEY,
                observation.initialized()
        );
        lifecycleTag.putBoolean(
                AUTHORITY_LAST_OWNED_KEY,
                observation.lastOwnedAuthority()
        );
        lifecycleTag.putBoolean(
                AUTHORITY_ACQUIRED_THIS_LIFE_KEY,
                observation.acquisitionConfirmedThisLife()
        );
        save();
    }

    public int getRevision() {
        return Math.max(0, lifecycleTag.getInt(REVISION_KEY));
    }

    public String getLifeToken() {
        return clean(lifecycleTag.getString(LIFE_TOKEN_KEY));
    }

    public int getResetSequence() {
        return Math.max(0, lifecycleTag.getInt(RESET_SEQUENCE_KEY));
    }

    public String getLastResetReason() {
        return clean(lifecycleTag.getString(LAST_RESET_REASON_KEY));
    }

    public long getLastResetEpochMillis() {
        return Math.max(0L, lifecycleTag.getLong(LAST_RESET_EPOCH_KEY));
    }

    public long getResetGuardUntilEpochMillis() {
        return Math.max(
                0L,
                lifecycleTag.getLong(RESET_GUARD_UNTIL_KEY)
        );
    }

    public long getResetGuardUntilGameTime() {
        return Math.max(
                0L,
                lifecycleTag.getLong(RESET_GUARD_UNTIL_GAME_TIME_KEY)
        );
    }

    public String getNativeEndowmentIncarnation() {
        return clean(
                lifecycleTag.getString(ENDOWMENT_INCARNATION_KEY)
        );
    }

    public int getNativeEndowmentAttempts() {
        return Math.max(
                0,
                lifecycleTag.getInt(ENDOWMENT_ATTEMPTS_KEY)
        );
    }

    public long getNativeEndowmentNextAttemptEpochMillis() {
        return Math.max(
                0L,
                lifecycleTag.getLong(ENDOWMENT_NEXT_ATTEMPT_KEY)
        );
    }

    public boolean isAuthorityObservationInitialized() {
        return lifecycleTag.getBoolean(
                AUTHORITY_OBSERVATION_INITIALIZED_KEY
        );
    }

    public boolean wasAuthorityLastOwned() {
        return lifecycleTag.getBoolean(
                AUTHORITY_LAST_OWNED_KEY
        );
    }

    public boolean isAuthorityAcquiredThisLife() {
        return lifecycleTag.getBoolean(
                AUTHORITY_ACQUIRED_THIS_LIFE_KEY
        );
    }

    private void migrate() {
        int revision = getRevision();
        boolean changed = false;

        if (revision < AddonLifecyclePolicy.CURRENT_STATE_REVISION) {
            lifecycleTag.putInt(
                    REVISION_KEY,
                    AddonLifecyclePolicy.CURRENT_STATE_REVISION
            );
            changed = true;
        }

        if (getLifeToken().isBlank()) {
            lifecycleTag.putString(
                    LIFE_TOKEN_KEY,
                    UUID.randomUUID().toString()
            );
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    private void save() {
        CompoundTag persistentRoot = player.getPersistentData();
        CompoundTag persisted = persistentRoot.getCompound(
                Player.PERSISTED_NBT_TAG
        );
        persisted.put(ROOT_KEY, lifecycleTag);
        persistentRoot.put(
                Player.PERSISTED_NBT_TAG,
                persisted
        );
    }

    private static CompoundTag getPersistedPlayerTag(
            ServerPlayer player
    ) {
        CompoundTag persistentRoot = player.getPersistentData();
        CompoundTag persisted = persistentRoot.getCompound(
                Player.PERSISTED_NBT_TAG
        );
        persistentRoot.put(
                Player.PERSISTED_NBT_TAG,
                persisted
        );
        return persisted;
    }

    private static String clean(
            String value
    ) {
        return value == null ? "" : value.trim();
    }

    public record Snapshot(
            int revision,
            String lifeToken,
            int resetSequence,
            String lastResetReason,
            long lastResetEpochMillis,
            long resetGuardUntilEpochMillis,
            long resetGuardUntilGameTime,
            boolean resetGuardActive,
            String nativeEndowmentIncarnation,
            int nativeEndowmentAttempts,
            long nativeEndowmentNextAttemptEpochMillis,
            boolean authorityObservationInitialized,
            boolean authorityLastOwned,
            boolean authorityAcquiredThisLife
    ) {
    }
}