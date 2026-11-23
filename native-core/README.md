# BIMOB Native Core

This directory is the initial C++20 contract port. It is intentionally small and has no Android, JNI, rendering, persistence, or third-party dependencies.

## Build and test

```sh
cmake -S native-core -B build/native-core -DCMAKE_BUILD_TYPE=Debug
cmake --build build/native-core --parallel
ctest --test-dir build/native-core --output-on-failure
```

## Current contracts

- Canonical lengths are metres, represented as `double`.
- `ModelId` is a stable opaque identifier; this slice does not generate IDs or change their format.
- Wall axis geometry uses finite 2D points and Euclidean length.
- Validation rejects empty project metadata, invalid/duplicate wall IDs, non-finite coordinates or derived lengths, zero-length axes, and non-positive/non-finite wall dimensions.
- `ModelSnapshot` owns a copy and provides only const access to it. It is not yet a thread-safe publication primitive; that belongs to the native transaction stage.

The Kotlin implementation remains the behavior reference until explicit parity coverage is expanded. Do not route Android UI or mutate Kotlin state through this library yet.
