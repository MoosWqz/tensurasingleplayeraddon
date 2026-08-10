#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

TAG_NAMES = [
    "civilians",
    "hostile_to_civilians",
    "major_enemies",
    "malevolent_bosses",
    "benevolent_bosses",
    "baby_kill_morality",
    "ignored",
]

RESOURCE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


def load_tags(root: Path):
    base = root / "src/main/resources/data/moostensuraaddon/tags/entity_type/recognition"
    tags = {}
    errors = []
    for name in TAG_NAMES:
        path = base / f"{name}.json"
        if not path.is_file():
            errors.append(f"missing {path}")
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"{path}: JSON parse failed: {exc}")
            continue
        if data.get("replace") is not False:
            errors.append(f"{path}: replace must be false")
        values = data.get("values")
        if not isinstance(values, list):
            errors.append(f"{path}: values must be a list")
            continue
        if len(values) != len(set(values)):
            errors.append(f"{path}: duplicate direct entries")
        for value in values:
            if not isinstance(value, str) or not value:
                errors.append(f"{path}: non-string/blank entry")
                continue
            if value.startswith("#"):
                if not value.startswith("#moostensuraaddon:recognition/"):
                    errors.append(f"{path}: unsupported tag reference {value}")
            elif not RESOURCE.fullmatch(value):
                errors.append(f"{path}: invalid resource location {value}")
        tags[name] = values
    return tags, errors


def expand(name: str, tags: dict[str, list[str]], seen=None):
    seen = set() if seen is None else set(seen)
    if name in seen:
        raise ValueError(f"recursive tag reference at {name}")
    seen.add(name)
    out = set()
    for value in tags[name]:
        prefix = "#moostensuraaddon:recognition/"
        if value.startswith(prefix):
            ref = value[len(prefix):]
            if ref not in tags:
                raise ValueError(f"{name}: missing referenced tag {ref}")
            out |= expand(ref, tags, seen)
        else:
            out.add(value)
    return out


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    registry_path = Path(
        sys.argv[2] if len(sys.argv) > 2
        else root / "docs/datapacks/entity_classification/verified_tensura_registry_ids.json"
    )
    tags, errors = load_tags(root)

    if not errors and registry_path.is_file():
        registry = set(json.loads(registry_path.read_text(encoding="utf-8")))
        for name, values in tags.items():
            for value in values:
                if value.startswith("tensura:") and value not in registry:
                    errors.append(f"{name}: unknown Tensura registry ID {value}")
    elif not registry_path.is_file():
        errors.append(f"missing registry manifest {registry_path}")

    effective = {}
    if not errors:
        try:
            effective = {name: expand(name, tags) for name in TAG_NAMES}
        except ValueError as exc:
            errors.append(str(exc))

    if effective:
        checks = [
            ("civilians/hostile", effective["civilians"] & effective["hostile_to_civilians"]),
            ("malevolent/benevolent", effective["malevolent_bosses"] & effective["benevolent_bosses"]),
        ]
        for label, overlap in checks:
            if overlap:
                errors.append(f"contradictory overlap {label}: {sorted(overlap)}")
        for scored in TAG_NAMES:
            if scored == "ignored":
                continue
            overlap = effective["ignored"] & effective[scored]
            if overlap:
                errors.append(f"ignored overlaps {scored}: {sorted(overlap)}")

    if errors:
        print("Recognition entity classification validation: FAIL")
        for error in errors:
            print(f"[FAIL] {error}")
        return 1

    print("Recognition entity classification validation: PASS")
    print(f"Validated {len(TAG_NAMES)} tag files.")
    print(f"Verified registry IDs: {len(registry)}.")
    for name in TAG_NAMES:
        print(f"[PASS] {name}: {len(tags[name])} direct / {len(effective[name])} effective")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
