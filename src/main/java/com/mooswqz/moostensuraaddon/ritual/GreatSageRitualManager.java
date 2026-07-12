package com.mooswqz.moostensuraaddon.ritual;

import com.mooswqz.moostensuraaddon.block.BlockRegistry;
import com.mooswqz.moostensuraaddon.block.GreatCrystalAltarBlock;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.util.AddonAdvancementHelper;
import com.mooswqz.moostensuraaddon.util.GreatSageAwakeningHelper;
import com.mooswqz.moostensuraaddon.util.GreatSageEvolutionService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GreatSageRitualManager {
    private static final Map<UUID, RitualState> ACTIVE_RITUALS = new HashMap<>();

    private static final int DEFAULT_RITUAL_DURATION_TICKS = 20 * 15;
    private static final int DARKNESS_REFRESH_INTERVAL_TICKS = 20;

    private static final Component[] PHASE_MESSAGES = new Component[]{
            Component.translatable("message.moostensuraaddon.great_sage_ritual.phase_0"),
            Component.translatable("message.moostensuraaddon.great_sage_ritual.phase_1"),
            Component.translatable("message.moostensuraaddon.great_sage_ritual.phase_2"),
            Component.translatable("message.moostensuraaddon.great_sage_ritual.phase_3"),
            Component.translatable("message.moostensuraaddon.great_sage_ritual.phase_4")
    };

    private GreatSageRitualManager() {
    }

    public static void tryStartRitual(ServerPlayer player, BlockPos altarPos) {
        if (player == null || altarPos == null) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!MoosTensuraConfig.GREAT_SAGE_RITUAL_ENABLED.get()) {
            player.sendSystemMessage(
                    Component.literal("The Great Crystal Altar is dormant.")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (isRitualActive(player)) {
            player.displayClientMessage(
                    Component.translatable("message.moostensuraaddon.great_sage_ritual.already_active")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
            return;
        }

        BlockPos lowerAltarPos = getLowerAltarPos(serverLevel, altarPos);

        if (lowerAltarPos == null || !isValidAltar(serverLevel, lowerAltarPos)) {
            player.sendSystemMessage(
                    Component.translatable("message.moostensuraaddon.great_sage_ritual.invalid_altar")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        GreatSageAwakeningHelper.RequirementCheck check =
                GreatSageAwakeningHelper.checkAltarRequirements(player);

        if (!check.successful()) {
            sendAltarFailure(player, Component.translatable("message.moostensuraaddon.great_sage_ritual.requirements_failed"));
            return;
        }

        awardAltarResonance(player);

        int configuredDuration = MoosTensuraConfig.GREAT_SAGE_RITUAL_DURATION_TICKS.get();
        int durationTicks = Math.max(DEFAULT_RITUAL_DURATION_TICKS, configuredDuration);

        RitualState state = new RitualState(
                serverLevel.dimension(),
                lowerAltarPos,
                player.position(),
                durationTicks,
                player.getYRot()
        );

        ACTIVE_RITUALS.put(player.getUUID(), state);

        player.displayClientMessage(
                Component.translatable("message.moostensuraaddon.great_sage_ritual.started")
                        .withStyle(ChatFormatting.AQUA),
                false
        );

        sendPhaseMessage(player, state, 0);
        playPhaseSound(serverLevel, lowerAltarPos, 0);
    }

    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }

        RitualState state = ACTIVE_RITUALS.get(player.getUUID());

        if (state == null) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            cancelRitual(player);
            return;
        }

        if (!player.level().dimension().equals(state.dimension)) {
            cancelRitual(player);
            return;
        }

        if (!player.isAlive()) {
            cancelRitual(player);
            return;
        }

        if (!isValidAltar(serverLevel, state.altarPos)) {
            cancelRitual(player);
            return;
        }

        applyRitualStasis(player, state);
        spawnRitualParticles(serverLevel, player, state);

        int phase = getPhase(state);

        if (phase != state.lastPhase) {
            sendPhaseMessage(player, state, phase);
            playPhaseSound(serverLevel, state.altarPos, phase);
            state.lastPhase = phase;
        }

        state.elapsedTicks++;

        if (state.elapsedTicks >= state.durationTicks) {
            completeRitual(player, serverLevel, state);
        }
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            tick(player);
        }
    }

    public static boolean isRitualActive(ServerPlayer player) {
        return player != null && ACTIVE_RITUALS.containsKey(player.getUUID());
    }

    public static void cancelForLogout(ServerPlayer player) {
        cancelRitual(player);
    }

    public static void cancelForDeath(ServerPlayer player) {
        cancelRitual(player);
    }

    public static void cancelRitual(ServerPlayer player) {
        if (player == null) {
            return;
        }

        RitualState removed = ACTIVE_RITUALS.remove(player.getUUID());

        if (removed != null) {
            clearRitualPose(player);

            player.displayClientMessage(
                    Component.translatable("message.moostensuraaddon.great_sage_ritual.cancelled")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
        }
    }

    public static void cancel(ServerPlayer player) {
        cancelRitual(player);
    }

    private static void completeRitual(ServerPlayer player, ServerLevel level, RitualState state) {
        ACTIVE_RITUALS.remove(player.getUUID());

        clearRitualPose(player);
        spawnCompletionParticles(level, player, state);
        playCompletionSound(level, state.altarPos);

        GreatSageEvolutionService.EvolutionResult result =
                GreatSageEvolutionService.attemptNormalUpgrade(player);

        if (result.successful()) {
            player.displayClientMessage(
                    Component.translatable("message.moostensuraaddon.great_sage_ritual.completed")
                            .withStyle(ChatFormatting.GOLD),
                    false
            );
        } else {
            player.sendSystemMessage(result.message());
        }
    }

    private static void applyRitualStasis(ServerPlayer player, RitualState state) {
        Vec3 pos = state.stasisPos;

        player.teleportTo(pos.x, pos.y, pos.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.setSprinting(false);

        player.setYRot(state.lockedYaw);
        player.setYHeadRot(state.lockedYaw);
        player.setYBodyRot(state.lockedYaw);
        player.setXRot(0.0F);

        player.setPose(Pose.SLEEPING);

        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                40,
                255,
                false,
                false,
                false
        ));

        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                40,
                255,
                false,
                false,
                false
        ));

        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                40,
                255,
                false,
                false,
                false
        ));

        if (state.elapsedTicks % DARKNESS_REFRESH_INTERVAL_TICKS == 0) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS,
                    80,
                    0,
                    false,
                    false,
                    true
            ));
        }
    }

    private static void clearRitualPose(ServerPlayer player) {
        player.setPose(Pose.STANDING);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    private static void spawnRitualParticles(ServerLevel level, ServerPlayer player, RitualState state) {
        double progress = Math.min(1.0D, (double) state.elapsedTicks / Math.max(1, state.durationTicks));

        double playerX = player.getX();
        double playerY = player.getY() + 0.25D;
        double playerZ = player.getZ();

        double altarX = state.altarPos.getX() + 0.5D;
        double altarY = state.altarPos.getY() + 1.2D;
        double altarZ = state.altarPos.getZ() + 0.5D;

        int soulCount = progress < 0.5D ? 3 : 6;
        int flameCount = progress < 0.5D ? 2 : 4;

        level.sendParticles(
                ParticleTypes.SOUL,
                playerX,
                playerY,
                playerZ,
                soulCount,
                0.75D,
                0.15D,
                0.75D,
                0.015D
        );

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                playerX,
                playerY + 0.15D,
                playerZ,
                flameCount,
                0.45D,
                0.20D,
                0.45D,
                0.01D
        );

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                altarX,
                altarY,
                altarZ,
                flameCount,
                0.35D,
                0.45D,
                0.35D,
                0.015D
        );

        if (state.elapsedTicks % 10 == 0) {
            level.sendParticles(
                    ParticleTypes.SOUL,
                    altarX,
                    altarY + 0.35D,
                    altarZ,
                    8,
                    0.65D,
                    0.35D,
                    0.65D,
                    0.02D
            );
        }
    }

    private static void spawnCompletionParticles(ServerLevel level, ServerPlayer player, RitualState state) {
        double playerX = player.getX();
        double playerY = player.getY() + 0.75D;
        double playerZ = player.getZ();

        double altarX = state.altarPos.getX() + 0.5D;
        double altarY = state.altarPos.getY() + 1.4D;
        double altarZ = state.altarPos.getZ() + 0.5D;

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                playerX,
                playerY,
                playerZ,
                48,
                1.0D,
                0.6D,
                1.0D,
                0.05D
        );

        level.sendParticles(
                ParticleTypes.SOUL,
                playerX,
                playerY,
                playerZ,
                36,
                1.1D,
                0.7D,
                1.1D,
                0.04D
        );

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                altarX,
                altarY,
                altarZ,
                40,
                0.9D,
                0.8D,
                0.9D,
                0.05D
        );

        level.sendParticles(
                ParticleTypes.SOUL,
                altarX,
                altarY,
                altarZ,
                28,
                0.8D,
                0.7D,
                0.8D,
                0.04D
        );
    }

    private static int getPhase(RitualState state) {
        int phaseCount = PHASE_MESSAGES.length;
        int phase = (int) ((long) state.elapsedTicks * phaseCount / Math.max(1, state.durationTicks));

        if (phase < 0) {
            return 0;
        }

        return Math.min(phase, phaseCount - 1);
    }

    private static void sendPhaseMessage(ServerPlayer player, RitualState state, int phase) {
        if (phase < 0 || phase >= PHASE_MESSAGES.length) {
            return;
        }

        player.displayClientMessage(
                PHASE_MESSAGES[phase].copy().withStyle(ChatFormatting.AQUA),
                true
        );
    }

    private static void playPhaseSound(ServerLevel level, BlockPos altarPos, int phase) {
        float pitch = 0.8F + (phase * 0.12F);

        level.playSound(
                null,
                altarPos,
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS,
                0.75F,
                pitch
        );

        if (phase >= 2) {
            level.playSound(
                    null,
                    altarPos,
                    SoundEvents.BEACON_AMBIENT,
                    SoundSource.BLOCKS,
                    0.35F,
                    1.0F
            );
        }
    }

    private static void playCompletionSound(ServerLevel level, BlockPos altarPos) {
        level.playSound(
                null,
                altarPos,
                SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS,
                0.9F,
                1.25F
        );

        level.playSound(
                null,
                altarPos,
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.75F,
                1.2F
        );

        level.playSound(
                null,
                altarPos,
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS,
                1.0F,
                1.6F
        );
    }

    private static void awardAltarResonance(ServerPlayer player) {
        AddonAdvancementHelper.awardGreatCrystalResonance(player);
    }

    private static void sendAltarFailure(ServerPlayer player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
    }

    private static BlockPos getLowerAltarPos(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (!state.is(BlockRegistry.GREAT_CRYSTAL_ALTAR.get())) {
            return null;
        }

        if (!state.hasProperty(GreatCrystalAltarBlock.HALF)) {
            return null;
        }

        if (state.getValue(GreatCrystalAltarBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }

        return pos;
    }

    private static boolean isValidAltar(ServerLevel level, BlockPos lowerPos) {
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(lowerPos.above());

        if (!lowerState.is(BlockRegistry.GREAT_CRYSTAL_ALTAR.get())) {
            return false;
        }

        if (!upperState.is(BlockRegistry.GREAT_CRYSTAL_ALTAR.get())) {
            return false;
        }

        if (!lowerState.hasProperty(GreatCrystalAltarBlock.HALF)) {
            return false;
        }

        if (!upperState.hasProperty(GreatCrystalAltarBlock.HALF)) {
            return false;
        }

        return lowerState.getValue(GreatCrystalAltarBlock.HALF) == DoubleBlockHalf.LOWER
                && upperState.getValue(GreatCrystalAltarBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    private static final class RitualState {
        private final ResourceKey<Level> dimension;
        private final BlockPos altarPos;
        private final Vec3 stasisPos;
        private final int durationTicks;
        private final float lockedYaw;

        private int elapsedTicks;
        private int lastPhase;

        private RitualState(ResourceKey<Level> dimension, BlockPos altarPos, Vec3 stasisPos, int durationTicks, float lockedYaw) {
            this.dimension = dimension;
            this.altarPos = altarPos.immutable();
            this.stasisPos = stasisPos;
            this.durationTicks = durationTicks;
            this.lockedYaw = lockedYaw;
            this.elapsedTicks = 0;
            this.lastPhase = -1;
        }
    }
}