package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.network.OpenUltimateMultiGrantScreenPayload;
import com.mooswqz.moostensuraaddon.util.AuthorityActionMode;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UltimateMultiGrantUiEntryFactory {

    private UltimateMultiGrantUiEntryFactory() {
    }

    public static BuildResult build(
            OpenUltimateMultiGrantScreenPayload payload
    ) {
        if (payload == null) {
            return BuildResult.empty();
        }

        AuthorityActionMode mode = payload.actionMode();
        List<SkillUiEntry> entries = new ArrayList<>();
        Map<String, CostBreakdown> costs = new LinkedHashMap<>();

        for (OpenUltimateMultiGrantScreenPayload.SkillEntry source :
                payload.skills()) {
            if (source == null
                    || source.skillId().isBlank()) {
                continue;
            }

            SkillUiCategory category = toUiCategory(
                    source.category()
            );
            List<Component> details = new ArrayList<>();

            if (mode.takeBack()) {
                details.add(
                        Component.literal(
                                source.affectedTargets() == 1
                                        ? "Reclaimable from 1 subordinate."
                                        : "Reclaimable from "
                                          + source.affectedTargets()
                                          + " subordinates."
                        )
                );
                details.add(
                        Component.literal(
                                "No magicule cost."
                        )
                );
            } else if (mode.massGrant()) {
                details.add(
                        Component.literal(
                                "Mastered skill required for Mass Grant."
                        )
                );
                details.add(
                        Component.literal(
                                source.affectedTargets()
                                        + " eligible recipient"
                                        + (source.affectedTargets() == 1
                                        ? ""
                                        : "s")
                                        + "."
                        )
                );
                details.add(
                        Component.literal(
                                "Cost per recipient: "
                                        + formatNumber(
                                        source.standardCost()
                                )
                                        + " magicules."
                        )
                );
                details.add(
                        Component.literal(
                                "Total cost: "
                                        + formatNumber(
                                        source.finalCost()
                                )
                                        + " magicules."
                        )
                );
            } else {
                if (source.mastered()) {
                    details.add(
                            Component.literal(
                                    "Mastered skill. Standard transfer cost applies."
                            )
                    );
                } else {
                    details.add(
                            Component.literal(
                                    "WARNING: Unmastered skill. Increased magicule cost applies."
                            )
                    );
                }

                details.add(
                        Component.literal(
                                "Standard cost: "
                                        + formatNumber(
                                        source.standardCost()
                                )
                                        + " magicules."
                        )
                );

                if (source.surcharge() > 0.0D) {
                    details.add(
                            Component.literal(
                                    "Mastery bypass surcharge: +"
                                            + formatNumber(
                                            source.surcharge()
                                    )
                                            + " magicules."
                            )
                    );
                }

                details.add(
                        Component.literal(
                                "Final cost: "
                                        + formatNumber(
                                        source.finalCost()
                                )
                                        + " magicules."
                        )
                );
            }

            if (!source.selectable()
                    && !source.disabledReason().isBlank()) {
                details.add(
                        Component.literal(
                                source.disabledReason()
                        )
                );
            }

            Component disabledReason = source.disabledReason().isBlank()
                    ? Component.empty()
                    : Component.literal(source.disabledReason());

            entries.add(
                    new SkillUiEntry(
                            source.skillId(),
                            Component.literal(source.displayName()),
                            category,
                            source.selectable(),
                            source.mastered(),
                            disabledReason,
                            Component.empty(),
                            details,
                            source.mastered()
                                    ? category.defaultAccentColor()
                                    : 0xF0B35A
                    )
            );

            costs.put(
                    source.skillId(),
                    new CostBreakdown(
                            source.standardCost(),
                            source.surcharge(),
                            source.finalCost(),
                            source.mastered(),
                            source.affectedTargets()
                    )
            );
        }

        return new BuildResult(
                List.copyOf(entries),
                Map.copyOf(costs),
                mode
        );
    }

    public static SkillUiCategory toUiCategory(
            String rawCategory
    ) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return SkillUiCategory.OTHER;
        }

        String cleaned = rawCategory.trim()
                .toUpperCase(Locale.ROOT);

        return switch (cleaned) {
            case "UNIQUE", "ULTIMATE" -> SkillUiCategory.UNIQUE;
            case "EXTRA" -> SkillUiCategory.EXTRA;
            case "BASIC", "COMMON" -> SkillUiCategory.BASIC;
            case "RESISTANCE", "RESIST" -> SkillUiCategory.RESISTANCE;
            default -> SkillUiCategory.OTHER;
        };
    }

    public static double calculateTotalCost(
            List<String> selectedIds,
            Map<String, CostBreakdown> costs
    ) {
        if (selectedIds == null
                || selectedIds.isEmpty()
                || costs == null
                || costs.isEmpty()) {
            return 0.0D;
        }

        double total = 0.0D;

        for (String skillId : selectedIds) {
            CostBreakdown cost = costs.get(skillId);

            if (cost != null) {
                total += cost.finalCost();
            }
        }

        return Double.isFinite(total)
                ? Math.max(0.0D, total)
                : Double.MAX_VALUE;
    }

    public static SelectionSummary summarizeSelection(
            List<String> selectedIds,
            Map<String, CostBreakdown> costs
    ) {
        int mastered = 0;
        int unmastered = 0;
        int affectedTargets = 0;
        double standardCost = 0.0D;
        double surcharge = 0.0D;
        double totalCost = 0.0D;

        if (selectedIds != null && costs != null) {
            for (String skillId : selectedIds) {
                CostBreakdown cost = costs.get(skillId);

                if (cost == null) {
                    continue;
                }

                if (cost.mastered()) {
                    mastered++;
                } else {
                    unmastered++;
                }

                affectedTargets = Math.max(
                        affectedTargets,
                        cost.affectedTargets()
                );
                standardCost += cost.standardCost();
                surcharge += cost.surcharge();
                totalCost += cost.finalCost();
            }
        }

        return new SelectionSummary(
                mastered,
                unmastered,
                affectedTargets,
                standardCost,
                surcharge,
                totalCost
        );
    }

    public static String formatNumber(double value) {
        return String.format(
                Locale.US,
                "%,.0f",
                Math.max(0.0D, value)
        );
    }

    public record BuildResult(
            List<SkillUiEntry> entries,
            Map<String, CostBreakdown> costs,
            AuthorityActionMode mode
    ) {
        public BuildResult {
            entries = entries == null
                    ? List.of()
                    : List.copyOf(entries);
            costs = costs == null
                    ? Map.of()
                    : Map.copyOf(costs);
            mode = mode == null
                    ? AuthorityActionMode.BENEVOLENT_BESTOW
                    : mode;
        }

        public static BuildResult empty() {
            return new BuildResult(
                    List.of(),
                    Map.of(),
                    AuthorityActionMode.BENEVOLENT_BESTOW
            );
        }
    }

    public record CostBreakdown(
            double standardCost,
            double surcharge,
            double finalCost,
            boolean mastered,
            int affectedTargets
    ) {
        public CostBreakdown {
            standardCost = sanitize(standardCost);
            surcharge = sanitize(surcharge);
            finalCost = sanitize(finalCost);
            affectedTargets = Math.max(0, affectedTargets);
        }

        private static double sanitize(double value) {
            return Double.isFinite(value) && value > 0.0D
                    ? value
                    : 0.0D;
        }
    }

    public record SelectionSummary(
            int mastered,
            int unmastered,
            int affectedTargets,
            double standardCost,
            double surcharge,
            double totalCost
    ) {
        public SelectionSummary {
            mastered = Math.max(0, mastered);
            unmastered = Math.max(0, unmastered);
            affectedTargets = Math.max(0, affectedTargets);
            standardCost = Math.max(0.0D, standardCost);
            surcharge = Math.max(0.0D, surcharge);
            totalCost = Math.max(0.0D, totalCost);
        }
    }
}