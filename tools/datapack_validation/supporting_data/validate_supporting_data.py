#!/usr/bin/env python3
from __future__ import annotations
import json, math, sys, zipfile
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
JAR = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else None

BASE = ROOT / 'src/main/resources/data'
ERRORS = []


def load(rel):
    p = ROOT / rel
    if not p.is_file():
        ERRORS.append(f'missing: {rel}')
        return None
    try:
        return json.loads(p.read_text(encoding='utf-8'))
    except Exception as exc:
        ERRORS.append(f'bad JSON {rel}: {exc}')
        return None


def check(cond, msg):
    if not cond:
        ERRORS.append(msg)

# Independence.
ind_rel = 'src/main/resources/data/moostensuraaddon/recognition_independence_milestones/default.json'
ind = load(ind_rel)
if ind:
    check(ind.get('schema_version') == 1, 'independence schema_version must be 1')
    ms = ind.get('milestones')
    check(isinstance(ms, list), 'independence milestones must be a list')
    if isinstance(ms, list):
        ids = [x.get('id') for x in ms if isinstance(x, dict)]
        adv = [x.get('advancement') for x in ms if isinstance(x, dict)]
        pts = [x.get('points') for x in ms if isinstance(x, dict)]
        check(len(ms) == 7, f'expected 7 independence milestones, found {len(ms)}')
        check(len(ids) == len(set(ids)), 'duplicate independence semantic ID')
        check(len(adv) == len(set(adv)), 'duplicate independence advancement ID')
        check(all(isinstance(x, (int,float)) and math.isfinite(x) and x > 0 for x in pts), 'invalid independence point value')
        check(abs(sum(float(x) for x in pts if isinstance(x,(int,float))) - 24.0) < 1e-9, 'independence total must equal 24.0')
        expected_adv = {
            'minecraft:adventure/adventuring_time', 'minecraft:nether/uneasy_alliance',
            'minecraft:adventure/arbalistic', 'minecraft:adventure/two_birds_one_arrow',
            'minecraft:adventure/sniper_duel', 'minecraft:end/elytra',
            'minecraft:adventure/bullseye'
        }
        check(set(adv) == expected_adv, f'independence advancement set differs: {set(adv) ^ expected_adv}')

# Generic additive-tag checks.
biome_dir = 'src/main/resources/data/moostensuraaddon/tags/worldgen/biome/has_structure'
biome_names = ['great_crystal_shrine_temperate','great_crystal_shrine_hot','great_crystal_shrine_cold','great_crystal_shrine_cave']
biome_data = {}
for name in biome_names:
    rel = f'{biome_dir}/{name}.json'
    data = load(rel)
    if data:
        check(data.get('replace') is False, f'{name}: replace must be false')
        vals = data.get('values')
        check(isinstance(vals, list) and vals, f'{name}: values must be a non-empty list')
        if isinstance(vals, list):
            check(len(vals) == len(set(vals)), f'{name}: duplicate value')
            biome_data[name] = vals

# Ban the previously guessed/nonexistent Tensura biome IDs.
for name, vals in biome_data.items():
    for bad in ['tensura:great_jura_forest','tensura:frozen_tundra','tensura:sealed_cave']:
        check(bad not in vals, f'{name}: invalid guessed biome survived: {bad}')

# Exact intended semantic composition.
expected_biomes = {
    'great_crystal_shrine_temperate': ['#minecraft:is_forest','minecraft:plains','minecraft:sunflower_plains','minecraft:meadow','tensura:ancient_forest'],
    'great_crystal_shrine_hot': ['#tensura:is_desert','#minecraft:is_badlands','#minecraft:is_savanna'],
    'great_crystal_shrine_cold': ['#tensura:is_cold'],
    'great_crystal_shrine_cave': ['#tensura:is_cave'],
}
for k, v in expected_biomes.items():
    check(biome_data.get(k) == v, f'{k}: values differ from release-locked composition')

# Structure grouping.
struct_rel = 'src/main/resources/data/moostensuraaddon/tags/worldgen/structure/great_crystal_shrines.json'
struct = load(struct_rel)
if struct:
    check(struct.get('replace') is False, 'great_crystal_shrines: replace must be false')
    expected = [
        'moostensuraaddon:great_crystal_shrine_temperate',
        'moostensuraaddon:great_crystal_shrine_hot',
        'moostensuraaddon:great_crystal_shrine_cold',
        'moostensuraaddon:great_crystal_shrine_cave',
    ]
    check(struct.get('values') == expected, 'great_crystal_shrines membership differs')

# Skill tags.
skills_rel = 'src/main/resources/data/tensura/tags/manascore_skill/skills/skills.json'
unique_rel = 'src/main/resources/data/tensura/tags/manascore_skill/skills/unique_skills.json'
skills = load(skills_rel)
unique = load(unique_rel)
if skills:
    check(skills.get('replace') is False, 'skills.json: replace must be false')
    check(skills.get('values') == [
        'moostensuraaddon:granter',
        'moostensuraaddon:benevolent_empowerment',
        'moostensuraaddon:absolute_governance'
    ], 'skills.json membership differs')
if unique:
    check(unique.get('replace') is False, 'unique_skills.json: replace must be false')
    check(unique.get('values') == ['moostensuraaddon:granter'], 'unique_skills.json must contain Granter only')

# Optional exact dependency verification.
if JAR is not None:
    if not JAR.is_file():
        ERRORS.append(f'Tensura jar not found: {JAR}')
    else:
        with zipfile.ZipFile(JAR) as z:
            names = set(z.namelist())
            for biome in ['ancient_forest']:
                path = f'data/tensura/worldgen/biome/{biome}.json'
                check(path in names, f'missing Tensura biome in jar: tensura:{biome}')
            for tag in ['is_desert','is_cold','is_cave']:
                path = f'data/tensura/tags/worldgen/biome/{tag}.json'
                check(path in names, f'missing Tensura biome tag in jar: #tensura:{tag}')
            text = '\n'.join(
                z.read(n).decode('utf-8','ignore')
                for n in names
                if n.startswith('data/tensura/tags/worldgen/biome/') and n.endswith('.json')
            )
            for tag in ['#minecraft:is_forest','#minecraft:is_badlands','#minecraft:is_savanna']:
                check(tag in text, f'Minecraft tag ref not observed in exact Tensura jar: {tag}')

if ERRORS:
    print('Supporting datapack validation: FAIL')
    for e in ERRORS:
        print('[FAIL]', e)
    raise SystemExit(1)

print('Supporting datapack validation: PASS')
print('[PASS] Independence schema: 7 milestones / 24.0 points')
print('[PASS] Four shrine biome tags match release-locked composition')
print('[PASS] Four shrine structures are grouped for locator compatibility')
print('[PASS] Skill tags: 3 general / Granter-only Unique')
if JAR is not None:
    print('[PASS] Exact Tensura dependency biome/tag verification')
