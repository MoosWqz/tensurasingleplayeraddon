package com.mooswqz.moostensuraaddon.ritual;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.block.BlockRegistry;
import com.mooswqz.moostensuraaddon.block.GreatCrystalAltarBlock;
import com.mooswqz.moostensuraaddon.lifecycle.RecognitionNativeEndowmentService;
import com.mooswqz.moostensuraaddon.recognition.*;
import com.mooswqz.moostensuraaddon.util.AddonAdvancementHelper;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

public final class RecognitionNamingRitualManager {

    private static final Map<UUID, RitualState>
            ACTIVE_RITUALS =
            new HashMap<>();

    private static final int RITUAL_DURATION_TICKS =
            20 * 15;

    private static final int DARKNESS_REFRESH_INTERVAL_TICKS =
            20;

    private static final Component[] PHASE_MESSAGES =
            new Component[]{
                    Component.literal(
                            "The crystal reaches toward the shape of your soul..."
                    ),
                    Component.literal(
                            "Your deeds gather into a single answer..."
                    ),
                    Component.literal(
                            "The answer settles beyond the reach of change..."
                    ),
                    Component.literal(
                            "A name begins to anchor itself to your existence..."
                    ),
                    Component.literal(
                            "The world prepares to bear witness..."
                    )
            };

    private RecognitionNamingRitualManager() {
    }

    /**
     * Returns true while the altar should treat the player as a naming
     * candidate rather than forwarding them to the Great Sage ritual.
     */
    public static boolean shouldHandleNaming(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        if (hasPendingReveal(player)) {
            return true;
        }

        return !data.isNamingCommitted();
    }

    /**
     * A committed reveal is resumed before either normal altar flow. This
     * preserves the already-fixed result across updates and interruptions.
     */
    public static boolean hasPendingReveal(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        return data.isNamingCommitted()
                && data.getFlag(
                RecognitionStatKeys.REVEAL_PENDING
        );
    }

    public static void tryStartRitual(
            ServerPlayer player,
            BlockPos altarPos
    ) {
        if (player == null || altarPos == null) {
            return;
        }

        if (!(player.level()
                instanceof ServerLevel serverLevel)) {
            return;
        }

        if (isRitualActive(player)) {
            player.displayClientMessage(
                    Component.literal(
                                    "The crystal is already reading your soul."
                            )
                            .withStyle(ChatFormatting.AQUA),
                    true
            );

            return;
        }

        if (GreatSageRitualManager.isRitualActive(
                player
        )) {
            player.displayClientMessage(
                    Component.literal(
                                    "Another altar ritual is already in progress."
                            )
                            .withStyle(ChatFormatting.RED),
                    true
            );

            return;
        }

        BlockPos lowerAltarPos =
                getLowerAltarPos(
                        serverLevel,
                        altarPos
                );

        if (lowerAltarPos == null
                || !isValidAltar(
                serverLevel,
                lowerAltarPos
        )) {
            player.sendSystemMessage(
                    Component.literal(
                                    "The altar structure is incomplete."
                            )
                            .withStyle(ChatFormatting.RED)
            );

            return;
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        CommitmentResult commitment =
                resolveOrCommitResult(
                        player,
                        data
                );

        if (!commitment.successful()) {
            return;
        }

        /*
         * The path and title were committed before the runtime ritual state
         * is created.
         *
         * An interruption can therefore stop the presentation, but it cannot
         * cause another recognition result to be generated.
         */
        RitualState state =
                new RitualState(
                        serverLevel.dimension(),
                        lowerAltarPos,
                        player.position(),
                        RITUAL_DURATION_TICKS,
                        player.getYRot(),
                        commitment.primaryPathId(),
                        commitment.secondaryPathId(),
                        commitment.pure(),
                        commitment.bestowedTitle()
                );

        ACTIVE_RITUALS.put(
                player.getUUID(),
                state
        );

        player.displayClientMessage(
                Component.literal(
                                commitment.resumed()
                                        ? "The crystal resumes its unfinished recognition."
                                        : "The Great Crystal Altar begins to judge your existence."
                        )
                        .withStyle(ChatFormatting.AQUA),
                false
        );

        sendPhaseMessage(
                player,
                state,
                0
        );

        playPhaseSound(
                serverLevel,
                lowerAltarPos,
                0
        );
    }

    public static void tick(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        RitualState state =
                ACTIVE_RITUALS.get(
                        player.getUUID()
                );

        if (state == null) {
            return;
        }

        if (!(player.level()
                instanceof ServerLevel serverLevel)) {
            cancelRitual(player);
            return;
        }

        if (!player.level()
                .dimension()
                .equals(state.dimension)) {

            cancelRitual(player);
            return;
        }

        if (!player.isAlive()) {
            cancelRitual(player);
            return;
        }

        if (!isValidAltar(
                serverLevel,
                state.altarPos
        )) {
            cancelRitual(player);
            return;
        }

        applyRitualStasis(
                player,
                state
        );

        spawnRitualParticles(
                serverLevel,
                player,
                state
        );

        int phase =
                getPhase(state);

        if (phase != state.lastPhase) {
            sendPhaseMessage(
                    player,
                    state,
                    phase
            );

            playPhaseSound(
                    serverLevel,
                    state.altarPos,
                    phase
            );

            state.lastPhase = phase;
        }

        state.elapsedTicks++;

        if (state.elapsedTicks
                >= state.durationTicks) {

            completeRitual(
                    player,
                    serverLevel,
                    state
            );
        }
    }

    public static boolean isRitualActive(
            ServerPlayer player
    ) {
        return player != null
                && ACTIVE_RITUALS.containsKey(
                player.getUUID()
        );
    }

    public static void cancelForLogout(
            ServerPlayer player
    ) {
        cancelRitual(player);
    }

    public static void cancelForDeath(
            ServerPlayer player
    ) {
        cancelRitual(player);
    }

    public static void cancelRitual(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        RitualState removed =
                ACTIVE_RITUALS.remove(
                        player.getUUID()
                );

        if (removed == null) {
            return;
        }

        clearRitualPose(player);

        player.displayClientMessage(
                Component.literal(
                                "The ritual was interrupted, but its answer remains fixed."
                        )
                        .withStyle(ChatFormatting.GRAY),
                true
        );
    }

    private static CommitmentResult resolveOrCommitResult(
            ServerPlayer player,
            RecognitionData data
    ) {
        boolean alreadyCommitted =
                data.getFlag(
                        RecognitionStatKeys.NAMING_COMMITTED
                );

        if (alreadyCommitted) {
            boolean revealPending =
                    data.getFlag(
                            RecognitionStatKeys.REVEAL_PENDING
                    );

            String primaryPathId =
                    safeString(
                            data.getString(
                                    RecognitionStatKeys.PRIMARY_PATH
                            )
                    );

            String secondaryPathId =
                    safeString(
                            data.getString(
                                    RecognitionStatKeys.SECONDARY_PATH
                            )
                    );

            String bestowedTitle =
                    safeString(
                            data.getString(
                                    RecognitionStatKeys.BESTOWED_TITLE
                            )
                    );

            boolean pure =
                    data.getFlag(
                            RecognitionStatKeys.PURE_RECOGNITION
                    );

            if (!revealPending) {
                player.sendSystemMessage(
                        Component.literal(
                                        "Your recognition has already been presented."
                                )
                                .withStyle(ChatFormatting.GRAY)
                );

                return CommitmentResult.failed();
            }

            if (primaryPathId.isBlank()
                    || bestowedTitle.isBlank()) {

                player.sendSystemMessage(
                        Component.literal(
                                        "The committed recognition record is incomplete. Use the admin unname command before testing again."
                                )
                                .withStyle(ChatFormatting.RED)
                );

                return CommitmentResult.failed();
            }

            return CommitmentResult.success(
                    true,
                    primaryPathId,
                    secondaryPathId,
                    pure,
                    bestowedTitle
            );
        }

        RecognitionNamingEligibility eligibility =
                RecognitionNamingService.evaluate(
                        player
                );

        if (!eligibility.eligible()) {
            sendEligibilityFailure(
                    player,
                    eligibility
            );

            return CommitmentResult.failed();
        }

        RecognitionNamingCandidate candidate =
                eligibility.candidate();

        if (candidate == null) {
            player.sendSystemMessage(
                    Component.literal(
                                    "The crystal cannot form a stable answer from your deeds."
                            )
                            .withStyle(ChatFormatting.RED)
            );

            return CommitmentResult.failed();
        }

        /*
         * Atomic commitment point.
         *
         * Use the authoritative service so the full frozen result, reward
         * profile, identity-strength snapshot and incarnation metadata are
         * written before the visual ritual begins.
         */
        if (!RecognitionNamingService.commitRecognition(
                player,
                eligibility
        )) {
            player.sendSystemMessage(
                    Component.literal(
                                    "The crystal could not preserve its answer. Try the altar again."
                            )
                            .withStyle(ChatFormatting.RED)
            );

            return CommitmentResult.failed();
        }

        String primaryPathId =
                candidate.primaryPath()
                        .getId();

        String secondaryPathId =
                candidate.hasSecondaryPath()
                        ? candidate.secondaryPath()
                        .getId()
                        : "";

        String bestowedTitle =
                candidate.bestowedTitle();

        RecognitionDisplayNameSyncService.refreshAndBroadcast(player);

        return CommitmentResult.success(
                false,
                primaryPathId,
                secondaryPathId,
                candidate.pure(),
                bestowedTitle
        );
    }

    private static void sendEligibilityFailure(
            ServerPlayer player,
            RecognitionNamingEligibility eligibility
    ) {
        MutableComponent message =
                switch (eligibility.status()) {
                    case NOT_ENOUGH_LEVEL ->
                            Component.translatable(
                                    "message.moostensuraaddon.recognition.eligibility.not_enough_level",
                                    eligibility.requiredLevel()
                            );

                    case NO_RECOGNITION_SELECTION ->
                            Component.literal(
                                    "Your deeds have not yet formed a stable recognition."
                            );

                    case ALREADY_NAMED ->
                            Component.literal(
                                    "Your existence is already anchored by a name."
                            );

                    case ALREADY_COMMITTED ->
                            Component.literal(
                                    "The altar remembers an answer that has already been fixed."
                            );

                    case READY ->
                            Component.literal(
                                    "The crystal hesitates despite recognizing your readiness."
                            );
                };

        ChatFormatting messageColor =
                eligibility.status()
                        == RecognitionNamingEligibility.Status
                        .NO_RECOGNITION_SELECTION
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.RED;

        player.sendSystemMessage(
                message.withStyle(messageColor)
        );
    }

    private static void completeRitual(
            ServerPlayer player,
            ServerLevel level,
            RitualState state
    ) {
        ACTIVE_RITUALS.remove(
                player.getUUID()
        );

        clearRitualPose(player);

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        data.setFlag(
                RecognitionStatKeys.REVEAL_PENDING,
                false
        );

        player.setData(
                AttachmentRegistry.RECOGNITION_DATA,
                data
        );

        /*
         * The reveal guard is cleared only at the presentation boundary.
         * Periodic lifecycle reconciliation therefore cannot publish the
         * frozen title during the 15-second ritual or after an interruption.
         *
         * The lifecycle service owns the exactly-once native HIGH endowment.
         * If a compatible native name already exists, it updates the stored
         * and custom names without sending another HIGH request.
         */
        RecognitionNativeEndowmentService.synchronize(
                player
        );

        if (!isNativeIdentityPublished(
                player,
                data
        )) {
            /*
             * Restore the pending guard if Tensura rejected the native naming
             * operation. The same committed result can then be presented
             * again without a reroll.
             */
            data.setFlag(
                    RecognitionStatKeys.REVEAL_PENDING,
                    true
            );

            player.setData(
                    AttachmentRegistry.RECOGNITION_DATA,
                    data
            );

            player.sendSystemMessage(
                    Component.literal(
                                    "The recognition was fixed, but Tensura did not accept the complete native name. Interact with the altar to try presenting the same result again."
                            )
                            .withStyle(ChatFormatting.RED)
            );

            return;
        }

        /*
         * Refresh after the native identity is coherent so chat, tab-list,
         * client nametag and Tensura menu consumers publish the same frozen
         * name at the completed reveal boundary.
         */
        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(
                        player
                );

        spawnCompletionParticles(
                level,
                player,
                state
        );

        playCompletionSounds(
                level,
                state.altarPos
        );

        AddonAdvancementHelper.awardNameAnchor(
                player
        );

        AddonAdvancementHelper
                .awardStateBasedAdvancements(
                        player
                );

        String username =
                player.getGameProfile()
                        .getName();

        String displayTitle =
                state.bestowedTitle.isBlank()
                        ? username
                        : username
                          + " "
                          + state.bestowedTitle;

        player.sendSystemMessage(
                Component.literal(
                                "Your soul has been recognized."
                        )
                        .withStyle(
                                ChatFormatting.GOLD,
                                ChatFormatting.BOLD
                        )
        );

        player.sendSystemMessage(
                Component.literal(displayTitle)
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        )
        );

        String pathDescription =
                state.pure
                        ? "Pure "
                          + state.primaryPathId
                        : state.secondaryPathId.isBlank()
                          ? state.primaryPathId
                          : state.primaryPathId
                            + " + "
                            + state.secondaryPathId;

        player.sendSystemMessage(
                Component.literal(
                                "Recognition: "
                                        + pathDescription
                        )
                        .withStyle(ChatFormatting.AQUA)
        );

        Component globalMessage =
                Component.literal(
                                username
                                        + " has been recognized as "
                                        + displayTitle
                                        + "."
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        );

        level.getServer()
                .getPlayerList()
                .broadcastSystemMessage(
                        globalMessage,
                        false
                );

        /*
         * The announcement sound is sent to every online player, regardless
         * of dimension.
         */
        for (ServerPlayer onlinePlayer :
                level.getServer()
                        .getPlayerList()
                        .getPlayers()) {

            onlinePlayer.playNotifySound(
                    SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS,
                    0.85F,
                    1.15F
            );

            onlinePlayer.playNotifySound(
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.65F,
                    1.45F
            );
        }
    }

    private static void applyRitualStasis(
            ServerPlayer player,
            RitualState state
    ) {
        Vec3 position =
                state.stasisPos;

        player.teleportTo(
                position.x,
                position.y,
                position.z
        );

        player.setDeltaMovement(
                Vec3.ZERO
        );

        player.resetFallDistance();
        player.setSprinting(false);

        player.setYRot(
                state.lockedYaw
        );

        player.setYHeadRot(
                state.lockedYaw
        );

        player.setYBodyRot(
                state.lockedYaw
        );

        player.setXRot(0.0F);
        player.setPose(Pose.SLEEPING);

        player.addEffect(
                new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        40,
                        255,
                        false,
                        false,
                        false
                )
        );

        player.addEffect(
                new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        40,
                        255,
                        false,
                        false,
                        false
                )
        );

        player.addEffect(
                new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE,
                        40,
                        255,
                        false,
                        false,
                        false
                )
        );

        if (state.elapsedTicks
                % DARKNESS_REFRESH_INTERVAL_TICKS
                == 0) {

            player.addEffect(
                    new MobEffectInstance(
                            MobEffects.DARKNESS,
                            80,
                            0,
                            false,
                            false,
                            true
                    )
            );
        }
    }

    private static void clearRitualPose(
            ServerPlayer player
    ) {
        player.setPose(Pose.STANDING);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    private static void spawnRitualParticles(
            ServerLevel level,
            ServerPlayer player,
            RitualState state
    ) {
        double progress =
                Math.min(
                        1.0D,
                        (double) state.elapsedTicks
                                / Math.max(
                                1,
                                state.durationTicks
                        )
                );

        double playerX =
                player.getX();

        double playerY =
                player.getY() + 0.25D;

        double playerZ =
                player.getZ();

        double altarX =
                state.altarPos.getX()
                        + 0.5D;

        double altarY =
                state.altarPos.getY()
                        + 1.2D;

        double altarZ =
                state.altarPos.getZ()
                        + 0.5D;

        int soulCount =
                progress < 0.5D
                        ? 4
                        : 7;

        int flameCount =
                progress < 0.5D
                        ? 2
                        : 5;

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
                    10,
                    0.65D,
                    0.35D,
                    0.65D,
                    0.02D
            );
        }
    }

    private static void spawnCompletionParticles(
            ServerLevel level,
            ServerPlayer player,
            RitualState state
    ) {
        double playerX =
                player.getX();

        double playerY =
                player.getY() + 0.75D;

        double playerZ =
                player.getZ();

        double altarX =
                state.altarPos.getX()
                        + 0.5D;

        double altarY =
                state.altarPos.getY()
                        + 1.4D;

        double altarZ =
                state.altarPos.getZ()
                        + 0.5D;

        level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                playerX,
                playerY,
                playerZ,
                64,
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
                48,
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
                48,
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
                36,
                0.8D,
                0.7D,
                0.8D,
                0.04D
        );
    }

    private static int getPhase(
            RitualState state
    ) {
        int phaseCount =
                PHASE_MESSAGES.length;

        int phase =
                (int) (
                        (long) state.elapsedTicks
                                * phaseCount
                                / Math.max(
                                1,
                                state.durationTicks
                        )
                );

        if (phase < 0) {
            return 0;
        }

        return Math.min(
                phase,
                phaseCount - 1
        );
    }

    private static void sendPhaseMessage(
            ServerPlayer player,
            RitualState state,
            int phase
    ) {
        if (phase < 0
                || phase >= PHASE_MESSAGES.length) {
            return;
        }

        player.displayClientMessage(
                PHASE_MESSAGES[phase]
                        .copy()
                        .withStyle(ChatFormatting.AQUA),
                true
        );
    }

    private static void playPhaseSound(
            ServerLevel level,
            BlockPos altarPos,
            int phase
    ) {
        float pitch =
                0.75F
                        + phase * 0.13F;

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
                    0.95F
            );
        }
    }

    private static void playCompletionSounds(
            ServerLevel level,
            BlockPos altarPos
    ) {
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

    private static boolean isNativeIdentityPublished(
            ServerPlayer player,
            RecognitionData data
    ) {
        if (player == null || data == null) {
            return false;
        }

        IExistence existence =
                TensuraStorages.getExistenceFrom(
                        player
                );

        if (existence == null) {
            return false;
        }

        String nativeName =
                existence.getName();

        String expectedName =
                RecognitionDisplayNameService
                        .buildNativeTensuraName(
                                player.getGameProfile()
                                        .getName(),
                                data.getString(
                                        RecognitionStatKeys.BESTOWED_TITLE
                                )
                        );

        Component customName =
                player.getCustomName();

        String storedNativeName =
                nativeName == null
                        ? ""
                        : nativeName.trim();

        String storedCustomName =
                customName == null
                        ? ""
                        : customName.getString().trim();

        return !expectedName.isBlank()
                && storedNativeName.equals(expectedName)
                && storedCustomName.equals(expectedName);
    }

    private static BlockPos getLowerAltarPos(
            ServerLevel level,
            BlockPos position
    ) {
        BlockState state =
                level.getBlockState(
                        position
                );

        if (!state.is(
                BlockRegistry.GREAT_CRYSTAL_ALTAR.get()
        )) {
            return null;
        }

        if (!state.hasProperty(
                GreatCrystalAltarBlock.HALF
        )) {
            return null;
        }

        if (state.getValue(
                GreatCrystalAltarBlock.HALF
        ) == DoubleBlockHalf.UPPER) {

            return position.below();
        }

        return position;
    }

    private static boolean isValidAltar(
            ServerLevel level,
            BlockPos lowerPosition
    ) {
        BlockState lowerState =
                level.getBlockState(
                        lowerPosition
                );

        BlockState upperState =
                level.getBlockState(
                        lowerPosition.above()
                );

        if (!lowerState.is(
                BlockRegistry.GREAT_CRYSTAL_ALTAR.get()
        )) {
            return false;
        }

        if (!upperState.is(
                BlockRegistry.GREAT_CRYSTAL_ALTAR.get()
        )) {
            return false;
        }

        if (!lowerState.hasProperty(
                GreatCrystalAltarBlock.HALF
        )) {
            return false;
        }

        if (!upperState.hasProperty(
                GreatCrystalAltarBlock.HALF
        )) {
            return false;
        }

        return lowerState.getValue(
                GreatCrystalAltarBlock.HALF
        ) == DoubleBlockHalf.LOWER
                && upperState.getValue(
                GreatCrystalAltarBlock.HALF
        ) == DoubleBlockHalf.UPPER;
    }

    private static String safeString(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private record CommitmentResult(
            boolean successful,
            boolean resumed,
            String primaryPathId,
            String secondaryPathId,
            boolean pure,
            String bestowedTitle
    ) {

        private CommitmentResult {
            primaryPathId =
                    safeString(primaryPathId);

            secondaryPathId =
                    safeString(secondaryPathId);

            bestowedTitle =
                    safeString(bestowedTitle);
        }

        private static CommitmentResult success(
                boolean resumed,
                String primaryPathId,
                String secondaryPathId,
                boolean pure,
                String bestowedTitle
        ) {
            return new CommitmentResult(
                    true,
                    resumed,
                    primaryPathId,
                    secondaryPathId,
                    pure,
                    bestowedTitle
            );
        }

        private static CommitmentResult failed() {
            return new CommitmentResult(
                    false,
                    false,
                    "",
                    "",
                    false,
                    ""
            );
        }
    }

    private static final class RitualState {

        private final ResourceKey<Level> dimension;
        private final BlockPos altarPos;
        private final Vec3 stasisPos;
        private final int durationTicks;
        private final float lockedYaw;

        private final String primaryPathId;
        private final String secondaryPathId;
        private final boolean pure;
        private final String bestowedTitle;

        private int elapsedTicks;
        private int lastPhase;

        private RitualState(
                ResourceKey<Level> dimension,
                BlockPos altarPos,
                Vec3 stasisPos,
                int durationTicks,
                float lockedYaw,
                String primaryPathId,
                String secondaryPathId,
                boolean pure,
                String bestowedTitle
        ) {
            this.dimension = dimension;
            this.altarPos =
                    altarPos.immutable();

            this.stasisPos = stasisPos;
            this.durationTicks =
                    durationTicks;

            this.lockedYaw =
                    lockedYaw;

            this.primaryPathId =
                    safeString(primaryPathId);

            this.secondaryPathId =
                    safeString(secondaryPathId);

            this.pure = pure;

            this.bestowedTitle =
                    safeString(bestowedTitle);

            this.elapsedTicks = 0;
            this.lastPhase = -1;
        }
    }
}
