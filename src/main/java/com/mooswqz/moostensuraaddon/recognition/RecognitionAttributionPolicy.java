package com.mooswqz.moostensuraaddon.recognition;

/**
 * Pure precedence rules for combat ownership and negative recognition deeds.
 *
 * <p>The policy intentionally has no "named entity" input. A custom name is
 * presentation metadata, not proof that a mob belongs to a player.</p>
 */
public final class RecognitionAttributionPolicy {

    private RecognitionAttributionPolicy() {
    }

    public static ActorKind classifyActor(
            boolean playerProjectile,
            boolean directPlayer,
            boolean tensuraSubordinate,
            boolean ownedCompanion
    ) {
        if (playerProjectile) {
            return ActorKind.PLAYER_PROJECTILE;
        }

        if (directPlayer) {
            return ActorKind.DIRECT_PLAYER;
        }

        if (tensuraSubordinate) {
            return ActorKind.TENSURA_SUBORDINATE;
        }

        if (ownedCompanion) {
            return ActorKind.OWNED_COMPANION;
        }

        return ActorKind.NONE;
    }

    public static NegativeDeed classifyNegativeDeed(
            boolean ownedSubordinate,
            boolean ownedCompanion,
            boolean civilian,
            boolean benevolentBoss,
            boolean passiveBaby
    ) {
        if (ownedSubordinate) {
            return NegativeDeed.OWNED_SUBORDINATE_KILLED;
        }

        if (ownedCompanion) {
            return NegativeDeed.OWNED_COMPANION_KILLED;
        }

        if (civilian) {
            return NegativeDeed.CIVILIAN_KILLED;
        }

        if (benevolentBoss) {
            return NegativeDeed.BENEVOLENT_BOSS_KILLED;
        }

        if (passiveBaby) {
            return NegativeDeed.PASSIVE_BABY_KILLED;
        }

        return NegativeDeed.NONE;
    }

    public enum ActorKind {
        NONE,
        DIRECT_PLAYER,
        PLAYER_PROJECTILE,
        OWNED_COMPANION,
        TENSURA_SUBORDINATE
    }

    public enum NegativeDeed {
        NONE,
        OWNED_SUBORDINATE_KILLED,
        OWNED_COMPANION_KILLED,
        CIVILIAN_KILLED,
        BENEVOLENT_BOSS_KILLED,
        PASSIVE_BABY_KILLED
    }
}
