#!/usr/bin/env python3
"""Verify all canonical Kotlin parity snapshots exist and are valid JSON."""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1] / "core" / "build" / "parity-out" / "kotlin"
REQUIRED = (
    "crud_batch.json",
    "walljoin_topologies.json",
    "cascade_delete.json",
    "cycle_rollback.json",
    "engine_limits.json",
    "undo_redo_gestures.json",
)
errors = []
for name in REQUIRED:
    path = ROOT / name
    if not path.is_file():
        errors.append(f"missing snapshot: {path}")
        continue
    payload = path.read_bytes()
    if not payload:
        errors.append(f"empty snapshot: {path}")
        continue
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except Exception as exc:
        errors.append(f"invalid JSON snapshot {path}: {exc}")
        continue
    if not isinstance(parsed, dict) or not isinstance(parsed.get("projectId"), str):
        errors.append(f"snapshot is not a canonical project object: {path}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f"Validated {len(REQUIRED)} canonical Kotlin snapshots in {ROOT}")
