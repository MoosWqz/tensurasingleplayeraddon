package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.network.OpenUltimateMultiGrantScreenPayload;
import com.mooswqz.moostensuraaddon.util.AuthorityActionMode;
import com.mooswqz.moostensuraaddon.util.UiTranslationToken;
import net.minecraft.network.chat.Component;

import java.util.*;

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
                        SkillUiText.component(
                                source.affectedTargets() == 1
                                        ? "details.reclaimable_one"
                                        : "details.reclaimable_many",
                                source.affectedTargets()
                        )
                );
                details.add(
                        SkillUiText.component(
                                "details.no_magicule_cost"
                        )
                );
            } else if (mode.massGrant()) {
                details.add(
                        SkillUiText.component(
                                "details.mass_grant_mastery_required"
                        )
                );
                details.add(
                        SkillUiText.component(
                                source.affectedTargets() == 1
                                        ? "details.eligible_recipient_one"
                                        : "details.eligible_recipient_many",
                                source.affectedTargets()
                        )
                );
                details.add(
                        SkillUiText.component(
                                "details.cost_per_recipient",
                                formatNumber(source.standardCost())
                        )
                );
                details.add(
                        SkillUiText.component(
                                "details.total_cost",
                                formatNumber(source.finalCost())
                        )
                );
            } else {
                if (source.mastered()) {
                    details.add(
                            SkillUiText.component(
                                    "details.mastered_standard_cost"
                            )
                    );
                } else {
                    details.add(
                            SkillUiText.component(
                                    "warning.unmastered_increased_cost"
                            )
                    );
                }

                details.add(
                        SkillUiText.component(
                                "details.standard_cost",
                                formatNumber(source.standardCost())
                        )
                );

                if (source.surcharge() > 0.0D) {
                    details.add(
                            SkillUiText.component(
                                    "details.mastery_bypass_surcharge",
                                    formatNumber(source.surcharge())
                            )
                    );
                }

                details.add(
                        SkillUiText.component(
                                "details.final_cost",
                                formatNumber(source.finalCost())
                        )
                );
            }

            if (!source.selectable()
                    && !source.disabledReason().isBlank()) {
                details.add(
                        UiTranslationToken.toComponent(
                                source.disabledReason()
                        )
                );
            }

            Component disabledReason = source.disabledReason().isBlank()
                    ? Component.empty()
                    : UiTranslationToken.toComponent(
                    source.disabledReason()
            );

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
        return SkillUiCategory.fromRaw(rawCategory);
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