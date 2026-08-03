package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SubordinateOverviewService {

    private static final Map<UUID, Long> LAST_REFRESH_NANOS =
            new ConcurrentHashMap<>();
    private static final long STATE_EXPIRY_NANOS =
            300_000_000_000L;
    private static volatile long nextCleanupNanos;

    private SubordinateOverviewService() {
    }

    public static List<LivingEntity> discoverNearbySubordinates(
            ServerPlayer player
    ) {
        if (player == null) {
            return List.of();
        }

        double radius = SubordinateOverviewPolicy.NEARBY_RADIUS;
        double maximumDistanceSquared = radius * radius;

        return player.serverLevel()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(radius),
                        target -> target != player
                                && target.isAlive()
                                && !target.isRemoved()
                                && player.distanceToSqr(target)
                                <= maximumDistanceSquared
                                && SubordinateHelper.isSubordinate(
                                player,
                                target
                        )
                )
                .stream()
                .sorted(
                        Comparator
                                .comparingDouble(
                                        (LivingEntity target) ->
                                                player.distanceToSqr(target)
                                )
                                .thenComparing(
                                        target -> target
                                                .getDisplayName()
                                                .getString(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        target -> target
                                                .getUUID()
                                                .toString()
                                )
                )
                .toList();
    }

    public static void sendSnapshot(
            ServerPlayer player,
            boolean benevolent,
            List<LivingEntity> discoveredTargets
    ) {
        if (player == null) {
            return;
        }

        List<LivingEntity> safeTargets = discoveredTargets == null
                ? List.of()
                : discoveredTargets.stream()
                .filter(target -> target != null)
                .toList();
        boolean truncated = safeTargets.size()
                > SubordinateOverviewPolicy.MAX_TARGETS_PER_PAYLOAD;
        List<OpenSubordinateOverviewScreenPayload.TargetEntry> entries =
                new ArrayList<>(
                        Math.min(
                                safeTargets.size(),
                                SubordinateOverviewPolicy
                                        .MAX_TARGETS_PER_PAYLOAD
                        )
                );

        for (LivingEntity target : safeTargets.stream()
                .limit(
                        SubordinateOverviewPolicy
                                .MAX_TARGETS_PER_PAYLOAD
                )
                .toList()) {
            entries.add(buildTargetEntry(player, target));
        }

        PacketDistributor.sendToPlayer(
                player,
                new OpenSubordinateOverviewScreenPayload(
                        benevolent
                                ? OpenSubordinateOverviewScreenPayload
                                  .THEME_BENEVOLENT
                                : OpenSubordinateOverviewScreenPayload
                                  .THEME_GOVERNANCE,
                        SubordinateOverviewPolicy.NEARBY_RADIUS,
                        true,
                        truncated,
                        entries
                )
        );
    }

    public static void refresh(
            ServerPlayer player,
            boolean benevolent
    ) {
        if (player == null
                || !hasRequiredAuthority(player, benevolent)) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable(
                                        "message.moostensuraaddon.subordinate_overview.authority_missing"
                                )
                                .withStyle(ChatFormatting.RED),
                        false
                );
            }
            return;
        }

        long now = System.nanoTime();
        cleanupExpiredStates(now);
        Long previous = LAST_REFRESH_NANOS.put(
                player.getUUID(),
                now
        );

        if (previous != null
                && now - previous
                < SubordinateOverviewPolicy
                .MINIMUM_REFRESH_INTERVAL_NANOS) {
            LAST_REFRESH_NANOS.put(
                    player.getUUID(),
                    previous
            );
            return;
        }

        sendSnapshot(
                player,
                benevolent,
                discoverNearbySubordinates(player)
        );
    }

    public static void forget(
            UUID playerUuid
    ) {
        if (playerUuid != null) {
            LAST_REFRESH_NANOS.remove(playerUuid);
        }
    }

    private static OpenSubordinateOverviewScreenPayload.TargetEntry
    buildTargetEntry(
            ServerPlayer viewer,
            LivingEntity target
    ) {
        IExistence existence = TensuraStorages.getExistenceFrom(target);
        double magicules = existence == null
                ? 0.0D
                : existence.getMagicule();
        double ep = existence == null
                ? 0.0D
                : existence.getEP();
        GrantedSkillData grantedSkillData = target.getData(
                AttachmentRegistry.GRANTED_SKILL_DATA
        );
        List<OpenSubordinateOverviewScreenPayload.SkillEntry> skills =
                new ArrayList<>();

        for (ManasSkillInstance instance :
                SkillAPI.getSkillsFrom(target).getLearnedSkills()) {
            if (instance == null || instance.getSkillId() == null) {
                continue;
            }

            ResourceLocation skillId = instance.getSkillId();
            Component displayName = instance.getDisplayName();
            SkillCategoryHelper.SkillCategory category =
                    SkillCategoryHelper.getCategory(
                            instance,
                            skillId,
                            displayName
                    );
            String categoryId = UiFinalPolicy.canonicalCategoryId(
                    category.name()
            );
            boolean grantedByViewer = grantedSkillData.getGrant(
                    skillId.toString(),
                    viewer.getUUID()
            ).isPresent();

            skills.add(
                    new OpenSubordinateOverviewScreenPayload.SkillEntry(
                            skillId.toString(),
                            displayName.getString(),
                            categoryId,
                            UiFinalPolicy.categoryOrder(categoryId),
                            instance.isMastered(target),
                            grantedByViewer
                    )
            );
        }

        skills.sort(
                Comparator
                        .comparingInt(
                                OpenSubordinateOverviewScreenPayload
                                        .SkillEntry::categoryOrder
                        )
                        .thenComparing(
                                OpenSubordinateOverviewScreenPayload
                                        .SkillEntry::displayName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                OpenSubordinateOverviewScreenPayload
                                        .SkillEntry::skillId
                        )
        );

        return new OpenSubordinateOverviewScreenPayload.TargetEntry(
                target.getUUID().toString(),
                target.getDisplayName().getString(),
                UiTranslationToken.encode(
                        target.getType().getDescriptionId()
                ),
                target.getHealth(),
                target.getMaxHealth(),
                magicules,
                ep,
                viewer.distanceTo(target),
                skills
        );
    }

    private static boolean hasRequiredAuthority(
            ServerPlayer player,
            boolean benevolent
    ) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(
                        benevolent
                                ? SkillRegistry.BENEVOLENT_EMPOWERMENT
                                .get()
                                .getRegistryName()
                                : SkillRegistry.ABSOLUTE_GOVERNANCE
                                .get()
                                .getRegistryName()
                )
                .isPresent();
    }

    private static void cleanupExpiredStates(
            long now
    ) {
        if (now < nextCleanupNanos) {
            return;
        }

        nextCleanupNanos = now + STATE_EXPIRY_NANOS;
        LAST_REFRESH_NANOS.entrySet().removeIf(
                entry -> now - entry.getValue()
                        >= STATE_EXPIRY_NANOS
        );
    }
}