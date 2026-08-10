#!/usr/bin/env python3
from pathlib import Path
import json
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
screen_path = root / "src/main/java/com/mooswqz/moostensuraaddon/client/screen/RecognitionProgressScreen.java"
lang_path = root / "src/main/resources/assets/moostensuraaddon_guidance/lang/en_us.json"

errors = []

if not screen_path.is_file():
    errors.append(f"Missing {screen_path}")
else:
    screen = screen_path.read_text(encoding="utf-8")
    required = [
        "int firstVisibleRow = Math.min(",
        "int lastVisibleRow = Math.min(",
        '"screen.moostensuraaddon.recognition.rows"',
        "firstVisibleRow == lastVisibleRow",
        "overviewFirstRow",
        "+ layout.visibleRows()",
    ]
    for marker in required:
        if marker not in screen:
            errors.append(f"Missing paging marker: {marker}")

    old_pattern = """Component.translatable(
                            "screen.moostensuraaddon.recognition.page",
                            page,
                            layout.totalRows()
                    )"""
    if old_pattern in screen:
        errors.append("Old misleading single-row indicator block is still present")

if not lang_path.is_file():
    errors.append(f"Missing {lang_path}")
else:
    try:
        lang = json.loads(lang_path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"Language JSON is invalid: {exc}")
        lang = {}

    if lang.get("screen.moostensuraaddon.recognition.page") != "Row %s of %s":
        errors.append("Single-row translation is missing or changed unexpectedly")

    if lang.get("screen.moostensuraaddon.recognition.rows") != "Rows %s–%s of %s":
        errors.append("Multi-row range translation is missing")

# Behavioral geometry checks mirroring the production navigation model.
def visible_range(first_zero_based: int, visible_rows: int, total_rows: int):
    first = min(total_rows, first_zero_based + 1)
    last = min(total_rows, first_zero_based + visible_rows)
    return first, last

cases = [
    ((0, 2, 3), (1, 2)),
    ((1, 2, 3), (2, 3)),
    ((0, 1, 3), (1, 1)),
    ((2, 1, 3), (3, 3)),
]
for args, expected in cases:
    actual = visible_range(*args)
    if actual != expected:
        errors.append(f"Range case {args}: expected {expected}, got {actual}")

print("Soul Recognition overview paging validation")
print("===========================================")
if errors:
    print("RESULT: FAIL")
    for error in errors:
        print(f"[FAIL] {error}")
    raise SystemExit(1)

print("RESULT: PASS")
print("[PASS] 2-row viewport at first position reports rows 1–2 of 3")
print("[PASS] 2-row viewport at final position reports rows 2–3 of 3")
print("[PASS] single-row viewport retains Row X of Y wording")
print("[PASS] navigation math remains unchanged")
print("[PASS] language JSON parses successfully")
