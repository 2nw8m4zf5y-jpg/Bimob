# ADR 005: Native transaction and dependency core, Stage G2

- Status: Initial implementation, pending remote CI and Kotlin golden parity acceptance
- Date: 2026-11-23

## Decision
Port a portable transaction/dependency core into C++20. ModelRevision is immutable and stores shared const snapshots. Wall entity objects are reused when unchanged across revisions. Dependency topology and scheduler order are defined by sorted node/edge lists and sorted adjacency, with FIFO Kahn processing.

The evaluator receives only direct dependency values. Configured evaluators are immutable; transaction overrides use a local registry copy. Graph admission and recomputation enforce all six EngineLimits and return structured errors. A mutation is published only after candidate validation and recomputation succeed.

Undo/Redo restore immutable snapshots. Repeated commits with a matching gesture ID coalesce into one undo entry.

## Validation
Local CMake build with warnings-as-errors, ASan and UBSan, and CTest passed. The stress test evaluates a 10,000-node chain plus 50 fan-out leaves (10,050 total) exactly once per node.

## Known gaps
This is an initial contract port, not accepted full parity. Kotlin golden fixtures have not yet been exported and compared byte-for-byte/value-for-value. Cancellation, event queue and history-memory limits, exact cycle diagnostic path parity, persistence and production JNI are deferred. Do not route Android through native-core until parity gates pass.
