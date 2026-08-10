#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
EXPECTED_HARDENING_VERSION = "1.4.0b4"

FAILURES: list[str] = []
WARNINGS: list[str] = []
PASSES: list[str] = []

TEXT_SUFFIXES = {
    ".java", ".json", ".toml", ".properties", ".gradle", ".kts",
    ".mcmeta", ".yml", ".yaml", ".txt"
}

INTERNAL_LABEL_PATTERNS = [
    ("Packet label", re.compile(r"\bPacket\s+[0-9A-Za-z]+(?:\.[0-9A-Za-z]+)+\b")),
    ("Packet identifier", re.compile(r"\bPacket_[A-Za-z0-9_.-]+\b")),
    ("UI packet label", re.compile(r"\bUI\.[A-Z0-9]+(?:\.[A-Z0-9]+)*\b")),
    ("QoL packet label", re.compile(r"\bQoL\.[A-Z0-9]+(?:\.[A-Z0-9]+)*\b", re.I)),
    ("6G packet label", re.compile(r"\b6G\.[A-Za-z0-9.]+\b")),
]

DEV_PATH_PATTERNS = [
    re.compile(r"[A-Za-z]:\\Users\\", re.I),
    re.compile(r"[A-Za-z]:\\Programme\\", re.I),
    re.compile(r"/mnt/data/", re.I),
    re.compile(r"/home/oai/", re.I),
]

SOURCE_DEBT_MARKERS = re.compile(r"\b(TODO|FIXME|XXX|HACK)\b", re.I)


def iter_text_files(base: Path):
    if not base.exists():
        return
    for path in base.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            yield path, path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue


def rel(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def line_matches(path: Path, text: str, regex, label: str, sink: list[str]):
    for lineno, line in enumerate(text.splitlines(), start=1):
        if regex.search(line):
            sink.append(f"{label}: {rel(path)}:{lineno}: {line.strip()}")


def get_property(text: str, key: str) -> str | None:
    match = re.search(rf"(?m)^\s*{re.escape(key)}\s*=\s*(.*?)\s*$", text)
    return match.group(1).strip() if match else None


gradle_props = ROOT / "gradle.properties"
if not gradle_props.is_file():
    FAILURES.append("Missing gradle.properties")
else:
    props = gradle_props.read_text(encoding="utf-8")
    mod_version = get_property(props, "mod_version")
    if mod_version == EXPECTED_HARDENING_VERSION:
        PASSES.append(f"gradle.properties mod_version = {EXPECTED_HARDENING_VERSION}")
    else:
        FAILURES.append(
            f"gradle.properties mod_version expected {EXPECTED_HARDENING_VERSION!r}, "
            f"found {mod_version!r}"
        )

    minecraft_version = get_property(props, "minecraft_version")
    if minecraft_version == "1.21.1":
        PASSES.append("minecraft_version = 1.21.1")
    else:
        WARNINGS.append(f"minecraft_version expected '1.21.1', found {minecraft_version!r}")

    neo_version = get_property(props, "neo_version") or get_property(props, "neoforge_version")
    if neo_version == "21.1.234":
        PASSES.append("NeoForge version = 21.1.234")
    else:
        WARNINGS.append(f"NeoForge property expected '21.1.234', found {neo_version!r}")

mods_toml = ROOT / "src/main/resources/META-INF/neoforge.mods.toml"
if not mods_toml.is_file():
    FAILURES.append("Missing src/main/resources/META-INF/neoforge.mods.toml")
else:
    toml = mods_toml.read_text(encoding="utf-8")
    if (
        'version="${mod_version}"' in toml
        or "version='${mod_version}'" in toml
        or '${file.jarVersion}' in toml
    ):
        PASSES.append("neoforge.mods.toml version is metadata-driven")
    else:
        literal = re.search(r'(?m)^\s*version\s*=\s*["\']([^"\']+)["\']', toml)
        if literal and literal.group(1) == EXPECTED_HARDENING_VERSION:
            PASSES.append(
                f"neoforge.mods.toml literal version matches {EXPECTED_HARDENING_VERSION}"
            )
        elif literal:
            FAILURES.append(
                f"neoforge.mods.toml version {literal.group(1)!r} does not match "
                f"{EXPECTED_HARDENING_VERSION!r}"
            )
        else:
            WARNINGS.append("Could not determine neoforge.mods.toml version source")

production_files = []
for prod in (ROOT / "src/main/java", ROOT / "src/main/resources"):
    production_files.extend(list(iter_text_files(prod) or []))

for path, text in production_files:
    for label, pattern in INTERNAL_LABEL_PATTERNS:
        line_matches(path, text, pattern, label, FAILURES)

    for pattern in DEV_PATH_PATTERNS:
        line_matches(path, text, pattern, "Developer-local path", FAILURES)

    line_matches(path, text, SOURCE_DEBT_MARKERS, "Source-debt marker", WARNINGS)

    # Standalone validation harnesses intentionally print their focused result when
    # launched directly; this is not normal mod-runtime logging.
    if path.suffix.lower() == ".java" and not path.name.endswith("ValidationHarness.java"):
        for marker in ("System.out.print", "System.err.print", ".printStackTrace("):
            for lineno, line in enumerate(text.splitlines(), start=1):
                if marker in line:
                    FAILURES.append(
                        f"Direct runtime console output: {rel(path)}:{lineno}: {line.strip()}"
                    )

if not any(
    x.startswith(("Packet label:", "Packet identifier:", "UI packet label:",
                  "QoL packet label:", "6G packet label:"))
    for x in FAILURES
):
    PASSES.append("No internal packet labels found under src/main")

if not any(x.startswith("Developer-local path:") for x in FAILURES):
    PASSES.append("No developer-local filesystem paths found under src/main")

if not any(x.startswith("Direct runtime console output:") for x in FAILURES):
    PASSES.append("No direct console output in runtime production classes")

PASSES.append("Standalone ValidationHarness console output is explicitly permitted")

registry = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/command/ModCommandRegistry.java"
if registry.is_file():
    text = registry.read_text(encoding="utf-8")
    if ".registerLegacyAlias(" in text:
        FAILURES.append("ModCommandRegistry still registers a standalone legacy alias")
    else:
        PASSES.append("No standalone development compatibility alias registration")
else:
    FAILURES.append("Missing ModCommandRegistry.java")

network_candidates = list((ROOT / "src/main/java").rglob("NetworkRegistry.java"))
if not network_candidates:
    WARNINGS.append("NetworkRegistry.java not found; protocol version not checked")
else:
    network_text = network_candidates[0].read_text(encoding="utf-8")
    protocol_11 = bool(
        re.search(r'versioned\s*\(\s*"11"\s*\)', network_text)
        or re.search(r'PROTOCOL(?:_VERSION)?\s*=\s*"11"', network_text)
        or re.search(r'PROTOCOL(?:_VERSION)?\s*=\s*11\b', network_text)
    )
    if protocol_11:
        PASSES.append("Network protocol 11 detected")
    else:
        WARNINGS.append(f"Could not prove protocol 11 from {rel(network_candidates[0])}")

version_literal = re.compile(r"\b1\.4\.0b(?:\d+)?\b")
for path, text in production_files:
    for lineno, line in enumerate(text.splitlines(), start=1):
        for match in version_literal.finditer(line):
            value = match.group(0)
            if value != EXPECTED_HARDENING_VERSION:
                WARNINGS.append(
                    f"Stale-looking version literal: {rel(path)}:{lineno}: {value}"
                )

print("Release Hardening A2 — Source Hygiene Audit")
print("===========================================")
for item in PASSES:
    print(f"[PASS] {item}")

if WARNINGS:
    print("")
    print(f"WARNINGS ({len(WARNINGS)})")
    for item in WARNINGS:
        print(f"[WARN] {item}")

if FAILURES:
    print("")
    print(f"FAILURES ({len(FAILURES)})")
    for item in FAILURES:
        print(f"[FAIL] {item}")

print("")
print("RESULT: " + ("FAIL" if FAILURES else ("PASS WITH WARNINGS" if WARNINGS else "PASS")))
raise SystemExit(1 if FAILURES else 0)
