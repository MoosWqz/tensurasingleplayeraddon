package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists and reconciles the permanent recognition-strength reward.
 *
 * <p>Stable modifier IDs make reconciliation idempotent. The existing
 * 40-tick recognition synchronizer calls this service, so removal after
 * unname/reset happens promptly without adding a new tick handler.</p>
 */
public final class RecognitionStrengthRewardService {

    public static final String MIGRATION_SOURCE_NATIVE =
            "native_identity_snapshot_v2";

    public static final String MIGRATION_SOURCE_LEGACY =
            "legacy_live_snapshot_v1_to_v2";

    public static final String MIGRATION_SOURCE_INTERRUPTED =
            "interrupted_profile2_repair";

    public static final ResourceLocation MAX_HEALTH_MODIFIER_ID =
            id("recognition_strength_max_health");

    public static final ResourceLocation ATTACK_DAMAGE_MODIFIER_ID =
            id("recognition_strength_attack_damage");

    public static final ResourceLocation MOVEMENT_SPEED_MODIFIER_ID =
            id("recognition_strength_movement_speed");

    public static final ResourceLocation ATTACK_SPEED_MODIFIER_ID =
            id("recognition_strength_attack_speed");

    public static final ResourceLocation KNOCKBACK_RESISTANCE_MODIFIER_ID =
            id("recognition_strength_knockback_resistance");

    private RecognitionStrengthRewardService() {
    }

    /**
     * Stores the exact identity-strength snapshot used by a new altar
     * commitment. A later reconciliation applies the matching modifiers.
     */
    public static boolean initializeNewCommit(
            RecognitionData data,
            double identityStrength,
            double identityStrengthMaximum,
            boolean pure
    ) {
        if (data == null
                || !data.isNamingCommitted()
                || data.getRewardProfileVersion()
                > RecognitionStrengthRewardFormula.PROFILE_VERSION) {
            return false;
        }

        RecognitionStrengthRewardFormula.Reward reward =
                RecognitionStrengthRewardFormula.calculate(
                        identityStrength,
                        identityStrengthMaximum,
                        pure
                );

        storeReward(
                data,
                reward,
                MIGRATION_SOURCE_NATIVE
        );

        return true;
    }

    /**
     * Repairs metadata when necessary and makes actual attributes exactly
     * match the frozen reward. Repeated calls do not stack.
     */
    public static ReconcileResult reconcile(
            ServerPlayer player
    ) {
        if (player == null) {
            return ReconcileResult.rejected(
                    "A server player is required."
            );
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        RecognitionCommittedResult committed =
                data.getCommittedResult();

        if (!data.isNamingCommitted()
                || !committed.valid()) {
            boolean removed = removeAllModifiers(player);
            return new ReconcileResult(
                    true,
                    removed,
                    false,
                    "No valid committed recognition; reward modifiers removed."
            );
        }

        int profileVersion = data.getRewardProfileVersion();

        if (profileVersion
                > RecognitionStrengthRewardFormula.PROFILE_VERSION) {
            return new ReconcileResult(
                    false,
                    false,
                    false,
                    "Future reward profile preserved without mutation."
            );
        }

        boolean initialized = data.getFlag(
                RecognitionStatKeys.RECOGNITION_REWARD_INITIALIZED
        );

        boolean metadataChanged = false;

        if (!initialized) {
            RecognitionEvaluation evaluation =
                    RecognitionPathEvaluator.evaluate(data);

            double identityMaximum = evaluation.getBalance()
                    .identityStrength()
                    .maximum();

            RecognitionStrengthRewardFormula.Reward reward =
                    RecognitionStrengthRewardFormula.calculate(
                            evaluation.getDimensions()
                                    .identityStrength(),
                            identityMaximum,
                            data.isPureRecognition()
                    );

            boolean legacyCommit = profileVersion
                    < RecognitionStrengthRewardFormula.PROFILE_VERSION
                    || !RecognitionCommitRecord
                    .NATIVE_MIGRATION_SOURCE
                    .equals(
                            data.getRecognitionMigrationSource()
                    );

            String source = legacyCommit
                    ? MIGRATION_SOURCE_LEGACY
                    : MIGRATION_SOURCE_INTERRUPTED;

            storeReward(data, reward, source);
            metadataChanged = true;
        } else if (profileVersion
                < RecognitionStrengthRewardFormula.PROFILE_VERSION) {
            data.setCounter(
                    RecognitionStatKeys.REWARD_PROFILE_VERSION,
                    RecognitionStrengthRewardFormula.PROFILE_VERSION
            );
            metadataChanged = true;
        }

        RecognitionStrengthRewardFormula.Reward reward =
                rewardFromStoredData(data);

        boolean attributesChanged = applyExpectedModifiers(
                player,
                reward
        );

        RecognitionEndowmentEffortRewardService.ReconcileResult
                endowmentResult =
                RecognitionEndowmentEffortRewardService.reconcile(
                        player
                );

        boolean endowmentChanged =
                endowmentResult.supported()
                        && endowmentResult.changed();

        return new ReconcileResult(
                true,
                metadataChanged
                        || attributesChanged
                        || endowmentChanged,
                true,
                metadataChanged
                        ? "Recognition reward metadata migrated and modifiers reconciled."
                        : attributesChanged
                                || endowmentChanged
                          ? "Recognition reward modifiers repaired."
                          : "Recognition reward already current."
        );
    }

    /** Read-only inspection. This never repairs metadata or attributes. */
    public static RecognitionStrengthRewardSnapshot inspect(
            ServerPlayer player
    ) {
        if (player == null) {
            return emptySnapshot();
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        RecognitionCommittedResult committed =
                data.getCommittedResult();

        int version = data.getRewardProfileVersion();
        boolean future = version
                > RecognitionStrengthRewardFormula.PROFILE_VERSION;

        boolean initialized = data.getFlag(
                RecognitionStatKeys.RECOGNITION_REWARD_INITIALIZED
        );

        RecognitionStrengthRewardFormula.Reward expected;

        if (initialized && !future) {
            expected = rewardFromStoredData(data);
        } else if (!future && committed.valid()) {
            RecognitionEvaluation evaluation =
                    RecognitionPathEvaluator.evaluate(data);

            expected = RecognitionStrengthRewardFormula.calculate(
                    evaluation.getDimensions()
                            .identityStrength(),
                    evaluation.getBalance()
                            .identityStrength()
                            .maximum(),
                    data.isPureRecognition()
            );
        } else {
            expected = zeroReward();
        }

        List<String> mismatches =
                initialized && !future && committed.valid()
                        ? findMismatches(player, expected)
                        : List.of();

        return new RecognitionStrengthRewardSnapshot(
                data.isNamingCommitted(),
                committed.valid(),
                version,
                initialized,
                data.getString(
                        RecognitionStatKeys
                                .RECOGNITION_REWARD_MIGRATION_SOURCE
                ),
                data.getMeasurement(
                        RecognitionStatKeys
                                .IDENTITY_STRENGTH_AT_COMMIT
                ),
                data.getMeasurement(
                        RecognitionStatKeys
                                .IDENTITY_STRENGTH_MAXIMUM_AT_COMMIT
                ),
                data.getMeasurement(
                        RecognitionStatKeys
                                .RECOGNITION_STRENGTH_REWARD
                ),
                future,
                mismatches.isEmpty(),
                mismatches,
                expected
        );
    }

    public static boolean removeAllModifiers(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        float oldMaximum = player.getMaxHealth();
        float oldHealth = player.getHealth();

        boolean changed = false;
        changed |= remove(player.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH_MODIFIER_ID);
        changed |= remove(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_DAMAGE_MODIFIER_ID);
        changed |= remove(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED_MODIFIER_ID);
        changed |= remove(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_MODIFIER_ID);
        changed |= remove(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_RESISTANCE_MODIFIER_ID);

        boolean endowmentChanged =
                RecognitionEndowmentEffortRewardService
                        .removeAllModifiers(player);

        if (changed) {
            preserveHealthRatio(player, oldHealth, oldMaximum);
        }

        return changed || endowmentChanged;
    }

    private static void storeReward(
            RecognitionData data,
            RecognitionStrengthRewardFormula.Reward reward,
            String migrationSource
    ) {
        data.setMeasurement(
                RecognitionStatKeys.IDENTITY_STRENGTH_AT_COMMIT,
                reward.frozenIdentityStrength()
        );
        data.setMeasurement(
                RecognitionStatKeys.IDENTITY_STRENGTH_MAXIMUM_AT_COMMIT,
                reward.identityStrengthMaximum()
        );
        data.setMeasurement(
                RecognitionStatKeys.RECOGNITION_STRENGTH_REWARD,
                reward.totalStrength()
        );
        data.setString(
                RecognitionStatKeys.RECOGNITION_REWARD_MIGRATION_SOURCE,
                migrationSource
        );
        data.setCounter(
                RecognitionStatKeys.REWARD_PROFILE_VERSION,
                RecognitionStrengthRewardFormula.PROFILE_VERSION
        );
        data.setFlag(
                RecognitionStatKeys.RECOGNITION_REWARD_INITIALIZED,
                true
        );
    }

    private static RecognitionStrengthRewardFormula.Reward rewardFromStoredData(
            RecognitionData data
    ) {
        double identity = data.getMeasurement(
                RecognitionStatKeys.IDENTITY_STRENGTH_AT_COMMIT
        );
        double maximum = data.getMeasurement(
                RecognitionStatKeys.IDENTITY_STRENGTH_MAXIMUM_AT_COMMIT
        );

        RecognitionStrengthRewardFormula.Reward calculated =
                RecognitionStrengthRewardFormula.calculate(
                        identity,
                        maximum,
                        data.isPureRecognition()
                );

        double storedTotal = data.getMeasurement(
                RecognitionStatKeys.RECOGNITION_STRENGTH_REWARD
        );

        if (storedTotal <= 0.0D) {
            return calculated;
        }

        double safeTotal = Math.max(
                RecognitionStrengthRewardFormula.BASE_REWARD,
                Math.min(
                        RecognitionStrengthRewardFormula.maximumReward(
                                data.isPureRecognition()
                        ),
                        storedTotal
                )
        );

        return new RecognitionStrengthRewardFormula.Reward(
                RecognitionStrengthRewardFormula.PROFILE_VERSION,
                RecognitionStrengthRewardFormula.PROFILE_ID,
                calculated.frozenIdentityStrength(),
                calculated.identityStrengthMaximum(),
                data.isPureRecognition(),
                safeTotal,
                safeTotal,
                safeTotal,
                safeTotal * RecognitionStrengthRewardFormula
                        .MOVEMENT_AND_ATTACK_SPEED_SHARE,
                safeTotal * RecognitionStrengthRewardFormula
                        .MOVEMENT_AND_ATTACK_SPEED_SHARE,
                safeTotal * RecognitionStrengthRewardFormula
                        .KNOCKBACK_RESISTANCE_SHARE
        );
    }

    private static boolean applyExpectedModifiers(
            ServerPlayer player,
            RecognitionStrengthRewardFormula.Reward reward
    ) {
        float oldMaximum = player.getMaxHealth();
        float oldHealth = player.getHealth();

        boolean changed = false;
        changed |= reconcile(
                player.getAttribute(Attributes.MAX_HEALTH),
                MAX_HEALTH_MODIFIER_ID,
                reward.maxHealthMultiplier(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        changed |= reconcile(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                ATTACK_DAMAGE_MODIFIER_ID,
                reward.attackDamageMultiplier(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        changed |= reconcile(
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                MOVEMENT_SPEED_MODIFIER_ID,
                reward.movementSpeedMultiplier(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        changed |= reconcile(
                player.getAttribute(Attributes.ATTACK_SPEED),
                ATTACK_SPEED_MODIFIER_ID,
                reward.attackSpeedMultiplier(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        changed |= reconcile(
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE),
                KNOCKBACK_RESISTANCE_MODIFIER_ID,
                reward.knockbackResistanceAddition(),
                AttributeModifier.Operation.ADD_VALUE
        );

        if (changed) {
            preserveHealthRatio(player, oldHealth, oldMaximum);
        }

        return changed;
    }

    private static boolean reconcile(
            AttributeInstance instance,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation operation
    ) {
        if (instance == null) {
            return false;
        }

        AttributeModifier existing = instance.getModifier(id);

        if (existing != null
                && approximately(existing.amount(), amount)
                && existing.operation() == operation) {
            return false;
        }

        instance.addOrReplacePermanentModifier(
                new AttributeModifier(id, amount, operation)
        );
        return true;
    }

    private static boolean remove(
            AttributeInstance instance,
            ResourceLocation id
    ) {
        return instance != null && instance.removeModifier(id);
    }

    private static List<String> findMismatches(
            ServerPlayer player,
            RecognitionStrengthRewardFormula.Reward reward
    ) {
        List<String> result = new ArrayList<>();

        check(result, "max_health", player.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH_MODIFIER_ID, reward.maxHealthMultiplier(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        check(result, "attack_damage", player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_DAMAGE_MODIFIER_ID, reward.attackDamageMultiplier(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        check(result, "movement_speed", player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED_MODIFIER_ID, reward.movementSpeedMultiplier(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        check(result, "attack_speed", player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_MODIFIER_ID, reward.attackSpeedMultiplier(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        check(result, "knockback_resistance", player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_RESISTANCE_MODIFIER_ID, reward.knockbackResistanceAddition(), AttributeModifier.Operation.ADD_VALUE);

        return List.copyOf(result);
    }

    private static void check(
            List<String> result,
            String name,
            AttributeInstance instance,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation operation
    ) {
        if (instance == null) {
            result.add(name + " attribute unavailable");
            return;
        }

        AttributeModifier modifier = instance.getModifier(id);

        if (modifier == null) {
            result.add(name + " modifier missing");
        } else if (!approximately(modifier.amount(), amount)
                || modifier.operation() != operation) {
            result.add(name + " modifier differs");
        }
    }

    private static void preserveHealthRatio(
            ServerPlayer player,
            float oldHealth,
            float oldMaximum
    ) {
        float newMaximum = player.getMaxHealth();

        if (newMaximum <= 0.0F) {
            return;
        }

        if (oldHealth <= 0.0F || oldMaximum <= 0.0F) {
            player.setHealth(Math.min(player.getHealth(), newMaximum));
            return;
        }

        float ratio = Math.max(
                0.0F,
                Math.min(1.0F, oldHealth / oldMaximum)
        );

        player.setHealth(
                Math.max(
                        1.0F,
                        Math.min(newMaximum, newMaximum * ratio)
                )
        );
    }

    private static boolean approximately(
            double first,
            double second
    ) {
        return Math.abs(first - second) <= 0.000_000_1D;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MoosTensuraAddon.MODID,
                path
        );
    }

    private static RecognitionStrengthRewardSnapshot emptySnapshot() {
        return new RecognitionStrengthRewardSnapshot(
                false,
                false,
                0,
                false,
                "",
                0.0D,
                0.0D,
                0.0D,
                false,
                true,
                List.of(),
                zeroReward()
        );
    }

    private static RecognitionStrengthRewardFormula.Reward zeroReward() {
        return new RecognitionStrengthRewardFormula.Reward(
                RecognitionStrengthRewardFormula.PROFILE_VERSION,
                RecognitionStrengthRewardFormula.PROFILE_ID,
                0.0D,
                0.0D,
                false,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
    }

    public record ReconcileResult(
            boolean supported,
            boolean changed,
            boolean active,
            String message
    ) {
        public ReconcileResult {
            message = message == null ? "" : message.trim();
        }

        public static ReconcileResult rejected(String message) {
            return new ReconcileResult(false, false, false, message);
        }
    }
}
