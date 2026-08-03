package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.UiFinalPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class AddonOnboardingEvents {

    public static final int CURRENT_ONBOARDING_REVISION = 1;

    private static final String REVISION_KEY =
            "moostensuraaddon_onboarding_revision";

    private AddonOnboardingEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag persistentRoot = player.getPersistentData();
        CompoundTag persistedPlayerData = persistentRoot.getCompound(
                Player.PERSISTED_NBT_TAG
        );
        int seenRevision = persistedPlayerData.getInt(
                REVISION_KEY
        );

        if (!UiFinalPolicy.shouldShowOnboarding(
                seenRevision,
                CURRENT_ONBOARDING_REVISION
        )) {
            return;
        }

        persistedPlayerData.putInt(
                REVISION_KEY,
                CURRENT_ONBOARDING_REVISION
        );
        persistentRoot.put(
                Player.PERSISTED_NBT_TAG,
                persistedPlayerData
        );

        sendOnboardingMessage(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event
    ) {
        CompoundTag originalPersisted = event.getOriginal()
                .getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);

        if (!originalPersisted.contains(REVISION_KEY)) {
            return;
        }

        CompoundTag cloneRoot = event.getEntity().getPersistentData();
        CompoundTag clonePersisted = cloneRoot.getCompound(
                Player.PERSISTED_NBT_TAG
        );
        clonePersisted.putInt(
                REVISION_KEY,
                originalPersisted.getInt(REVISION_KEY)
        );
        cloneRoot.put(
                Player.PERSISTED_NBT_TAG,
                clonePersisted
        );
    }

    private static void sendOnboardingMessage(
            ServerPlayer player
    ) {
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(
                Component.translatable(
                                "message.moostensuraaddon.onboarding.header"
                        )
                        .withStyle(
                                ChatFormatting.GOLD,
                                ChatFormatting.BOLD
                        )
        );
        player.sendSystemMessage(
                Component.translatable(
                        "message.moostensuraaddon.onboarding.first_open"
                )
        );
        player.sendSystemMessage(
                Component.translatable(
                                "message.moostensuraaddon.onboarding.helpful_commands"
                        )
                        .withStyle(ChatFormatting.GRAY)
        );
        player.sendSystemMessage(
                commandLine(
                        "/moostensura guide",
                        "message.moostensuraaddon.onboarding.guide_hover"
                )
        );
        player.sendSystemMessage(
                commandLine(
                        "/moostensura paths",
                        "message.moostensuraaddon.onboarding.paths_hover"
                )
        );
        player.sendSystemMessage(
                commandLine(
                        "/moostensura help",
                        "message.moostensuraaddon.onboarding.help_hover"
                )
        );
    }

    private static MutableComponent commandLine(
            String command,
            String hoverTranslationKey
    ) {
        MutableComponent commandComponent = Component.literal(command)
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
                                                hoverTranslationKey
                                        )
                                )
                        ));

        return Component.literal(" • ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(commandComponent);
    }
}