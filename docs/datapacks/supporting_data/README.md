# Supporting Datapack Lock

This package freezes the non-balance, non-title recognition/worldgen support data for the
1.4.0 release line.

## Independence

The existing release balance is preserved exactly:

```text
Adventuring Time       5
Uneasy Alliance        5
Arbalistic             4
Two Birds, One Arrow   3
Sniper Duel            3
Sky's the Limit        2
Bullseye                2
Total                  24
```

No Freedom/recognition balance change is made.

## Great Crystal Shrine biome compatibility

The biome files remain additive (`replace: false`). The release composition is:

```text
Temperate:
  #minecraft:is_forest
  minecraft:plains
  minecraft:sunflower_plains
  minecraft:meadow
  tensura:ancient_forest

Hot:
  #tensura:is_desert
  #minecraft:is_badlands
  #minecraft:is_savanna

Cold:
  #tensura:is_cold

Cave:
  #tensura:is_cave
```

This deliberately removes/avoids the speculative IDs:

```text
tensura:great_jura_forest
tensura:frozen_tundra
tensura:sealed_cave
```

Those IDs do not exist in the supplied Tensura 2.0.1.0 jar.

The exact jar inspected was:

```text
tensura-neoforge-2.0.1.0.jar
SHA-256 efe5133a60fc2f079cb17d9f9c6f869dc3111ec9e65d3541b44839103acf4925
```

The jar contains `tensura:ancient_forest` and the `#tensura:is_desert`,
`#tensura:is_cold`, and `#tensura:is_cave` tags. It also uses the selected
Minecraft forest/badlands/savanna tag references itself.

No spacing, separation, salts, structure JSON, processor, template pool, NBT, terrain
adaptation or locator code is changed.

## Structure grouping

`great_crystal_shrines.json` continues to contain all four variants so the existing
locator can treat them as one shrine family.

## Skill tags

General Tensura skill tag:

```text
moostensuraaddon:granter
moostensuraaddon:benevolent_empowerment
moostensuraaddon:absolute_governance
```

Unique-only tag:

```text
moostensuraaddon:granter
```

Benevolent Empowerment and Absolute Governance remain Ultimate evolutions and are not
misclassified as Unique skills.

## Extension rule

Pack authors should extend these tags additively with `replace: false`. Optional IDs from
another mod should use object form with `required: false`. Recognition titles and
independence milestones are separate additive JSON systems; adding an independence
milestone changes scoring and should be treated as a balance change.
