# ADR 001: Revisioned immutable snapshots and atomic CommandBus

- **Status:** Accepted for Kotlin prototype; subject to parity review during C++20 port.
- **Date:** 2026-11-23
- **Phase:** C — Command & Transaction Engine

## Context

BIMOB mutations must not be applied directly by UI/tools. A mutation must validate as a whole, failure must preserve the previous state, readers need stable snapshots, and undo/redo must not rewind revision numbers. The Kotlin prototype is intended to define portable contracts for native implementation.

## Decision

1. A command implements `ModelMutation` and returns a candidate `Project`; it does not mutate the store in place.
2. `CommandBus` serializes write/validation/commit with a monitor in this prototype. A candidate is defensively copied (collection containers) before validation/publication.
3. A published `ModelRevision` is immutable by API convention; revision numbers increase on commit, undo and redo.
4. Validation errors reject the candidate. Warnings remain attached to the committed result.
5. Expected revision supports optimistic stale-write rejection.
6. Undo/redo restore project snapshots, but assign a new monotonic revision.
7. Subscribers receive events after commit and outside the monitor; exceptions from observers are isolated.
8. No-op candidates do not increment revision or enter history. History is bounded.
9. Initial project must pass validation. Cancellation is checked at staging boundaries.

## Alternatives considered

- In-place command execution with inverse commands: rejected for prototype because partial mutation/rollback is easier to get wrong and command inverses may be incomplete.
- Event sourcing as the sole state representation: deferred; may later support journal/replay but is not required to establish atomic transaction semantics.
- Lock-free publication immediately: deferred until native snapshot lifetime, memory model, and performance are proven. Prototype uses a simple synchronized writer/read path for correctness.

## Consequences and follow-up

- Snapshot history uses memory proportional to retained revisions; bounded history is required. C++ port should use structural sharing/COW and explicit memory budget.
- Kotlin `List` is read-only interface, not deep immutability. Defensive collection copies protect model containers currently defined, but future mutable payload types require freeze/ownership policy and tests.
- Listener event ordering across concurrent commits must be verified/strengthened before concurrent writers are exposed to production.
- Cancellation is cooperative; commands must not perform unbounded work while holding the transaction lock. A later staging/optimistic commit design should separate expensive work from the atomic publication gate and re-check revision.
- Add tests for multi-element mutations, history limits, listener disposal, concurrency/event ordering, deep-copy/freeze behavior, and revision overflow before declaring Phase C complete.
