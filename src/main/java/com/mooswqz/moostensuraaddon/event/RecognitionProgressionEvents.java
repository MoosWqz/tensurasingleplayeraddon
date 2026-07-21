package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.CureAttributionData;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.recognition.CivilianDefenseTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAuthorityProgress;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatAttribution;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatAttribution.CombatCredit;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEntityTags;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;
import com.mooswqz.moostensuraaddon.recognition.RecognitionSubordinateCombatTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionSubordinateSupport;
import com.mooswqz.moostensuraaddon.recognition.TensuraRecognitionStateHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class RecognitionProgressionEvents {

    private static final int STATE_SYNC_INTERVAL_TICKS = 40;

    private RecognitionProgressionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        if (player.tickCount
                % STATE_SYNC_INTERVAL_TICKS != 0) {
            return;
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        data.setCounterMaximum(
                RecognitionStatKeys.HIGHEST_EXPERIENCE_LEVEL,
                player.experienceLevel
        );

        recordDimensionMilestone(
                data,
                player.level().dimension()
        );

        synchronizeRaidVictories(
                player,
                data
        );

        TensuraRecognitionStateHelper.synchronize(
                player,
                data
        );

        RecognitionAuthorityProgress.synchronize(
                player,
                data
        );

        RecognitionSubordinateCombatTracker.cleanup(
                player.level().getGameTime()
        );

        CivilianDefenseTracker.cleanup(
                player.level().getGameTime()
        );
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        recordDimensionMilestone(
                data,
                event.getTo()
        );

        synchronizeRaidVictories(
                player,
                data
        );

        TensuraRecognitionStateHelper.synchronize(
                player,
                data
        );

        RecognitionAuthorityProgress.synchronize(
                player,
                data
        );
    }

    /**
     * Records who began a valid zombie-villager cure.
     *
     * The interaction event runs before vanilla performs the interaction.
     * We therefore only arm attribution when the vanilla cure prerequisites
     * are already present.
     */
    @SubscribeEvent
    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getTarget()
                instanceof ZombieVillager zombieVillager)) {
            return;
        }

        if (!event.getItemStack().is(
                Items.GOLDEN_APPLE
        )) {
            return;
        }

        if (!zombieVillager.hasEffect(
                MobEffects.WEAKNESS
        )) {
            return;
        }

        if (zombieVillager.isConverting()) {
            return;
        }

        CureAttributionData attribution =
                zombieVillager.getData(
                        AttachmentRegistry
                                .CURE_ATTRIBUTION_DATA
                );

        attribution.arm(
                player.getUUID(),
                player.level().getGameTime()
        );
    }

    /**
     * Awards the cure only when a zombie villager genuinely becomes a
     * villager.
     */
    @SubscribeEvent
    public static void onLivingConverted(
            LivingConversionEvent.Post event
    ) {
        if (!(event.getEntity()
                instanceof ZombieVillager zombieVillager)) {
            return;
        }

        if (!(event.getOutcome()
                instanceof Villager)) {
            return;
        }

        if (!(zombieVillager.level()
                instanceof ServerLevel serverLevel)) {
            return;
        }

        CureAttributionData attribution =
                zombieVillager.getData(
                        AttachmentRegistry
                                .CURE_ATTRIBUTION_DATA
                );

        Optional<UUID> starterUuid =
                attribution.getStarterUuid();

        if (starterUuid.isEmpty()) {
            return;
        }

        ServerPlayer player = serverLevel
                .getServer()
                .getPlayerList()
                .getPlayer(starterUuid.orElseThrow());

        if (player == null) {
            /*
             * In the addon's primary singleplayer use case, the integrated
             * server pauses/stops with the player, so the starter is normally
             * online when conversion completes.
             */
            return;
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        data.incrementCounter(
                RecognitionStatKeys.VILLAGERS_CURED
        );

        attribution.clear();
    }

    @SubscribeEvent
    public static void onLivingChangedTarget(
            LivingChangeTargetEvent event
    ) {
        LivingEntity aggressor = event.getEntity();

        LivingEntity newTarget =
                event.getNewAboutToBeSetTarget();

        CivilianDefenseTracker.recordTargetChange(
                aggressor,
                newTarget
        );
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(
            LivingIncomingDamageEvent event
    ) {
        LivingEntity victim = event.getEntity();

        RecognitionSubordinateCombatTracker.recordIncomingDamage(
                victim,
                event.getSource()
        );

        if (!CivilianDefenseTracker.isCivilian(
                victim
        )) {
            return;
        }

        CivilianDefenseTracker.recordCivilianDamage(
                victim,
                event.getSource()
        );
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event
    ) {
        LivingEntity victim = event.getEntity();

        if (victim.level().isClientSide()) {
            return;
        }

        ServerPlayer victimSubordinateOwner =
                RecognitionSubordinateSupport.findOnlineOwner(
                        victim
                );

        Set<UUID> subordinateParticipants =
                RecognitionSubordinateCombatTracker
                        .consumeParticipants(victim);

        Optional<CombatCredit> optionalCredit =
                RecognitionCombatAttribution.resolve(
                        event.getSource()
                );

        if (optionalCredit.isEmpty()) {
            RecognitionSubordinateCombatTracker.forget(
                    victim
            );

            if (victimSubordinateOwner != null) {
                RecognitionAuthorityProgress.removeDeadSubordinate(
                        victimSubordinateOwner,
                        victim.getUUID()
                );
            }

            CivilianDefenseTracker.forget(victim);
            return;
        }

        CombatCredit credit =
                optionalCredit.orElseThrow();

        ServerPlayer responsiblePlayer =
                credit.player();

        if (responsiblePlayer == victim) {
            RecognitionSubordinateCombatTracker.forget(
                    victim
            );

            if (victimSubordinateOwner != null) {
                RecognitionAuthorityProgress.removeDeadSubordinate(
                        victimSubordinateOwner,
                        victim.getUUID()
                );
            }

            CivilianDefenseTracker.forget(victim);
            return;
        }

        try {
            if (victim.getType().is(
                    RecognitionEntityTags.IGNORED
            )) {
                return;
            }

            RecognitionData data =
                    responsiblePlayer.getData(
                            AttachmentRegistry
                                    .RECOGNITION_DATA
                    );

            ResourceLocation entityTypeId =
                    BuiltInRegistries.ENTITY_TYPE
                            .getKey(victim.getType());

            if (entityTypeId == null) {
                return;
            }

            String entityTypeString =
                    entityTypeId.toString();

            if (CivilianDefenseTracker.consumeDefense(
                    victim,
                    responsiblePlayer
            )) {
                data.incrementCounter(
                        RecognitionStatKeys
                                .CIVILIANS_DEFENDED
                );
            }

            if (victim.getType().is(
                    RecognitionEntityTags.MAJOR_ENEMIES
            )) {
                data.addUniqueValue(
                        RecognitionStatKeys
                                .MAJOR_ENEMY_TYPES_DEFEATED,
                        entityTypeString
                );

                if (credit.isSoloPlayerAction()) {
                    data.addUniqueValue(
                            RecognitionStatKeys
                                    .SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
                            entityTypeString
                    );
                }

                if (subordinateParticipants.contains(
                        responsiblePlayer.getUUID()
                )) {
                    data.incrementCounter(
                            RecognitionStatKeys
                                    .SUBORDINATE_ASSISTED_MAJOR_VICTORIES
                    );
                }
            }

            /*
             * Only one negative deed category is applied to a death.
             *
             * Betrayal of a Tensura subordinate takes highest priority,
             * followed by owned companions, civilians, benevolent bosses
             * and passive baby animals.
             */
            if (victimSubordinateOwner != null
                    && victimSubordinateOwner.getUUID().equals(
                    responsiblePlayer.getUUID()
            )) {
                data.incrementCounter(
                        RecognitionStatKeys
                                .OWNED_SUBORDINATE_KILLS
                );
                return;
            }

            if (isOwnedCompanion(
                    victim,
                    responsiblePlayer
            )) {
                data.incrementCounter(
                        RecognitionStatKeys
                                .OWNED_COMPANION_KILLS
                );
                return;
            }

            if (CivilianDefenseTracker.isCivilian(
                    victim
            )) {
                data.incrementCounter(
                        RecognitionStatKeys.CIVILIAN_KILLS
                );
                return;
            }

            if (victim.getType().is(
                    RecognitionEntityTags
                            .BENEVOLENT_BOSSES
            )) {
                data.addUniqueValue(
                        RecognitionStatKeys
                                .BENEVOLENT_BOSS_TYPES_KILLED,
                        entityTypeString
                );
                return;
            }

            if (victim.isBaby()
                    && victim.getType().is(
                    RecognitionEntityTags
                            .BABY_KILL_MORALITY
            )) {
                data.incrementCounter(
                        RecognitionStatKeys
                                .PASSIVE_BABY_KILLS
                );
                return;
            }

            if (victim.getType().is(
                    RecognitionEntityTags
                            .MALEVOLENT_BOSSES
            )) {
                data.addUniqueValue(
                        RecognitionStatKeys
                                .MALEVOLENT_BOSS_TYPES_DEFEATED,
                        entityTypeString
                );
            }
        } finally {
            RecognitionSubordinateCombatTracker.forget(
                    victim
            );

            if (victimSubordinateOwner != null) {
                RecognitionAuthorityProgress.removeDeadSubordinate(
                        victimSubordinateOwner,
                        victim.getUUID()
                );
            }

            CivilianDefenseTracker.forget(victim);
        }
    }

    private static void synchronizeRaidVictories(
            ServerPlayer player,
            RecognitionData data
    ) {
        int vanillaRaidWins = player
                .getStats()
                .getValue(
                        Stats.CUSTOM.get(
                                Stats.RAID_WIN
                        )
                );

        /*
         * Maximum preserves recognition history even if a command or external
         * tool later resets the player's vanilla statistics.
         */
        data.setCounterMaximum(
                RecognitionStatKeys.RAID_VICTORIES,
                vanillaRaidWins
        );
    }

    private static boolean isOwnedCompanion(
            LivingEntity victim,
            ServerPlayer responsiblePlayer
    ) {
        if (!(victim
                instanceof TamableAnimal tamableAnimal)) {
            return false;
        }

        UUID ownerUuid =
                tamableAnimal.getOwnerUUID();

        return ownerUuid != null
                && ownerUuid.equals(
                responsiblePlayer.getUUID()
        );
    }

    private static void recordDimensionMilestone(
            RecognitionData data,
            ResourceKey<Level> dimension
    ) {
        if (data == null || dimension == null) {
            return;
        }

        if (Level.NETHER.equals(dimension)) {
            data.addUniqueValue(
                    RecognitionStatKeys
                            .DISCOVERY_MILESTONES,
                    "minecraft:entered_nether"
            );
            return;
        }

        if (Level.END.equals(dimension)) {
            data.addUniqueValue(
                    RecognitionStatKeys
                            .DISCOVERY_MILESTONES,
                    "minecraft:entered_end"
            );
        }
    }
}