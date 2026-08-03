package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;
import com.mooswqz.moostensuraaddon.util.UltimateBorrowSeizePolicy;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UltimateBorrowSeizeUiEntryFactory {

    private UltimateBorrowSeizeUiEntryFactory() {
    }

    public static BuildResult build(
            OpenUltimateSubordinateSkillScreenPayload payload
    ) {
        if (payload == null) {
            return new BuildResult(
                    List.of(),
                    Map.of()
            );
        }

        List<SkillUiEntry> entries = new ArrayList<>();
        Map<String, Double> borrowChances =
                new LinkedHashMap<>();

        for (OpenUltimateSubordinateSkillScreenPayload.SkillEntry source :
                payload.skills()) {
            if (source == null
                    || source.skillId().isBlank()) {
                continue;
            }

            SkillUiCategory category =
                    SkillUiCategory.fromRaw(
                            source.category()
                    );
            List<Component> details = new ArrayList<>();

            if (payload.seize()) {
                details.add(
                        SkillUiText.component(
                                "details.seize_permanent_removal",
                                payload.targetName()
                        )
                );
                details.add(
                        SkillUiText.component(
                                "details.seize_backlash_risk"
                        )
                );
            } else {
                details.add(
                        SkillUiText.component(
                                "details.borrow_target_keeps",
                                payload.targetName()
                        )
                );
                details.add(
                        SkillUiText.component(
                                "details.borrow_permanent_chance",
                                UltimateBorrowSeizePolicy.formatPercent(
                                        source.borrowPermanentChance()
                                )
                        )
                );
                details.add(
                        SkillUiText.component(
                                "details.borrow_duration"
                        )
                );
            }

            details.add(
                    SkillUiText.component(
                            "details.cost",
                            UltimateBorrowSeizePolicy.formatNumber(
                                    payload.costPerSkill()
                            )
                    )
            );

            SkillUiEntry entry = new SkillUiEntry(
                    source.skillId(),
                    Component.literal(
                            source.displayName().isBlank()
                                    ? source.skillId()
                                    : source.displayName()
                    ),
                    category,
                    true,
                    source.mastered(),
                    Component.empty(),
                    Component.literal(payload.targetName()),
                    details,
                    category.defaultAccentColor()
            );

            entries.add(entry);
            borrowChances.put(
                    source.skillId(),
                    source.borrowPermanentChance()
            );
        }

        return new BuildResult(
                List.copyOf(entries),
                Map.copyOf(borrowChances)
        );
    }

    public record BuildResult(
            List<SkillUiEntry> entries,
            Map<String, Double> borrowChances
    ) {

        public BuildResult {
            entries = entries == null
                    ? List.of()
                    : List.copyOf(entries);
            borrowChances = borrowChances == null
                    ? Map.of()
                    : Map.copyOf(borrowChances);
        }
    }
}