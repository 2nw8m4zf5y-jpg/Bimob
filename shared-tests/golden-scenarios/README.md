# Shared BIMOB Golden Scenarios (v1)

These JSON files define language-neutral input scenarios intended for Kotlin and C++20 parity runners.

**Current limitation:** fixtures are not yet consumed by both engines. Until both runners emit canonical outputs and CI diffs them, this directory is only a test-contract definition, not proof of parity. The Kotlin model currently does not represent the native Level/Opening/CatalogInstance/WallJoin domain one-for-one. Unsupported commands must fail, never be silently skipped.

## Format

Each fixture includes:
- `schema`: exact schema identifier;
- `scenarioId`: stable scenario identifier;
- `description`;
- `initialState`: explicit state with deterministic IDs;
- `commands`: ordered commands, with explicit `gestureId` for gesture updates;
- `assertions`: expected state/history/diagnostic behavior.

Distances are metres and angles radians. Canonical output must sort object keys and collections by stable ID or explicit evaluation order, round finite numbers to 6 decimal places, normalize negative zero, and include state, diagnostics, command results, history and dependency evaluation order.

## Coverage

- `crud_batch.json`: CRUD and atomic BatchCommand.
- `walljoin_topologies.json`: L/T/X, acute/obtuse and disconnect/edit cases.
- `cascade_delete.json`: host wall with hosted opening and catalog instance, cascade delete and Undo/Redo.
- `cycle_rollback.json`: dependency cycle rejection and transaction rollback.
- `engine_limits.json`: each of the six EngineLimits budgets.
- `undo_redo_gestures.json`: multi-step Undo/Redo and per-entity gesture coalescing.

## Gesture failure semantics

Gesture updates are individual commands with the same `gestureId`, not an implicit atomic batch. A rejected command must leave model state and history unchanged at the rejection point. Previously accepted updates in that gesture remain committed/coalesced and must not be retroactively discarded.

A runner must fail on unsupported operations, unmet assertions, or an unconsumed fixture. No runner may filter scenarios by language.
