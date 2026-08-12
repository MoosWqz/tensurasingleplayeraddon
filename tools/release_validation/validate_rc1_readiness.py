#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
JAVA = ROOT / "src/main/java/com/mooswqz/moostensuraaddon"
RESOURCES = ROOT / "src/main/resources"
EXPECTED_VERSION = "1.4.0b8"

CHECKS: list[tuple[str, bool, str]] = []
WARNINGS: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    return path.read_text(encoding="utf-8-sig") if path.is_file() else ""


def expect(name: str, condition: bool, detail: str = "") -> None:
    CHECKS.append((name, bool(condition), detail))


def property_value(text: str, key: str) -> str | None:
    match = re.search(
        rf"(?m)^\s*{re.escape(key)}\s*=\s*(.*?)\s*$",
        text,
    )
    return match.group(1).strip() if match else None


gradle = read("gradle.properties")
mods_toml = read("src/main/resources/META-INF/neoforge.mods.toml")
gitignore = read(".gitignore")
workflow = read(".github/workflows/build.yml")
wrapper_properties = read("gradle/wrapper/gradle-wrapper.properties")

expect(
    "Gradle metadata is the b8 RC1 candidate",
    property_value(gradle, "mod_version") == EXPECTED_VERSION,
)
expect(
    "NeoForge metadata matches the b8 RC1 candidate",
    f'version="{EXPECTED_VERSION}"' in mods_toml,
)
expect(
    "Tracked Gradle configuration contains no developer-local Java paths",
    "org.gradle.java.home" not in gradle
    and "org.gradle.java.installations.paths" not in gradle
    and "C:/Program Files/" not in gradle,
)
expect(
    "The Gradle wrapper JAR is explicitly allowed by .gitignore",
    "!gradle/wrapper/gradle-wrapper.jar" in gitignore,
)
expect(
    "Gradle wrapper scripts and properties are present",
    (ROOT / "gradlew").is_file()
    and (ROOT / "gradlew.bat").is_file()
    and "gradle-9.2.1-bin.zip" in wrapper_properties,
)

wrapper_jar = ROOT / "gradle/wrapper/gradle-wrapper.jar"
if wrapper_jar.is_file() and wrapper_jar.stat().st_size > 10_000:
    expect("The Gradle wrapper JAR is present", True)
else:
    WARNINGS.append(
        "gradle/wrapper/gradle-wrapper.jar is not present in this checkout. "
        "It must be staged from the working local wrapper before the b8 push; "
        "the GitHub build intentionally fails without it."
    )

expect(
    "GitHub Actions uses portable Java 21 and a clean wrapper build",
    "java-version: '21'" in workflow
    and "bash ./gradlew clean build --stacktrace" in workflow,
)
expect(
    "GitHub Actions runs release and datapack validators",
    "tools/release_validation/validate_*.py" in workflow
    and "tools/datapack_validation/*/validate_*.py" in workflow,
)

metadata_markers = (
    "Soul Recognition",
    "Great Crystal Shrines",
    "Granter",
    "Benevolent Empowerment",
    "Absolute Governance",
)
expect(
    "NeoForge description reflects the current release",
    all(marker in mods_toml for marker in metadata_markers)
    and "survival-friendly commands" not in mods_toml,
)

json_errors: list[str] = []
duplicate_keys: list[str] = []


def parse_json(path: Path):
    local_duplicates: list[str] = []

    def object_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                local_duplicates.append(key)
            result[key] = value
        return result

    try:
        parsed = json.loads(
            path.read_text(encoding="utf-8-sig"),
            object_pairs_hook=object_pairs,
        )
    except (OSError, json.JSONDecodeError) as exception:
        json_errors.append(f"{path.relative_to(ROOT)}: {exception}")
        return None

    for key in local_duplicates:
        duplicate_keys.append(f"{path.relative_to(ROOT)}: {key}")
    return parsed


all_json = sorted(RESOURCES.rglob("*.json"))
parsed_json = {path: parse_json(path) for path in all_json}
expect(
    "All resource JSON parses successfully",
    not json_errors,
    "; ".join(json_errors),
)
expect(
    "Resource JSON contains no duplicate object keys",
    not duplicate_keys,
    "; ".join(duplicate_keys),
)

language_path = (
    RESOURCES / "assets/moostensuraaddon/lang/en_us.json"
)
language = parsed_json.get(language_path)
if not isinstance(language, dict):
    language = {}

expect(
    "Addon translations have one canonical asset namespace",
    not (
        RESOURCES
        / "assets/moostensuraaddon_guidance/lang/en_us.json"
    ).exists(),
)

translation_literal = re.compile(
    r'"((?:message|screen|skill|item|block|advancement)'
    r'\.moostensuraaddon\.[^"]+)"'
)
missing_translations: dict[str, set[str]] = {}

for source in sorted(JAVA.rglob("*.java")):
    source_text = source.read_text(encoding="utf-8-sig")
    for match in translation_literal.finditer(source_text):
        key = match.group(1)
        if key.endswith(".") or key in language:
            continue
        missing_translations.setdefault(key, set()).add(
            source.relative_to(ROOT).as_posix()
        )

expect(
    "Every complete addon translation key used by Java exists in en_us",
    not missing_translations,
    "; ".join(
        f"{key} ({', '.join(sorted(paths))})"
        for key, paths in sorted(missing_translations.items())
    ),
)

stage_ids = (
    "incarnation_rebuilding",
    "form_identity",
    "seek_sage",
    "awaken_great_sage",
    "awaken_granter",
    "evolve_authority",
    "benevolent_empowerment",
    "absolute_governance",
)
guidance_policy = read(
    "src/main/java/com/mooswqz/moostensuraaddon/command/PlayerGuidancePolicy.java"
)
stage_keys = {
    f"message.moostensuraaddon.guide.stage.{stage_id}.{suffix}"
    for stage_id in stage_ids
    for suffix in ("title", "detail")
}
expect(
    "Every player-guidance stage has a title and detail translation",
    all(f'("{stage_id}")' in guidance_policy for stage_id in stage_ids)
    and stage_keys.issubset(language),
)

benefit_states = (
    "not_recognized",
    "invalid",
    "future_profile",
    "synchronizing",
    "active",
)
expect(
    "Every recognition-benefit state has a translation",
    all(
        f"screen.moostensuraaddon.recognition.benefit_state.{state}"
        in language
        for state in benefit_states
    ),
)

skill_registry = read(
    "src/main/java/com/mooswqz/moostensuraaddon/skill/SkillRegistry.java"
)
expect(
    "Granter and both Ultimate Skills are registered",
    all(
        f'SKILLS.register("{skill_id}"' in skill_registry
        for skill_id in (
            "granter",
            "benevolent_empowerment",
            "absolute_governance",
        )
    ),
)

network = read(
    "src/main/java/com/mooswqz/moostensuraaddon/network/NetworkRegistry.java"
)
expect(
    "Network protocol and core client/server payloads remain registered",
    '.versioned("11")' in network
    and all(
        marker in network
        for marker in (
            "SelectSkillPayload::handle",
            "ExecuteUltimateMultiGrantPayload::handle",
            "OpenRecognitionProgressScreenPayload",
            "SyncRecognitionDisplayNamePayload",
            "SyncRecognitionBenefitsPayload",
        )
    ),
)

command_registry = read(
    "src/main/java/com/mooswqz/moostensuraaddon/command/ModCommandRegistry.java"
)
command_root = read(
    "src/main/java/com/mooswqz/moostensuraaddon/command/MoosTensuraCommand.java"
)
expect(
    "Canonical public and permission-gated debug command surfaces remain wired",
    "MoosTensuraCommand.register(" in command_registry
    and "DebugCommand.attachToMoosTensuraRoot(" in command_registry
    and all(
        f'Commands.literal("{literal}")' in command_root
        for literal in ("moostensura", "guide", "paths", "help")
    ),
)
expect(
    "Legacy self-endowment remains administrator-only",
    "GetNamedCommand.register(" in command_registry
    and ".hasPermission(2)" in read(
        "src/main/java/com/mooswqz/moostensuraaddon/command/GetNamedCommand.java"
    ),
)

config = read(
    "src/main/java/com/mooswqz/moostensuraaddon/config/MoosTensuraConfig.java"
)
migration_v4_match = re.search(
    r"private static void migrateToVersion4\(\)\s*\{(.*?)\n\s*\}",
    config,
    re.DOTALL,
)
migration_v4 = migration_v4_match.group(1) if migration_v4_match else ""
expect(
    "Great Sage altar uses the 60,000 EP release balance",
    "CURRENT_CONFIG_VERSION = 4" in config
    and '.defineInRange("requiredEp", 60_000.0D' in config
    and "GREAT_SAGE_RITUAL_REQUIRED_EP.set(60_000.0D);" in config
    and "migrateToVersion4();" in config,
)
expect(
    "Great Sage balance migration preserves custom EP values",
    "Double.compare(" in migration_v4
    and "GREAT_SAGE_RITUAL_REQUIRED_EP.get()," in migration_v4
    and "100_000.0D" in migration_v4
    and "GREAT_SAGE_RITUAL_REQUIRED_EP.set(60_000.0D);" in migration_v4,
)
expect(
    "Legacy self-endowment remains balanced at 200,000 EP",
    '.defineInRange("requiredEp", 200_000.0D' in config
    and "SELF_ENDOWMENT_REQUIRED_EP.set(200_000.0D);" in config,
)

router = read(
    "src/main/java/com/mooswqz/moostensuraaddon/ritual/GreatCrystalAltarInteractionRouter.java"
)
pending_index = router.find("hasPendingReveal(player)")
sage_index = router.find("shouldPrioritizeGreatSage(player)")
recognition_index = router.find("shouldHandleNaming(player)")
expect(
    "Altar routing remains pending reveal, Great Sage, then new recognition",
    -1 < pending_index < sage_index < recognition_index,
)

required_resources = [
    "assets/moostensuraaddon/blockstates/great_crystal_altar.json",
    "assets/moostensuraaddon/models/block/great_crystal_altar_lower.json",
    "assets/moostensuraaddon/models/block/great_crystal_altar_upper.json",
    "assets/moostensuraaddon/models/item/great_crystal_altar.json",
    "assets/moostensuraaddon/models/item/soul_resonator.json",
    "data/moostensuraaddon/recipe/soul_resonator.json",
    "data/moostensuraaddon/loot_table/blocks/great_crystal_altar.json",
    "data/moostensuraaddon/tags/worldgen/structure/great_crystal_shrines.json",
]
expect(
    "Core altar, Resonator, recipe, loot, and structure-tag resources exist",
    all((RESOURCES / relative).is_file() for relative in required_resources),
)

variants = ("temperate", "hot", "cold", "cave")
expect(
    "All four Great Crystal Shrine variants and biome tags exist",
    all(
        (
            RESOURCES
            / f"data/moostensuraaddon/structure/great_crystal_shrine_{variant}.nbt"
        ).is_file()
        and (
            RESOURCES
            / f"data/moostensuraaddon/worldgen/structure/great_crystal_shrine_{variant}.json"
        ).is_file()
        and (
            RESOURCES
            / f"data/moostensuraaddon/tags/worldgen/biome/has_structure/great_crystal_shrine_{variant}.json"
        ).is_file()
        for variant in variants
    ),
)

model_frames = list(
    (RESOURCES / "assets/moostensuraaddon/models/item")
    .glob("soul_resonator_[0-9][0-9].json")
)
texture_frames = list(
    (RESOURCES / "assets/moostensuraaddon/textures/item")
    .glob("soul_resonator_[0-9][0-9].png")
)
expect(
    "Soul Resonator has exactly 32 model and texture frames",
    len(model_frames) == 32 and len(texture_frames) == 32,
)

readme = read("README.md")
expect(
    "README documents the supported runtime and core release features",
    all(
        marker in readme
        for marker in (
            "Minecraft 1.21.1",
            "NeoForge 21.1.234",
            "Java 21",
            "## Soul Recognition",
            "## Great Crystal Shrines and the Soul Resonator",
            "## Granter",
            "## Ultimate Skills",
            "## Character Reset and Save Safety",
        )
    ),
)

print("Release Hardening RC1 — Final Readiness Validation")
print("================================================")

failed = 0
for name, passed, detail in CHECKS:
    if passed:
        print(f"[PASS] {name}")
    else:
        print(f"[FAIL] {name}")
        if detail:
            print(f"       {detail}")
        failed += 1

if WARNINGS:
    print()
    print(f"WARNINGS ({len(WARNINGS)})")
    for warning in WARNINGS:
        print(f"[WARN] {warning}")

print()
if failed:
    print(f"RESULT: FAIL ({failed} failed checks)")
    raise SystemExit(1)

print("RESULT: PASS" + (" WITH WARNINGS" if WARNINGS else ""))
