# BIMOB MASTER PROMPT v1.4

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

Master prompt updated to **v1.3** with license policy, store compatibility, offline-first/serverless autonomy, BillingProvider abstraction and policy-reviewed voluntary support. This is architecture guidance; verify current official store policies and dependency license texts before release.

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

Relevant existing files: `docs/architecture/BIMOB_KERNEL_BLUEPRINT_V1.md`; prototype `DomainModel.kt`, `ModelCommands.kt`, `ModelValidator.kt` and validator tests. Always re-check current HEAD and files before future work.

## 27. Progress tracking (verified state at 2026-11-23)

- **Phase B — Canonical Model:** prototype exists with typed IDs/entities, wall geometry fields and validation; this is not yet production complete.
- **Phase C — CommandBus/Transaction Engine:** first prototype commit landed. Current `CommandBus.kt` implements mutation-to-candidate, validation gate, revision snapshots, expected-revision rejection, cancellation checkpoints, bounded snapshot undo/redo, no-op handling, and event notification. Tests cover commit/event, stale revision, validation rejection, thrown command, cancellation, undo/redo, no-op, and observer exception.
- **Snapshot note:** model collections are defensively copied and wrapped as unmodifiable lists; current nested model collections are copied too. This guards collection mutation and source-list aliasing, but review new mutable model fields whenever the domain expands.
- **Revision safety:** commit and history restore reject counter overflow instead of wrapping. A pure boundary test covers `Long.MAX_VALUE`.
- **Listener error policy:** ordinary `Exception`s from command code/listeners are isolated; fatal JVM errors are not swallowed.
- **CI:** Run #36 on `e63ff36...` initially failed only because Maven Central returned HTTP 429 for JUnit; retry attempt 2 completed green. The newest run for snapshot immutability tests is pending; verify its final result before claiming this set of changes passes.
- **Acceptance criteria status:** tests for multi-entity atomic commit, concurrent writer event order, and subscription disposal have been added. Remaining gaps: event queue/backpressure policy under slow subscribers, history memory/disposal budget, richer transaction staging and cooperative cancellation semantics, and green CI for latest commits.

## 28. Roadmap and next work

A. Architecture contracts and blueprint — documented; verify periodically.  
B. Canonical Model — prototype; continue parity/contract tests.  
C. **CommandBus + Transaction Engine — in progress, not complete.** FIFO event delivery is now queued at commit time and dispatched outside the model monitor; concurrent commit events should retain revision order. Tests added for atomic multi-entity changes, concurrent writers/event ordering, and listener disposal. Next: verify the new immutability/overflow tests in CI, define event queue/backpressure policy, bound history by memory as well as count, and strengthen cooperative cancellation semantics.  
D. Persistence/recovery.  
E. Dependency DAG/incremental recompute.  
F. Geometry APIs and backend adapters.  
G. Architectural families and constraints.  
H. Projection/drawing and spatial subsystem.  
I. Quantities/schedules.  
J. IFC interop.  
K. Mobile rendering/performance.  
L. Extension system and AI workflows.

Do not advance to Phase D until Phase C acceptance gates and CI pass. Include an offline smoke test proving core modeling and project open/save/export do not require network access.