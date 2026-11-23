# ADR 004: Begin the C++20 core port with contracts, not UI integration

- **Status:** Accepted for Stage G1 implementation
- **Date:** 2026-11-23

## Context

The Kotlin core has stabilized through ModelSession and persistence contracts, but translating the whole kernel in one step would create two competing implementations and make failures difficult to isolate. Android/JNI integration also introduces lifetime and ABI concerns before the domain contracts are proven.

## Decision

Stage G begins with an independent, dependency-free C++20 Foundation/Model slice under `native-core/`, built and tested by CMake/CTest on CI. The first parity surface is intentionally limited to IDs, metric units, 2D points, wall axes, wall invariants, project validation, and owned immutable snapshot access. Kotlin remains the reference during the migration.

Native public contracts use value types and explicit validation. Canonical length values are metres (`double`). No Android headers, JNI, renderer, third-party geometry library, persistence implementation, or platform services are introduced in G1. The snapshot is an owned value with const access; thread-safe revision publication and mutation transactions are deferred to G2.

## G1 acceptance gates

1. C++20 compiler and CMake build with warnings treated as errors.
2. CTest verifies metric length, valid model, invalid and zero-length geometry, duplicate IDs, invalid dimensions, non-finite and overflowed derived lengths, and snapshot copy isolation.
3. CI executes native tests alongside Kotlin tests and Android debug build.
4. Kotlin remains unchanged and the Android app does not link native code in G1.

## Migration sequence

- G1: Foundation/domain contracts and parity tests (this ADR).
- G2: Revision, mutation, validation pipeline and transaction rollback parity.
- G3: Persistence codec and recovery contract port, with golden fixtures.
- G4: Versioned C ABI, opaque handles, ownership and ABI tests.
- G5: Thin JNI adapter and Android integration; retire Kotlin production implementation only after parity gates.

## Consequences

The native kernel is introduced incrementally and can be independently tested. This temporarily duplicates model contracts, so parity tables and fixtures are mandatory. No production behavior changes until later stages.
