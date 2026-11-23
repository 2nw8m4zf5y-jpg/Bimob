# BIMOB Kernel: extensible foundation

Status: architecture contract | Version 1.0 | 2026-11-23

This document defines rules for growing BIMOB without rewriting the core for every new tool or discipline.

## Core goal
The kernel owns the project model, invariants, command execution, dependency tracking, persistence contracts and extension interfaces. Feature modules add capabilities through stable contracts; they must not fork or directly mutate the model.

## Kernel responsibilities
1. Identity/model: UUIDs, typed entities, relationships, units, levels, constraints and parameters.
2. Validation: domain rules, integrity checks, version compatibility and capability checks.
3. Command engine: commands, transactions, undo/redo, conflict checks and rollback.
4. Dependency engine: graph, dirty propagation and incremental recompute scheduling.
5. Repository contract: versioned storage, migrations, atomic writes, autosave and recovery.
6. Events: immutable domain/application events, ordered delivery and schema versions.
7. Extension registry: tools, element types, property editors, geometry providers, import/export adapters, validators and schedules.
8. Jobs: background work with progress, cancellation, priority and result validation.
9. Capabilities: feature availability and platform/performance requirements.
10. Diagnostics: logs, model audit and deterministic test hooks.

## Module boundaries
- kernel-api: stable public contracts and IDs; minimal dependencies.
- kernel-domain: model, rules, parameters, relationships and units.
- kernel-application: command bus, transactions, history, events and jobs.
- kernel-repository-api: storage interfaces, migrations and recovery contracts.
- kernel-geometry-api: geometry definitions, generators, validation and cache contracts.
- kernel-interaction-api: tools, selection, snapping and input contracts.
- kernel-render-api: renderer-neutral scene snapshots/render packets.
- kernel-registry: extension discovery, registration and compatibility validation.
- feature-* modules: independently developed capabilities.
- platform-android: Android UI, sensors, permissions and device adapters.
- interop-* modules: IFC, DXF, glTF, PDF and exchange adapters.

Dependency direction is inward: features/platform/adapters depend on kernel contracts; the kernel never imports a feature or Android UI. Circular dependencies are forbidden.

## Extension manifest
Every feature registers a stable feature ID, semantic version, compatible kernel API range, capabilities, dependencies, tools and commands, element/type providers, property editors, geometry providers, validators, import/export formats, schedule providers, data migrations and permissions if needed.

Unknown optional feature data must be preserved on round-trip. Projects opened without a feature should show an unavailable-feature notice while retaining their data.

## Tool system
A tool is a state machine, not a button. It declares activation/deactivation, input requirements, preview, snapping policy, selection filters, command creation, cancellation and cleanup. Pointer/gesture routing is centralized to prevent conflicting gestures.

Future tools can include wall, opening, door/window placement, room, floor, roof, stair, pipe, duct, cable tray, dimension, annotation, measure, section, grid, terrain, furniture, node editing and inspection.

## Commands and transactions
All model writes pass through CommandBus. Commands have stable type IDs, versions, validation, execute, undo, affected entity IDs and optional serialization. Transactions group commands into one undo step or roll back. Commands are replayable for journal recovery. Direct mutation from UI, renderer or plugin is forbidden.

## Geometry providers
Providers consume immutable model snapshots and return deterministic geometry plus diagnostics. They cannot change authoritative parameters. Geometry is tagged with source revision, provider version, tolerance and cache key. Preview may be approximate; committed geometry must validate. Rebuilds run off the UI thread and support cancellation.

## Parameters and families
Parameters have stable IDs, explicit type/unit/scope, validation and formula AST. Families/types are data-driven and versioned. New families should be added through registration and schemas rather than scattered UI conditionals. Formula execution must not run arbitrary scripts.

## Events and dependency graph
Events describe committed facts, not gestures. Subscribers declare dependencies. Events are versioned and delivered after transaction commit. Dependency nodes use dirty revisions, cycle detection and diagnostics.

## Persistence and migrations
The repository is the only persistence entry point. Project and feature schemas are independently versioned. Migrations are deterministic, staged, testable and preserve unknown fields. Save uses atomic replacement/checkpointing; recovery replays the journal only after integrity checks. Never silently downgrade a project.

## Compatibility and quality gates
- Kernel API changes require compatibility review and tests.
- Feature modules can be disabled without corrupting project data.
- Registration checks duplicate IDs, dependency cycles and version mismatch.
- Tests cover invariants, geometry properties, import/export fixtures, migrations and large-model performance.
- CI compiles the core and app, runs unit tests and packages a debug APK.
- Feature flags gate incomplete capabilities.
- Architecture checks prevent UI/rendering/interop from bypassing CommandBus or importing feature implementations into the kernel.

## Growth strategy
To add a feature: create its module, define model data/commands/geometry/tool providers, register it, write tests and migrations, then expose UI. Keep the kernel stable as features expand.

## Long-term capability map
- Architecture: walls, openings, doors/windows, floors, roofs, stairs, rooms, structural systems, sheets and views.
- MEP: plumbing, HVAC, electrical, routing, connectors, systems and clash detection.
- Analysis: quantities, constraints, accessibility, energy/thermal and structural checks using validated engines.
- Collaboration: revision history, merge/conflict handling, BCF/issues and permissions.
- Interoperability: IFC, DXF, DWG adapter subject to licensing/SDK, glTF, PDF, CSV/XLSX.
- Automation: stable API, batch commands and reusable templates.
- Visualization: 2D, 3D, sections, elevations, AR and VR through renderer/view adapters.

These are extension points and roadmap items, not claims that the capabilities already exist.