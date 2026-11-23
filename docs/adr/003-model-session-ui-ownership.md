# ADR 003 — ModelSession and UI Ownership

**Status:** Implemented; Stage F code CI green; full-branch acceptance blocked by unrelated Stage G2 compile failure  
**Date:** 2026-11-23  
**Branch:** `feature/model-core-v1`

## Context

Stage E closed the dependency engine and revision transaction boundary. The Android editor previously maintained a mutable local `walls` list in MainActivity and directly edited it from Canvas, creating a second source of truth outside CommandBus.

## Decision

Use the boundary:

`Compose UI / Canvas → ModelSession → CommandBus → ModelRevision → ProjectRepository`

- ModelSession is the serialized application façade. It owns the CommandBus and publishes immutable SessionSnapshot values through StateFlow.
- SessionSnapshot is a strict UI DTO: revision number, project ID/name, immutable WallCanvasSnapshot list, element count, canUndo/canRedo, hasUnsavedChanges, diagnostics and gestureActive. It does **not** expose ModelRevision, Project, DependencyGraph, derived-value maps or command internals to Compose.
- Compose uses scalar façade methods (`addWall`, `moveWall`, `deleteWall`, `setWallThickness`) and scalar gesture-preview methods (`previewDrawWall`, `previewMoveWall`). The ModelSession translates these into typed core commands: AddWallCommand, MoveWallCommand, DeleteWallCommand and SetWallThicknessCommand.
- A gesture begins against one base revision. Scalar preview updates publish immutable Canvas DTOs but do not add history entries. Drag end submits exactly one semantic command with the captured expected revision; cancel discards the preview. Undo/Redo cancel an active preview before operating on CommandBus history.
- Dirty state compares saved revision content (Project, dependency graph, derived values), not merely revision number; this permits Undo back to saved content to become clean. Save and export execute under the ModelSession serialization lock; save records the exact revision successfully persisted.
- ModelRevisionCodec deterministically stores revision number, canonical Project, typed dependency nodes/edges and derived values. ProjectRepository writes and read-back verifies an exact revision through AtomicStorage.
- Android PrivateFileAtomicStorage owns internal files. SAF Uri/DocumentFile code remains only at external archive import/export boundaries.
- Legacy SharedPreferences sketch JSON is migrated with deterministic project/site/wall IDs, validation, then submitted through a ModelSession initialization mutation. SharedPreferences is no longer the authoritative model store.
- Compose UI must depend only on SessionSnapshot and command APIs. This façade is designed to be replaced by a C++20/JNI implementation without Compose depending on Kotlin model internals.

## Gesture contract

1. beginGesture captures a base revision.
2. updateGesture publishes a private preview snapshot only.
3. endGesture commits a single ModelMutation with expectedRevision.
4. cancelGesture drops preview and restores displayed base revision.
5. Engine-budget rejection is surfaced as a diagnostic; no partial revision is published.

## Verification

- F1 tests in `:core:test`: gesture coalescing (generic and scalar Canvas APIs), cancel rollback, Undo/Redo, Engine limit rejection, immutable preview collection freezing and concurrent command serialization.
- F2 tests in `:core:test`: deterministic revision codec and private repository round-trip; session save/dirty-state tracking; legacy migrator deterministic IDs and validation.
- F3 tests: typed wall mutation/history through public façade and Compose Canvas compile.
- Stage F code CI green at commit `73ba2b9`, run [#45188662818](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45188662818): `:core:test`, `testDebugUnitTest`, `assembleDebug`, native contract configure/build/ctest; debug APK artifact uploaded.
- Current documentation-head CI [#45189249052](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45189249052) is RED before Kotlin tasks because Stage G2 `native-core/src/engine.cpp:48` contains a literal backslash (`stray '\\'`). The G2 commits (`5a25b11`, `e1d8466`) were already on the shared feature branch and are outside Stage F scope; they were not changed in this work. Full branch-wide acceptance remains blocked until G2 build is repaired.
- Stage F implementation is complete, but it is not formally closed against the branch-wide CI gate until the unrelated Stage G2 native compile failure is resolved and the final branch head passes.

## Deferred / Yellow

- Device-level integration tests for actual pointer dispatch, cancellation and accessibility.
- Parent-directory fsync and crash recovery for the internal revision file.
- Native C++20 implementation and parity tests.
