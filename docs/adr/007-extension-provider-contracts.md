# ADR 007: Typed extension provider contracts and runtime admission

## Status
Proposed; implementation is limited to portable provider contracts and an immutable admission registry.

## Context
Manifest registration proves an extension's declared metadata is compatible. It does not prove that an in-process provider instance is bound to a declared contribution. The kernel must not make Android, UI, file-system, or arbitrary mutable project access part of provider contracts.

## Decision
- Provider instances expose a stable identity (extension ID, contribution ID, extension point, contract version).
- The runtime provider registry is immutable and scoped to one extension point.
- Admission requires a registered manifest, an exact declared contribution, and an exact contribution contract-version match. Batch admission is atomic and deterministic.
- Portable contracts are provided for element types, families, geometry, validation, schedules, import and export.
- Geometry payloads use metre coordinates and validate finite vertices and triangle indices.
- Family defaults are checked against declared parameter types.
- Schedule outputs validate column IDs and finite numeric cells.
- Import/export use host-owned stream interfaces; providers do not close host resources.
- Providers may report cancellation and progress through host context interfaces.
- The registry binds instances but does not discover, load, sandbox, or execute code. Tool sessions and command-bus-mediated mutations are intentionally deferred until their state-machine contract is designed.

## Consequences
This establishes typed extension points without coupling the core to Android or a plug-in loading mechanism. A future runtime must add permission/capability policy, lifecycle isolation, failure containment, resource limits and command-bus-only model mutation before executing third-party code.
