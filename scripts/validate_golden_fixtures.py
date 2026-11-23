#!/usr/bin/env python3
"""Validate the shared golden fixture contract (not engine parity itself)."""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1] / "shared-tests" / "golden-scenarios"
REQUIRED = {
    "crud_batch.json",
    "walljoin_topologies.json",
    "cascade_delete.json",
    "cycle_rollback.json",
    "engine_limits.json",
    "undo_redo_gestures.json",
}
files = sorted(ROOT.glob("*.json"))
names = {p.name for p in files}
missing = REQUIRED - names
if missing:
    raise SystemExit(f"Missing required fixtures: {', '.join(sorted(missing))}")
errors = []
scenario_ids = set()
for path in files:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{path.name}: invalid JSON: {exc}")
        continue
    if data.get("schema") != "bimob.golden.v1":
        errors.append(f"{path.name}: schema must be bimob.golden.v1")
    sid = data.get("scenarioId")
    if not isinstance(sid, str) or not sid:
        errors.append(f"{path.name}: scenarioId missing")
    elif sid in scenario_ids:
        errors.append(f"{path.name}: duplicate scenarioId {sid}")
    else:
        scenario_ids.add(sid)
    if not isinstance(data.get("initialState"), dict):
        errors.append(f"{path.name}: initialState must be an object")
    if not isinstance(data.get("commands"), list):
        errors.append(f"{path.name}: commands must be an array")
    if not isinstance(data.get("assertions"), dict):
        errors.append(f"{path.name}: assertions must be an object")
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f"Fixture contract OK: {len(files)} JSON files; required coverage set present.")
print("NOTE: this validates fixture shape only; cross-language runner/diff remains a separate acceptance gate.")
