# Shared BIMOB Golden Scenarios (v1)

These JSON files define language-neutral input scenarios for Kotlin and C++20 parity tests.

**Important:** this directory is the fixture contract only. Until both runners consume the fixtures and emit canonical output, these files are not evidence of cross-language parity. Current Kotlin domain is Wall-centric and does not yet model the native Level/Opening/CatalogInstance/WallJoin types one-for-one. The parity implementation must explicitly close that model gap or report unsupported operations as a failing gate; it must never silently skip them.

## Format

Each fixture:
- `schema`: exact fixture schema identifier;
- `scenarioId`: stable identifier;
- `description`: human-readable case;
- `initialState`: explicit, deterministic model state (IDs are fixed; no generated UUIDs);
- `commands`: ordered operations with stable IDs and explicit gesture IDs;
- `assertions`: state/history/diagnostic expectations.

All distances use metres; angles use radians. Canonical JSON output must sort object keys and collections by stable ID or explicit evaluation order. Floating-point values are rounded to 6 decimal places; negative zero must normalize to zero. Diagnostics and command results must be included in output.

## Coverage

- `crud_batch.json`: create/move/resize/delete and atomic batch behavior.
- `walljoin_topologies.json`: L/T/X, acute/obtuse and disconnect/edit cases.
- `cascade_delete.json`: host wall with hosted opening and catalog instance, delete and undo.
- `cycle_rollback.json`: dependency cycle rejection and transaction rollback.
- `engine_limits.json`: each of six native/Kotlin engine budgets.
- `undo_redo_gestures.json`: multi-step history and per-entity gesture coalescing expectations.

A runner must fail when a command is unsupported, an assertion is unmet, or a fixture cannot be consumed. No runner may filter scenarios by language.
