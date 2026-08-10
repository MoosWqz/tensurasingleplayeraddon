#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

NAMING = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionNamingService.java"
PROGRESS = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/recognition/RecognitionProgressScreenService.java"

errors: list[str] = []
changed: list[str] = []

if not NAMING.is_file():
    errors.append(f"Missing {NAMING.relative_to(ROOT).as_posix()}")
if not PROGRESS.is_file():
    errors.append(f"Missing {PROGRESS.relative_to(ROOT).as_posix()}")

if errors:
    for error in errors:
        print(f"[FAIL] {error}")
    raise SystemExit(1)

# ---------------------------------------------------------------------------
# 1. Recognition eligibility:
#    native Tensura naming is metadata, not a Soul Recognition blocker.
#
# We deliberately remove only the status-selection branch that maps nativeNamed
# to ALREADY_NAMED. The nativeNamed/nativeName fields themselves remain intact
# and continue to be sent to the UI and diagnostics.
# ---------------------------------------------------------------------------
naming = NAMING.read_text(encoding="utf-8")

blocking_branch = re.compile(
    r"""
    \n[ \t]*\}\s*else\s+if\s*\(\s*nativeNamed\s*\)\s*\{
    \s*status\s*=\s*
    RecognitionNamingEligibility\.Status
    \s*\.ALREADY_NAMED\s*;
    """,
    re.VERBOSE,
)

matches = list(blocking_branch.finditer(naming))

if len(matches) == 1:
    naming_new = blocking_branch.sub("", naming, count=1)
    with NAMING.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(naming_new)
    changed.append("recognition eligibility")
elif len(matches) == 0:
    # Idempotent rerun: accept only if the blocking branch is already absent.
    if re.search(
        r"else\s+if\s*\(\s*nativeNamed\s*\).*?ALREADY_NAMED",
        naming,
        re.DOTALL,
    ):
        errors.append("Native-named blocking branch exists but did not match the guarded patch shape.")
    else:
        print("[INFO] Native-named eligibility blocker already absent.")
else:
    errors.append(
        f"Expected exactly one native-named eligibility blocker, found {len(matches)}. "
        "No guess-based multi-edit was applied."
    )

# ---------------------------------------------------------------------------
# 2. Defensive UI wording:
#    ALREADY_NAMED remains in the enum for source/network compatibility, but if
#    an older/external caller ever supplies it, the UI must not claim that a
#    native name permanently blocks Soul Recognition.
# ---------------------------------------------------------------------------
progress = PROGRESS.read_text(encoding="utf-8")

replacements = (
    (
        '"Tensura already recognizes another name."',
        '"A native Tensura name is already present."',
        "status heading",
    ),
    (
        '"Recognition naming cannot begin while a native name is active."',
        '"Soul Recognition remains available independently of the native name."',
        "status detail",
    ),
)

progress_new = progress
for old, new, label in replacements:
    if old in progress_new:
        progress_new = progress_new.replace(old, new, 1)
        changed.append(label)
    elif new in progress_new:
        print(f"[INFO] {label} already updated.")
    else:
        errors.append(f"Could not find the expected current-tree {label} string.")

if progress_new != progress:
    with PROGRESS.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(progress_new)

print("Release Hardening B2B1 — Native-Named Compatibility Recovery")
print("=============================================================")
for item in changed:
    print(f"[CHANGED] {item}")

if errors:
    print("")
    for error in errors:
        print(f"[FAIL] {error}")
    print("")
    print("RESULT: FAIL — current tree differed from the guarded patch assumptions.")
    raise SystemExit(1)

if not changed:
    print("[INFO] No production edits were needed; recovery appears already applied.")

print("")
print("RESULT: PASS")
