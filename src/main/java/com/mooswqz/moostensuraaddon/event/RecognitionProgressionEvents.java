package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.CureAttributionData;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.recognition.CivilianDefenseTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAuthorityProgress;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAttributionPolicy;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatAttribution.CombatCredit;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatCreditTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEntityTags;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistoryIntegration;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIndependenceProgress;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStrengthRewardService;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
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
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        RecognitionIndependenceProgress.synchronize(
                player,
                data
        );

        RecognitionIdentityHistoryIntegration
                .synchronizeAuthorityCounters(
                        data,
                        getOverworldGameTime(
                                player
                        )
                );

        RecognitionStrengthRewardService.reconcile(
                player
        );
    }

    @SubscribeEvent
    public static void onPlayerRespawned(
            PlayerEvent.PlayerRespawnEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionStrengthRewardService.reconcile(
                player
        );
    }

    @SubscribeEvent
    public static void onAdvancementEarned(
            AdvancementEvent.AdvancementEarnEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionIndependenceProgress.recordEarned(
                player,
                event.getAdvancement()
                        .id()
        );
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
                player,
                data,
                player.level().dimension()
        );

        synchronizeRaidVictories(
                player,
                data
        );

        RecognitionIndependenceProgress.synchronize(
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

        RecognitionIdentityHistoryIntegration
                .synchronizeAuthorityCounters(
                        data,
                        getOverworldGameTime(
                                player
                        )
                );

        RecognitionStrengthRewardService.reconcile(
                player
        );

        RecognitionSubordinateCombatTracker.cleanup(
                getOverworldGameTime(player)
        );

        RecognitionCombatCreditTracker.cleanup(
                player.getServer()
        );

        CivilianDefenseTracker.cleanup(
                player.getServer()
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
                player,
                data,
                event.getTo()
        );

        synchronizeRaidVictories(
                player,
                data
        );

        RecognitionIndependenceProgress.synchronize(
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

        RecognitionIdentityHistoryIntegration
                .synchronizeAuthorityCounters(
                        data,
                        getOverworldGameTime(
                                player
                        )
                );

        RecognitionStrengthRewardService.reconcile(
                player
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

        RecognitionIdentityHistoryIntegration.record(
                data,
                RecognitionIdentityHistoryIntegration
                        .TrackedDeed
                        .VILLAGER_CURED,
                getOverworldGameTime(player)
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

    /** Records only damage that actually reduced health. */
    @SubscribeEvent
    public static void onLivingDamageApplied(
            LivingDamageEvent.Post event
    ) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        LivingEntity victim = event.getEntity();

        RecognitionCombatCreditTracker.recordIncomingDamage(
                victim,
                event.getSource()
        );

        RecognitionSubordinateCombatTracker.recordIncomingDamage(
                victim,
                event.getSource()
        );

        if (CivilianDefenseTracker.isCivilian(victim)) {
            CivilianDefenseTracker.recordCivilianDamage(
                    victim,
                    event.getSource()
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
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

        RecognitionCombatCreditTracker.DeathResolution
                deathResolution =
                RecognitionCombatCreditTracker.consumeDeath(
                        victim,
                        event.getSource()
                );

        if (deathResolution.duplicateSuppressed()) {
            return;
        }

        Optional<CombatCredit> optionalCredit =
                deathResolution.credit();

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

                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .CIVILIAN_DEFENDED,
                        getOverworldGameTime(
                                responsiblePlayer
                        )
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
                    boolean newSoloType =
                            data.addUniqueValue(
                                    RecognitionStatKeys
                                            .SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
                                    entityTypeString
                            );

                    if (newSoloType) {
                        RecognitionIdentityHistoryIntegration.record(
                                data,
                                RecognitionIdentityHistoryIntegration
                                        .TrackedDeed
                                        .SOLO_MAJOR_VICTORY,
                                getOverworldGameTime(
                                        responsiblePlayer
                                )
                        );
                    }
                }

                if (subordinateParticipants.contains(
                        responsiblePlayer.getUUID()
                )) {
                    data.incrementCounter(
                            RecognitionStatKeys
                                    .SUBORDINATE_ASSISTED_MAJOR_VICTORIES
                    );

                    RecognitionIdentityHistoryIntegration.record(
                            data,
                            RecognitionIdentityHistoryIntegration
                                    .TrackedDeed
                                    .SUBORDINATE_ASSISTED_MAJOR_VICTORY,
                            getOverworldGameTime(
                                    responsiblePlayer
                            )
                    );
                }
            }

            RecognitionAttributionPolicy.NegativeDeed negativeDeed =
                    RecognitionAttributionPolicy.classifyNegativeDeed(
                            victimSubordinateOwner != null
                                    && victimSubordinateOwner.getUUID().equals(
                                    responsiblePlayer.getUUID()
                            ),
                            isOwnedCompanion(
                                    victim,
                                    responsiblePlayer
                            ),
                            CivilianDefenseTracker.isCivilian(victim),
                            victim.getType().is(
                                    RecognitionEntityTags.BENEVOLENT_BOSSES
                            ),
                            victim.isBaby()
                                    && victim.getType().is(
                                    RecognitionEntityTags.BABY_KILL_MORALITY
                            )
                    );

            /* Exactly one highest-priority negative deed is applied. */
            if (negativeDeed
                    == RecognitionAttributionPolicy.NegativeDeed
                    .OWNED_SUBORDINATE_KILLED) {
                data.incrementCounter(
                        RecognitionStatKeys
                                .OWNED_SUBORDINATE_KILLS
                );

                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .OWNED_SUBORDINATE_KILLED,
                        getOverworldGameTime(
                                responsiblePlayer
                        )
                );

                return;
            }

            if (negativeDeed
                    == RecognitionAttributionPolicy.NegativeDeed
                    .OWNED_COMPANION_KILLED) {
                data.incrementCounter(
                        RecognitionStatKeys
                                .OWNED_COMPANION_KILLS
                );

                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .OWNED_COMPANION_KILLED,
                        getOverworldGameTime(
                                responsiblePlayer
                        )
                );

                return;
            }

            if (negativeDeed
                    == RecognitionAttributionPolicy.NegativeDeed
                    .CIVILIAN_KILLED) {
                data.incrementCounter(
                        RecognitionStatKeys.CIVILIAN_KILLS
                );

                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .CIVILIAN_KILLED,
                        getOverworldGameTime(
                                responsiblePlayer
                        )
                );

                return;
            }

            if (negativeDeed
                    == RecognitionAttributionPolicy.NegativeDeed
                    .BENEVOLENT_BOSS_KILLED) {
                boolean newBenevolentBossType =
                        data.addUniqueValue(
                                RecognitionStatKeys
                                        .BENEVOLENT_BOSS_TYPES_KILLED,
                                entityTypeString
                        );

                if (newBenevolentBossType) {
                    RecognitionIdentityHistoryIntegration.record(
                            data,
                            RecognitionIdentityHistoryIntegration
                                    .TrackedDeed
                                    .BENEVOLENT_BOSS_KILLED,
                            getOverworldGameTime(
                                    responsiblePlayer
                            )
                    );
                }

                return;
            }

            if (negativeDeed
                    == RecognitionAttributionPolicy.NegativeDeed
                    .PASSIVE_BABY_KILLED) {
                data.incrementCounter(
                        RecognitionStatKeys
                                .PASSIVE_BABY_KILLS
                );

                RecognitionIdentityHistoryIntegration.record(
                        data,
                        RecognitionIdentityHistoryIntegration
                                .TrackedDeed
                                .PASSIVE_BABY_KILLED,
                        getOverworldGameTime(
                                responsiblePlayer
                        )
                );

                return;
            }

            if (victim.getType().is(
                    RecognitionEntityTags
                            .MALEVOLENT_BOSSES
            )) {
                boolean newMalevolentBossType =
                        data.addUniqueValue(
                                RecognitionStatKeys
                                        .MALEVOLENT_BOSS_TYPES_DEFEATED,
                                entityTypeString
                        );

                if (newMalevolentBossType) {
                    RecognitionIdentityHistoryIntegration.record(
                            data,
                            RecognitionIdentityHistoryIntegration
                                    .TrackedDeed
                                    .MALEVOLENT_BOSS_DEFEATED,
                            getOverworldGameTime(
                                    responsiblePlayer
                            )
                    );
                }
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

        int storedRaidWins = data.getCounter(
                RecognitionStatKeys.RAID_VICTORIES
        );

        /*
         * Maximum preserves recognition history even if a command or external
         * tool later resets the player's vanilla statistics.
         */
        data.setCounterMaximum(
                RecognitionStatKeys.RAID_VICTORIES,
                vanillaRaidWins
        );

        int newRaidVictories = Math.max(
                0,
                vanillaRaidWins - storedRaidWins
        );

        if (newRaidVictories > 0) {
            RecognitionIdentityHistoryIntegration.recordOccurrences(
                    data,
                    RecognitionIdentityHistoryIntegration
                            .TrackedDeed
                            .RAID_VICTORY,
                    newRaidVictories,
                    getOverworldGameTime(player)
            );
        }
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
            ServerPlayer player,
            RecognitionData data,
            ResourceKey<Level> dimension
    ) {
        if (player == null
                || data == null
                || dimension == null) {
            return;
        }

        String milestoneId = "";

        if (Level.NETHER.equals(dimension)) {
            milestoneId = "minecraft:entered_nether";
        } else if (Level.END.equals(dimension)) {
            milestoneId = "minecraft:entered_end";
        }

        if (milestoneId.isBlank()) {
            return;
        }

        boolean newlyRecorded =
                data.addUniqueValue(
                        RecognitionStatKeys
                                .DISCOVERY_MILESTONES,
                        milestoneId
                );

        if (newlyRecorded) {
            RecognitionIdentityHistoryIntegration.record(
                    data,
                    RecognitionIdentityHistoryIntegration
                            .TrackedDeed
                            .DISCOVERY_MILESTONE,
                    getOverworldGameTime(player)
            );
        }
    }

    private static long getOverworldGameTime(
            ServerPlayer player
    ) {
        if (player == null) {
            return 0L;
        }

        if (player.getServer() != null) {
            return Math.max(
                    0L,
                    player.getServer()
                            .overworld()
                            .getGameTime()
            );
        }

        return Math.max(
                0L,
                player.level()
                        .getGameTime()
        );
    }

}
