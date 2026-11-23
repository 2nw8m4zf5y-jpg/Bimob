# BIMOB MASTER PROMPT v1.1

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
Все решения оценивать по лицензии, Android-поддержке, размеру, стабильности, точности, скорости, тестируемости и возможности заменить backend.

## 4. Модули и направление зависимостей

Зависимости направлены внутрь; запрещены циклы:
Foundation → Domain → Command/Transaction → Dependency → Geometry API → backend/derived systems; платформенный слой и features подключаются к API ядра, не мутируя модель напрямую.

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

## 19. Rendering

Rendering is a consumer of immutable revision snapshots, never a model writer. Render API/backend abstraction, scene cache revision, LOD, culling, picking, camera-relative transforms, GPU float boundary. Handle device loss and low memory. Render fidelity can degrade under budget, canonical data cannot.

## 20. Jobs, threading and mobile constraints

Bounded worker pools; cancelable jobs; no unbounded thread creation; immutable input snapshots; progress/errors; memory budget; backpressure; incremental operations; LOD and streaming. Avoid blocking UI thread. Low-memory degradation should evict/reduce derived caches, not semantic model.

## 21. Extensions/plugins

Versioned API, capability-based permissions, registry, stable contracts, lifecycle and compatibility policy. Plugins mutate only via validated commands. No arbitrary code execution by formulas or model data. ABI/API compatibility tests and isolation boundaries.

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

## 25. Kotlin Prototype → C++20 Core

Current prototype under `app/src/main/java/com/bimob/app/core/model/` is a contract laboratory, not the production kernel.

- Phase B Canonical Model and Phase C Command/Transaction Engine are prototyped in **pure Kotlin** and must have comprehensive tests.
- **Strict rule: package `core` is pure Kotlin; no `android.*` imports.** Keep platform code outside core. Verify via source checks/Gradle modules as practical.
- Tests in Kotlin are executable behavioral specification for C++20 port. Add matching native tests during port and maintain parity.
- After Phase C/D contract stabilization, port foundation/model/command/transaction and persistence contracts into `native-core/` (C++20), preserving semantics. Do not make Kotlin and C++ competing sources of truth; define transition and parity gates.
- JNI is a thin adapter over versioned C ABI; no domain logic there.
- Port phases: contracts → native foundation/model → commands/transactions → persistence → parity tests → JNI integration → Kotlin prototype retired or retained only as test reference, according to documented ADR.
- No premature UI expansion while core gates fail.

## 26. Repository state (verified 2026-11-23)

Repo: `2nw8m4zf5y-jpg/Bimob`; working branch `feature/model-core-v1`; draft PR #1 targets main (do not merge without approval). Branch exists and is writable.

Latest commits in this work session:
- `8b41c09991b3077b8f76c50fc082cb97a2892c19` — add this master prompt v1.1.
- `28f7ea79d67b4bb472f9466ec49190e9cadca261` — initial CommandBus implementation.
- `38f0d01ec11f40d61ad4a97e46e77af69f19aa23` — CommandBus tests.
- `d88fc22a7d65e0b4eb2f96921c24b345dc10a32e` — defensive snapshot collection copies and initial-state validation.
- `2fd1eed9d781fa5f9ec7550b034830dc69220f8b` — ADR 001 transaction design.

Relevant existing files: `docs/architecture/BIMOB_KERNEL_BLUEPRINT_V1.md`; prototype `DomainModel.kt`, `ModelCommands.kt`, `ModelValidator.kt` and validator tests. Always re-check current HEAD and files before future work.

## 27. Progress tracking (verified state at 2026-11-23)

- **Phase B — Canonical Model:** prototype exists with typed IDs/entities, wall geometry fields and validation; this is not yet production complete.
- **Phase C — CommandBus/Transaction Engine:** first prototype commit landed. Current `CommandBus.kt` implements mutation-to-candidate, validation gate, revision snapshots, expected-revision rejection, cancellation checkpoints, bounded snapshot undo/redo, no-op handling, and event notification. Tests cover commit/event, stale revision, validation rejection, thrown command, cancellation, undo/redo, no-op, and observer exception.
- **Snapshot note:** Kotlin collection defensive copies and initial model validation were added. Deep immutability and concurrent event ordering remain follow-up gaps, documented in ADR 001.
- **CI:** GitHub API combined statuses returned an empty status list for the latest test commit; commit-associated workflow-run query also returned no runs. This means CI is **not verified**, not passing. The web Actions page was inaccessible from this session.
- **Acceptance criteria not yet met:** multi-change atomicity tests, history limit/disposal, concurrency and event ordering, freeze/deep-copy guarantees, revision overflow, and successful CI run.

## 28. Roadmap and next work

A. Architecture contracts and blueprint — documented; verify periodically.  
B. Canonical Model — prototype; continue parity/contract tests.  
C. **CommandBus + Transaction Engine — in progress, not complete.** Next tasks: improve staging/commit concurrency design; add tests for multi-element atomicity, history bounds, listener disposal, event ordering/concurrent writers, defensive copies, and revision overflow; ensure initial-state invariant behavior and cancellation semantics; run and verify CI.  
D. Persistence/recovery.  
E. Dependency DAG/incremental recompute.  
F. Geometry APIs and backend adapters.  
G. Architectural families and constraints.  
H. Projection/drawing and spatial subsystem.  
I. Quantities/schedules.  
J. IFC interop.  
K. Mobile rendering/performance.  
L. Extension system and AI workflows.

Do not advance to Phase D until Phase C acceptance gates and CI pass.