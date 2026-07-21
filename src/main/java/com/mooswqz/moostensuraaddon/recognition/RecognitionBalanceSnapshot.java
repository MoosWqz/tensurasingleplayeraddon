package com.mooswqz.moostensuraaddon.recognition;

import java.util.List;

/**
 * Immutable recognition-balance definition used by every evaluation.
 *
 * A complete snapshot is assembled during datapack reload and then installed
 * atomically. Evaluation code only reads this already-validated object; it
 * never parses JSON or performs resource lookups.
 */
public final class RecognitionBalanceSnapshot {

    public static final int SCHEMA_VERSION = 1;

    private final String sourceId;
    private final Selection selection;
    private final IdentityDistribution identityDistribution;
    private final IdentityStrength identityStrength;
    private final Good good;
    private final Evil evil;
    private final Order order;
    private final Freedom freedom;
    private final Mastery mastery;
    private final Discovery discovery;
    private final Neutrality neutrality;
    private final RawPaths rawPaths;

    public RecognitionBalanceSnapshot(
            String sourceId,
            Selection selection,
            IdentityDistribution identityDistribution,
            IdentityStrength identityStrength,
            Good good,
            Evil evil,
            Order order,
            Freedom freedom,
            Mastery mastery,
            Discovery discovery,
            Neutrality neutrality,
            RawPaths rawPaths
    ) {
        this.sourceId = sourceId == null || sourceId.isBlank()
                ? "built-in defaults"
                : sourceId.trim();

        this.selection = requireNonNull(
                selection,
                "selection"
        );

        this.identityDistribution = requireNonNull(
                identityDistribution,
                "identityDistribution"
        );

        this.identityStrength = requireNonNull(
                identityStrength,
                "identityStrength"
        );

        this.good = requireNonNull(
                good,
                "good"
        );

        this.evil = requireNonNull(
                evil,
                "evil"
        );

        this.order = requireNonNull(
                order,
                "order"
        );

        this.freedom = requireNonNull(
                freedom,
                "freedom"
        );

        this.mastery = requireNonNull(
                mastery,
                "mastery"
        );

        this.discovery = requireNonNull(
                discovery,
                "discovery"
        );

        this.neutrality = requireNonNull(
                neutrality,
                "neutrality"
        );

        this.rawPaths = requireNonNull(
                rawPaths,
                "rawPaths"
        );
    }

    public String sourceId() {
        return sourceId;
    }

    public Selection selection() {
        return selection;
    }

    public IdentityDistribution identityDistribution() {
        return identityDistribution;
    }

    public IdentityStrength identityStrength() {
        return identityStrength;
    }

    public Good good() {
        return good;
    }

    public Evil evil() {
        return evil;
    }

    public Order order() {
        return order;
    }

    public Freedom freedom() {
        return freedom;
    }

    public Mastery mastery() {
        return mastery;
    }

    public Discovery discovery() {
        return discovery;
    }

    public Neutrality neutrality() {
        return neutrality;
    }

    public RawPaths rawPaths() {
        return rawPaths;
    }

    public static RecognitionBalanceSnapshot createDefaults() {
        return new RecognitionBalanceSnapshot(
                "built-in defaults",
                new Selection(
                        35.0D,
                        70.0D,
                        2.0D,
                        35.0D,
                        1.6D,
                        6.0D
                ),
                new IdentityDistribution(
                        0.15D,
                        0.60D,
                        12.0D,
                        0.60D
                ),
                new IdentityStrength(
                        40.0D,
                        0.65D,
                        0.35D,
                        new Contribution(
                                1.5D,
                                10.0D
                        )
                ),
                new Good(
                        15.0D,
                        new Contribution(
                                8.0D,
                                24.0D
                        ),
                        new Contribution(
                                3.0D,
                                18.0D
                        ),
                        new Contribution(
                                3.0D,
                                24.0D
                        ),
                        new Contribution(
                                6.0D,
                                30.0D
                        )
                ),
                new Evil(
                        15.0D,
                        new Contribution(
                                6.0D,
                                30.0D
                        ),
                        new Contribution(
                                1.0D,
                                12.0D
                        ),
                        new Contribution(
                                6.0D,
                                24.0D
                        ),
                        new Contribution(
                                10.0D,
                                30.0D
                        ),
                        new Contribution(
                                8.0D,
                                32.0D
                        )
                ),
                new Order(
                        new Contribution(
                                2.0D,
                                20.0D
                        ),
                        new Contribution(
                                0.5D,
                                10.0D
                        ),
                        new Contribution(
                                4.0D,
                                20.0D
                        ),
                        new Contribution(
                                1.0D,
                                10.0D
                        ),
                        new Contribution(
                                0.5D,
                                5.0D
                        ),
                        new Contribution(
                                0.5D,
                                5.0D
                        )
                ),
                new Freedom(
                        new Contribution(
                                5.0D,
                                30.0D
                        ),
                        new Contribution(
                                2.0D,
                                12.0D
                        )
                ),
                new Mastery(
                        List.of(
                                new SkillTier(
                                        5,
                                        2.0D
                                ),
                                new SkillTier(
                                        3,
                                        3.0D
                                ),
                                new SkillTier(
                                        4,
                                        2.0D
                                ),
                                new SkillTier(
                                        8,
                                        1.0D
                                ),
                                new SkillTier(
                                        20,
                                        0.25D
                                )
                        ),
                        40.0D,
                        new Contribution(
                                4.0D,
                                16.0D
                        ),
                        new EpContribution(
                                100_000.0D,
                                2.5D,
                                20.0D
                        ),
                        new Contribution(
                                2.0D,
                                20.0D
                        )
                ),
                new Discovery(
                        new Contribution(
                                5.0D,
                                30.0D
                        )
                ),
                new Neutrality(
                        new NeutralMorality(
                                0.90D,
                                0.75D,
                                20.0D,
                                40.0D
                        ),
                        new NeutralBehaviour(
                                0.90D,
                                12.0D,
                                18.0D,
                                40.0D
                        )
                ),
                new RawPaths(
                        0.90D,
                        0.90D,
                        0.50D,
                        0.25D,
                        10.0D,
                        0.25D,
                        12.0D,
                        0.35D,
                        12.0D
                )
        );
    }

    private static <T> T requireNonNull(
            T value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Recognition balance field '"
                            + fieldName
                            + "' cannot be null."
            );
        }

        return value;
    }

    public record Selection(
            double establishedThreshold,
            double pureThreshold,
            double dominanceRatio,
            double rawPureThreshold,
            double rawDominanceRatio,
            double minimumDirectionalMoralityEvidence
    ) {
    }

    public record IdentityDistribution(
            double universalShare,
            double focusedShare,
            double minimumFocusAffinity,
            double secondaryFocusRatio
    ) {
    }

    public record IdentityStrength(
            double maximum,
            double masteryWeight,
            double discoveryWeight,
            Contribution majorEnemyTypes
    ) {

        public IdentityStrength {
            majorEnemyTypes = requireNonNull(
                    majorEnemyTypes,
                    "identityStrength.majorEnemyTypes"
            );
        }
    }

    public record Good(
            double trueHeroModifier,
            Contribution raidVictories,
            Contribution villagersCured,
            Contribution civiliansDefended,
            Contribution malevolentBossTypesDefeated
    ) {

        public Good {
            raidVictories = requireNonNull(
                    raidVictories,
                    "good.raidVictories"
            );

            villagersCured = requireNonNull(
                    villagersCured,
                    "good.villagersCured"
            );

            civiliansDefended = requireNonNull(
                    civiliansDefended,
                    "good.civiliansDefended"
            );

            malevolentBossTypesDefeated = requireNonNull(
                    malevolentBossTypesDefeated,
                    "good.malevolentBossTypesDefeated"
            );
        }
    }

    public record Evil(
            double trueDemonLordModifier,
            Contribution civilianKills,
            Contribution passiveBabyKills,
            Contribution ownedCompanionKills,
            Contribution ownedSubordinateKills,
            Contribution benevolentBossTypesKilled
    ) {

        public Evil {
            civilianKills = requireNonNull(
                    civilianKills,
                    "evil.civilianKills"
            );

            passiveBabyKills = requireNonNull(
                    passiveBabyKills,
                    "evil.passiveBabyKills"
            );

            ownedCompanionKills = requireNonNull(
                    ownedCompanionKills,
                    "evil.ownedCompanionKills"
            );

            ownedSubordinateKills = requireNonNull(
                    ownedSubordinateKills,
                    "evil.ownedSubordinateKills"
            );

            benevolentBossTypesKilled = requireNonNull(
                    benevolentBossTypesKilled,
                    "evil.benevolentBossTypesKilled"
            );
        }
    }

    public record Order(
            Contribution subordinateRosterPrimary,
            Contribution subordinateRosterEstablished,
            Contribution subordinateAssistedMajorVictories,
            Contribution uniqueSubordinatesEmpowered,
            Contribution massGrantsPerformed,
            Contribution globalTakeBacksPerformed
    ) {

        public Order {
            subordinateRosterPrimary = requireNonNull(
                    subordinateRosterPrimary,
                    "order.subordinateRosterPrimary"
            );

            subordinateRosterEstablished = requireNonNull(
                    subordinateRosterEstablished,
                    "order.subordinateRosterEstablished"
            );

            subordinateAssistedMajorVictories = requireNonNull(
                    subordinateAssistedMajorVictories,
                    "order.subordinateAssistedMajorVictories"
            );

            uniqueSubordinatesEmpowered = requireNonNull(
                    uniqueSubordinatesEmpowered,
                    "order.uniqueSubordinatesEmpowered"
            );

            massGrantsPerformed = requireNonNull(
                    massGrantsPerformed,
                    "order.massGrantsPerformed"
            );

            globalTakeBacksPerformed = requireNonNull(
                    globalTakeBacksPerformed,
                    "order.globalTakeBacksPerformed"
            );
        }
    }

    public record Freedom(
            Contribution soloMajorEnemyTypesDefeated,
            Contribution discoveryMilestones
    ) {

        public Freedom {
            soloMajorEnemyTypesDefeated = requireNonNull(
                    soloMajorEnemyTypesDefeated,
                    "freedom.soloMajorEnemyTypesDefeated"
            );

            discoveryMilestones = requireNonNull(
                    discoveryMilestones,
                    "freedom.discoveryMilestones"
            );
        }
    }

    public record Mastery(
            List<SkillTier> skillTiers,
            double skillMaximum,
            Contribution masteredSkillCategories,
            EpContribution highestEp,
            Contribution majorEnemyTypesDefeated
    ) {

        public Mastery {
            skillTiers = skillTiers == null
                    ? List.of()
                    : List.copyOf(
                    skillTiers
            );

            if (skillTiers.isEmpty()) {
                throw new IllegalArgumentException(
                        "Recognition balance requires at least one mastery tier."
                );
            }

            masteredSkillCategories = requireNonNull(
                    masteredSkillCategories,
                    "mastery.masteredSkillCategories"
            );

            highestEp = requireNonNull(
                    highestEp,
                    "mastery.highestEp"
            );

            majorEnemyTypesDefeated = requireNonNull(
                    majorEnemyTypesDefeated,
                    "mastery.majorEnemyTypesDefeated"
            );
        }
    }

    public record Discovery(
            Contribution milestones
    ) {

        public Discovery {
            milestones = requireNonNull(
                    milestones,
                    "discovery.milestones"
            );
        }
    }

    public record Neutrality(
            NeutralMorality morality,
            NeutralBehaviour behaviour
    ) {

        public Neutrality {
            morality = requireNonNull(
                    morality,
                    "neutrality.morality"
            );

            behaviour = requireNonNull(
                    behaviour,
                    "neutrality.behaviour"
            );
        }
    }

    public record NeutralMorality(
            double balanceWeight,
            double discoveryWeight,
            double moralVolumeDivisor,
            double maximum
    ) {
    }

    public record NeutralBehaviour(
            double balanceWeight,
            double postureBase,
            double behaviourVolumeDivisor,
            double maximum
    ) {
    }

    public record RawPaths(
            double moralWeight,
            double temperamentWeight,
            double overlapWeight,
            double contradictionFactor,
            double contradictionMaximum,
            double trueNeutralDiscoveryFactor,
            double trueNeutralDiscoveryMaximum,
            double chaoticNeutralDiscoveryFactor,
            double chaoticNeutralDiscoveryMaximum
    ) {
    }

    public record Contribution(
            double pointsPerEntry,
            double maximum
    ) {
    }

    public record EpContribution(
            double epPerUnit,
            double pointsPerUnit,
            double maximum
    ) {
    }

    public record SkillTier(
            int entries,
            double pointsPerEntry
    ) {
    }
}