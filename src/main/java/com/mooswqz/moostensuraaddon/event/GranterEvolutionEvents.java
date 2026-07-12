package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public class GranterEvolutionEvents {
    private static final int CHECK_INTERVAL_TICKS = 100;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        tryEvolveGranter(player);
    }

    private static void tryEvolveGranter(ServerPlayer player) {
        if (!hasGranter(player)) {
            return;
        }

        if (hasAnyEvolvedGranterSkill(player)) {
            return;
        }

        ManasSkillInstance granterInstance = SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.GRANTER.get().getRegistryName())
                .orElse(null);

        if (granterInstance == null || !granterInstance.isMastered(player)) {
            return;
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return;
        }

        if (!isNamed(existence)) {
            return;
        }

        if (existence.getEP() < MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_EP.get()) {
            return;
        }

        if (existence.getMagicule() < MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_MAGICULES.get()) {
            return;
        }

        GranterProgressData progress = player.getData(AttachmentRegistry.GRANTER_PROGRESS_DATA);

        if (getMasteredSkillCountExcludingGranter(player) < MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_MASTERED_SKILLS.get()) {
            return;
        }

        if (progress.getSuccessfulGrants() < MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_SUCCESSFUL_GRANTS.get()) {
            return;
        }

        List<LivingEntity> nearbySubordinates = getNearbySubordinates(player);

        boolean hasEnoughSubordinates = nearbySubordinates.size() >= MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_SUBORDINATES.get()
                || progress.getRecognizedSubordinateCount() >= MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_SUBORDINATES.get();

        if (!hasEnoughSubordinates) {
            return;
        }

        boolean isTrueDemonLord = existence.isTrueDemonLord();
        boolean isTrueHero = existence.isTrueHero();

        /*
         * If both paths are technically available, Absolute Governance takes priority.
         */
        if (isTrueDemonLord) {
            evolveIntoAbsoluteGovernance(player, existence, progress);
            return;
        }

        if (isTrueHero) {
            evolveIntoBenevolentEmpowerment(player, existence, progress);
        }
    }

    private static void evolveIntoBenevolentEmpowerment(ServerPlayer player, IExistence existence, GranterProgressData progress) {
        TensuraSkillInstance ultimateInstance = new TensuraSkillInstance(SkillRegistry.BENEVOLENT_EMPOWERMENT.get());
        ultimateInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learned = SkillHelper.learnSkill(
                player,
                ultimateInstance,
                -1,
                Component.translatable("moostensuraaddon.skill.benevolent_empowerment.acquired")
        );

        if (!learned) {
            player.sendSystemMessage(Component.translatable("moostensuraaddon.granter.evolution.failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        removeGranter(player);
        consumeEvolutionMagicules(existence);

        progress.setGranterUltimateEvolution("benevolent_empowerment");
        player.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, progress);

        player.sendSystemMessage(Component.translatable("moostensuraaddon.granter.evolution.granter_replaced")
                .withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.translatable("moostensuraaddon.granter.evolution.benevolent_success")
                .withStyle(ChatFormatting.GOLD));
    }

    private static void evolveIntoAbsoluteGovernance(ServerPlayer player, IExistence existence, GranterProgressData progress) {
        TensuraSkillInstance ultimateInstance = new TensuraSkillInstance(SkillRegistry.ABSOLUTE_GOVERNANCE.get());
        ultimateInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learned = SkillHelper.learnSkill(
                player,
                ultimateInstance,
                -1,
                Component.translatable("moostensuraaddon.skill.absolute_governance.acquired")
        );

        if (!learned) {
            player.sendSystemMessage(Component.translatable("moostensuraaddon.granter.evolution.failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        removeGranter(player);
        consumeEvolutionMagicules(existence);

        progress.setGranterUltimateEvolution("absolute_governance");
        player.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, progress);

        player.sendSystemMessage(Component.translatable("moostensuraaddon.granter.evolution.granter_replaced")
                .withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(Component.translatable("moostensuraaddon.granter.evolution.absolute_success")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void removeGranter(ServerPlayer player) {
        SkillAPI.getSkillsFrom(player).forgetSkill(
                SkillRegistry.GRANTER.get().getRegistryName(),
                Component.translatable("moostensuraaddon.granter.evolution.granter_replaced")
        );
    }

    private static void consumeEvolutionMagicules(IExistence existence) {
        double cost = MoosTensuraConfig.ULTIMATE_EVOLUTION_REQUIRED_MAGICULES.get();

        if (cost <= 0.0D) {
            return;
        }

        existence.setMagicule(Math.max(0.0D, existence.getMagicule() - cost));
        existence.markDirty();
    }

    private static boolean hasGranter(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.GRANTER.get().getRegistryName())
                .isPresent();
    }

    private static boolean hasAnyEvolvedGranterSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName())
                .isPresent()
                || SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName())
                .isPresent();
    }

    private static boolean isNamed(IExistence existence) {
        String name = existence.getName();
        return name != null && !name.isBlank();
    }

    private static int getMasteredSkillCountExcludingGranter(ServerPlayer player) {
        ResourceLocation granterId = SkillRegistry.GRANTER.get().getRegistryName();

        return (int) SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .filter(instance -> instance != null && instance.isMastered(player))
                .filter(instance -> !granterId.equals(instance.getSkillId()))
                .count();
    }

    private static List<LivingEntity> getNearbySubordinates(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(MoosTensuraConfig.ULTIMATE_EVOLUTION_SCAN_RADIUS.get()),
                entity -> entity != player && SubordinateHelper.isSubordinate(player, entity)
        );
    }
}