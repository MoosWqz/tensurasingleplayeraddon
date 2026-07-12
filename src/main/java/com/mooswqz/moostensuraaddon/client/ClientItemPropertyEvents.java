package com.mooswqz.moostensuraaddon.client;

import com.mooswqz.moostensuraaddon.item.ItemRegistry;
import com.mooswqz.moostensuraaddon.item.SoulResonatorItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientItemPropertyEvents {
    private ClientItemPropertyEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientItemPropertyEvents::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ItemRegistry.SOUL_RESONATOR.get(),
                ResourceLocation.withDefaultNamespace("angle"),
                ClientItemPropertyEvents::getSoulResonatorAngle
        ));
    }

    private static float getSoulResonatorAngle(ItemStack stack, ClientLevel clientLevel, LivingEntity entity, int seed) {
        if (entity == null) {
            return 0.0F;
        }

        Level level = clientLevel != null ? clientLevel : entity.level();

        if (level == null) {
            return 0.0F;
        }

        GlobalPos target = SoulResonatorItem.getCompassTarget(level, stack, entity);

        if (target == null) {
            return 0.0F;
        }

        if (!target.dimension().equals(level.dimension())) {
            return 0.0F;
        }

        double targetX = target.pos().getX() + 0.5D;
        double targetZ = target.pos().getZ() + 0.5D;

        double deltaX = targetX - entity.getX();
        double deltaZ = targetZ - entity.getZ();

        double horizontalDistanceSqr = deltaX * deltaX + deltaZ * deltaZ;

        if (horizontalDistanceSqr < 0.25D) {
            return 0.0F;
        }

        double targetAngle = Math.atan2(deltaZ, deltaX) / (Math.PI * 2.0D);
        double playerAngle = Mth.positiveModulo(entity.getYRot() / 360.0D, 1.0D);

        double compassAngle = 0.5D - (playerAngle - 0.25D - targetAngle);

        /*
         * The Soul Resonator textures are compass-frame based, but the needle
         * currently points away from the shrine with the raw compass angle.
         * Adding 0.5 rotates the result by 180 degrees.
         */
        compassAngle += 0.5D;

        return Mth.positiveModulo((float) compassAngle, 1.0F);
    }
}