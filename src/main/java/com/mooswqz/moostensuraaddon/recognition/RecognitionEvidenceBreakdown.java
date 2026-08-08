package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a read-only, source-by-source explanation of the active recognition
 * dimension totals.
 *
 * <p>This class mirrors the public balance snapshot consumed by
 * {@link RecognitionPathEvaluator}. It never mutates recognition data. The
 * debug command compares its totals with the evaluator result and reports a
 * mismatch immediately, preventing a stale inspector from silently presenting
 * different numbers after a future formula change.</p>
 */
public final class RecognitionEvidenceBreakdown {

    private static final double DEFAULT_TOLERANCE = 0.000_001D;

    private final Dimension good;
    private final Dimension evil;
    private final Dimension order;
    private final Dimension freedom;
    private final Dimension mastery;
    private final Dimension discovery;
    private final Dimension identityStrength;

    private RecognitionEvidenceBreakdown(
            Dimension good,
            Dimension evil,
            Dimension order,
            Dimension freedom,
            Dimension mastery,
            Dimension discovery,
            Dimension identityStrength
    ) {
        this.good = requireDimension(good, "Good");
        this.evil = requireDimension(evil, "Evil");
        this.order = requireDimension(order, "Order");
        this.freedom = requireDimension(freedom, "Freedom");
        this.mastery = requireDimension(mastery, "Mastery");
        this.discovery = requireDimension(discovery, "Discovery");
        this.identityStrength = requireDimension(
                identityStrength,
                "Identity Strength"
        );
    }

    public static RecognitionEvidenceBreakdown calculate(
            RecognitionData rawData,
            RecognitionBalanceSnapshot rawBalance
    ) {
        RecognitionData data = rawData == null
                ? new RecognitionData()
                : rawData;

        RecognitionBalanceSnapshot balance =
                rawBalance == null
                        ? RecognitionBalanceSnapshot.createDefaults()
                        : rawBalance;

        Dimension good = createGood(data, balance);
        Dimension evil = createEvil(data, balance);
        Dimension order = createOrder(data, balance);
        Dimension freedom = createFreedom(data, balance);
        Dimension mastery = createMastery(data, balance);
        Dimension discovery = createDiscovery(data, balance);

        int distinctMajorEnemies =
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .MAJOR_ENEMY_TYPES_DEFEATED
                );

        RecognitionBalanceSnapshot.IdentityStrength identity =
                balance.identityStrength();

        List<Entry> identityEntries = new ArrayList<>();

        identityEntries.add(
                entry(
                        "Mastery resonance",
                        "calculated_mastery",
                        formatRaw(mastery.total()),
                        mastery.total()
                                * identity.masteryWeight(),
                        0.0D
                )
        );

        identityEntries.add(
                entry(
                        "Discovery resonance",
                        "calculated_discovery",
                        formatRaw(discovery.total()),
                        discovery.total()
                                * identity.discoveryWeight(),
                        0.0D
                )
        );

        identityEntries.add(
                uniqueContributionEntry(
                        "Distinct major enemy types",
                        RecognitionStatKeys
                                .MAJOR_ENEMY_TYPES_DEFEATED,
                        distinctMajorEnemies,
                        identity.majorEnemyTypes()
                )
        );

        Dimension identityStrength =
                dimension(
                        "identity_strength",
                        "Identity Strength",
                        identityEntries,
                        identity.maximum()
                );

        return new RecognitionEvidenceBreakdown(
                good,
                evil,
                order,
                freedom,
                mastery,
                discovery,
                identityStrength
        );
    }

    public Dimension good() {
        return good;
    }

    public Dimension evil() {
        return evil;
    }

    public Dimension order() {
        return order;
    }

    public Dimension freedom() {
        return freedom;
    }

    public Dimension mastery() {
        return mastery;
    }

    public Dimension discovery() {
        return discovery;
    }

    public Dimension identityStrength() {
        return identityStrength;
    }

    public Consistency compare(
            RecognitionDimensions dimensions
    ) {
        if (dimensions == null) {
            return new Consistency(
                    false,
                    "dimensions",
                    Double.POSITIVE_INFINITY
            );
        }

        List<DimensionDifference> differences = List.of(
                difference(
                        "good",
                        good.total(),
                        dimensions.good()
                ),
                difference(
                        "evil",
                        evil.total(),
                        dimensions.evil()
                ),
                difference(
                        "order",
                        order.total(),
                        dimensions.order()
                ),
                difference(
                        "freedom",
                        freedom.total(),
                        dimensions.freedom()
                ),
                difference(
                        "mastery",
                        mastery.total(),
                        dimensions.mastery()
                ),
                difference(
                        "discovery",
                        discovery.total(),
                        dimensions.discovery()
                ),
                difference(
                        "identity_strength",
                        identityStrength.total(),
                        dimensions.identityStrength()
                )
        );

        DimensionDifference largest =
                differences.get(0);

        for (DimensionDifference candidate : differences) {
            if (candidate.difference()
                    > largest.difference()) {
                largest = candidate;
            }
        }

        return new Consistency(
                largest.difference()
                        <= DEFAULT_TOLERANCE,
                largest.dimensionId(),
                largest.difference()
        );
    }

    private static Dimension createGood(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Good good =
                balance.good();

        List<Entry> entries = new ArrayList<>();

        entries.add(
                flagEntry(
                        "True Hero",
                        RecognitionStatKeys.TRUE_HERO,
                        data.getFlag(
                                RecognitionStatKeys.TRUE_HERO
                        ),
                        good.trueHeroModifier()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Raid victories",
                        RecognitionStatKeys.RAID_VICTORIES,
                        data.getCounter(
                                RecognitionStatKeys.RAID_VICTORIES
                        ),
                        good.raidVictories()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Villagers cured",
                        RecognitionStatKeys.VILLAGERS_CURED,
                        data.getCounter(
                                RecognitionStatKeys.VILLAGERS_CURED
                        ),
                        good.villagersCured()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Civilians defended",
                        RecognitionStatKeys.CIVILIANS_DEFENDED,
                        data.getCounter(
                                RecognitionStatKeys.CIVILIANS_DEFENDED
                        ),
                        good.civiliansDefended()
                )
        );

        entries.add(
                uniqueContributionEntry(
                        "Malevolent boss types defeated",
                        RecognitionStatKeys
                                .MALEVOLENT_BOSS_TYPES_DEFEATED,
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .MALEVOLENT_BOSS_TYPES_DEFEATED
                        ),
                        good.malevolentBossTypesDefeated()
                )
        );

        return dimension(
                "good",
                "Good",
                entries,
                0.0D
        );
    }

    private static Dimension createEvil(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Evil evil =
                balance.evil();

        List<Entry> entries = new ArrayList<>();

        entries.add(
                flagEntry(
                        "True Demon Lord",
                        RecognitionStatKeys.TRUE_DEMON_LORD,
                        data.getFlag(
                                RecognitionStatKeys.TRUE_DEMON_LORD
                        ),
                        evil.trueDemonLordModifier()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Civilian kills",
                        RecognitionStatKeys.CIVILIAN_KILLS,
                        data.getCounter(
                                RecognitionStatKeys.CIVILIAN_KILLS
                        ),
                        evil.civilianKills()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Passive baby kills",
                        RecognitionStatKeys.PASSIVE_BABY_KILLS,
                        data.getCounter(
                                RecognitionStatKeys.PASSIVE_BABY_KILLS
                        ),
                        evil.passiveBabyKills()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Owned companion kills",
                        RecognitionStatKeys.OWNED_COMPANION_KILLS,
                        data.getCounter(
                                RecognitionStatKeys
                                        .OWNED_COMPANION_KILLS
                        ),
                        evil.ownedCompanionKills()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Owned subordinate kills",
                        RecognitionStatKeys.OWNED_SUBORDINATE_KILLS,
                        data.getCounter(
                                RecognitionStatKeys
                                        .OWNED_SUBORDINATE_KILLS
                        ),
                        evil.ownedSubordinateKills()
                )
        );

        entries.add(
                uniqueContributionEntry(
                        "Benevolent boss types killed",
                        RecognitionStatKeys
                                .BENEVOLENT_BOSS_TYPES_KILLED,
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .BENEVOLENT_BOSS_TYPES_KILLED
                        ),
                        evil.benevolentBossTypesKilled()
                )
        );

        return dimension(
                "evil",
                "Evil",
                entries,
                0.0D
        );
    }

    private static Dimension createOrder(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Order order =
                balance.order();

        int currentSubordinates =
                data.getCounter(
                        RecognitionStatKeys.CURRENT_SUBORDINATES
                );

        int highestSubordinates =
                data.getCounter(
                        RecognitionStatKeys.HIGHEST_SUBORDINATES
                );

        int recognizedSubordinates =
                Math.max(
                        currentSubordinates,
                        highestSubordinates
                );

        List<Entry> entries = new ArrayList<>();

        entries.add(
                entry(
                        "Recognized subordinate roster (primary tier)",
                        RecognitionStatKeys.CURRENT_SUBORDINATES
                                + "|"
                                + RecognitionStatKeys.HIGHEST_SUBORDINATES,
                        Integer.toString(recognizedSubordinates)
                                + " (current "
                                + currentSubordinates
                                + ", highest "
                                + highestSubordinates
                                + ")",
                        contribution(
                                recognizedSubordinates,
                                order.subordinateRosterPrimary()
                        ),
                        order.subordinateRosterPrimary()
                                .maximum()
                )
        );

        entries.add(
                entry(
                        "Recognized subordinate roster (established tier)",
                        RecognitionStatKeys.CURRENT_SUBORDINATES
                                + "|"
                                + RecognitionStatKeys.HIGHEST_SUBORDINATES,
                        Integer.toString(recognizedSubordinates)
                                + " (current "
                                + currentSubordinates
                                + ", highest "
                                + highestSubordinates
                                + ")",
                        contribution(
                                recognizedSubordinates,
                                order.subordinateRosterEstablished()
                        ),
                        order.subordinateRosterEstablished()
                                .maximum()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Subordinate-assisted major victories",
                        RecognitionStatKeys
                                .SUBORDINATE_ASSISTED_MAJOR_VICTORIES,
                        data.getCounter(
                                RecognitionStatKeys
                                        .SUBORDINATE_ASSISTED_MAJOR_VICTORIES
                        ),
                        order.subordinateAssistedMajorVictories()
                )
        );

        entries.add(
                uniqueContributionEntry(
                        "Unique subordinates empowered",
                        RecognitionStatKeys
                                .UNIQUE_SUBORDINATES_EMPOWERED,
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .UNIQUE_SUBORDINATES_EMPOWERED
                        ),
                        order.uniqueSubordinatesEmpowered()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Mass Grants performed",
                        RecognitionStatKeys.MASS_GRANTS_PERFORMED,
                        data.getCounter(
                                RecognitionStatKeys
                                        .MASS_GRANTS_PERFORMED
                        ),
                        order.massGrantsPerformed()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Global Take Backs performed",
                        RecognitionStatKeys
                                .GLOBAL_TAKE_BACKS_PERFORMED,
                        data.getCounter(
                                RecognitionStatKeys
                                        .GLOBAL_TAKE_BACKS_PERFORMED
                        ),
                        order.globalTakeBacksPerformed()
                )
        );

        return dimension(
                "order",
                "Order",
                entries,
                0.0D
        );
    }

    private static Dimension createFreedom(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Freedom freedom =
                balance.freedom();

        List<Entry> entries = new ArrayList<>();

        entries.add(
                uniqueContributionEntry(
                        "Solo major enemy types defeated",
                        RecognitionStatKeys
                                .SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .SOLO_MAJOR_ENEMY_TYPES_DEFEATED
                        ),
                        freedom.soloMajorEnemyTypesDefeated()
                )
        );

        entries.add(
                uniqueContributionEntry(
                        "Discovery milestones",
                        RecognitionStatKeys.DISCOVERY_MILESTONES,
                        data.getUniqueValueCount(
                                RecognitionStatKeys.DISCOVERY_MILESTONES
                        ),
                        freedom.discoveryMilestones()
                )
        );

        return dimension(
                "freedom",
                "Freedom",
                entries,
                0.0D
        );
    }

    private static Dimension createMastery(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        RecognitionBalanceSnapshot.Mastery mastery =
                balance.mastery();

        int masteredSkills =
                data.getCounter(
                        RecognitionStatKeys.MASTERED_SKILLS
                );

        int masteredCategories =
                data.getCounter(
                        RecognitionStatKeys
                                .MASTERED_SKILL_CATEGORIES
                );

        double highestEp =
                data.getMeasurement(
                        RecognitionStatKeys.HIGHEST_EP
                );

        int majorEnemyTypes =
                data.getUniqueValueCount(
                        RecognitionStatKeys
                                .MAJOR_ENEMY_TYPES_DEFEATED
                );

        List<Entry> entries = new ArrayList<>();

        entries.add(
                entry(
                        "Mastered skills",
                        RecognitionStatKeys.MASTERED_SKILLS,
                        Integer.toString(masteredSkills),
                        skillMasteryContribution(
                                masteredSkills,
                                mastery
                        ),
                        mastery.skillMaximum()
                )
        );

        entries.add(
                counterContributionEntry(
                        "Mastered skill categories",
                        RecognitionStatKeys
                                .MASTERED_SKILL_CATEGORIES,
                        masteredCategories,
                        mastery.masteredSkillCategories()
                )
        );

        entries.add(
                entry(
                        "Highest EP",
                        RecognitionStatKeys.HIGHEST_EP,
                        formatRaw(highestEp),
                        epContribution(
                                highestEp,
                                mastery.highestEp()
                        ),
                        mastery.highestEp()
                                .maximum()
                )
        );

        entries.add(
                uniqueContributionEntry(
                        "Distinct major enemy types",
                        RecognitionStatKeys
                                .MAJOR_ENEMY_TYPES_DEFEATED,
                        majorEnemyTypes,
                        mastery.majorEnemyTypesDefeated()
                )
        );

        return dimension(
                "mastery",
                "Mastery",
                entries,
                0.0D
        );
    }

    private static Dimension createDiscovery(
            RecognitionData data,
            RecognitionBalanceSnapshot balance
    ) {
        List<Entry> entries = List.of(
                uniqueContributionEntry(
                        "Discovery milestones",
                        RecognitionStatKeys.DISCOVERY_MILESTONES,
                        data.getUniqueValueCount(
                                RecognitionStatKeys.DISCOVERY_MILESTONES
                        ),
                        balance.discovery().milestones()
                )
        );

        return dimension(
                "discovery",
                "Discovery",
                entries,
                0.0D
        );
    }

    private static Dimension dimension(
            String id,
            String displayName,
            List<Entry> rawEntries,
            double maximum
    ) {
        List<Entry> entries =
                rawEntries == null
                        ? List.of()
                        : List.copyOf(rawEntries);

        double total = 0.0D;

        for (Entry entry : entries) {
            total += entry.contribution();
        }

        if (maximum > 0.0D) {
            total = Math.min(
                    maximum,
                    total
            );
        }

        return new Dimension(
                clean(id, "dimension"),
                clean(displayName, "Dimension"),
                sanitize(total),
                entries
        );
    }

    private static Entry counterContributionEntry(
            String label,
            String statKey,
            int count,
            RecognitionBalanceSnapshot.Contribution balance
    ) {
        return entry(
                label,
                statKey,
                Integer.toString(Math.max(0, count)),
                contribution(count, balance),
                balance == null
                        ? 0.0D
                        : balance.maximum()
        );
    }

    private static Entry uniqueContributionEntry(
            String label,
            String statKey,
            int count,
            RecognitionBalanceSnapshot.Contribution balance
    ) {
        return counterContributionEntry(
                label,
                statKey,
                count,
                balance
        );
    }

    private static Entry flagEntry(
            String label,
            String statKey,
            boolean enabled,
            double enabledContribution
    ) {
        return entry(
                label,
                statKey,
                Boolean.toString(enabled),
                enabled
                        ? enabledContribution
                        : 0.0D,
                Math.max(
                        0.0D,
                        enabledContribution
                )
        );
    }

    private static Entry entry(
            String label,
            String statKey,
            String rawValue,
            double contribution,
            double maximum
    ) {
        return new Entry(
                clean(label, "Evidence"),
                clean(statKey, "unknown"),
                rawValue == null
                        ? ""
                        : rawValue,
                sanitize(contribution),
                sanitize(maximum)
        );
    }

    private static double contribution(
            int count,
            RecognitionBalanceSnapshot.Contribution balance
    ) {
        if (count <= 0
                || balance == null
                || balance.pointsPerEntry() <= 0.0D
                || balance.maximum() <= 0.0D) {
            return 0.0D;
        }

        return Math.min(
                balance.maximum(),
                count * balance.pointsPerEntry()
        );
    }

    private static double epContribution(
            double ep,
            RecognitionBalanceSnapshot.EpContribution balance
    ) {
        if (!Double.isFinite(ep)
                || ep <= 0.0D
                || balance == null
                || balance.epPerUnit() <= 0.0D
                || balance.pointsPerUnit() <= 0.0D
                || balance.maximum() <= 0.0D) {
            return 0.0D;
        }

        return Math.min(
                balance.maximum(),
                ep
                        / balance.epPerUnit()
                        * balance.pointsPerUnit()
        );
    }

    private static double skillMasteryContribution(
            int masteredSkills,
            RecognitionBalanceSnapshot.Mastery mastery
    ) {
        if (mastery == null
                || masteredSkills <= 0) {
            return 0.0D;
        }

        int remaining = masteredSkills;
        double total = 0.0D;

        for (RecognitionBalanceSnapshot.SkillTier tier :
                mastery.skillTiers()) {

            if (remaining <= 0) {
                break;
            }

            int applied = Math.min(
                    remaining,
                    Math.max(0, tier.entries())
            );

            total += applied
                    * Math.max(
                    0.0D,
                    tier.pointsPerEntry()
            );

            remaining -= applied;
        }

        return Math.min(
                Math.max(
                        0.0D,
                        mastery.skillMaximum()
                ),
                sanitize(total)
        );
    }

    private static DimensionDifference difference(
            String dimensionId,
            double expected,
            double actual
    ) {
        double safeExpected = sanitize(expected);
        double safeActual = sanitize(actual);

        return new DimensionDifference(
                dimensionId,
                Math.abs(
                        safeExpected - safeActual
                )
        );
    }

    private static Dimension requireDimension(
            Dimension dimension,
            String fallbackName
    ) {
        return dimension == null
                ? new Dimension(
                fallbackName
                        .toLowerCase(Locale.ROOT)
                        .replace(' ', '_'),
                fallbackName,
                0.0D,
                List.of()
        )
                : dimension;
    }

    private static double sanitize(
            double value
    ) {
        return Double.isFinite(value)
                && value > 0.0D
                ? value
                : 0.0D;
    }

    private static String clean(
            String value,
            String fallback
    ) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim();
    }

    private static String formatRaw(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f",
                sanitize(value)
        );
    }

    public record Dimension(
            String id,
            String displayName,
            double total,
            List<Entry> entries
    ) {
        public Dimension {
            id = clean(id, "dimension");
            displayName = clean(
                    displayName,
                    "Dimension"
            );
            total = sanitize(total);
            entries = entries == null
                    ? List.of()
                    : List.copyOf(entries);
        }
    }

    public record Entry(
            String label,
            String statKey,
            String rawValue,
            double contribution,
            double maximum
    ) {
        public Entry {
            label = clean(label, "Evidence");
            statKey = clean(statKey, "unknown");
            rawValue = rawValue == null
                    ? ""
                    : rawValue;
            contribution = sanitize(contribution);
            maximum = sanitize(maximum);
        }
    }

    public record Consistency(
            boolean matches,
            String dimensionId,
            double difference
    ) {
        public Consistency {
            dimensionId = clean(
                    dimensionId,
                    "unknown"
            );
            difference = Double.isFinite(difference)
                    && difference >= 0.0D
                    ? difference
                    : Double.POSITIVE_INFINITY;
        }
    }

    private record DimensionDifference(
            String dimensionId,
            double difference
    ) {
    }
}
