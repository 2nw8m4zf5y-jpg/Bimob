# ADR 002: Dependency Engine and Model Ownership

- **Status:** Proposed / accepted for design phase; implementation not started
- **Date:** 2026-11-23
- **Stage:** E — Model Ownership & Dependency Engine (DAG)
- **Decision scope:** Pure-core dependency graph, transactional graph updates, derived-value scheduling, and ownership boundary. No implementation code is authorized by this ADR step.

## Context

The canonical BIM model must remain the sole source of truth. Derived quantities, room boundaries/areas, geometry, schedules, constraints and formula values must not become independently mutable copies. Recomputing the whole project after each edit is wasteful and may produce inconsistent intermediate state. Dependency edges must be validated before a candidate transaction is published.

The existing CommandBus stages a private candidate Project, validates it, then publishes a new revision atomically. TransactionContext provides cooperative cancellation checkpoints. The Dependency Engine must fit into this lifecycle rather than becoming a second mutation/publishing path.

## 1. Ownership and graph representation

### Canonical ownership
- Project owns semantic entities by stable ModelId; entity identity is independent of graph node indices, geometry topology, or cache keys.
- A dependency node is a typed, stable reference to an entity or a derived property, not an owning pointer.
- Graph, adjacency indexes, dirty flags, scheduler state, and computed caches are derived state, revision-bound and rebuildable. They are not serialized as canonical project truth.
- No dependency graph mutation or derived result is visible outside the candidate transaction until commit.
- Graph storage should use compact stable node handles/IDs and adjacency indexes. Avoid object-pointer ownership cycles.

### Node categories (initial vocabulary)
1. **Semantic entity nodes:** Project, Site, Building, Level, Element, ElementType, Material, Space/Room, Parameter.
2. **Derived-property nodes:** geometry/bounds, area, volume, perimeter, length, opening cut, room boundary/area, quantity/schedule row, formula result.
3. **Constraint/evaluation nodes:** constraint inputs and outputs, parameter/formula evaluation. A constraint is not permitted to mutate an unrelated canonical entity outside its transaction candidate.

Nodes should be property-granular when useful. A wall entity node alone is too coarse if edits only affect its length or layer quantity; use property keys (e.g. wall ID / axis, height, area, volume) with explicit typed references. The granularity must not create unbounded node proliferation; enforce budgets.

## 2. Dependency types and edge semantics

Edges are directed **source → dependent**. If A changes, B becomes dirty and may require reevaluation. Edge metadata records dependency kind, property mapping, and deterministic evaluation priority where needed. No edge may silently invert this convention.

Initial relationships include:
- Project → Site / Building / Level and project-level settings.
- Building → Level (membership/ownership, where semantic rules define it).
- Level → Wall / Opening / Door / Window / Floor / Roof / Stair / Column / Beam / MEP element (level placement/elevation context).
- Wall → Opening (host/void relationship; opening geometry depends on host wall placement/shape).
- Wall + Opening → affected wall geometry and quantities (cutout-aware derived properties).
- Opening → Door/Window instance (hosted insert relationship; insert position/size and host validity are constrained together).
- Element/Type → parameter evaluation and element-derived geometry.
- Material + Element layer → wall layer quantities, material takeoff, cost/thermal derived values.
- Bounding/geometry/element relations → Space/Room boundary candidates and containment; room area/volume depends on validated boundary inputs.
- Level/Element/Space/Material → schedule/specification rows and aggregate quantities.
- Semantic source properties → formula, constraint, quantity, drawing/projection and spatial-index derived nodes.
- Explicit formula/reference dependencies → referenced parameter/property result.

The graph distinguishes relationship semantics from recompute dependencies. For example, a Level membership edge is not itself a geometric dependency unless a relevant level elevation/transform is read by the dependent computation. Relationship metadata can exist without creating a scheduling edge. All dependency edges must declare whether they are semantic-reference, ownership/context, hosted geometry, formula, constraint, or derived aggregation.

## 3. Cycle detection and graph admission

- The accepted dependency graph is a DAG. Cycles are forbidden in Stage E; feedback/constraint solving loops are a future separately designed subsystem with bounded solver semantics, not graph cycles.
- For each proposed graph mutation, validate the candidate graph **before the transaction commit**. Batch changes are checked as a whole, not edge-by-edge against a partial published graph.
- Edge insertion u → v is cyclic if u == v or v can already reach u in the candidate graph. Use bounded DFS/BFS for the initial implementation; a deterministic incremental topological-order algorithm may be considered after profiling.
- Graph updates and model candidate changes are staged in the same TransactionContext; cycle rejection is a typed validation error and publishes no model revision, graph revision, cache invalidation, or events.
- Multi-edge insertion/removal must be atomic. Removing/replacing edges should stage a candidate adjacency representation and validate the resulting DAG before commit.
- Diagnostic output must identify a concrete cycle path and relevant IDs/edge kinds, with deterministic ordering.

## 4. Dirty propagation and invalidation

- Mutations declare their changed semantic properties (not only entity IDs). These property keys are the graph invalidation roots.
- Propagate dirtiness forward from changed sources to reachable dependents. Deduplicate nodes; process deterministically. Dirty sets are transaction-local and revision-tagged.
- A cache entry is valid only for the input dependency fingerprint/revision it records. A dependent is not considered clean merely because its computation returned: it must have consumed the current versions of all inputs and passed validation.
- Unaffected nodes and caches are retained where fingerprints remain valid. Invalidation never mutates published caches in place; stage new/invalidated cache state, then publish with the model revision.
- If an edge changes, invalidate both its former downstream effects (when required by semantic change) and new downstream effects. Edge-removal semantics must explicitly define whether dependent values become absent, recomputed, or retain a valid independent value.

## 5. Topological scheduling

- Schedule only the dirty subgraph. Compute a deterministic Kahn topological order with a stable tie-breaker (typed node key/ModelId plus node kind), or deterministic DFS postorder with explicit cycle detection. Kahn is preferred for clear cycle diagnostics.
- Run each node once per transaction unless a future iterative solver explicitly specifies otherwise. Dependencies must be evaluated before dependents.
- Execution is initially single-threaded and deterministic. Parallel scheduling is deferred until nodes have explicit read/write sets, purity, cancellation and resource budgets, and deterministic result/error aggregation.
- Evaluation receives an immutable candidate snapshot/read view and typed inputs; it returns a result plus diagnostics, never mutates Project or published caches directly.
- Validation runs at defined boundaries: graph validity before scheduling, input validation before evaluation, derived-result validation after each node (or bounded validation batch), and final model validation before commit.
- If a dirty node fails, has invalid output, is cancelled, or exceeds a budget, abort the transaction. No partially evaluated model, cache, graph, revision, or event may be published.

## 6. CommandBus and TransactionContext integration

Proposed pipeline:
1. Command declares semantic property changes and any graph-edge edits.
2. CommandBus stages candidate canonical Project and candidate graph changes in one transaction context.
3. Validate identities, references and candidate graph DAG (cycle detection).
4. Compute changed-property roots and dirty closure.
5. Schedule dirty nodes topologically; compute derived candidates into transaction-local state.
6. Checkpoint cancellation and enforce graph/evaluation budgets between bounded operations.
7. Run model and derived-state validation.
8. Atomically publish Project revision + graph revision + derived-cache snapshot as one commit envelope.
9. Publish commit/dependency events only after atomic publication.

TransactionContext now provides the typed dependency-evaluation interface and resource-budget accounting. The Dependency Engine does not call CommandBus.execute recursively; derived evaluation is part of the active transaction and cannot create nested commits. Undo/Redo restores canonical model state and the revision-scoped derived snapshot; canonical model snapshots remain authoritative.

## 7. Graph and computation limits

Implemented E3 safety budgets (configuration is explicit and testable):
- Maximum graph nodes: **250,000**.
- Maximum graph edges: **1,000,000**.
- Maximum dirty nodes per transaction: **100,000**.
- Maximum traversal depth: **4,096** (iterative traversal; no recursive stack walk).
- Maximum evaluation operations per transaction: **1,000,000** node visits/steps, with cancellation checkpoints at least every 256 operations.
- Maximum dependency fan-out per node: **10,000**.
- Maximum candidate graph mutation batch: **100,000 edges**.

These are safety ceilings, not target project sizes or performance promises. Graph node/edge limits are preflighted before admission; other limits are enforced during dirty planning/evaluation. Exceeding a limit yields deterministic ENGINE_LIMIT_EXCEEDED diagnostics (budget, node where applicable, actual and maximum), safely rejects the candidate and preserves the current revision. The proposed 100,000-edge batch cap is not implemented and remains deferred. No silent truncation.

## 8. Diagnostics, observability and testing

- Typed errors: cycle detected (cycle path), missing/stale node/reference, invalid dependency, graph limit exceeded, evaluator failure, invalid derived result, cancellation, stale revision.
- Diagnostics include stable node IDs, edge kinds, dependency path, affected dirty count and transaction/revision context; do not include nondeterministic timing in canonical hashes.
- Tests: edge direction/type; self-cycle and multi-node cycle; atomic rejection of batched cycle; graph update rollback; deterministic cycle path; dirty closure and dedup; unaffected cache retained; topological order; stable tie-break; evaluator failure and validation rollback; cancellation; limits (nodes/edges/fan-out/depth/dirty/evaluation); stale revision; undo/redo rebuild; no partial events or cache publication.
- Property/fuzz tests should generate bounded random DAGs and mutations, compare incremental recompute to a full recompute oracle, and verify deterministic schedules and outputs.

## 9. UI ownership audit and migration plan

### Current audited state (2026-11-23)
MainActivity is not yet connected to CommandBus or ProjectRepository. It owns a mutable list of Wall; PlanCanvas mutates this local list directly on touch. Wall is a UI-local float record. Local project persistence uses SharedPreferences/JSON for this sketch. SAF import/export creates a conversion boundary to/from Project and archive writer/reader, but does not make core authoritative. Therefore the UI currently duplicates canonical wall data and can lose or degrade BIM semantics through this conversion.

### Required follow-up, now that Stage E dependency-engine acceptance is complete
1. Introduce an app-level model session owning a CommandBus initialized from ProjectRepository.load() (or a new project) and a persistence coordinator using ProjectRepository.save() after committed revisions.
2. Implement wall-create/edit/delete mutations and execute exclusively through CommandBus with expected-revision checks. UI gestures generate intent/commands; no direct model mutation.
3. Canvas subscribes to revision events and renders immutable Project snapshots; wall drawing uses a read-only projection/adapter of canonical WallElement data. Remove UI mutable Wall list as source of truth.
4. Autosave is revision-aware and debounced only at the app boundary; repository atomic-save/recovery remains canonical. SAF is used for explicit import/export, not as the live session store.
5. Migrate legacy SharedPreferences sketches through a deterministic schema migration; preserve IDs where valid, report losses, and do not silently discard unsupported data.
6. Add integration tests proving UI intent → CommandBus commit → snapshot update → repository save/load round-trip and that rejected commands leave UI/model unchanged.
7. Delete the conversion-based duplicate wall model after the migration and keep only UI projection types that cannot be mutated into canonical truth.

## 10. Decision and staged delivery

This ADR is owner-approved and authorizes staged implementation. Stage E implementation was delivered in atomic sub-stages:
- E1 graph data model and typed dependency keys/edges, deterministic graph admission/cycle checks and tests — closed.
- E2 transaction-local graph staging and rollback integration, revision snapshots and Undo/Redo — closed.
- E3 evaluator registry, hard budgets, deterministic diagnostics, scheduler/input efficiency and 10k stress test — closed.
- Deferred hardening track (not a blocker for Stage E): cache fingerprints, full-recompute oracle, fuzz/property tests, detailed performance and heap profiling.
- Stage E is accepted; begin the UI ownership migration listed in section 9.

## Consequences

**Positive:** deterministic incremental evaluation, clear ownership, bounded resource behavior, atomic model/derived state, testable contract and future C++20 parity.

**Costs/risks:** graph bookkeeping and memory overhead; correctness depends on accurate changed-property declarations and dependency edges; cache fingerprints and invalidation require careful testing; initial single-thread scheduling may constrain throughput; limits require profiling/tuning. The current Android UI remains temporary legacy debt until its planned migration.

## 11. Implementation status (2026-11-23)

- **E1 — ACCEPTED / CLOSED:** typed immutable graph, edge admission, deterministic cycle-path diagnostics, max-depth rejection and graph cleanup. CI green on b879e8f (:core:test, testDebugUnitTest, assembleDebug).
- **E2 — ACCEPTED / CLOSED:** revision-owned Project, DependencyGraph and derived values; transaction staging; atomic rollback; deterministic dirty closure/recompute; Undo/Redo synchronization. Owner accepted 2026-11-23; CI green on add8a18.
- **E3 — ACCEPTED / CLOSED:** configuration-level immutable EvaluatorRegistry with transaction-local override; configurable dirty/evaluation/fan-out/graph node/edge budgets; preflight resource rejection; deterministic ENGINE_LIMIT_EXCEEDED diagnostics; linear adjacency grouping and FIFO Kahn traversal; evaluators receive immutable direct dependency inputs; 10,000-node chain+fan-out stress verification. CI green on eb8b751 (:core:test, testDebugUnitTest, assembleDebug).
- **Stage E — ACCEPTED / CLOSED** at owner direction after E3 verification. Deferred hardening (YELLOW): derived-cache fingerprints, full-recompute oracle, fuzz/property stress and detailed heap profiling. These are not claimed complete and are not blockers to the next UI migration.