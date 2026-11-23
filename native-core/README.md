# BIMOB Native Core

Platform-independent C++20 model, dependency scheduler, revision store and transaction layer. No Android/JNI/rendering dependencies.

## Build/test
```sh
cmake -S native-core -B build/native-core -DCMAKE_BUILD_TYPE=Debug -DBIMOB_NATIVE_ENABLE_SANITIZERS=ON
cmake --build build/native-core --parallel
ctest --test-dir build/native-core --output-on-failure
```

## G1 contracts
- Metric lengths in metres; `double`.
- `kCoordinateEpsilon=1e-9`, `kMinWallLength=1e-6`.
- Validation is deterministic and reports explicit issues. Public model validation does not throw.

## G2 contracts
- `ModelRevision` has const members and shared immutable project, graph, derived-value and entity-store snapshots.
- Wall entity pointers are reused across revisions when an entity is unchanged (entity-level sharing).
- Sorted node/edge lists define scheduler order; hash tables are not used. FIFO Kahn scheduling and direct-dependency-only evaluator inputs.
- Transaction evaluator overrides are local and do not alter the configured registry.
- All six defensive limits are configurable and return structured `Error` diagnostics.
- Mutations stage privately; validation or evaluation failure leaves the current revision and history untouched.
- Undo/Redo snapshots share immutable state; repeated commit with the same gesture ID coalesces into one undo entry.

## Scope caveat
G2 is an initial native contract port, not yet full Kotlin parity. Exact cross-language golden fixtures, cancellation/event delivery, history memory/event budgets, richer BIM model types and persistence are follow-on work. Do not integrate JNI or route Android production behavior through native-core yet.
