package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Shows one concise benefits link after a recognized incarnation stabilizes.
 */
public final class RecognitionRewardNoticeService {

    private static final String ROOT_KEY =
            "moostensuraaddon_recognition_notice";
    private static final String SHOWN_IDENTITY_KEY =
            "shown_identity";
    private static final int CHECK_INTERVAL_TICKS = 40;

    private RecognitionRewardNoticeService() {
    }

    public static void tick(
            ServerPlayer player
    ) {
        if (player == null
                || player.level().isClientSide()
                || player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        tryShow(player);
    }

    public static boolean tryShow(
            ServerPlayer player
    ) {
        if (player == null
                || player.level().isClientSide()
                || AddonIncarnationState.isResetGuardActive(player)) {
            return false;
        }

        RecognitionData recognition = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        if (!recognition.isNamingCommitted()) {
            return false;
        }

        AddonIncarnationState.Snapshot lifecycle =
                AddonIncarnationState.inspect(player);
        String identity = RecognitionRewardNoticePolicy.createIdentity(
                recognition.getIncarnationId(),
                lifecycle.lifeToken(),
                recognition.getRewardProfileVersion()
        );
        String shownIdentity = getShownIdentity(player);

        if (!identity.isBlank()
                && identity.equals(shownIdentity)) {
            return false;
        }

        RecognitionStrengthRewardSnapshot reward =
                RecognitionStrengthRewardService.inspect(player);
        String recognitionIncarnation = recognition.getIncarnationId()
                .isBlank()
                ? lifecycle.lifeToken()
                : recognition.getIncarnationId();
        boolean nativeMarkerMatches = !recognitionIncarnation.isBlank()
                && recognitionIncarnation.equals(
                lifecycle.nativeEndowmentIncarnation()
        );

        if (!RecognitionRewardNoticePolicy.shouldShow(
                reward.recognitionCommitted(),
                reward.committedResultValid(),
                reward.rewardMetadataInitialized(),
                reward.futureProfilePreserved(),
                lifecycle.resetGuardActive(),
                nativeMarkerMatches,
                identity,
                shownIdentity
        )) {
            return false;
        }

        sendNotice(player);
        setShownIdentity(player, identity);
        return true;
    }

    public static void copyPersistentState(
            Player original,
            Player clone
    ) {
        if (original == null || clone == null) {
            return;
        }

        CompoundTag originalRoot = original.getPersistentData();
        CompoundTag originalPersisted = originalRoot.getCompound(
                Player.PERSISTED_NBT_TAG
        );

        if (!originalPersisted.contains(ROOT_KEY)) {
            return;
        }

        CompoundTag cloneRoot = clone.getPersistentData();
        CompoundTag clonePersisted = cloneRoot.getCompound(
                Player.PERSISTED_NBT_TAG
        );
        clonePersisted.put(
                ROOT_KEY,
                originalPersisted.getCompound(ROOT_KEY).copy()
        );
        cloneRoot.put(
                Player.PERSISTED_NBT_TAG,
                clonePersisted
        );
    }

    private static void sendNotice(
            ServerPlayer player
    ) {
        String command = "/moostensura paths";
        MutableComponent link = Component.translatable(
                        "message.moostensuraaddon.recognition_notice.link"
                )
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(
                                new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        command
                                )
                        )
                        .withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(
                                                "message.moostensuraaddon.recognition_notice.hover"
                                        )
                                )
                        )
                );

        player.sendSystemMessage(
                Component.translatable(
                                "message.moostensuraaddon.recognition_notice.text"
                        )
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(" "))
                        .append(link)
        );
    }

    private static String getShownIdentity(
            ServerPlayer player
    ) {
        CompoundTag persisted = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);

        if (!persisted.contains(ROOT_KEY)) {
            return "";
        }

        return persisted.getCompound(ROOT_KEY)
                .getString(SHOWN_IDENTITY_KEY)
                .trim();
    }

    private static void setShownIdentity(
            ServerPlayer player,
            String identity
    ) {
        if (player == null
                || identity == null
                || identity.isBlank()) {
            return;
        }

        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(
                Player.PERSISTED_NBT_TAG
        );
        CompoundTag notice = persisted.getCompound(ROOT_KEY);
        notice.putString(SHOWN_IDENTITY_KEY, identity.trim());
        persisted.put(ROOT_KEY, notice);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}