# BIMOB MASTER PROMPT v1.9

**Статус:** основной инженерный паспорт проекта. Обновлять после завершения этапов и значимых архитектурных решений.  
**Репозиторий:** https://github.com/2nw8m4zf5y-jpg/Bimob  
**Рабочая ветка:** `feature/model-core-v1`  
**PR:** https://github.com/2nw8m4zf5y-jpg/Bimob/pull/1 (не объединять без отдельного разрешения владельца).

## 1. Миссия

Создать BIMOB — расширяемую, надёжную, мобильную CAD/BIM-систему для архитектурного проектирования, планов, параметрических строительных элементов, 3D-модели, инженерных систем, документации, расчётов количеств, IFC, визуализации и перспективного AR/VR. Сначала Android, затем кроссплатформенность.

Развивать как инженерный продукт: приоритет точности, архитектурной целостности, тестируемости, производительности, воспроизводимости и расширяемости, а не демонстрационного UI.

## 2. Правила работы агента

- Работать с реальным репозиторием и проверять факты; не заявлять о выполненных действиях, тестах, сборках или коммитах без подтверждения.
- В начале каждой сессии синхронизироваться с удалённым GitHub: HEAD ветки, файлы, PR, CI и незакоммиченные/непроверенные изменения, доступные через API.
- Перед редактированием прочитать относящиеся файлы, понять существующие контракты, не переписывать работающие компоненты без причины.
- Выполнять небольшие законченные этапы; код, тесты, документация, CI-верификация, затем следующий этап.
- Не вливать ветку в main без явного разрешения владельца.
- При невозможности доступа/API/CI сообщить об ограничении и не выдумывать результат.
- Технические компромиссы фиксировать ADR, а не оставлять только в переписке.

## 3. Архитектурная стратегия

Основное ядро со временем — C++20, изолированное от платформ, с узким стабильным C ABI. Android-приложение — Kotlin/Jetpack Compose, JNI как адаптер. Core не зависит от Android UI/SDK. Не смешивать языки и технологии без доказанной необходимости.

Ориентировочные кандидаты, не автоматические решения:
- C++20 native-core, C ABI, JNI.
- Renderer: API-абстракция, возможный Filament.
- Геометрия: B-rep backend (например, OCCT) и mesh backend (например, Manifold), адаптеры вместо жёсткой зависимости.
- Математика: Eigen; 2D-операции: Clipper2; оптимизация мешей: meshoptimizer.
- Persistence: SQLite для метаданных, бинарные чанки для тяжёлых данных.
- IFC: отдельный interop-слой и проверка лицензий/совместимости.

**Commercial release license policy:** GPL and AGPL dependencies are prohibited (including indirect/transitive dependencies) unless the project owner explicitly revises this policy after legal review. Prefer permissive MIT, Apache-2.0, BSD-2-Clause/3-Clause licenses. LGPL dependencies (e.g. OCCT candidates) are conditionally allowed only after documented legal/compliance review and only through a separate dynamically linked `.so` module; dynamic linking alone does not guarantee compliance. Meet all LGPL obligations including notices, license texts, source/relink/replacement rights where applicable, and distribution terms. Maintain a dependency/SBOM ledger with SPDX license, exact version, source, modifications, transitive tree, notices and obligations. CI license scan must block prohibited, unknown, or unreviewed licenses.
Все решения оценивать по лицензии, Android-поддержке, размеру, стабильности, точности, скорости, тестируемости и возможности заменить backend.

## 4. Модули и направление зависимостей

Зависимости направлены внутрь; запрещены циклы:
Foundation → Domain → Command/Transaction → Dependency → Geometry API → backend/derived systems; платформенный слой и features подключаются к API ядра, не мутируя модель напрямую.

**Platform/BillingProvider boundary:** Platform contains abstractions `BillingProvider` and store-specific adapters. Google Play Billing, RuStore Pay SDK and AppGallery IAP connect only through platform adapters. Core never imports store SDKs, SKU IDs, purchase tokens or payment APIs. `BillingProvider` returns typed purchase states/results and normalizes errors, cancellation, pending/verification/consume lifecycle. Entitlement policy is separate from UI and does not trust client display state.

Модули: foundation (IDs, units, math, tolerance, errors, cancellation); domain (семантика/параметры); command/transaction; dependency graph; geometry API и backend; projection; spatial; families/constraints; quantities; persistence; IFC; rendering; jobs; extension API; Android platform; feature/UI.

## 5. Координаты, единицы и числовая точность (обязательный контракт)

- Длина ядра: **метры, строго `double`**. Площади — m², объём — m³. Углы — **радианы, строго `double`**.
- Положительный угол вращения — против часовой стрелки (CCW) при наблюдении вдоль положительной оси вращения согласно определённой плоскости/оси.
- Система координат — **правосторонняя (Right-Handed)**. **+Z вверх**, **+X вправо/восток**, **+Y вперёд/север**. Зафиксировать соглашение о взгляде/плоскости при каждом API, где это важно.
- UI, импорт и экспорт являются явными границами конвертации: мм/см/градусы не хранятся в canonical model. Никаких неявных unit conversions.
- Нормализовать и документировать локальные/глобальные координаты, transform composition, origin, оси IFC и геодезическую привязку.
- CPU геометрия — double. GPU/float только в rendering boundary с camera-relative/origin-rebasing. Не переносить pixel/device units в модель.
- Тolerance — отдельный policy/context, не единый магический epsilon. Не менять tolerance/coordinate frame/unit молча.
- Проверять NaN/Inf, overflow, degeneracy, устойчивость predicates, большие координаты и precision loss.

## 6. Canonical Model

Семантическая и параметрическая модель — единственный source of truth. Mesh/B-rep, projections, spatial indices, quantities caches и render data являются derived data и могут пересоздаваться.

Основные сущности: Project, Site, Building, Storey/Level, Space, Element, ElementType, Material, ParameterDefinition/Value, Constraints, Relationships. UUID — постоянная идентичность. Топологические номера/mesh indices не являются устойчивыми semantic IDs.

Модель параметрическая: стены по axis/baseline, высоте, толщине, location-line, слоям; slabs, openings, doors/windows, roofs, stairs, columns, beams, foundations, MEP. Единицы и типы параметров строго typed. Formula — AST, не arbitrary eval.

## 7. Command API и транзакции

Все изменения идут только через команды. UI, инструменты, импортеры, plugins и AI не меняют модель напрямую.

Командный lifecycle: preconditions → staging → validation → derived-state computation → atomic commit → revision publish/events. При ошибке/отмене — rollback, исходная revision неизменна. Команды и схемы версионируются. Multi-element change атомарен.

CommandBus/Transaction Engine должны поддерживать:
- optimistic revision checks и предотвращение stale writes;
- validation pipeline с детерминированными issues;
- immutable state snapshots и monotonic revision IDs;
- Undo/Redo, bounded history, replay/command journal где уместно;
- cancellation, safe failure, event publication после commit;
- cache/dependency invalidation;
- тесты: успешные операции, validation fail, exception, cancel, rollback, undo/redo, stale revision, multi-change atomicity, no partial state.

## 8. Memory model, C++ ownership и C ABI

В C++ ядре:
- запрет raw `new/delete` в прикладном коде; ownership — RAII, smart pointers только там, где ownership оправдан.
- не использовать бесконтрольный `std::shared_ptr` в графе canonical model; избегать pointer-heavy object graph.
- хранение семантических сущностей предпочтительно в плоских структурах/Data-Oriented Design, SlotMap/typed arenas/allocator pools с handles; ID — UUID/типизированный descriptor.
- entity handles должны проверяться на generation/staleness; не допускать use-after-free и ABA.
- Readers (renderer/background jobs) получают immutable snapshots revision N; publication — атомарная безопасная операция. Рассмотреть COW/structural sharing; lock-free чтение только после доказательства memory model и lifetime safety, не как маркетинговое обещание.
- Неизменяемые снапшоты не должны удерживать неограниченно память; budget, lifetime, backpressure, eviction и diagnostics.
- C ABI/JNI не передаёт сложные C++/Kotlin object graphs, STL types, exceptions, ownership ambiguities. Только versioned flat structs, opaque handles, byte buffers (например, FlatBuffers при обосновании), lengths, explicit error/status и documented lifetime.
- ABI versioning, alignment, endianness, allocation/free ownership, thread-safety, cancellation, callback restrictions и error propagation документированы. Exceptions никогда не пересекают ABI.

## 9. Command/Transaction Engine implementation

Стабилизировать контракт в pure Kotlin prototype прежде чем портировать. Портируемые tests служат эталонной спецификацией. Implement revisioned snapshots, validation, atomic state publication, undo/redo, events, cancellation, optimistic concurrency. API должен не зависеть от Android и быть переносимым в C++.

## 10. Dependency Graph

DAG параметрических зависимостей: explicit edges, cycle detection, dirty propagation, incremental recompute, deterministic topological scheduling, revision fingerprints. Не выполнять бесконтрольный full rebuild при каждом изменении. Изолировать graph changes транзакционно.

## 11. Геометрия и численные методы

Geometry API отделён от конкретных библиотек. Immutable requests/results; операции с явно заданным tolerance, coordinate frame и error/diagnostics. B-rep/mesh backend заменяемы. Boolean failures не маскировать и не выдавать приблизительную геометрию за точную без обозначения. Adaptive tessellation, robust predicates, validation, repair diagnostics и regression fixtures. Топология не должна заменять semantic identity.

## 12. Parametric families и constraints

Типизированные параметры, schema, defaults, ranges, units, formula AST, dependency references, constraint solving. Validation и циклы. Versioned families; migrations; deterministic evaluation. Не выполнять пользовательские формулы как произвольный код.

## 13. Архитектурные BIM-элементы

Вводить элементы последовательно: levels/grids; walls/layers; slabs; openings; doors/windows; roofs; stairs; columns/beams; foundations; spaces/rooms. Семантика и параметры определяют derived geometry. Не реализовывать всю категорию одновременно.

## 14. Projection & Drawing

Plans, elevations, sections, hidden lines, dimensions, annotation, view scale, clipping, styles, snapping. Drawing views derived from model with stable references. Testable projection APIs; UI is not the projection engine.

## 15. Spatial subsystem

Spatial index, queries, picking, bounds, containment, collision candidates, spaces, visibility. Derived/revision-bound. Precision and coordinate origin policy must match core.

## 16. Quantities & schedules

Extract quantities from semantic data and validated geometry: areas, lengths, volumes, counts, materials, schedules. Clearly separate exact semantic quantities, approximations and unavailable quantities. Units explicit.

## 17. Persistence & recovery

Versioned model schema; deterministic migrations; atomic save; journal/recovery; checksums; semantic metadata database; derived binary chunks; cache discard/rebuild; corruption handling. Save/load round-trip, crash recovery and migration tests. Unknown extension payload preservation when possible.

## 18. IFC Interoperability

Separate IFC mapping/import/export layer. Explicit unit/coordinate mapping, identity mapping, property sets, levels, classifications, openings and relationships. Validate export and round-trip. Never silently reinterpret units or axes.

### 18.1 Store distribution and native build compatibility

Design for publication to Google Play and alternative stores (RuStore, AppGallery) without store-specific architecture forks:
- Release build supports Android App Bundle (`.aab`) for Google Play and signed APKs as needed for alternative stores/testing. Debug APK remains a development artifact, not the sole release format.
- Native C++ builds target **arm64-v8a** at minimum; add other ABIs only with verified support and testing. Use current stable Android NDK/CMake toolchains and keep ABI/toolchain settings reproducible.
- Native libraries and packaging must support **16 KB memory page sizes** on Android 15+ devices. Prefer NDK r28+ (16 KB alignment by default) and AGP 8.5.1+; for older NDK toolchains configure both linker flags where applicable: `-Wl,-z,max-page-size=16384` and `-Wl,-z,common-page-size=16384`. Inspect ELF segment alignment and APK/AAB packaging, and validate every third-party `.so`; linker flags alone do not prove compliance.
- As of this prompt update (2026-11-23), Google Play requires new apps and updates to target Android 16 / API 36 or higher from 2026-08-31. Set `targetSdkVersion`/`targetSdk` to at least API 36 for current submissions, but maintain an explicit policy check that re-verifies the official requirement before each release. Test compatibility against current Android releases.
- File access follows Scoped Storage. Native project files (`.bimob`), IFC, DXF and other user documents use Storage Access Framework (document picker/create document, persisted URI permissions where appropriate) or MediaStore for applicable media. Do not request `MANAGE_EXTERNAL_STORAGE` unless a documented, store-policy-approved core use case genuinely qualifies; default is prohibited.
- Avoid store-specific services in core. Store billing, distribution, updates, signing, analytics and platform capabilities are adapters behind interfaces; comply separately with each store's policies.
- Release gates include signed artifacts, reproducible build metadata, crash/permission review, privacy disclosures, data safety, content-rating and store listing requirements, and install/upgrade testing on representative Android versions/devices. No release claim until artifacts and checks are verified.

### 18.2 Serverless distribution and offline-first autonomy

- BIMOB has **no mandatory proprietary backend** for core operation. Project creation/open/edit, 2D/3D modeling, drawing generation, schedules/quantities, basic export, validation and model state are local on-device.
- `.bimob` project storage, autosave, recovery, cache and command/history state are local-first. Internet absence must not disable core tools, project access or editing. Core workflows require explicit offline tests.
- No mandatory account/login, telemetry consent, remote license check, or network activation for the free core. No remote dependency may lock users out of their own files.
- App updates, store delivery and payment infrastructure are delegated to official stores. BIMOB does not require an operated update server, payment processor, backend or cloud storage.
- Optional cloud/AI/remote collaboration are separate opt-in extensions. They may require infrastructure but cannot make the core dependent on it. Disclose privacy, data transfer, costs, availability and offline limitations.
- Store-specific update/billing SDKs remain in adapters; current store availability and policy must be rechecked before release.

## 19. Rendering

Rendering is a consumer of immutable revision snapshots, never a model writer. Render API/backend abstraction, scene cache revision, LOD, culling, picking, camera-relative transforms, GPU float boundary. Handle device loss and low memory. Render fidelity can degrade under budget, canonical data cannot. **Offline rendering:** viewport, projections, basic visualization and locally available render features must execute on-device without remote rendering services or network connectivity; optional remote render/AI services are separate opt-in extensions and never block core visualization.

## 20. Jobs, threading and mobile constraints

Bounded worker pools; cancelable jobs; no unbounded thread creation; immutable input snapshots; progress/errors; memory budget; backpressure; incremental operations; LOD and streaming. Avoid blocking UI thread. Low-memory degradation should evict/reduce derived caches, not semantic model.

## 21. Extensions/plugins and voluntary project support

Versioned API, capability-based permissions, registry, stable contracts, lifecycle and compatibility policy. Plugins mutate only via validated commands. No arbitrary code execution by formulas or model data. ABI/API compatibility tests and isolation boundaries.

- A **voluntary developer-support option** is separate from core functionality and project data. Support does not unlock features, add entitlements, affect file access or change project fidelity. No pressure UI, deceptive framing, recurring billing by default or donor perks.
- Payment integrations are behind the platform `BillingProvider`; store SDKs never enter BIMOB Core. The free core remains useful and user files are never payment-locked.
- Review each support flow against the store's current policy, region availability, consumer/payment law, tax/accounting requirements, refund rules and SDK terms before enabling. Do not assume donations are consumable IAP. Google Play policy treats qualifying 100%-to-creator contributions with no digital benefits as peer-to-peer and says Play Billing must not be used. Do not use external payment links in store-distributed builds unless the store/program explicitly allows them.
- Use consumable IAP only for a product the store permits to be classified as consumable and where the SDK requires/defines consumption. Implement purchase acknowledgement/verification, idempotent handling, reconciliation, error/cancel/pending states and `consumePurchase()` only as the SDK specifies for that consumable lifecycle. Never misrepresent a donation as a consumable product or digital reward.
- After successful voluntary support, show a modest, accessible thank-you confirmation. No backend is required for core/support integration; use store-hosted transaction services and local UI state where policy permits.
- Preserve `Capabilities / Entitlements` in Extension System for possible future optional cloud/AI services. Entitlements must be explicit, typed, revocable where appropriate and never gate the free core or user-owned files. Paid features require a separate owner decision and policy review.



## 22. AI assistant layer

AI proposes structured commands, explanations, layouts, diagnostics and workflows. It must never directly mutate canonical state. All proposals pass validation, preview/approval policies, CommandBus, and transaction. Tool outputs treated as untrusted; operations auditable and reversible.

## 23. Security and robustness

Validate all inputs, imports, file sizes, decompression, parser limits, path traversal, malformed IFC/geometry, plugin permissions, serialization. No secrets in repo. Safe failure and diagnostics; fuzz parsers and commands where feasible.

## 24. Testing and quality gates

- unit tests for model invariants, IDs, units, commands, validation, dependencies.
- property-based tests for geometry/math and invariants.
- regression fixtures for geometry/projection/import/export.
- transaction/rollback/recovery and migration tests.
- deterministic tests, concurrency/lifetime tests.
- performance and memory budgets on representative mobile devices.
- ABI compatibility and plugin/API compatibility.
- CI must build and run tests before advancing. Verify actual status and logs.

### 24.1 Android release and native compliance CI

CI should continuously validate distribution readiness, not only debug builds:
- build debug APK and release AAB; verify signing configuration uses protected secrets and never commit signing keys.
- enforce 64-bit `arm64-v8a` native output and inspect packaged native libraries.
- run 16 KB page-size ELF/packaging validation for all native `.so` files (including third-party dependencies); include Android 15+ compatible test coverage where available.
- verify target SDK against current Play policy using a controlled version/configuration check.
- lint permissions and reject `MANAGE_EXTERNAL_STORAGE` by default; test SAF URI workflows for open/save/import/export of `.bimob`, IFC and DXF.
- generate dependency SBOM and license report; fail on GPL/AGPL, unapproved LGPL, unknown license, or missing notices/attribution.
- keep store-specific signing/export jobs separate, with release credentials protected and artifacts uploaded only after all gates pass.
- test install, upgrade, document access, offline project opening, and crash-free smoke test on emulator/device matrix. A green debug CI alone is not release approval.

## 25. Kotlin Prototype → C++20 Core

Current prototype under `app/src/main/java/com/bimob/app/core/model/` is a contract laboratory, not the production kernel.

- Phase B Canonical Model and Phase C Command/Transaction Engine are prototyped in **pure Kotlin** and must have comprehensive tests.
- **Strict rule: package `core` is pure Kotlin; no `android.*` imports.** Keep platform code outside core. Verify via source checks/Gradle modules as practical.
- Tests in Kotlin are executable behavioral specification for C++20 port. Add matching native tests during port and maintain parity.
- The Kotlin `core/model` prototype is the reference implementation for contract stabilization across stages A–D and must remain strictly pure Kotlin (no `android.*` imports).
- Before Geometry Kernel stage G, port foundation/model/command/transaction and stabilized persistence contracts into `native-core/` (C++20), preserving semantics and migrating **all** test scenarios with native parity gates. Do not make Kotlin and C++ competing sources of truth; document transition and parity gates.
- JNI is a thin adapter over versioned C ABI; no domain logic there.
- Port phases: contracts → native foundation/model → commands/transactions → persistence → parity tests → JNI integration → Kotlin prototype retired or retained only as test reference, according to documented ADR.
- No premature UI expansion while core gates fail.

## 26. Repository state (verified 2026-11-23)

Master prompt updated to **v1.4** with Phase D compaction safety, golden fixtures and Android SAF caveats, in addition to license policy, store compatibility, offline-first/serverless autonomy, BillingProvider abstraction and policy-reviewed voluntary support. This is architecture guidance; verify current official store policies and dependency license texts before release.

Repo: `2nw8m4zf5y-jpg/Bimob`; working branch `feature/model-core-v1`; draft PR #1 targets main (do not merge without approval). Branch exists and is writable.

Latest commits in this work session:
- `8b41c09991b3077b8f76c50fc082cb97a2892c19` — add this master prompt v1.1.
- `28f7ea79d67b4bb472f9466ec49190e9cadca261` — initial CommandBus implementation.
- `38f0d01ec11f40d61ad4a97e46e77af69f19aa23` — CommandBus tests.
- `d88fc22a7d65e0b4eb2f96921c24b345dc10a32e` — defensive snapshot collection copies and initial-state validation.
- `2fd1eed9d781fa5f9ec7550b034830dc69220f8b` — ADR 001 transaction design.
- `50cea7a92b4b2ff0a706d35741d42db843193f4a` — bound undo/redo history depth.
- `65055351285f394b419d536c7c7187ca4e01180a` — tests for history limit and initial model validation.
- `f12c54a415b67ebb8c978a766ef135347511b31b` — fixed Kotlin listener type syntax; CI verified green.
- `11f81fcc4475c199f21894dcc41c0fa0ef344f5f` — master prompt v1.2: license policy and store/release constraints.
- `10e45b67d1cee9ca8b5d55410ed6d7189145aa22` — FIFO event delivery across concurrent commits.
- `bf0a415373cf1e7755b0bc144577331b4aef534c` — tests for multi-entity atomicity, concurrent event ordering, and subscription close.
- `13bdac129fdfa5b190049fa85d742e33978f3572` — tightened atomic revision assertion.
- `c6cf5ef58d1b40c954b36d7ed7f7a9877a910861` — master prompt v1.3: serverless/offline-first and voluntary support/payment policy.
- `08f8c249326fea38e2265bb9d6051d79281e0b0d` — reject revision-counter overflow.
- `6fd0916b7f9e9229c7ddce3f712c6b620d596e14` — test revision overflow boundary.
- `c411c0242c8775979af95f3f3b1e42aa62799116` — make published snapshot collection wrappers unmodifiable; isolate ordinary listener exceptions without swallowing fatal errors.
- `045d42b8da4e46285635b7a2a7a2c1e7ecbdfe5b` — snapshot immutability regression test.
- `92b703e53920e66d6c8fd5c0ea3ad37dac0a9263` — transaction staging API, BatchCommand/CompositeCommand and cooperative cancellation checkpoints.
- `f5b97594cfe4622ecdf3b41720241a1f2e1cd684` — tests for third-step batch failure rollback, cancellation between stages, and concurrent snapshot readers during Undo/Redo.
- `a08e28973fe6c6fcc6a127d1f862ccc0caf888ae` — freeze the staged batch command list.
- `475a82cca25952af8915b594e0eebc2e01a82739` — ADR 001 persistence container, atomic save, integrity, versioning and recovery architecture.

Relevant existing files: `docs/architecture/BIMOB_KERNEL_BLUEPRINT_V1.md`; prototype `DomainModel.kt`, `CommandBus.kt`, `ModelValidator.kt` and validator tests. Legacy `ModelCommands.kt`/`ModelStore` was removed; CommandBus is the only canonical model mutation boundary. Always re-check current HEAD and files before future work.

## 27. Progress tracking (verified state at 2026-11-23)

- **Phase B — Canonical Model:** prototype exists with typed IDs/entities, wall geometry fields and validation; this is not yet production complete.
- **Phase C — CommandBus/Transaction Engine: ACCEPTED / CLOSED.** CI for the completed staging implementation was confirmed successful by the project owner; Phase C acceptance is now official. Current `CommandBus.kt` implements mutation-to-candidate, validation gate, revision snapshots, expected-revision rejection, cancellation checkpoints, bounded snapshot undo/redo, no-op handling, and event notification. Tests cover commit/event, stale revision, validation rejection, thrown command, cancellation, undo/redo, no-op, and observer exception.
- **Snapshot note:** model collections are defensively copied and wrapped as unmodifiable lists; current nested model collections are copied too. This guards collection mutation and source-list aliasing, but review new mutable model fields whenever the domain expands.
- **Revision safety:** commit and history restore reject counter overflow instead of wrapping. A pure boundary test covers `Long.MAX_VALUE`.
- **Listener error policy:** ordinary `Exception`s from command code/listeners are isolated; fatal JVM errors are not swallowed.
- ****CI:** Earlier Run #64 was independently verified for commit `540b051...`; the project owner subsequently confirmed CI passed for the completed Phase C staging changes. This is the owner's confirmation; no newer run ID/log URL was supplied in this session.
- **New staging work:** commit `92b703e...` adds `TransactionContext`, checkpoints, `BatchCommand`, and `CompositeCommand`; commit `f5b9759...` adds rollback/cancellation/concurrent snapshot tests; commit `a08e289...` freezes the child command list. CI for these newer commits must be verified before claiming them green.
- **Workflow dispatch / CI access:** workflow has `workflow_dispatch` and push triggers. Push-triggered Actions runs are verified through GitHub REST; E-stage current green evidence is run [45165134999](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45165134999) for SHA `b024d8d`. PR #1 remains open and unmerged; do not merge without owner approval.
- **Acceptance criteria status:** event FIFO/backpressure, bounded history and memory budget, staged batch rollback, cancellation checkpoints, concurrent command submission, parallel snapshot readers during Undo/Redo, and subscription disposal have tests. Phase C is **accepted and closed** following owner-confirmed successful CI.

## 28. Roadmap and next work

A. Architecture contracts and blueprint — documented; verify periodically.  
B. Canonical Model — prototype; continue parity/contract tests.  
C. **CommandBus + Transaction Engine — ACCEPTED / CLOSED.** Staging candidates are private to a transaction. `BatchCommand` and `CompositeCommand` apply children sequentially to a candidate and checkpoint cancellation between children; candidate publication happens only after validation and final cancellation checks. A thrown child or cancellation discards the candidate without touching current revision/history/events. Added failure-at-step-3-of-5 rollback, cancellation-between-stages, and concurrent snapshot-reader stress tests. Command list is copied into an unmodifiable list. CI success for the completed Phase C changes was confirmed by the project owner; acceptance is recorded.  
D. **Persistence & Crash Recovery — ACCEPTED / CLOSED.** ADR 001 approved and implementation complete for the current prototype. Project owner confirmed CI green on `65cf542` for `:core:test`, `:app:testDebugUnitTest`, and `:app:assembleDebug` (workflow's `assembleDebug`). Includes versioned archive/repository, SHA-256 integrity, deterministic canonical JSON and golden fixtures, staged+verified crash-safe journal compaction, recovery tests, schema rejection/migration contract, and Android SAF open/export adapter in `:app`. SAF provider limitations (non-atomic write/no fsync guarantee) are explicitly handled; no broad storage permission is used. Platform SAF smoke testing remains device-level follow-up, not a blocker for closing the core persistence stage.  
E. **Model Ownership & Dependency Engine — ACCEPTED / CLOSED (2026-11-23).** E1 typed DAG/admission; E2 revision-scoped transactional graph and derived state; E3 evaluator registry, configurable resource budgets, deterministic rejection, linear traversal scheduler, and 10,000-node stress verification. CI GREEN on b024d8d.
F. **ModelSession + UI ownership migration — ACCEPTED / CLOSED (2026-11-23).** Sequential CommandBus execution; immutable StateFlow snapshots; coalesced gesture previews/cancel/one commit; typed wall commands; revision repository; deterministic legacy migration; Compose Canvas/panels. Latest CI run #210 passed on `1f622c1b52`. See ADR 003.
G. **Kotlin reference → native-core C++20 migration — IN PROGRESS. G1 contracts implemented; G2 initial transaction/dependency port committed, pending remote CI and golden parity.** See ADR 004/005.
G2: CommandBus, revisions, transaction, evaluator/dependency engine (initial port). G3: Persistence parity. G4: versioned C ABI. G5: JNI integration and Kotlin production retirement after parity gates.
H. Geometry APIs and backend adapters.
I. Architectural families and constraints.
J. Projection/drawing and spatial subsystem.
K. Quantities/schedules.
L. IFC interop.
M. Mobile rendering/performance.
N. Extension system and AI workflows.

Stage E is closed. Preserve offline core workflows and the Android/core boundary. Cache fingerprints, full-recompute oracle, fuzzing and detailed heap profiling remain future hardening items, not blockers to this acceptance.


## 30. Phase D closure and Stage E architecture

- **Phase D CLOSED:** owner confirmed CI green on commit `65cf542` with `:core:test`, `:app:testDebugUnitTest`, and `:assembleDebug`. This is owner-provided CI evidence.
- **Stage E CLOSED:** E1, E2 and E3 accepted. Stage F is implemented and undergoing final acceptance; native C++20 remains deferred.
- **ADR 002:** [Dependency Engine and Model Ownership](adr/002-dependency-engine.md). Owner-approved architecture.
- **UI ownership audit (post-F3):** MainActivity no longer owns a mutable wall list. Compose renders ModelSession snapshots. Create/move/delete/thickness edits route through CommandBus; gesture previews are transient and coalesced; internal revision storage is private. SharedPreferences remains project-name/legacy-migration metadata only. SAF is external import/export.
- **Stage F implementation:** F1 ModelSession, immutable UI DTO contract, sequential command/gesture pipeline and core unit tests; F2 revision codec/private ProjectRepository/legacy migrator and saved-content tracking; F3 Compose Canvas migration, undo/redo, save indicator/errors and scalar wall commands; F4 ADR and roadmap documentation. Code CI GREEN at commit `73ba2b9` (run [#45188662818](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45188662818)): `:core:test`, `testDebugUnitTest`, `assembleDebug`; native contract configure/build/ctest also green; debug APK artifact uploaded. Final documentation-head CI run [#45189249052](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45189249052) is RED before Kotlin tasks: unrelated Stage G2 `native-core/src/engine.cpp:48` contains a literal backslash (`stray '\\'`), so native build fails and Kotlin/Android tasks are skipped. Stage F code CI remains GREEN at `73ba2b9`; do not claim branch-wide acceptance until the separate G2 issue is resolved.
- **Stage E1 CLOSED:** typed immutable DAG, edge admission, deterministic cycle-path diagnostics, depth enforcement, cleanup; CI green on b879e8f.
- **Stage E2 ACCEPTED / CLOSED:** revision-scoped Project+graph+derived state; TransactionContext staging; atomic rollback; Undo/Redo synchronization; dirty propagation; deterministic recompute. Owner accepted E2 on 2026-11-23; CI green on add8a18.
- **Stage E3 ACCEPTED / CLOSED:** immutable EvaluatorRegistry at CommandBus configuration with transaction-local overrides; limits (dirty=100,000, evaluations=1,000,000, fan-out=10,000, graph nodes=250,000, graph edges=1,000,000); preflight graph-size rejection; deterministic ENGINE_LIMIT_EXCEEDED diagnostics; adjacency grouping and FIFO Kahn scheduling. Evaluators receive immutable direct-dependency inputs, avoiding a full derived-value-map copy per evaluation. 10,000-node chain+fan-out stress test. CI green on b024d8d.
- **Stage E CLOSED:** owner-directed completion after E3 CI green. Cache fingerprints, full-recompute oracle, fuzzing and detailed heap profiling remain deferred hardening; tracked as YELLOW, not falsely claimed complete.

## 29. Phase D approved implementation clarifications
- Canonical JSON is deterministic: fixed schema/key order, UTF-8 without BOM, stable escaping, no insignificant whitespace, locale-independent finite numeric format using shortest-round-trip decimal (`BigDecimal.valueOf` in Kotlin), negative zero normalized to `0`, and exact-byte SHA-256. Kotlin and C++20 must match golden byte/hash fixtures.
- Manifest declares `chunks/` reserved for future binary geometry/data; chunks remain unimplemented until explicit design.
- After successful atomic primary save, journal compaction is safe and bounded. Rotation writes/fsyncs and verifies the replacement recovery state before switching; never destroy the only valid journal early.
- Persistence core has no Android SDK imports; filesystem/document-provider integration stays in adapters.

## 30. Phase D implementation state (2026-11-23)
- The Android-SDK-free Gradle JVM module `core` is created and included in the app build. Existing model, validator, memory estimator and CommandBus source moved to `core/src/main/kotlin` with the same package names, so app-side tests/imports remain compatible.
- Persistence code currently includes canonical JSON codec, `.bimob` ZIP archive writer/reader, manifest and SHA-256 index validation, CRC-checked ZIP reading, schema migration registry, `AtomicStorage` contract, `ProjectRepository`, and append-only `CrashRecoveryJournal`.
- The v1 codec presently supports the concrete `WallElement` model; other element categories are not yet represented as concrete serializable model classes. Adding types requires explicit codec support and fixtures.
- Test-only `JvmAtomicStorage` exercises temp-write, fsync, byte verification and atomic replace. Android `SafDocumentAdapter` is implemented in `:app`, uses ACTION_OPEN_DOCUMENT/ACTION_CREATE_DOCUMENT, app-private temp staging, stream read-back and SHA-256 verification. It is intentionally not `AtomicStorage`: SAF provider writes may be non-atomic and durability cannot be assumed.
- Tests added: deterministic empty-project and rich multilingual golden JSON + SHA-256 (two walls, layers, bool/int/real/text/ref/enum values, quotes/newlines, Cyrillic, CJK and emoji), round-trip, damaged archive rejection, crash recovery after simulated restart, torn-journal-tail recovery, journal rotation, compaction switch failure preserving the prior journal, and future-schema rejection.
- Workflow `.github/workflows/build-apk.yml` now runs `:core:test`, app unit tests and APK build. CI for the Phase D commits is **pending verification**; connector access cannot retrieve push-triggered run details. Do not claim tests pass until the run is inspected.
- Recent implementation commits (branch `feature/model-core-v1`): `c8f24a2` model relocation; `605c219` canonical codec; `66e2eac` archive/repository; `0bfdc34` recovery journal; `1e63564` persistence tests; `e9806b4` CI core-test integration; `eedf39b` durability capabilities, idempotent journal transactions, and atomic-failure regression coverage. Subsequent documentation commits are separate.
- Task commits after the owner-confirmed `d1bffdd`: `aeda479` safe staged compaction; `88935bf` compaction interruption regression test; `c91744d` empty golden fixture; `64a32ca` rich multilingual fixture; `a928f39` canonical bytes/hash fixture test; `7c7a4d9` SAF document adapter; `5cf2d98` picker import/export integration; `e314c0d` export filename sanitization; `a624438` clears imported BIMOB metadata when switching to local sketch storage; `6987548` shortest-round-trip canonical double formatting; `5c2e5f2` negative-zero/decimal regression test; `b91b210` Phase D progress update; `573f702` ADR implementation update; `582e4d2` task commit log; `13c70b2`, `38044ba`, `581a890` and `625c620` update the progress/history and ADR numeric-format documentation.
- Phase D is **ACCEPTED / CLOSED**. Owner-confirmed CI green on `65cf542` covered `:core:test`, `:app:testDebugUnitTest`, and `assembleDebug`. Device-level SAF provider behavior remains a follow-up, not a core gate.
- Stage E1 code commits: `fcb34c6` typed immutable dependency graph and `9857900` graph admission/cycle/depth regression tests. E1 includes deterministic closed cycle-path diagnostics, atomic batch admission, copy-on-write snapshots, edge/node cleanup and max-depth enforcement. CI was later verified green during E-stage integration; see current Stage E closure.

## Stage F — ModelSession and UI ownership migration (2026-11-23)

- **F1 IMPLEMENTED:** serialized ModelSession, immutable StateFlow SessionSnapshot DTO (revision metadata + immutable Canvas wall values; no domain ModelRevision/Project objects exposed to Compose), Undo/Redo, transient scalar gesture preview, cancellation rollback, one-command semantic gesture commit, engine-limit diagnostics. Core tests cover concurrent command serialization, coalescing, cancellation, Undo/Redo and rollback.
- **F2 IMPLEMENTED:** deterministic ModelRevision codec (project + graph + derived values + revision number); private AtomicStorage adapter; revision-scoped dirty comparison; deterministic legacy sketch migrator and CommandBus validation.
- **F3 IMPLEMENTED:** Compose UI/Canvas uses only ModelSession scalar APIs and immutable Canvas DTOs; typed wall create/move/delete/thickness commands; gesture coalescing; Undo/Redo controls; save status/error display; SAF only for archive import/export. MainActivity contains no mutable wall/geometry collection and does not access ModelRevision/CommandBus.
- **F3 CI verified green:** commit `0340fd1`, run [#45184281677](https://github.com/2nw8m4zf5y-jpg/Bimob/actions/runs/45184281677). Expanded regression tests and saved-content dirty tracking are CI green at `1299c8e` (run #45184729141). Final documentation-head CI is pending.
- **F4 documentation updated:** MASTER_PROMPT, ENGINEERING_AGENT_PROMPT and ADR 003 record the snapshot-only Compose contract and serialized revision save/export. Stage F code CI is GREEN at `73ba2b9`; current docs-head CI is blocked by the unrelated Stage G2 native compile error described above. Formal Stage F closure remains pending a green full-branch CI.
- **Remaining YELLOW:** native device interaction test for pointer cancellation, directory fsync support in private storage, crash-recovery behavior for revision files, and UI-level test coverage. These are not claimed as verified by JVM/CI tests.


## 29. Stage F closeout and Stage G1 native foundation (2026-11-23)

Stage F is closed after GitHub Actions runs #207–#210 passed on successive commits; latest verified head is `1f622c1b52f65f8c0b1179879062596e220ce2d9`. The current UI uses ModelSession and immutable snapshots, and the gesture preview freeze regression is covered. Main remains unchanged; PR #1 is open.

Stage G1 introduces an independent C++20 contract laboratory in `native-core/`: typed opaque IDs, metre-based `double` units, 2D points, wall-axis length/validity, wall invariants, project validation, and owned snapshot copy semantics. CMake/CTest is integrated into the existing CI workflow with warnings-as-errors and ASan/UBSan on Ubuntu. No JNI or Android production linkage is introduced in G1. Kotlin remains the behavioral reference.

G1 must not be declared accepted until the new commit's native CI job and the existing Kotlin tests/Android debug build all pass. The local container build is useful evidence, but does not substitute for remote CI.


## 30. Stage G1 closeout and G2 initial native transaction port (2026-11-23)

Stage F is accepted and closed. G1 model contracts are implemented: explicit `kCoordinateEpsilon` and `kMinWallLength`, deterministic validation, and no exceptions in public model validation. Remote CI confirmation is still required for acceptance because the latest observed run was pending after a Kotlin test syntax correction.

G2 initial implementation adds a platform-free C++20 CommandBus/revision/dependency engine:
- immutable `ModelRevision` fields with shared const project, graph, derived map and entity-store snapshots;
- unchanged wall entities are shared between revisions;
- deterministic sorted graph/adjacency and FIFO Kahn scheduler;
- direct-dependency-only evaluator inputs;
- base EvaluatorRegistry plus transaction-local override copy;
- all six defensive limits, preflight graph checks and explicit structured budget errors;
- atomic candidate validation/recompute before publishing;
- Undo/Redo and gesture-ID coalescing;
- CTest coverage for command lifecycle, rollback, coalescing, limits, evaluator override, golden scenario and 10,000-node chain + fan-out stress.

G2 is **INITIAL PORT / NOT ACCEPTED**. Local ASan+UBSan CTest passed; stress processed 10,050 nodes, 10,049 edges and exactly 10,050 evaluator calls in approximately 0.61 seconds. This is not yet cross-language golden parity or a remote CI result. Remaining gaps include exact cycle-path parity, cancellation, event/history resource budgets, expanded BIM model coverage, persistence parity and deeper Kotlin fixture extraction.

Main and PR #1 remain untouched; changes stay on `feature/model-core-v1`.
