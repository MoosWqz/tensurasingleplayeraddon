package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
                        Component.literal(
                                        "Subordinate overview closed: "
                                                + "the required Ultimate "
                                                + "Skill is no longer active."
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

    private static OpenSubordinateOverviewScreenPayload.TargetEntry
    buildTargetEntry(
            ServerPlayer viewer,
            LivingEntity target
    ) {
        OpenSubordinateOverviewScreenPayload.TargetEntry base =
                GranterActions.buildSubordinateOverviewEntry(
                        viewer,
                        target
                );

        return new OpenSubordinateOverviewScreenPayload.TargetEntry(
                base.targetUuid(),
                base.targetName(),
                target.getType().getDescription().getString(),
                base.health(),
                base.maxHealth(),
                base.magicules(),
                base.ep(),
                viewer.distanceTo(target),
                base.skills()
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