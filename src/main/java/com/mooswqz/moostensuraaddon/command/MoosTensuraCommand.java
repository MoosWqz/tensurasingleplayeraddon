package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.BorrowedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class MoosTensuraCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moostensura")
                .requires(source -> source.hasPermission(0))
                .executes(context -> sendHelp(context.getSource()))

                .then(Commands.literal("guide")
                        .executes(context -> sendGuide(context.getSource()))
                        .then(Commands.literal("sage")
                                .executes(context -> sendSageGuide(context.getSource()))
                        )
                )

                .then(Commands.literal("help")
                        .executes(context -> sendHelp(context.getSource())))

                .then(Commands.literal("resetconfig")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("confirm")
                                .executes(context -> resetConfig(context.getSource()))
                        )
                )

                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("confirm")
                                        .executes(context -> resetAddonData(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")
                                        )))
                        )
                )
        );
    }

    private static int sendGuide(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Moos Tensura Addon Guide")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);

        source.sendSuccess(() -> Component.literal("1. Become named or endowed to anchor your soul.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("2. Obtain Sage and listen for the pull of crystallized soul data.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("3. Craft a Soul Resonator and attune it to a Great Crystal Shrine.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("4. Follow the resonator needle, meet the altar requirements, and begin the ritual.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("5. Awaken Great Sage, then continue mastering skills and gathering subordinates.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("6. If your authority matures, the Unique Skill Granter may awaken.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("7. Granter can bestow mastered skills, reclaim what was granted, and eventually evolve into an Ultimate Skill path.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("Use /moostensura guide sage for detailed Sage -> Great Sage guidance.")
                .withStyle(ChatFormatting.DARK_AQUA), false);

        source.sendSuccess(() -> Component.literal("Hint: Some awakenings do not reveal themselves through exact numbers. Experiment, grow, and guide your subordinates.")
                .withStyle(ChatFormatting.DARK_AQUA), false);

        return 1;
    }

    private static int sendSageGuide(CommandSourceStack source) {
        int requiredXpLevel = MoosTensuraConfig.SAGE_UPGRADE_REQUIRED_XP_LEVEL.get();
        int relativeLevelDeduction = MoosTensuraConfig.SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION.get();
        double requiredEp = MoosTensuraConfig.GREAT_SAGE_RITUAL_REQUIRED_EP.get();
        int requiredMasteredSkills = MoosTensuraConfig.GREAT_SAGE_RITUAL_REQUIRED_MASTERED_SKILLS.get();
        boolean requireNamed = MoosTensuraConfig.GREAT_SAGE_RITUAL_REQUIRE_NAMED.get();
        int ritualDurationTicks = MoosTensuraConfig.GREAT_SAGE_RITUAL_DURATION_TICKS.get();
        int ritualDurationSeconds = Math.max(1, ritualDurationTicks / 20);

        source.sendSuccess(() -> Component.literal("Sage Progression")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);

        source.sendSuccess(() -> Component.literal("Goal: evolve Sage into Great Sage through a Great Crystal Altar ritual.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("1. Obtain Sage.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("2. Craft a Soul Resonator:")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("   Corners: medium quality magic crystals")
                .withStyle(ChatFormatting.DARK_AQUA), false);

        source.sendSuccess(() -> Component.literal("   Edges: magic stones")
                .withStyle(ChatFormatting.DARK_AQUA), false);

        source.sendSuccess(() -> Component.literal("   Center: compass")
                .withStyle(ChatFormatting.DARK_AQUA), false);

        source.sendSuccess(() -> Component.literal("3. Right-click the Soul Resonator while you have Sage.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("4. If a shrine answers, follow the needle to the Great Crystal Shrine.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("5. Meet the ritual requirements and interact with the Great Crystal Altar.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("6. Endure the ritual while Sage analyzes the altar, deciphers its data, and petitions for evolution.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("7. If the ritual completes, Sage awakens into Great Sage.")
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("Current ritual requirements:")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal("   XP level: " + requiredXpLevel)
                .withStyle(ChatFormatting.YELLOW), false);

        source.sendSuccess(() -> Component.literal("   XP cost on success: " + relativeLevelDeduction + " relative levels")
                .withStyle(ChatFormatting.YELLOW), false);

        source.sendSuccess(() -> Component.literal("   EP: " + formatWholeNumber(requiredEp))
                .withStyle(ChatFormatting.YELLOW), false);

        source.sendSuccess(() -> Component.literal("   Mastered skills: " + requiredMasteredSkills)
                .withStyle(ChatFormatting.YELLOW), false);

        source.sendSuccess(() -> Component.literal("   Named/endowed required: " + (requireNamed ? "yes" : "no"))
                .withStyle(ChatFormatting.YELLOW), false);

        source.sendSuccess(() -> Component.literal("   Ritual duration: about " + ritualDurationSeconds + " seconds")
                .withStyle(ChatFormatting.YELLOW), false);

        source.sendSuccess(() -> Component.literal("Tip: /upgradesage is admin-only. Normal progression happens through the shrine ritual.")
                .withStyle(ChatFormatting.DARK_AQUA), false);

        return 1;
    }

    private static int sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Moos Tensura Addon Commands")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);

        source.sendSuccess(() -> Component.literal("/moostensura guide")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - Shows the intended progression path without exact spoilers.")
                        .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("/moostensura guide sage")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - Shows Sage -> Great Sage progression guidance.")
                        .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("/moostensura help")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - Shows this command list.")
                        .withStyle(ChatFormatting.GRAY)), false);

        source.sendSuccess(() -> Component.literal("/getnamed")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - Attempts self-endowment for singleplayer progression.")
                        .withStyle(ChatFormatting.GRAY)), false);

        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.literal("/upgradesage")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - Admin-only. Directly attempts Sage -> Great Sage using normal requirements.")
                            .withStyle(ChatFormatting.GRAY)), false);

            source.sendSuccess(() -> Component.literal("/upgradesage forceupgrade confirm")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - Admin-only. Forces Great Sage for testing.")
                            .withStyle(ChatFormatting.GRAY)), false);

            source.sendSuccess(() -> Component.literal("/moostensura resetconfig confirm")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - Admin-only. Resets this addon's config defaults.")
                            .withStyle(ChatFormatting.GRAY)), false);

            source.sendSuccess(() -> Component.literal("/moostensura reset <player> confirm")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - Admin-only. Resets this addon's stored data for that player.")
                            .withStyle(ChatFormatting.GRAY)), false);

            source.sendSuccess(() -> Component.literal("/checknamed")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - Admin debug command for named status and Granter requirements.")
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        source.sendSuccess(() -> Component.literal("Tip: Use /moostensura guide if you want progression hints instead of command details.")
                .withStyle(ChatFormatting.DARK_AQUA), false);

        return 1;
    }

    private static int resetConfig(CommandSourceStack source) {
        MoosTensuraConfig.resetToAddonDefaults();

        source.sendSuccess(() -> Component.literal("Moos Tensura Addon config was reset to addon defaults.")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    private static int resetAddonData(CommandSourceStack source, ServerPlayer target) {
        if (target == null) {
            source.sendFailure(Component.literal("Target player could not be found.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        target.setData(AttachmentRegistry.GRANTED_SKILL_DATA, new GrantedSkillData());
        target.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, new GranterProgressData());
        target.setData(AttachmentRegistry.BORROWED_SKILL_DATA, new BorrowedSkillData());

        MoosTensuraConfig.resetToAddonDefaults();

        String targetName = target.getGameProfile().getName();

        source.sendSuccess(() -> Component.literal("Reset Moos Tensura Addon data for " + targetName + " and restored addon config defaults.")
                .withStyle(ChatFormatting.GREEN), true);

        target.sendSystemMessage(Component.literal("Your Moos Tensura Addon data was reset by an administrator.")
                .withStyle(ChatFormatting.YELLOW));

        return 1;
    }

    private static String formatWholeNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }
}