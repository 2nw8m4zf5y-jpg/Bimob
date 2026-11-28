#!/usr/bin/env python3
"""Validate six canonical native parity outputs."""
from pathlib import Path
import json
import sys
ROOT = Path(__file__).resolve().parents[1] / "build" / "parity-out" / "native"
REQUIRED = ("crud_batch.json", "cascade_delete.json", "walljoin_topologies.json", "cycle_rollback.json", "engine_limits.json", "undo_redo_gestures.json")
errors=[]
for name in REQUIRED:
 p=ROOT/name
 if not p.is_file(): errors.append(f"missing: {p}"); continue
 raw=p.read_bytes()
 if not raw: errors.append(f"empty: {p}"); continue
 try: data=json.loads(raw.decode("utf-8"))
 except Exception as exc: errors.append(f"invalid JSON {p}: {exc}"); continue
 if not isinstance(data,dict) or not isinstance(data.get("projectId"),str): errors.append(f"not canonical project JSON: {p}")
if errors: print("\n".join(errors),file=sys.stderr);raise SystemExit(1)
print(f"Validated all {len(REQUIRED)} native snapshots in {ROOT}")
