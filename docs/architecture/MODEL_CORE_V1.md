# BIMOB Model Core v1.0

Status: implementation baseline | Version 1.0 | 2026-11-23

## Principles
- One canonical model powers plan, 3D, elevations, sections, schedules and sheets.
- Stable UUIDs for all model entities. Internal units: metres, square metres, cubic metres, radians; UI conversion only.
- Parametric definitions are authoritative. B-Rep, meshes, GPU buffers and spatial indexes are derived caches.
- Mutations use validated commands with undo/redo; multi-step operations use transactions and rollback.
- Dependency graph recomputes only affected geometry, rooms, quantities, views and schedules.
- Offline-first, schema-versioned projects, explicit migrations, autosave/recovery; no silent data loss.
- UI, domain, persistence, geometry, renderer and import/export are decoupled through interfaces.

## Layers
Presentation (Compose) → Interaction (tools, pointer routing, snapping, selection, camera) → Application (commands, transactions, undo, lifecycle, jobs) → Domain (hierarchy, elements, types, parameters, materials, relationships) → Geometry (2D topology, parametric generators, exact geometry, tessellation, spatial index) → Renderer (2D + Filament 3D). Persistence uses SQLite metadata + binary geometry/assets + journal + manifest in .bimob package. Interop adapters: IFC, DXF, glTF, PDF, CSV/XLSX, BCF.

Domain cannot depend on Android UI, DB, renderer or adapters. UI→application→domain interfaces.

## Spatial model
Project → Site → Building → Levels → Elements/Spaces. Grids, Views, Sheets, Schedules, Families, Materials and project parameters are registries. IFC containment and other relationships are explicit records, not implied only by nesting.

## Elements
Entity: UUID, category, type/family ref, level ref, transform, typed parameters, material assignments, parametric definition, cache reference, flags, provenance and revision. Categories include wall/opening/door/window/floor/roof/stair/railing/room/column/beam/foundation/pipe/duct/cable tray/electrical device/furniture/annotation/generic.

## Wall v1
Straight axis, height, base/top constraints, thickness or layer stack, location-line rule, levels, joins, hosted openings and type. Layers: material ID, thickness, function, priority, core flag, wrap rule. Total thickness is derived. Openings are hosted objects; wall geometry and room boundaries are derived.

## Parameters
Definition: stable ID, name, datatype, unit, scope (instance/type/project), group, readonly, formula and validation. Types: Bool, Integer, Real, String, Length, Area, Volume, Angle, MaterialRef, ElementRef, Enum, Formula. Formula uses parser/AST and dependency graph, never arbitrary code.

## Geometry
Definition (parameters/constraints), Exact (B-Rep/topology), Display (mesh/lines/LOD), Spatial (bounds/index). Only definition is authoritative. Cache key includes element revision, generator version and tolerance.

## Commands
Serializable command contract: execute, undo, validation, affected IDs. Journal only committed transactions. Initial commands: CreateProject/Level/Wall, MoveElement, ChangeParameter, DeleteElement, CreateOpening, AssignMaterial. UI-only state is not model history.

## Persistence
Package: manifest.json (schema/project/units), model.sqlite (entities/types/parameters/relationships/materials/levels/views/schedules), geometry/ (binary caches), assets/ (textures/families), journal/ (transactions), previews/. SQLite holds metadata and persisted domain state; large geometry is binary. Checksum, atomic save, checkpoint and recovery required. Alpha JSON is allowed only behind a repository interface.

## Dependencies
Level elevation→wall constraints→height→geometry→openings→room boundaries→area/volume→schedules/views. Dirty nodes have revisions. Worker results are immutable; preview may be approximate, committed geometry validated.

## Renderer
Consumes render packets/snapshots. MVP 2D canvas; later Filament, camera, mesh/material pipeline, edges, sections, shadows, AO, clipping, LOD, culling, instancing. GPU cache invalidated by revision.

## UX
Phone viewport-first, contextual tools, bottom sheets, large targets, magnifier and snap feedback. Tablet optional project browser + properties. Tool-aware gestures and stylus support.

## Roadmap gates
A: restart-safe save/load round-trip, stable IDs, model-space doubles, undo/redo create/delete/move, no data loss.
B: typed domain model, hierarchy, parameters, wall definition, command history, repository/schema version, tests.
C: wall types/layers, hosted openings, floors, rooms, shared 2D/3D, quantity schedules, dimensions, PDF.
D: IFC round-trip fixtures, DXF underlay, glTF, loss reports.
E: STR/MEP graph, clash/audit, sheets, BCF, point clouds, automation API.

## Mobile performance
No exact Boolean on UI thread. Lightweight previews; background rebuild with progress/cancellation. LOD, frustum culling, batching/instancing, cache invalidation, spatial partitioning. Thermal/battery state may lower rendering quality, never model accuracy. Low-memory recovery is required.

## Tech baseline
Kotlin + Compose; Kotlin domain initially; C++ behind GeometryEngine as justified by profiling; JNI; Filament; SQLite; OCCT candidate after Android/size/memory/license validation; IfcOpenShell candidate after build and license audit; robust planar geometry/JTS; CMake/NDK.

## Implementation order
1 typed domain and IDs; 2 wall model in metres/doubles; 3 CommandBus transactions (supersedes legacy ModelStore/commands); 4 adapt existing editor; 5 repository (JSON alpha, SQLite next); 6 tests/CI; 7 3D.

Definition of done: architecture document and compiling foundational model committed; current app remains runnable. Full BIM claims require feature gates and tests.

> **Architecture update:** The former `ModelStore` / `CommandHistory` prototype in `ModelCommands.kt` is retired. `CommandBus` is the sole canonical mutation path; this document's historical sequence is updated accordingly.
