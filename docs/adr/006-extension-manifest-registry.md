# ADR 006: Immutable extension manifest registry

- **Status:** Accepted for the first registry slice
- **Date:** 2026-11-24
- **Phase:** Kernel extensibility

## Context

Architecture, MEP, analysis and exchange capabilities must be addable without placing feature-specific branches inside the kernel. Before loading executable providers, the kernel needs a deterministic way to identify extensions, check API compatibility and reject invalid dependency graphs.

## Decision

1. Every extension has a stable ID, its own version and a half-open supported kernel API range.
2. Dependencies name another extension and declare an accepted version range.
3. Contributions declare a stable ID within a typed extension point: tool, element type, family, geometry, validator, schedule, import, export or migration.
4. An extension point plus contribution ID is globally unique.
5. Registration of one manifest or a batch is atomic. A rejected batch never publishes a partial registry.
6. Missing dependencies, incompatible versions, duplicate IDs, unsupported kernel API ranges and dependency cycles are rejected before publication.
7. An extension cannot be unregistered while another registered extension depends on it.
8. Registry snapshots are immutable and deterministically ordered.

## Explicit non-goals of this slice

- Loading code, reflection, dynamic class loading or executing third-party providers.
- Defining geometry payloads, interactive tool state machines, schedule row schemas or exchange-specific data formats.
- Claiming that any architecture, MEP or interoperability feature is implemented.

The registry currently admits and indexes extension metadata only. Runtime provider interfaces and Gradle module separation are subsequent increments and must be added behind the same stable contracts.

## Consequences

- Feature modules can declare their contribution surface without modifying kernel internals.
- Kernel API compatibility is checked before registration.
- Dependency resolution is deterministic and testable.
- Provider execution remains outside the registry, avoiding accidental Android/UI dependencies and untrusted code execution in this first step.
