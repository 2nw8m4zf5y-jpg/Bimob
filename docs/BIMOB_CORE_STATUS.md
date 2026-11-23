# BIMOB Core Status

**Date:** 2026-11-23  
**Repository:** 2nw8m4zf5y-jpg/Bimob  
**Active branch:** `feature/model-core-v1`  
**PR:** #1 open; do not merge without explicit authorization.  
**main:** untouched.

## Summary

- Stage F: accepted by owner; snapshot-only ModelSession boundary and scalar UI command API.
- Stage G1: model contract implemented; final full-branch CI confirmation is part of current G2 CI.
- Stage G2: initial native transaction/dependency port implemented; **not yet accepted**.
- No JNI, Android, rendering, or platform dependencies in native-core.

## Commits

| Commit | Scope | Status |
|---|---|---|
| `73ba2b91da41` | Stage F snapshot-only UI boundary | CI green, run #224 |
| `5a25b11276bc` | Initial G2 C++20 port | CI failed on strict indentation warning |
| `e1d8466e96cb` | Warning fix attempt | CI exposed a literal escaped newline |
| `82f7346fc529` | Stage F verification and blocker documentation | CI failed on the escaped newline |
| `d75e5917a100` | Fix literal newline; indexed deterministic scheduler | Run #229 full CI green (recorded below) |

The successive failed G2 runs were failure-driven fixes, not accepted builds. Run #229 is the first CI verification of the corrected indexed scheduler.

## Native implementation

### G1 model contracts
- C++20; CMake/CTest.
- `kCoordinateEpsilon = 1e-9`, `kMinWallLength = 1e-6`.
- Deterministic validation; explicit issue codes; no exception-based public model validation.

### G2 initial slice
- Immutable revision fields (`const`) with shared const Project/graph/derived-value/entity-store snapshots.
- Unchanged wall entity objects shared between revision snapshots.
- Command staging, validation and recomputation happen before publication; failures leave current revision/history unchanged.
- Undo/Redo restore immutable revision snapshots.
- Gesture IDs coalesce consecutive updates into one undo entry.
- Sorted graph nodes/edges and adjacency; FIFO Kahn scheduling. Hash index is lookup-only and never iterated.
- Direct dependency values only are passed to evaluators.
- Immutable base evaluator registry and transaction-local overrides.
- All six budgets: dirty nodes 100,000; evaluations 1,000,000; fan-out 10,000; graph nodes 250,000; graph edges 1,000,000; dependency depth 4,096.
- Budget errors include code, budget name, actual, maximum, and node when applicable.

## Remote CI verification\n\n- **Run #229** on code commit `d75e5917a100`: **SUCCESS**. Configure CMake, C++ build, CTest/ASan+UBSan, Kotlin core tests, Android unit tests, `assembleDebug`, and APK upload all succeeded. [Run #229](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45189756919). APK artifact `Bimob-debug-apk` is available on that run.\n- Documentation-only head commit `111f9b54c21d` run #230 is still running at the time of this update; native Configure/Build/CTest steps are already green, Kotlin/Android is in progress.\n\n## Local verification (ASan + UBSan)

- Configure: PASS
- Build with warnings-as-errors: PASS
- CTest: 1/1 PASS
- Command lifecycle, Undo/Redo, validation rollback, coalescing, concurrent writers, six budgets, evaluator failure, registry override isolation, golden scenario: PASS
- Stress: 10,050 nodes / 10,049 edges / 10,050 evaluator calls, each node evaluated once; approximately **349 ms** locally with sanitizers enabled.

## Red Flags / remaining acceptance gates

1. Run #229 is green for code commit `d75e5917a100`. The current docs-head CI run #230 remains pending; keep final acceptance tied to its completion. G2 still requires the parity and feature gaps below before acceptance.
2. “Golden parity” currently means deterministic expected-value contract scenarios. The Kotlin-produced fixture set has not yet been exported and compared comprehensively across 100% of scenarios.
3. Exact Kotlin cycle-path diagnostics are not yet ported.
4. Cancellation checkpoints, event dispatch/backpressure, history count/memory caps, and no-op semantics are not fully ported.
5. The native project/wall domain is intentionally smaller than the Kotlin BIM model; geometry, openings, levels, materials, persistence and richer command vocabulary remain future parity work.
6. Entity pointers are shared in the revision entity store, but the current value-based Project representation still copies its wall vector when producing a candidate. Further project-level persistent storage/COW is needed before claiming zero-copy full-model revisions.
7. JNI/Android production integration is explicitly deferred to G4–G5.

## Next work

1. Close the current remote CI gate.
2. Export Kotlin golden fixtures for revisions, derived values, ordering and error diagnostics.
3. Add exact parity checks and remaining rollback/history/cancellation behavior.
4. Expand model commands and persistent entity storage.
5. Only after G2 acceptance, begin G3 persistence parity. Keep main and PR merge untouched.


## Current G2 implementation increment (commit `6eca21643528`)

- Added native BIM value contracts: `Level`, wall level references, explicit `WallJoin`, `Opening` hosted by wall, and `CatalogInstance` with typed `ScalingMode`, target dimensions, per-dimension min/max bounds, asset ID, level/host references, category and schedule tag.
- Model validation now checks duplicate IDs, references, finite geometry/parameters, opening containment in host wall axis/height, catalog dimension bounds and host/level references.
- Added CTest contract scenario for valid opening/catalog instance plus invalid opening bounds and invalid catalog dimension.
- CI run #233 for commit `6eca21643528` is currently in progress. No acceptance claim until completion.
- **This is an initial model-domain slice, not yet a complete BIM engine:** Project collections still use value vectors; wall-join geometry solving, dependency wiring/recomputation, entity commands, BatchCommand, COW, history limits, cancellation checkpoints and Kotlin-generated cross-language goldens remain outstanding.


## G2 continuation work
- Run #235 for `037bc291345e`: **SUCCESS**, including strict native compilation and the full Android/Kotlin pipeline.
- Implemented configurable `HistoryLimits` default 100; oldest Undo snapshots are evicted on commit and Undo-stack growth during Redo.
- Added `CancellationToken` checkpoints before mutation/publication and during dirty traversal, scheduling and evaluation. Cancelled operations return before publishing a revision.
- Added pure `solveWallJoin`: moves the selected endpoint and explicit connected endpoint(s), keeps opposite endpoints anchored, and validates candidate wall/opening bounds before returning.
- Tests added for 3-entry history eviction, pre-cancel rollback, cancellation during recompute, and anchored endpoint geometry.
- **Not G2 acceptance:** COW collections, host-delete cascades, typed entity commands/BatchCommand, wall solver integration into dependency transactions, comprehensive Kotlin golden fixtures, exact cycle path diagnostics and stress coverage across 10k BIM entities remain outstanding.


### Project collection structural sharing
- `Project` entity collections are now backed by `CowVector<T>` shared storage. Copying a project/revision shares each collection; the first write detaches only the modified collection. Read access does not detach. A contract test verifies wall collection detachment and opening collection independence.
- This is collection-level COW, not yet per-entity persistent maps: modifying an entity within a collection clones that collection's vector. Unmodified sibling collections remain shared.
