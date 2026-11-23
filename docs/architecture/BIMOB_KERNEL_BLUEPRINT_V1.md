# BIMOB Kernel Blueprint v1.0

Status: engineering contract / evolving design  
Target: cross-platform BIM/CAD core; Android-first with future desktop and iOS.

## 1. Non-negotiable invariants

1. The semantic, parametric BIM model is the sole authoritative source of truth. Meshes, B-rep caches, projections, quantities caches, and spatial indices are derived.
2. Persistent identity is UUID-based. Geometry/topology indices are transient and never durable semantic references.
3. Model-space units are metres; internal scalar precision is double. Unit conversion occurs at explicit boundaries. Pixels and GPU floats never enter canonical data.
4. A committed model revision is valid, immutable to readers, and consistent. Failed mutations leave the prior revision unchanged.
5. Commands are the only supported mutation path. UI, tools, importers, and plugins submit commands, never direct writes.
6. Derived caches carry source revision/dependency fingerprints and can be discarded/rebuilt.
7. Schema and command versions are explicit; migrations are deterministic and tested. Unknown optional extension data is preserved where possible.
8. Geometry operations never silently change units, tolerance, identity, or coordinate frame.

## 2. Layered architecture

Dependency direction is inward; no cyclic dependencies.

- foundation: UUID, units, tolerance policy, result/error types, math primitives, logging, cancellation.
- domain: project/site/building/storey, element definitions, semantic properties, typed parameters, materials, constraints.
- command: command API, validation pipeline, transaction coordinator, undo/redo journal, revision publication.
- dependency: directed dependency graph, dirty propagation, cycle detection, incremental recompute scheduler.
- geometry-api: immutable geometry requests/results and operation policies.
- geometry-brep: optional B-rep backend adapter.
- geometry-mesh: mesh operations, repair, boolean adapter and validation.
- projection: plans, sections, hidden-line removal and dimension/view data.
- spatial: AABB tree, spatial queries, selection and collision candidates.
- families: parametric definitions, parameter schemas, formula AST and evaluation.
- quantities: quantities and schedules from semantic/derived facts.
- persistence: versioned repository, metadata DB, chunk storage, journal and recovery.
- interop-ifc: IFC mapping, import/export validation and round-trip tests.
- render-api / render implementation: renderer consumes snapshots only.
- jobs: bounded workers, cancellation, priority, memory budget and result validation.
- extension-api / registry: versioned plugin contracts, capability checks and registration.
- platform-android: Kotlin/JNI bridge and lifecycle adapters.
- feature modules: UI and product features depending on stable APIs.

## 3. Canonical data model

Persist semantic identity and intent, not triangulation.

Core entities: Project, Site, Building, Storey, Space, Element. Each Element has UUID, type/family ID, placement, typed parameters, material assignments, relationship IDs, property sets and revision.

Wall definition: baseline curve, height profile, thickness/layers, base/top constraints, location-line policy, openings and joins.  
Slab: boundary curves, thickness, slope/offset, openings, layer stack.  
Opening: host reference, profile, placement constraints, sill/head values and hosted component reference.  
Level: elevation and datum metadata.  
Coordinates: local project frame plus explicit transform to external/georeferenced frame.

Typed parameters include length, area, volume, angle, integer, boolean, string, enum, reference and formula. Formula is a restricted AST, never arbitrary code evaluation. Units are explicit.

## 4. Command and transaction contract

A command declares command ID/schema version, required capabilities, target IDs/preconditions, validation, deterministic apply, inverse/undo payload or reversible journal strategy, affected dependency nodes and cache invalidations.

Transaction lifecycle:
begin -> stage -> validate -> compute affected derived state -> atomic commit -> publish revision/event.

Any error, cancellation before commit, or validation failure rolls back staged changes. Readers see either prior committed snapshot or next snapshot, never partial state.

Undo/redo operates on semantic command history and revision checks. Stale undo is rejected or reconciled explicitly, never silently applied to unrelated state.

## 5. Geometry and numerical policy

- Use robust predicates for sign/orientation/intersection decisions.
- Separate modelling, display, import and backend tolerances. A documented policy chooses context values; no magic universal epsilon.
- Avoid catastrophic cancellation with local frames and origin rebasing.
- Keep double precision through CPU geometry. GPU floats only after camera-relative transformation.
- B-rep is behind a backend-neutral interface. Topological naming is not assumed stable; semantic anchors and explicit references are preferred.
- Tessellation is adaptive, bounded by chordal/angular error and memory budgets, with source revision metadata.
- Boolean results undergo validity checks. Failures return structured diagnostics, not silent repair.
- Geometry jobs operate on immutable snapshots, are cancelable, and publish only if source revision still matches.

## 6. Dependency and recomputation

Maintain a DAG of parameter, element, relationship and derived nodes. On change:
1. validate changed inputs;
2. mark downstream nodes dirty;
3. detect cycles and report path;
4. schedule only affected nodes topologically;
5. parallelize where dependencies permit;
6. verify revision fingerprints;
7. atomically publish results.

Cache keys include algorithm/backend version, inputs, tolerance policy and source revisions.

## 7. Concurrency, memory and mobile

- UI thread handles input and lightweight state only.
- Worker pool is bounded by device capability and memory budget.
- Geometry uses immutable snapshots; avoid global mutable kernel state.
- Jobs expose progress, cancellation and structured failure.
- Stream large geometry/assets by region/storey; support LOD, instancing, culling, spatial selection and explicit cache eviction.
- Low-memory mode degrades presentation/cache fidelity, not canonical accuracy.
- Recovery/autosave uses atomic checkpoints plus journal replay, with crash-injection tests.

## 8. Persistence contract

Repository interface is independent of storage engine. Format supports schema/version manifest, semantic records, binary geometry/assets as derived chunks, atomic boundaries, checksums, journal/recovery, migrations with backup/rollback, incremental save and corruption diagnostics.

SQLite is a candidate for metadata/indexes; immutable chunk files for large binary payloads. File format must not depend on private serialization of an unstable backend.

## 9. Interop and extension safety

Expose a narrow versioned C ABI for native/platform bridges. Kotlin/Swift wrap it; platform UI types do not leak into domain.

Extensions declare ID, version, compatibility range, dependencies and capabilities. Registry rejects duplicates/incompatibilities and unauthorized capabilities. Unknown optional records are preserved. Plugins mutate only through validated commands. Registration order cannot alter model semantics.

## 10. Quality gates

Applicable gates: unit and property tests; geometry/numerical regression fixtures; transaction atomicity, undo/redo and crash-recovery tests; dependency cycle/incremental tests; migration/corruption tests; deterministic output tests; performance/memory budgets; API compatibility; license and supply-chain review.

CI builds, tests, lints/static-analyzes, packages a debug artifact and reports failures.

## 11. Staged delivery

A — contracts: invariants, units, IDs, result/error types, module boundaries.  
B — canonical model: typed elements/parameters, relationships, revisions, validator.  
C — mutation engine: command bus, transactions, atomic publication, undo/redo.  
D — persistence: repository, journal, checkpoints, recovery, migration.  
E — dependency engine: DAG, dirty propagation, cycle diagnostics, jobs.  
F — geometry adapters: robust primitives, curves, B-rep/mesh interfaces, validation, tessellation.  
G — architectural elements: walls, slabs, openings, levels, spaces, joins.  
H — views: plans, sections, elevations, dimensions and hidden-line removal.  
I — interoperability: IFC mapping, import/export and round-trip fixtures.  
J — mobile production: streaming, memory budgets, adaptive LOD, offline reliability.  
K — extension platform: capability registry and plugin SDK.

Stages are gated by tests and review, not calendar promises. Never implement a feature by bypassing a foundational invariant.
