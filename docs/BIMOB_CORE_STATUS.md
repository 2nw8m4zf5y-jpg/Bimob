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


### Atomic Host -> Hosted deletion
- Added mutation helpers `deleteWallCascade` and `deleteLevelCascade`. Wall deletion removes its hosted openings, hosted catalog instances and wall joins. Level deletion cascades through its walls and their hosted dependents, and removes level-bound catalog instances.
- CommandBus transaction tests confirm Undo restores the complete host/dependent set.


### Deterministic cycle diagnostics and limit contract tests
- Cycle admission now computes an explicit closed cycle path using deterministic iterative DFS over sorted node/adjacency order; diagnostic includes path and starting node ID (example `A -> B -> C -> A`).
- Native tests assert exact cycle message and exact `code/budget/actual/limit/nodeId` fields for all six EngineLimits failures.
- This improves native diagnostic determinism. **Cross-language golden comparison against Kotlin-produced fixtures is still not completed.**

- Added 10,000-wall collection COW stress: copied project shares storage until a single wall mutation; mutation detaches only the walls collection and preserves source snapshot.
- Added negative WallJoin test: shortened connected wall causes its hosted opening to become out-of-bounds; solver rejects candidate and source remains unchanged.

## Current verification snapshot (2026-11-23)

- **Run #238 / commit `8c9679049f48`: FULL CI SUCCESS** (native build, CTest, Kotlin/Android tests, APK assembly/upload). This verified the COW collection wrapper and hosted-delete cascade implementation.
- **Run #240 / commit `68cc7544e713`: native Configure, Build, and CTest SUCCESS**. Kotlin/Android phase was still running when checked.
- Run #239 failed because cycle helper declarations were missing from that intermediate commit; fixed in #240.
- **Run #242 / current tested commit `46b8a8547be6`: IN PROGRESS**. Includes 10,000-wall COW stress and wall-join opening rollback assertions; no full acceptance claim until this run completes.
- Current work branch remains `feature/model-core-v1`; `main` is untouched; PR #1 remains open.

### Acceptance status
Partial G2 progress only. Implemented and tested in CI: collection-level COW, bounded Undo depth, cancellation checkpoints, anchored WallJoin helper, host cascades, deterministic cycle-path diagnostics, exact six-budget error contracts, and graph stress. Still outstanding: Kotlin-generated golden parity fixtures, typed Create/Move/Resize/Delete commands and BatchCommand, WallJoin integration into dependency-backed transactions, comprehensive cancellation under concurrent token flips, and richer 10k BIM entities parity/stress. G2 is **NOT ACCEPTED**.


## Final G2 sprint increment (2026-11-23)

- Added typed mutation factories in native-core for Create/Move/Resize/Delete of Level, Wall, Opening and CatalogInstance. Wall moves/end-point moves/resizes use the anchored WallJoin solver; wall and level deletion use the cascade helpers.
- Added atomic `commands::batch`: child mutations stage in sequence against a private Project candidate; the CommandBus validates and publishes once, so the batch is one revision/Undo entry and any failed child rejects the entire candidate.
- CommandBus `execute` now derives dirty node seeds from changed/added/deleted walls, levels, openings, catalog instances and joins, then recomputes before publishing. Join graph nodes are synchronized as a DAG (`wall:*:geometry -> join:*:geometry -> hosted opening/catalog dependent nodes`) and stale join-derived edges are removed when joins change.
- Added native tests for typed command lifecycle, batch rollback/revision/Undo behavior and WallJoin dependency recompute/rollback.
- Implementation commits: `abe1714` (typed API), `b5addc6` (typed mutations), `ae2cfb2` / `2022b89` / `0af4a2b` / `e99bebe` / `c29d2ce` (join graph/dirty propagation and fixes), `9788cd0` / `07aaea1` (contract tests).
- **Verification limitation:** This environment cannot clone GitHub (network DNS unavailable), and the GitHub Actions connector is not returning workflow runs/statuses for push commits. The latest native changes have therefore **not yet been verified by CMake/CTest or full CI** in this session. Do not accept G2 based on these commits until CI compiles/tests them.

### Still open / not accepted

- Kotlin-produced shared Golden fixtures and C++↔Kotlin exact comparison (derived values/order, cycle diagnostics and all six EngineLimits) are not complete.
- The typed factories are mutation values used with `CommandBus::execute`; command-specific transaction wrappers/gesture IDs are still caller-provided through the existing CommandBus API, and comprehensive per-command coalescing coverage is outstanding.
- Join recompute uses the native dependency graph conventions described above; it needs CI validation and cross-language parity confirmation.
- Latest complete CI green result for this sprint is not available yet. Stage G2 remains **NOT ACCEPTED**.


### Latest native test coverage extension

- Typed Move and Resize gesture coalescing is now covered for Wall, Opening and CatalogInstance (commit `d8c74f2`). This remains **unverified locally/remotely** until CMake/CTest succeeds; no PASS claim is made.


### Red-flag follow-up (2026-11-23)

- Review of the unverified native changes found a compile-time risk: `CatalogInstance` defaulted equality depends on `DimensionBounds` equality, but that operator was absent. Added default value equality to `DimensionBounds` in commit `355c7eb`.
- This is a confirmed source-level defect found by review, **not a confirmed explanation of the visible GitHub red check**, because workflow run/job logs are unavailable through the current GitHub connector. Latest status still requires CI verification.


## Owner acceptance and Stage G2 kickoff (2026-11-24)

- **Stage G · Step G1: ACCEPTED / CLOSED by owner** following real-device validation of APK from Run #542. Compact toolbar, home menu, grouped catalog, 92 dp properties panel, and numeric property editing were confirmed working.
- **Stage G · Step G2: IN PROGRESS** on `feature/model-core-v1`; `main` remains untouched.
- Initial precision-drawing implementation: 48 dp offset crosshair, 22 dp screen-space snap radius, Endpoint > Edge > Ortho > Grid priority, soft 5° orthogonal snap and explicit Ortho lock, endpoint-handle preview and snap guides. Native renderer already derives endpoint/T wall joins; release remains one native wall mutation for Undo/Redo integrity.
- Branding/app identity transition to NAGAPLAS and `.nagaplas` project file extension are in scope for this isolated G2 commit; Kotlin/C++ namespace and JNI package paths remain `com.bimob.*` by design.
- CI and owner device validation for this G2 commit remain pending.
