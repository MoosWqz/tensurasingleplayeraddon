# Moos' Tensura Singleplayer Addon

**Moos' Tensura Singleplayer Addon** is an unofficial companion addon for
**Tensura: Reincarnated**. It brings important multiplayer-style progression
into a meaningful singleplayer journey through Soul Recognition, exploration,
rituals, mastered skills, and authority over subordinates.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.234
- Java 21
- Tensura: Reincarnated 2.0.1.0
- ManasCore 4.0.0.2
- Architectury 13.0.8
- GeckoLib 4.9.2
- SmartBrainLib 1.16.11
- TerraBlender 4.1.0.8

Mod ID: `moostensuraaddon`

## Soul Recognition

The Great Crystal Altar can recognize the life your character has actually
lived. The evaluator observes compatible gameplay evidence rather than asking
you to grind one counter, then resolves that history across two axes:

- Good, Neutral, or Evil
- Lawful, Neutral, or Chaotic

This produces nine possible paths. A sufficiently dominant single path can
become a stricter **Pure** recognition, while two qualified paths can form a
combined identity. The chosen paths, title, display name, reward, and
incarnation are frozen when the ritual starts, so disconnecting, dying, or
changing dimension cannot reroll the answer.

Recognition grants:

- A permanent, alignment-neutral strength reward based on frozen Identity
  Strength: 10–20%, plus 2.5% for Pure recognition.
- An effort-scaled extension to Tensura's native HIGH endowment capacity.
  Tensura's own 900% calculation and 1,000,000 EP cap remain untouched; Soul
  Recognition can add 0–1,000,000 more EP capacity from the same frozen effort
  snapshot, split evenly between maximum magicules and aura.
- A curated title and synchronized display identity.
- Continued access to the addon's recognition-dependent progression.

The capacity reward is applied once. Reconciliation can repair a missing
modifier, but it does not periodically refill spent magicules or aura.

## Great Crystal Shrines and the Soul Resonator

Great Crystal Shrines generate naturally in temperate, hot, cold, and cave
variants. Their Great Crystal Altar hosts both Soul Recognition and the Sage to
Great Sage progression ritual.

The **Soul Resonator** is a Sage/Great Sage locator used to find a shrine. Its
recipe uses:

- Four `tensura:medium_quality_magic_crystal` in the corners
- Four `tensura:magic_stone` on the edges
- One `minecraft:compass` in the center

## Sage to Great Sage

After obtaining Sage, locate a shrine, meet the altar requirements, and allow
the ritual to complete. The sequence uses ritual stasis, a collapsed pose,
darkness, soul particles, sound, and Sage narration before awakening Great
Sage. While Sage is still present, this evolution has altar priority. Soul
Recognition follows as a separate altar interaction after Great Sage awakens.

## Granter

The Unique Skill **Granter** turns mastered skills into authority over your
subordinates. Its modes let you:

- Select and grant a mastered skill
- Reclaim a granted skill
- Inspect subordinate skills
- Progress through continued mastery and subordinate leadership

## Ultimate Skills

Granter can evolve into one of two Ultimate Skills:

- **Benevolent Empowerment** focuses on guidance, shared strength, protection,
  and empowering allies.
- **Absolute Governance** focuses on domination, global command, seizure, and
  absolute control.

Both paths expand Granter with mass actions, remote subordinate management,
skill borrowing or seizure, and higher-authority progression.

## Character Reset and Save Safety

Addon progression is tied to a persistent incarnation token. Ordinary death,
respawn, dimension travel, and relogging preserve the same life. A successful
Tensura Character Reset starts a genuinely new addon incarnation and clears
life-bound recognition, Granter, borrowed-skill, and endowment-retry state.

The reset integration uses Tensura's completed reset advancement as the
confirmation signal. Releasing the scroll too early does not reset addon data.

## Commands

The supported player-facing command surface is:

- `/moostensura`
- `/moostensura guide`
- `/moostensura paths`
- `/moostensura help`

Development and recovery tools are permission-gated below the canonical
command root. Legacy self-endowment is retained only as an administrator
recovery/testing route and is not part of normal survival progression.

## Datapack Compatibility

Entity morality, civilians, civilian-hostile mobs, bosses, structures,
advancements, and supporting recognition data are datapack-driven wherever
practical. Pack authors and other Tensura addons can extend the supplied tags
and data without replacing the evaluator.

Reference material is available in:

- `docs/datapacks/entity_classification/`
- `docs/datapacks/supporting_data/`

## Disclaimer

This addon is unofficial and is not affiliated with the developers of
Tensura: Reincarnated. Tensura: Reincarnated and related properties belong to
their respective creators.
