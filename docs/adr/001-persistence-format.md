# ADR 001: BIMOB Project Container, Persistence and Crash Recovery

- **Status:** Approved for implementation
- **Date:** 2026-11-23
- **Phase:** D — Persistence & Crash Recovery

## Context
BIMOB needs a portable project format independent of Android SDK, safe against interrupted writes, versioned, integrity-checked, and capable of autosave/recovery. Current Kotlin model is pure Kotlin but housed in the Android app module; persistence contracts should move to an Android-free core module/package. Platform adapters provide filesystem, SAF, and scheduling only.

## Decision
Adopt a versioned ZIP-based `.bimob` container. The initial Kotlin v1 codec supports the currently implemented `WallElement` concrete model type; adding further element types requires explicit tagged codec support and golden fixtures before those types are considered persistable. The canonical model payload is UTF-8 JSON, with assets and disposable derived geometry in separate entries. SQLite is not the canonical interchange file for v1; it may later serve as a local working database behind repository interfaces. The container specification must be implementable by pure Kotlin and future C++20 native-core.

### Layout
Required:
- `chunks/`: reserved directory namespace for future binary geometry/data chunks. Manifest declares it (empty in v1 until chunk payloads are implemented); each future chunk will have a stable ID, type/version, size and digest. Chunks are immutable/content-addressed where practical and referenced from model metadata, never treated as canonical model JSON.
- `manifest.json`: containerVersion, schemaVersion, minimumReaderVersion, writerVersion, projectId, feature flags, payload entry paths, sizes and digests.
- `model/model.json`: canonical model data, stable IDs, explicit units, finite numbers and deterministic serialization rules.
- `integrity/sha256.json`: SHA-256 digest list for canonical payloads/assets. ZIP CRC32 is also verified, but SHA-256 is authoritative.

Optional:
- `geometry/`: versioned disposable derived geometry/cache, rebuildable from canonical model.
- `assets/`: stable asset IDs, media type, size and digest.
- `preview/thumbnail.png`: disposable preview.
- `extensions/<namespace>/`: namespaced extension data; unknown optional data is preserved when possible, required unknown features block safe round-trip.

Reject duplicate entry names, absolute/traversal paths, symlinks, unreasonable entry counts/sizes/expansion ratios, and ZIP bombs.

## Versioning and compatibility
Container envelope and model schema are versioned independently. Reader rejects unsupported major container versions or required feature flags with typed errors. Migrations are deterministic pure functions operating on a staging copy; never overwrite the source during migration. Unknown required features must block saving unless the user explicitly accepts a loss report. Optional unknown fields/extensions should be retained where possible.

## Integrity and validation
Check ZIP CRC32 during entry read, then SHA-256 for all required and declared payloads. Validate UTF-8, manifest sizes/path rules, schema, IDs/references, units, finite values and domain invariants before publishing the model. Missing entries, digest mismatch, truncation, duplicate IDs, invalid references or unsupported required features fail load; never partially publish or silently repair. Salvage is a separate explicit operation that produces a recovered copy and diagnostics.

## Atomic save protocol
The platform-independent storage contract must support same-directory temporary file creation, write, durable sync, atomic replace, directory sync where available, and cleanup. Platform adapters expose guarantees/capabilities; SAF/cloud providers may not support filesystem-level atomicity.

1. Freeze snapshot revision R and validate.
2. Serialize to a temp file in the destination directory/filesystem, leaving destination untouched.
3. Close archive, flush buffers and fsync/force temp file.
4. Reopen temp and verify structure, lengths, checksums and model validity.
5. Atomically rename/replace temp over destination using platform primitive.
6. Sync parent directory where supported.
7. Mark R saved only after confirmed replacement; clean temp/backup conservatively. After the new primary file is durable and independently verified, journal compaction/rotation may begin. It must be crash-safe: write and fsync a replacement journal/snapshot first, then atomically switch it; never truncate the only recoverable journal before the verified primary and replacement recovery state are durable. If compaction fails, keep the valid old journal and report a warning.

Cancellation is allowed before replacement. Once replace starts, finish the durability sequence and report actual outcome. Failure before replace leaves prior project intact. If atomic replace is unsupported, fail safely or use a documented backup/replace fallback without claiming atomicity.

## Autosave and crash recovery
Use a separate append-only journal in app-managed local workspace, not inside the live project container. It is recovery metadata, never canonical project data. Records include journal version, project ID, base-save digest, schema version, sequence, transaction ID, operation/payload, length, checksum and commit marker.

Durability order must be selected and documented: journal record append -> flush/fsync -> publish model, or a clearly identified weaker mode. Periodically atomically persist and verify a recovery snapshot, then checkpoint/rotate journal only after snapshot durability. A successful primary-project save triggers safe journal checkpoint/rotation against the new base digest; journal growth is bounded by compaction policy, and interrupted rotation must leave either the old complete journal or the new complete journal/snapshot. Startup scans from snapshot, stops at first incomplete/invalid record, verifies base digest and sequence, replays committed records into a staging model, validates it, and offers a recovered copy. Preserve original file and journal until acceptance/retention expiry. Replay is idempotent using transaction IDs and sequence numbers. Initial implementation may journal bounded full snapshots rather than deltas; optimize only after measurement.

## Pure Kotlin / native-core boundary
Canonical serialization, schema migrations, integrity checks, and validation contracts must have no Android imports. Android file/document-provider adapters stay outside core. Future C++20 must consume the same format with golden fixtures. Canonical JSON rules: UTF-8 without BOM; object keys and schema fields emitted in a fixed documented order; no insignificant whitespace; strings escaped consistently; integers emitted in base-10 without leading zeros; floating-point values must be finite and emitted using a single locale-independent shortest-round-trip representation, normalize negative zero to `0`, and never emit exponent differences across implementations unless the canonical exponent grammar is fixed. SHA-256 is calculated over the exact canonical UTF-8 bytes. Golden fixtures assert byte-for-byte JSON and digest equality across Kotlin and future C++20. Define paths, digest coverage and migrations precisely. Model identity must not be a platform URI; use stable asset IDs and relative paths.

## Alternatives
- SQLite as project file: transactional and queryable, but ties interchange to database implementation and complicates packaging/cross-language contract; rejected as canonical v1. May be local working store.
- Single JSON file: portable, but awkward for assets/geometry and container metadata; rejected as envelope, retained as model payload.
- Custom binary: efficient but costly to inspect, evolve and implement consistently; defer until profiling justifies it.
- ZIP + manifest + canonical payload: portable, extensible, asset-friendly; selected with strict reader limits.

## Implementation status (2026-11-23)

Phase D implementation is underway in the Android-SDK-free Gradle module `:core`:
- Model, validator, memory estimator and CommandBus were moved into the JVM module without changing package names, preserving app/test imports.
- Canonical JSON codec, ZIP writer/reader, SHA-256 integrity index, manifest/schema checks, migration registry, atomic repository contract and crash-recovery journal skeleton are implemented.
- Test coverage added for deterministic canonical bytes/hash, model/archive round-trip, corrupted payload rejection, simulated restart recovery, truncated journal tail, idempotent transaction retry, atomic-save failure preserving the old project, journal compaction and future-schema rejection.
- `AtomicStorage` now exposes durability capabilities. `JvmAtomicStorage` is a test adapter only; Android/SAF storage adapter is not yet implemented.
- **CI verified green:** Run #115 for commit `eedf39b413a00bd68bed3686e3b9043159cd8199` ran `:core:test`, `:app:testDebugUnitTest`, `assembleDebug`, and artifact upload successfully.

## Consequences
Benefits: Android-independent, cross-language potential, atomic replacement, explicit version/migration, integrity checks, separate canonical data/cache/assets, independent recovery.
Costs: secure ZIP parsing, deterministic JSON, varying filesystem durability guarantees, journal retention/latency and compatibility fixtures.

## Phase D gates
1. Android-free persistence interfaces and storage capability model.
2. Container writer/reader with strict path/size validation.
3. Canonical serialization, manifest and schema version.
4. SHA-256 + CRC verification and corruption fixtures.
5. Atomic save with injected failure/crash at every stage; prior save remains readable.
6. Migration framework and fixtures; unsupported-version behavior.
7. Journal/recovery snapshots with truncation, corruption, replay duplication, interruption and idempotence tests.
8. Kotlin/C++20 golden format fixtures (C++ implementation may be later).
9. Android adapter and document-provider limitation tests; core tests remain Android-free.
10. CI round-trip, migration, corruption, atomic-save failure matrix, recovery and APK tests.

**Approved clarifications:** deterministic canonical JSON and cross-language digest fixtures; crash-safe journal compaction after verified atomic primary save; manifest-declared `chunks/` namespace reserved for future binary geometry/data. Implementation remains subject to Phase D gates.
