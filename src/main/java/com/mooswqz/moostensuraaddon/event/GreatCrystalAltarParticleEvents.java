package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.block.BlockRegistry;
import com.mooswqz.moostensuraaddon.block.GreatCrystalAltarBlock;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.ritual.GreatSageRitualManager;
import com.mooswqz.moostensuraaddon.util.GreatSageAwakeningHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public class GreatCrystalAltarParticleEvents {
    private GreatCrystalAltarParticleEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!MoosTensuraConfig.GREAT_CRYSTAL_READY_PARTICLES_ENABLED.get()) {
            return;
        }

        int interval = Math.max(5, MoosTensuraConfig.GREAT_CRYSTAL_READY_PARTICLE_INTERVAL_TICKS.get());

        if (player.tickCount % interval != 0) {
            return;
        }

        if (GreatSageRitualManager.isRitualActive(player)) {
            return;
        }

        GreatSageAwakeningHelper.RequirementCheck check = GreatSageAwakeningHelper.checkAltarRequirements(player);

        if (!check.successful()) {
            return;
        }

        BlockPos altarPos = findNearestLowerAltar(serverLevel, player);

        if (altarPos == null) {
            return;
        }

        spawnReadyParticles(serverLevel, altarPos);
    }

    private static BlockPos findNearestLowerAltar(ServerLevel level, ServerPlayer player) {
        int horizontalRange = Math.max(1, MoosTensuraConfig.GREAT_CRYSTAL_READY_PARTICLE_RANGE.get());
        int verticalRange = Math.max(1, MoosTensuraConfig.GREAT_CRYSTAL_READY_PARTICLE_VERTICAL_RANGE.get());

        BlockPos center = player.blockPosition();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.is(BlockRegistry.GREAT_CRYSTAL_ALTAR.get())) {
                        continue;
                    }

                    if (!state.hasProperty(GreatCrystalAltarBlock.HALF)) {
                        continue;
                    }

                    if (state.getValue(GreatCrystalAltarBlock.HALF) != DoubleBlockHalf.LOWER) {
                        continue;
                    }

                    double distance = distanceToPlayerSqr(pos, player);

                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = pos;
                    }
                }
            }
        }

        return closest;
    }

    private static double distanceToPlayerSqr(BlockPos pos, ServerPlayer player) {
        double dx = pos.getX() + 0.5D - player.getX();
        double dy = pos.getY() + 0.5D - player.getY();
        double dz = pos.getZ() + 0.5D - player.getZ();

        return dx * dx + dy * dy + dz * dz;
    }

    private static void spawnReadyParticles(ServerLevel level, BlockPos altarPos) {
        double x = altarPos.getX() + 0.5D;
        double y = altarPos.getY() + 1.2D;
        double z = altarPos.getZ() + 0.5D;

        level.sendParticles(
                ParticleTypes.END_ROD,
                x,
                y,
                z,
                2,
                0.15D,
                0.25D,
                0.15D,
                0.01D
        );

        level.sendParticles(
                ParticleTypes.ENCHANT,
                x,
                y + 0.45D,
                z,
                4,
                0.35D,
                0.35D,
                0.35D,
                0.02D
        );
    }
}