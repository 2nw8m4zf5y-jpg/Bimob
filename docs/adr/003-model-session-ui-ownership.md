# ADR 003 — ModelSession and UI Ownership

**Status:** Implemented; final Stage F acceptance pending CI on current HEAD  
**Date:** 2026-11-23  
**Branch:** `feature/model-core-v1`

## Context

Stage E closed the dependency engine and revision transaction boundary. The Android editor previously maintained a mutable local `walls` list in MainActivity and directly edited it from Canvas, creating a second source of truth outside CommandBus.

## Decision

Use the boundary:

`Compose UI / Canvas → ModelSession → CommandBus → ModelRevision → ProjectRepository`

- ModelSession is the serialized application façade. It owns the CommandBus and publishes immutable SessionSnapshot values through StateFlow.
- SessionSnapshot carries the current ModelRevision, canUndo/canRedo, hasUnsavedChanges, diagnostics and optional transient gesture preview.
- All committed model changes are ModelMutations. Typed wall commands are AddWallCommand, MoveWallCommand, DeleteWallCommand and SetWallThicknessCommand.
- A gesture begins against one base revision. Preview transforms are ephemeral and do not add history entries. Drag end submits one semantic command; cancel discards the preview. Undo/Redo cancel an active preview before operating on CommandBus history.
- Dirty state compares saved revision content (Project, dependency graph, derived values), not merely revision number; this permits Undo back to saved content to become clean.
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

- F1 tests: gesture coalescing, cancel rollback, Undo/Redo, Engine limit rejection.
- F2 tests: deterministic revision codec and private repository round-trip; legacy migrator deterministic IDs and validation.
- F3 tests: typed wall mutation/history and Compose Canvas compile.
- CI green at commit `0340fd1`, run [#45184281677](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45184281677). Later F3 tests and saved-content dirty comparison are pending a final green run.
- Stage F is not formally closed until F4 documentation and final CI on the final branch head.

## Deferred / Yellow

- Device-level integration tests for actual pointer dispatch, cancellation and accessibility.
- Parent-directory fsync and crash recovery for the internal revision file.
- Native C++20 implementation and parity tests.
