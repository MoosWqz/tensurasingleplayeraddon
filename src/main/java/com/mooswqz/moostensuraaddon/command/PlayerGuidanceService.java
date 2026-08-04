package com.mooswqz.moostensuraaddon.command;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import com.mooswqz.moostensuraaddon.util.GranterActions;
import com.mooswqz.moostensuraaddon.util.GreatSageAwakeningHelper;
import com.mooswqz.moostensuraaddon.util.TensuraPlayerStateHelper;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds a compact guide from the player's authoritative progression state.
 */
public final class PlayerGuidanceService {

    private PlayerGuidanceService() {
    }

    public static int sendGuide(
            CommandSourceStack source,
            ServerPlayer player
    ) {
        if (source == null || player == null) {
            return 0;
        }

        RecognitionData recognition = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );
        GranterProgressData granterProgress = player.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );

        boolean recognitionCommitted = recognition.isNamingCommitted();
        boolean namedOrRecognized = recognitionCommitted
                || TensuraPlayerStateHelper.isNamedOrEndowed(player);
        boolean hasSage = GreatSageAwakeningHelper
                .findSage(player)
                .isPresent();
        boolean hasGreatSage = GranterActions.hasGreatSage(player);
        boolean hasGranter = hasSkill(
                player,
                SkillRegistry.GRANTER.get().getRegistryName()
        );
        boolean hasBenevolent = hasSkill(
                player,
                SkillRegistry.BENEVOLENT_EMPOWERMENT
                        .get()
                        .getRegistryName()
        );
        boolean hasGovernance = hasSkill(
                player,
                SkillRegistry.ABSOLUTE_GOVERNANCE
                        .get()
                        .getRegistryName()
        );

        PlayerGuidancePolicy.Stage stage = PlayerGuidancePolicy.resolve(
                AddonIncarnationState.isResetGuardActive(player),
                namedOrRecognized,
                hasSage,
                hasGreatSage,
                hasGranter,
                hasBenevolent,
                hasGovernance
        );

        source.sendSuccess(
                () -> Component.translatable(
                                "message.moostensuraaddon.guide.header"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.translatable(
                                "message.moostensuraaddon.guide.stage."
                                        + stage.id()
                                        + ".title"
                        )
                        .withStyle(ChatFormatting.GOLD),
                false
        );

        source.sendSuccess(
                () -> Component.translatable(
                                "message.moostensuraaddon.guide.stage."
                                        + stage.id()
                                        + ".detail"
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        if (stage == PlayerGuidancePolicy.Stage.AWAKEN_GREAT_SAGE) {
            sendCommandLink(
                    source,
                    "message.moostensuraaddon.guide.link.sage",
                    "/moostensura guide sage",
                    "message.moostensuraaddon.guide.link.sage.hover"
            );
        }

        if (recognitionCommitted) {
            source.sendSuccess(
                    () -> Component.translatable(
                                    "message.moostensuraaddon.guide.recognition.committed"
                            )
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    false
            );
        } else {
            source.sendSuccess(
                    () -> Component.translatable(
                                    "message.moostensuraaddon.guide.recognition.developing"
                            )
                            .withStyle(ChatFormatting.DARK_AQUA),
                    false
            );
        }

        sendCommandLink(
                source,
                "message.moostensuraaddon.guide.link.paths",
                "/moostensura paths",
                "message.moostensuraaddon.guide.link.paths.hover"
        );

        if (hasGranter || hasBenevolent || hasGovernance) {
            int grants = Math.max(
                    0,
                    granterProgress.getSuccessfulGrants()
            );
            int subordinates = Math.max(
                    0,
                    granterProgress.getRecognizedSubordinateCount()
            );

            source.sendSuccess(
                    () -> Component.translatable(
                                    "message.moostensuraaddon.guide.authority_progress",
                                    grants,
                                    subordinates
                            )
                            .withStyle(ChatFormatting.DARK_AQUA),
                    false
            );
        }

        source.sendSuccess(
                () -> Component.translatable(
                                "message.moostensuraaddon.guide.help_hint"
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        return 1;
    }

    public static int sendHelp(
            CommandSourceStack source
    ) {
        if (source == null) {
            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable(
                                "message.moostensuraaddon.help.header"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );

        sendHelpLine(
                source,
                "/moostensura guide",
                "message.moostensuraaddon.help.guide",
                ChatFormatting.AQUA
        );
        sendHelpLine(
                source,
                "/moostensura paths",
                "message.moostensuraaddon.help.paths",
                ChatFormatting.AQUA
        );
        sendHelpLine(
                source,
                "/moostensura help",
                "message.moostensuraaddon.help.help",
                ChatFormatting.AQUA
        );

        if (source.hasPermission(2)) {
            source.sendSuccess(
                    () -> Component.translatable(
                                    "message.moostensuraaddon.help.admin_header"
                            )
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
            sendHelpLine(
                    source,
                    "/moostensura lifecycle [player]",
                    "message.moostensuraaddon.help.lifecycle",
                    ChatFormatting.YELLOW
            );
            sendHelpLine(
                    source,
                    "/moostensura unname",
                    "message.moostensuraaddon.help.unname",
                    ChatFormatting.RED
            );
            sendHelpLine(
                    source,
                    "/moostensura resetconfig",
                    "message.moostensuraaddon.help.resetconfig",
                    ChatFormatting.RED
            );
            sendHelpLine(
                    source,
                    "/moostensura reset <player>",
                    "message.moostensuraaddon.help.reset",
                    ChatFormatting.RED
            );
            sendHelpLine(
                    source,
                    "/moostensura debug status",
                    "message.moostensuraaddon.help.debug_status",
                    ChatFormatting.YELLOW
            );
        }

        if (source.hasPermission(
                DebugModeService.DANGEROUS_DEBUG_PERMISSION_LEVEL
        )) {
            sendHelpLine(
                    source,
                    "/moostensura debug enable",
                    "message.moostensuraaddon.help.debug_enable",
                    ChatFormatting.YELLOW
            );
            sendHelpLine(
                    source,
                    "/moostensura debug disable",
                    "message.moostensuraaddon.help.debug_disable",
                    ChatFormatting.YELLOW
            );
        }

        return 1;
    }

    private static boolean hasSkill(
            ServerPlayer player,
            net.minecraft.resources.ResourceLocation skillId
    ) {
        return player != null
                && skillId != null
                && SkillAPI.getSkillsFrom(player)
                .getSkill(skillId)
                .isPresent();
    }

    private static void sendCommandLink(
            CommandSourceStack source,
            String labelKey,
            String command,
            String hoverKey
    ) {
        MutableComponent link = Component.translatable(labelKey)
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
                                        Component.translatable(hoverKey)
                                )
                        )
                );

        source.sendSuccess(() -> link, false);
    }

    private static void sendHelpLine(
            CommandSourceStack source,
            String command,
            String descriptionKey,
            ChatFormatting commandColor
    ) {
        source.sendSuccess(
                () -> Component.literal(command)
                        .withStyle(commandColor)
                        .append(
                                Component.literal(" - ")
                                        .withStyle(ChatFormatting.GRAY)
                        )
                        .append(
                                Component.translatable(descriptionKey)
                                        .withStyle(ChatFormatting.GRAY)
                        ),
                false
        );
    }
}