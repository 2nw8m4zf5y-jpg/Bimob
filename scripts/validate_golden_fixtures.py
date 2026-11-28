#!/usr/bin/env python3
"""Validate fixture schema and required coverage (this is not engine parity)."""
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
REQUIRED_OPS = {
    "crud_batch.json": {"moveWall", "resizeWall", "batch", "createLevel", "createWall", "deleteWall", "undo", "redo"},
    "walljoin_topologies.json": {"moveWallEndpoint", "deleteJoin", "moveWall", "undo", "redo"},
    "cascade_delete.json": {"deleteWall", "undo", "redo"},
    "cycle_rollback.json": {"admitDependency", "assertRejected"},
    "engine_limits.json": {"limitCase"},
    "undo_redo_gestures.json": {"moveWall", "resizeWall", "moveOpening", "resizeOpening", "moveCatalogInstance", "resizeCatalogInstance", "undo", "redo"},
}
REQUIRED_LIMITS = {"dirty_nodes", "evaluations", "fan_out", "graph_nodes", "graph_edges", "dependency_depth"}

def flatten(commands):
    for command in commands:
        yield command
        if command.get("op") == "batch":
            yield from flatten(command.get("commands", []))

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
    initial = data.get("initialState")
    if not isinstance(initial, dict):
        errors.append(f"{path.name}: initialState must be an object")
    elif not isinstance(initial.get("project"), dict):
        errors.append(f"{path.name}: initialState.project must be an object")
    commands = data.get("commands")
    if not isinstance(commands, list):
        errors.append(f"{path.name}: commands must be an array")
        commands = []
    if not isinstance(data.get("assertions"), dict):
        errors.append(f"{path.name}: assertions must be an object")
    flat = list(flatten(commands))
    ops = {c.get("op") for c in flat if isinstance(c, dict)}
    required_ops = REQUIRED_OPS.get(path.name, set())
    absent_ops = required_ops - ops
    if absent_ops:
        errors.append(f"{path.name}: missing operations: {', '.join(sorted(absent_ops))}")
    if path.name == "engine_limits.json":
        budgets = {c.get("budget") for c in flat if c.get("op") == "limitCase"}
        if budgets != REQUIRED_LIMITS:
            errors.append(f"{path.name}: limit cases mismatch; missing={sorted(REQUIRED_LIMITS-budgets)}, extra={sorted(budgets-REQUIRED_LIMITS)}")
    if path.name == "undo_redo_gestures.json":
        gestures = [c for c in flat if c.get("gestureId")]
        if not gestures:
            errors.append(f"{path.name}: no gestureId commands")
        rejected = [c for c in flat if c.get("expectRejected") is True]
        if not rejected:
            errors.append(f"{path.name}: no explicit rejected command case")
        gesture_ops = {c.get("op") for c in gestures}
        for kind, expected_ops in {
            "Wall": {"moveWall", "resizeWall"},
            "Opening": {"moveOpening", "resizeOpening"},
            "CatalogInstance": {"moveCatalogInstance", "resizeCatalogInstance"},
        }.items():
            if not expected_ops <= gesture_ops:
                errors.append(f"{path.name}: missing gesture commands for {kind}: {sorted(expected_ops-gesture_ops)}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f"Fixture contract OK: {len(files)} JSON fixtures; required operations and budget coverage present.")
print("This is fixture-contract validation only. Kotlin/C++ runners and parity diff are not implemented by this script.")
