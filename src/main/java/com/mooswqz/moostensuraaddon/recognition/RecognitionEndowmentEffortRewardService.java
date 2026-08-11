package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.util.TensuraPlayerStateHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the frozen effort extension without changing Tensura's naming code.
 *
 * <p>Two stable permanent modifiers expand maximum magicules and aura. Current
 * energy receives only the positive modifier delta when capacity is first
 * added or repaired. An unchanged synchronization performs no energy write,
 * so this service can never act as a passive refill.</p>
 */
public final class RecognitionEndowmentEffortRewardService {

    public static final ResourceLocation MAX_MAGICULE_MODIFIER_ID =
            id("recognition_effort_max_magicule");

    public static final ResourceLocation MAX_AURA_MODIFIER_ID =
            id("recognition_effort_max_aura");

    private static final double EPSILON =
            0.000_000_1D;

    private RecognitionEndowmentEffortRewardService() {
    }

    public static ReconcileResult reconcile(
            ServerPlayer player
    ) {
        if (player == null) {
            return ReconcileResult.rejected(
                    "A server player is required."
            );
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        if (data.isWriteBlockedByFutureVersion()) {
            return new ReconcileResult(
                    false,
                    false,
                    false,
                    0.0D,
                    "Future recognition version preserved without mutation."
            );
        }

        RecognitionCommittedResult committed =
                data.getCommittedResult();

        boolean futureProfile =
                data.isWriteBlockedByFutureVersion();

        boolean metadataInitialized =
                data.getFlag(
                        RecognitionStatKeys.RECOGNITION_REWARD_INITIALIZED
                );

        boolean nativeNamed =
                TensuraPlayerStateHelper.isNamedOrEndowed(
                        player
                );

        boolean shouldApply =
                data.isNamingCommitted()
                        && committed.valid()
                        && metadataInitialized
                        && !futureProfile
                        && nativeNamed;

        if (!shouldApply) {
            boolean removed =
                    futureProfile
                            ? false
                            : removeAllModifiers(player);

            return new ReconcileResult(
                    true,
                    removed,
                    false,
                    0.0D,
                    futureProfile
                            ? "Future reward profile preserved without mutation."
                            : "No eligible Soul Recognition endowment; effort modifiers removed."
            );
        }

        IExistence existence =
                TensuraStorages.getExistenceFrom(
                        player
                );

        AttributeInstance maximumMagicule =
                player.getAttribute(
                        TensuraAttributes.MAX_MAGICULE
                );

        AttributeInstance maximumAura =
                player.getAttribute(
                        TensuraAttributes.MAX_AURA
                );

        if (existence == null
                || maximumMagicule == null
                || maximumAura == null) {
            return ReconcileResult.rejected(
                    "Tensura energy storage or attributes are unavailable."
            );
        }

        RecognitionEndowmentEffortRewardFormula.Reward reward =
                rewardFromStoredData(data);

        if (reward.energyIncreasePerPool()
                <= EPSILON) {
            boolean removed =
                    removeAllModifiers(player);

            return new ReconcileResult(
                    true,
                    removed,
                    true,
                    0.0D,
                    "Frozen effort grants no additional endowment capacity."
            );
        }

        ModifierChange magiculeChange =
                reconcileModifier(
                        maximumMagicule,
                        MAX_MAGICULE_MODIFIER_ID,
                        reward.energyIncreasePerPool()
                );

        ModifierChange auraChange =
                reconcileModifier(
                        maximumAura,
                        MAX_AURA_MODIFIER_ID,
                        reward.energyIncreasePerPool()
                );

        double energyGranted =
                applyOnlyNewEnergyCapacity(
                        existence,
                        maximumMagicule,
                        maximumAura,
                        magiculeChange.positiveDelta(),
                        auraChange.positiveDelta()
                );

        boolean changed =
                magiculeChange.changed()
                        || auraChange.changed()
                        || energyGranted > 0.0D;

        return new ReconcileResult(
                true,
                changed,
                true,
                energyGranted,
                changed
                        ? "Effort-scaled endowment capacity reconciled."
                        : "Effort-scaled endowment capacity already current."
        );
    }

    /** Read-only inspection; this never changes attributes or current energy. */
    public static RecognitionEndowmentEffortRewardSnapshot inspect(
            ServerPlayer player
    ) {
        if (player == null) {
            return emptySnapshot();
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        RecognitionCommittedResult committed =
                data.getCommittedResult();

        boolean futureProfile =
                data.isWriteBlockedByFutureVersion();

        boolean initialized =
                data.getFlag(
                        RecognitionStatKeys.RECOGNITION_REWARD_INITIALIZED
                );

        boolean nativeNamed =
                TensuraPlayerStateHelper.isNamedOrEndowed(
                        player
                );

        boolean active =
                data.isNamingCommitted()
                        && committed.valid()
                        && initialized
                        && !futureProfile
                        && nativeNamed;

        RecognitionEndowmentEffortRewardFormula.Reward reward =
                active
                        ? rewardFromStoredData(data)
                        : zeroReward();

        List<String> mismatches =
                findMismatches(
                        player,
                        active,
                        reward.energyIncreasePerPool()
                );

        return new RecognitionEndowmentEffortRewardSnapshot(
                data.isNamingCommitted(),
                committed.valid(),
                nativeNamed,
                initialized,
                futureProfile,
                mismatches.isEmpty(),
                mismatches,
                reward
        );
    }

    public static boolean removeAllModifiers(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        AttributeInstance maximumMagicule =
                player.getAttribute(
                        TensuraAttributes.MAX_MAGICULE
                );

        AttributeInstance maximumAura =
                player.getAttribute(
                        TensuraAttributes.MAX_AURA
                );

        boolean changed = false;
        changed |= remove(
                maximumMagicule,
                MAX_MAGICULE_MODIFIER_ID
        );
        changed |= remove(
                maximumAura,
                MAX_AURA_MODIFIER_ID
        );

        if (changed) {
            clampCurrentEnergy(
                    player,
                    maximumMagicule,
                    maximumAura
            );
        }

        return changed;
    }

    private static RecognitionEndowmentEffortRewardFormula.Reward
    rewardFromStoredData(
            RecognitionData data
    ) {
        return RecognitionEndowmentEffortRewardFormula.calculate(
                data.getMeasurement(
                        RecognitionStatKeys.IDENTITY_STRENGTH_AT_COMMIT
                ),
                data.getMeasurement(
                        RecognitionStatKeys.IDENTITY_STRENGTH_MAXIMUM_AT_COMMIT
                )
        );
    }

    private static ModifierChange reconcileModifier(
            AttributeInstance instance,
            ResourceLocation id,
            double expectedAmount
    ) {
        AttributeModifier existing =
                instance.getModifier(id);

        if (existing != null
                && existing.operation()
                == AttributeModifier.Operation.ADD_VALUE
                && approximately(
                        existing.amount(),
                        expectedAmount
                )) {
            return ModifierChange.unchanged();
        }

        double previousCapacity =
                existing != null
                        && existing.operation()
                        == AttributeModifier.Operation.ADD_VALUE
                        ? Math.max(
                                0.0D,
                                existing.amount()
                        )
                        : 0.0D;

        instance.addOrReplacePermanentModifier(
                new AttributeModifier(
                        id,
                        expectedAmount,
                        AttributeModifier.Operation.ADD_VALUE
                )
        );

        return new ModifierChange(
                true,
                Math.max(
                        0.0D,
                        expectedAmount - previousCapacity
                )
        );
    }

    /**
     * Grants only capacity that was newly added during this reconciliation.
     * With unchanged modifiers both deltas are zero and no setter is called.
     */
    private static double applyOnlyNewEnergyCapacity(
            IExistence existence,
            AttributeInstance maximumMagicule,
            AttributeInstance maximumAura,
            double newMagiculeCapacity,
            double newAuraCapacity
    ) {
        double totalGranted = 0.0D;
        boolean changed = false;

        if (newMagiculeCapacity > EPSILON) {
            double oldValue =
                    sanitizeEnergy(
                            existence.getMagicule()
                    );

            double newValue =
                    Math.min(
                            maximumMagicule.getValue(),
                            oldValue + newMagiculeCapacity
                    );

            if (!approximately(oldValue, newValue)) {
                existence.setMagicule(newValue);
                totalGranted +=
                        Math.max(
                                0.0D,
                                newValue - oldValue
                        );
                changed = true;
            }
        }

        if (newAuraCapacity > EPSILON) {
            double oldValue =
                    sanitizeEnergy(
                            existence.getAura()
                    );

            double newValue =
                    Math.min(
                            maximumAura.getValue(),
                            oldValue + newAuraCapacity
                    );

            if (!approximately(oldValue, newValue)) {
                existence.setAura(newValue);
                totalGranted +=
                        Math.max(
                                0.0D,
                                newValue - oldValue
                        );
                changed = true;
            }
        }

        if (changed) {
            existence.markDirty();
        }

        return totalGranted;
    }

    private static void clampCurrentEnergy(
            ServerPlayer player,
            AttributeInstance maximumMagicule,
            AttributeInstance maximumAura
    ) {
        IExistence existence =
                TensuraStorages.getExistenceFrom(
                        player
                );

        if (existence == null) {
            return;
        }

        boolean changed = false;

        if (maximumMagicule != null) {
            double oldValue =
                    sanitizeEnergy(
                            existence.getMagicule()
                    );

            double newValue =
                    Math.min(
                            oldValue,
                            maximumMagicule.getValue()
                    );

            if (!approximately(oldValue, newValue)) {
                existence.setMagicule(newValue);
                changed = true;
            }
        }

        if (maximumAura != null) {
            double oldValue =
                    sanitizeEnergy(
                            existence.getAura()
                    );

            double newValue =
                    Math.min(
                            oldValue,
                            maximumAura.getValue()
                    );

            if (!approximately(oldValue, newValue)) {
                existence.setAura(newValue);
                changed = true;
            }
        }

        if (changed) {
            existence.markDirty();
        }
    }

    private static List<String> findMismatches(
            ServerPlayer player,
            boolean active,
            double expectedAmount
    ) {
        List<String> result =
                new ArrayList<>();

        check(
                result,
                "max_magicule",
                player.getAttribute(
                        TensuraAttributes.MAX_MAGICULE
                ),
                MAX_MAGICULE_MODIFIER_ID,
                active,
                expectedAmount
        );

        check(
                result,
                "max_aura",
                player.getAttribute(
                        TensuraAttributes.MAX_AURA
                ),
                MAX_AURA_MODIFIER_ID,
                active,
                expectedAmount
        );

        return List.copyOf(result);
    }

    private static void check(
            List<String> result,
            String name,
            AttributeInstance instance,
            ResourceLocation id,
            boolean active,
            double expectedAmount
    ) {
        if (instance == null) {
            result.add(name + " attribute unavailable");
            return;
        }

        AttributeModifier modifier =
                instance.getModifier(id);

        boolean modifierExpected =
                active
                        && expectedAmount > EPSILON;

        if (!modifierExpected) {
            if (modifier != null) {
                result.add(name + " modifier remains while inactive");
            }
            return;
        }

        if (modifier == null) {
            result.add(name + " modifier missing");
        } else if (modifier.operation()
                != AttributeModifier.Operation.ADD_VALUE
                || !approximately(
                        modifier.amount(),
                        expectedAmount
                )) {
            result.add(name + " modifier differs");
        }
    }

    private static boolean remove(
            AttributeInstance instance,
            ResourceLocation id
    ) {
        return instance != null
                && instance.removeModifier(id);
    }

    private static double sanitizeEnergy(
            double value
    ) {
        return Double.isFinite(value)
                && value > 0.0D
                ? value
                : 0.0D;
    }

    private static boolean approximately(
            double first,
            double second
    ) {
        return Math.abs(first - second)
                <= EPSILON;
    }

    private static ResourceLocation id(
            String path
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                MoosTensuraAddon.MODID,
                path
        );
    }

    private static RecognitionEndowmentEffortRewardSnapshot
    emptySnapshot() {
        return new RecognitionEndowmentEffortRewardSnapshot(
                false,
                false,
                false,
                false,
                false,
                true,
                List.of(),
                zeroReward()
        );
    }

    private static RecognitionEndowmentEffortRewardFormula.Reward
    zeroReward() {
        return new RecognitionEndowmentEffortRewardFormula.Reward(
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
    }

    private record ModifierChange(
            boolean changed,
            double positiveDelta
    ) {
        private static ModifierChange unchanged() {
            return new ModifierChange(
                    false,
                    0.0D
            );
        }
    }

    public record ReconcileResult(
            boolean supported,
            boolean changed,
            boolean active,
            double currentEnergyGranted,
            String message
    ) {
        public ReconcileResult {
            currentEnergyGranted =
                    sanitizeEnergy(
                            currentEnergyGranted
                    );
            message =
                    message == null
                            ? ""
                            : message.trim();
        }

        public static ReconcileResult rejected(
                String message
        ) {
            return new ReconcileResult(
                    false,
                    false,
                    false,
                    0.0D,
                    message
            );
        }
    }
}
