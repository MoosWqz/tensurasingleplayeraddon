package com.mooswqz.moostensuraaddon.item;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

public class SoulResonatorItem extends Item {
    private static final String TARGET_TAG = "SoulResonatorTarget";
    private static final String TARGET_DIMENSION_TAG = "Dimension";
    private static final String TARGET_X_TAG = "X";
    private static final String TARGET_Y_TAG = "Y";
    private static final String TARGET_Z_TAG = "Z";

    private static final int SEARCH_RADIUS_CHUNKS = 96;
    private static final int COOLDOWN_TICKS = 20 * 5;

    private static final ResourceLocation SOUL_RESONANCE_ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(
            MoosTensuraAddon.MODID,
            "soul_resonance"
    );

    public static final TagKey<Structure> GREAT_CRYSTAL_SHRINES = TagKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "great_crystal_shrines")
    );

    public SoulResonatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.success(stack);
        }

        if (!hasSageOrGreatSage(serverPlayer)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.moostensuraaddon.soul_resonator.message.no_sage")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );

            serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            return InteractionResultHolder.success(stack);
        }

        BlockPos targetPos = serverLevel.findNearestMapStructure(
                GREAT_CRYSTAL_SHRINES,
                serverPlayer.blockPosition(),
                SEARCH_RADIUS_CHUNKS,
                false
        );

        if (targetPos == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.moostensuraaddon.soul_resonator.message.no_shrine")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );

            serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            return InteractionResultHolder.success(stack);
        }

        setTarget(stack, serverLevel, targetPos);
        awardSoulResonanceAdvancement(serverPlayer);

        int distance = Math.round((float) Math.sqrt(targetPos.distSqr(serverPlayer.blockPosition())));

        if (distance <= 8) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.moostensuraaddon.soul_resonator.message.shrine_here")
                            .withStyle(ChatFormatting.AQUA),
                    true
            );
        } else {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.moostensuraaddon.soul_resonator.message.locked", distance)
                            .withStyle(ChatFormatting.AQUA),
                    true
            );
        }

        serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);

        tooltip.add(
                Component.translatable("item.moostensuraaddon.soul_resonator.tooltip_requires_sage")
                        .withStyle(ChatFormatting.DARK_PURPLE)
        );

        if (hasTarget(stack)) {
            tooltip.add(
                    Component.translatable("item.moostensuraaddon.soul_resonator.tooltip_attuned")
                            .withStyle(ChatFormatting.AQUA)
            );
        } else {
            tooltip.add(
                    Component.translatable("item.moostensuraaddon.soul_resonator.tooltip_use")
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        tooltip.add(
                Component.translatable("item.moostensuraaddon.soul_resonator.tooltip_hint")
                        .withStyle(ChatFormatting.DARK_AQUA)
        );
    }

    public static void setTarget(ItemStack stack, ServerLevel level, BlockPos targetPos) {
        CompoundTag rootTag = getOrCreateCustomTag(stack);

        CompoundTag targetTag = new CompoundTag();
        targetTag.putString(TARGET_DIMENSION_TAG, level.dimension().location().toString());
        targetTag.putInt(TARGET_X_TAG, targetPos.getX());
        targetTag.putInt(TARGET_Y_TAG, targetPos.getY());
        targetTag.putInt(TARGET_Z_TAG, targetPos.getZ());

        rootTag.put(TARGET_TAG, targetTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));
    }

    public static GlobalPos getCompassTarget(Level level, ItemStack stack, Entity entity) {
        CompoundTag rootTag = getCustomTag(stack);

        if (rootTag == null || !rootTag.contains(TARGET_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag targetTag = rootTag.getCompound(TARGET_TAG);

        if (!targetTag.contains(TARGET_DIMENSION_TAG, Tag.TAG_STRING)) {
            return null;
        }

        String dimensionId = targetTag.getString(TARGET_DIMENSION_TAG);
        ResourceLocation dimensionLocation = ResourceLocation.tryParse(dimensionId);

        if (dimensionLocation == null) {
            return null;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);

        BlockPos targetPos = new BlockPos(
                targetTag.getInt(TARGET_X_TAG),
                targetTag.getInt(TARGET_Y_TAG),
                targetTag.getInt(TARGET_Z_TAG)
        );

        return GlobalPos.of(dimensionKey, targetPos);
    }

    private static boolean hasTarget(ItemStack stack) {
        CompoundTag rootTag = getCustomTag(stack);

        return rootTag != null && rootTag.contains(TARGET_TAG, Tag.TAG_COMPOUND);
    }

    private static boolean hasSageOrGreatSage(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .anyMatch(SoulResonatorItem::isSageOrGreatSageSkill);
    }

    private static boolean isSageOrGreatSageSkill(ManasSkillInstance instance) {
        if (instance == null) {
            return false;
        }

        ResourceLocation skillId = instance.getSkillId();

        if (skillId != null) {
            String id = skillId.toString();

            if (id.equals("tensura:sage") || id.equals("tensura:great_sage")) {
                return true;
            }
        }

        String displayName = instance.getDisplayName().getString();

        return displayName.equalsIgnoreCase("Sage")
                || displayName.equalsIgnoreCase("Great Sage")
                || displayName.equalsIgnoreCase("The Great Sage");
    }

    private static void awardSoulResonanceAdvancement(ServerPlayer player) {
        MinecraftServer server = player.getServer();

        if (server == null) {
            return;
        }

        AdvancementHolder advancement = server.getAdvancements().get(SOUL_RESONANCE_ADVANCEMENT);

        if (advancement == null) {
            return;
        }

        player.getAdvancements().award(advancement, "resonated");
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CompoundTag existingTag = getCustomTag(stack);

        if (existingTag != null) {
            return existingTag;
        }

        return new CompoundTag();
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

        if (customData == null) {
            return null;
        }

        return customData.copyTag();
    }
}