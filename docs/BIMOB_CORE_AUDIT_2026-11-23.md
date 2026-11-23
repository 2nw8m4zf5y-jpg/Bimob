# BIMOB Independent Core Audit

**Audit date:** 2026-11-23  
**Repository:** `2nw8m4zf5y-jpg/Bimob`  
**Audited branch:** `feature/model-core-v1`  
**Audited branch head (at audit start):** `a2fd9ca3bb91eb70df5c0e9b3109ce3d2eac13aa`  
**PR:** #1, open and draft; base `main`; no merge performed.  
**Scope:** source/documentation review through GitHub connector. No local build was run.

## Executive verdict

**Core G2: PARTIALLY IMPLEMENTED / NOT ACCEPTED.** The branch contains a substantial Kotlin model/session/transaction/persistence implementation and a native C++20 transaction/dependency slice with typed BIM commands, wall-join integration, history, defensive limits, and contract tests. The feature branch is not yet independently verified at its current head.

**Product verdict: EARLY 2D WALL-PLAN MVP.** The Android app can create/open projects, draw and move walls, select/delete walls, change wall thickness, Undo/Redo, save privately, and import/export BIMOB archives. It is still a very limited editor; it does not yet behave like a professional CAD/BIM application.

## Evidence and verification limits

- PR #1 currently reports head SHA `a2fd9ca3bb91eb70df5c0e9b3109ce3d2eac13aa`, base SHA `44cc928ff03f865baa0227014c1320ac64a96554`, state open, draft, not merged.
- The repository's existing `docs/BIMOB_CORE_STATUS.md` is an accumulated chronology and contains conflicting/stale “remaining work” lists. Its last sprint notes correctly say the final native additions were not verified.
- The GitHub connector returned no workflow runs for the current PR head and no commit status checks. The web browser could not load the Actions page. The user reported that the Actions flag is green, but that green run could not be tied independently to the audited SHA.
- This report is source-review evidence only. No compile, CTest, Kotlin test, Android build, or device test was run as part of this audit.

## Implementation inventory

### Kotlin Core — implemented in source

- Domain model and validation.
- Dependency graph, evaluator registry, recompute engine, engine budgets.
- CommandBus with serialized mutation staging, validation, recomputation, publication, revision checks, Undo/Redo, bounded history and memory accounting, event queue/backpressure.
- ModelSession façade exposes scalar/wall-oriented UI snapshot and commands, gesture preview/commit/cancel, save/export.
- Project persistence includes archive format, canonical JSON, revision codec, repository abstraction, crash-recovery journal and legacy migration.
- Tests exist for CommandBus, dependencies, model validation, wall commands, ModelSession, archive/revision/persistence and repository revision behavior.
- Golden JSON resources exist for canonical project serialization. These are not evidence of full Kotlin↔C++ engine parity.

### Native C++20 Core — present in source

- Platform-independent CMake library; strict warnings-as-errors; sanitizer option.
- Domain validation and explicit errors.
- Immutable revision snapshot structure; shared project/graph/derived state and wall entity references.
- CommandBus staging, validation, recomputation and publish-after-success; Undo/Redo; gesture coalescing.
- Deterministic dependency graph scheduling and cycle-path diagnostics.
- Six EngineLimits and structured diagnostics.
- Cancellation token/checkpoints and history count limit.
- Collection-level COW wrapper (detach on mutation; not a persistent per-entity tree).
- WallJoin solver and validation of connected wall/opening geometry.
- Cascade deletion helpers for walls/levels.
- Typed command factories for Create/Move/Resize/Delete Level, Wall, Opening, CatalogInstance.
- Batch mutation stages sequentially and is published as one transaction/revision/Undo.
- Dirty propagation and join-graph synchronization integrated into the native command path.
- Native contract tests cover model validation, command history/rollback, gesture coalescing, limits, evaluator/cycle behavior, graph stress, COW, cascades, WallJoin, typed commands/Batch and a coalescing test across entity kinds.

### Android App — current functionality

- Jetpack Compose shell.
- Home/project list, create project, open project.
- 2D canvas for walls.
- Draw wall by drag; select and move wall; delete selected wall.
- Set wall thickness numerically.
- Undo/Redo.
- Fixed canvas scale (80 px/m), fixed 0.1 m snapping and fixed grid spacing (0.5 m).
- Private revision snapshots for persistence; SAF import/export.
- No zoom or pan controls; no dimensions/annotations, room tool, levels, openings/windows/doors, BIM catalog placement, layer management, or 3D view.
- Canvas renders walls as fixed-width strokes, not true wall-thickness geometry.

## Architecture boundary

**Important:** the installed Android APK currently uses the Kotlin Core. Native C++ Core is not connected to the app through JNI or a production adapter. Therefore, testing the APK does not exercise the C++ engine. Native Core CI and Android app testing are separate validation targets at this stage.

## Test coverage assessment

| Area | Source evidence | Audit assessment |
|---|---|---|
| Kotlin command lifecycle | Unit tests and CommandBus source | Implemented; CI at audited SHA not verified |
| Kotlin dependencies | Graph/recompute tests | Implemented; CI not verified |
| Kotlin ModelSession | Session tests | Implemented; CI not verified |
| Kotlin persistence | Archive/revision/journal tests | Substantial; durability depends on storage adapter contracts and platform testing |
| Native transactions | Native contract tests and CommandBus source | Implemented slice; CI not verified at head |
| Native typed lifecycle | Native tests | Covered at command scenario level; CI not verified |
| Native Batch rollback | Native test | Covered in source |
| Native WallJoin | Solver/integration tests | Covered for selected cases; broader topology edge cases remain |
| Native gesture coalescing | Test checks several entity kinds and stack depth | **Coverage gap:** explicit Undo/Redo restoration is only asserted for the initial Wall move, not separately for every entity-kind/resize gesture |
| Cross-language parity | C++ synthetic `goldenParity()` test and Kotlin golden JSON files | **Not implemented comprehensively:** no demonstrated shared fixture runner comparing Kotlin-produced results to native results |
| Current full CI | Connector returned no current run/status | **NOT VERIFIED** |
| Android on-device | User installed and tried APK | User-confirmed install/run; functionality described as very rough |

## Red flags

1. **CI provenance gap:** green status must be matched to exact current SHA `a2fd9ca3...`, not merely the latest visible green run.
2. **Dual implementation drift:** Kotlin Core and native Core are independently implemented. Native code is not yet the production engine used by Android.
3. **Parity gap:** the native test named `goldenParity()` is a self-contained C++ scenario with hard-coded expected values. It does not compare outputs against Kotlin-generated fixtures. The Kotlin golden JSON fixtures are serialization fixtures, not engine-parity evidence.
4. **Gesture test weakness:** coalescing is asserted for multiple entity kinds, but each entity-kind's Undo/Redo restoration is not individually asserted.
5. **Status document drift:** `docs/BIMOB_CORE_STATUS.md` repeats historical snapshots and lists features as outstanding even after later sections claim implementation. It should be consolidated into a single current state plus a short history.
6. **Native COW scope:** collection-level detachment still clones the modified collection; this is not a persistent per-entity map/tree and should not be marketed as zero-copy model editing.
7. **UI limitations:** no viewport pan/zoom; fixed scale makes larger plans difficult to navigate; wall rendering is schematic; no dimensioning or room/BIM objects.
8. **No acceptance yet:** G2 should remain NOT ACCEPTED until exact-head CI is verified and parity/coverage gates are closed.

## Status matrix

| Stage/component | Status | Reason |
|---|---|---|
| Stage F ModelSession/UI boundary | IMPLEMENTED IN KOTLIN | Source and tests present; current CI not independently verified |
| Stage G1 model contracts | IMPLEMENTED | Kotlin and native contract sources present |
| Native G2 transaction engine | PARTIAL | Source contains transactions, history, limits, dependency recompute |
| Typed entity lifecycle | IMPLEMENTED IN NATIVE SOURCE | Commands/tests present; CI provenance not verified |
| BatchCommand atomicity | IMPLEMENTED IN NATIVE SOURCE | Contract test present; CI provenance not verified |
| WallJoin + dependency integration | IMPLEMENTED IN NATIVE SOURCE, PARTIAL EDGE COVERAGE | Integration test exists; broader cases/parity open |
| Cross-language golden parity | NOT IMPLEMENTED COMPREHENSIVELY | No shared engine-output parity suite found in reviewed evidence |
| JNI/native Android integration | NOT IMPLEMENTED | Deliberately deferred |
| Persistence | IMPLEMENTED IN KOTLIN | Archive, revision storage and journal sources/tests present |
| 2D editor | MVP / VERY EARLY | Wall-only, fixed viewport |
| Full CI at audited SHA | NOT VERIFIED | Connector could not return current workflow/status |

## Recommended next steps (ordered)

### Gate 1 — verify current CI
Obtain the Actions run URL or use GitHub Actions directly to confirm the run commit SHA matches `a2fd9ca3...`. Check native Configure/Build/CTest (including ASan/UBSan), Kotlin tests, Android tests, assembleDebug and artifact upload. Do not accept a stale green run.

### Gate 2 — genuine Kotlin↔C++ Golden parity
Define a shared fixture schema and have Kotlin produce fixtures for:
- model revisions and normalized model state;
- derived values and evaluation order;
- error/cycle diagnostics;
- each EngineLimits budget;
- transaction rollback;
- Undo/Redo and coalesced gestures.

Consume the same fixture files from C++ tests and compare canonical results. Start with a small deterministic set; expand by coverage, not by creating a giant test framework.

### Gate 3 — strengthen transaction tests
For Wall, Opening, CatalogInstance and Level: verify coalesced final state, Undo to the exact initial state, Redo to the exact final state, gesture boundaries, unrelated IDs and rejected mutations. Add multi-join and host-dependent geometry edge cases.

### Gate 4 — update the status ledger
Consolidate `docs/BIMOB_CORE_STATUS.md` so it has one authoritative current snapshot and a compact history. Avoid contradictory checklists and repeated old CI statuses.

### Gate 5 — product MVP
Prioritize a navigable canvas (pan/zoom), dimensions, reliable wall selection/editing, wall thickness visual representation, project save/open feedback and simple room detection before adding a broad tool palette. Keep UI commands routed through ModelSession.

## Acceptance rule

G2 remains **NOT ACCEPTED** until:
- exact-head full CI is green and verifiable;
- Kotlin↔C++ golden parity is implemented for the agreed core contracts;
- rollback, gesture Undo/Redo and dependency integration tests are complete enough to support the claims;
- status documentation is consolidated and matches the code.

Do not merge PR #1 or modify `main` without explicit owner authorization.
