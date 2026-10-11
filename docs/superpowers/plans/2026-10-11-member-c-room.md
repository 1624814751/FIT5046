# Member C Room Implementation Plan

> For agentic workers: execute inline using executing-plans and test-driven-development. Use requesting-code-review for independent read-only review. No automatic remote writes.

**Goal:** Persist user-scoped workout CRUD and feed the existing History, Search and Statistics screens from the same Room database.

**Architecture:** Room Entity and DAO are private storage details. RoomWorkoutRepository implements the shared contract. WorkoutRecordsViewModel exposes observable state and serializes writes. Existing screens use a documented legacy adapter rather than a second record store.

**Tech Stack:** Existing Kotlin 2.2.10 / AGP 9.3.2 / Compose; Room 2.8.x; KSP; coroutines Flow; JUnit and a host Android runtime, plus Android instrumentation tests.

## Task 1 Baseline and configuration

- [x] Run baseline tests using Android Studio JBR 21: existing 22 tests passed.
- [x] Add pinned Room 2.8.5 / KSP 2.3.10 and test runtime. Export schema under app/schemas; preserve IDE changes. KSP was adjusted after a verified built-in Kotlin compatibility error.

## Task 2 Database contract tests before implementation

Create app/src/test/java/com/example/movesmart/RoomWorkoutRepositoryTest.kt and its validation tests. Use a fixed clock and a real Room database; each test creates its own database, never deletes application production data.

```kotlin
val result = repository.upsert(record.copy(id = 0))
assertTrue("Insert must succeed", result.isSuccess)
assertTrue(result.getOrThrow() > 0)
assertEquals("Walk", repository.get("user-a", result.getOrThrow()).getOrThrow()!!.title)
```

- [x] Write cases for empty startup, persistent reopen, Flow ordering, update/delete, invalid records and foreign IDs.
- [x] Add minimal declarations and failing repository/validation stubs under data/local so tests compile.
- [x] Verify meaningful RED: 11 tests, 8 failed before implementation.

## Task 3 Room implementation

Create data/local/WorkoutEntity.kt, WorkoutDao.kt, MoveSmartDatabase.kt, WorkoutValidation.kt and RoomWorkoutRepository.kt. Entity uses primitives matching the shared schema. DAO uses userId in every get/update/delete statement; repository uses a transaction for insert ID checks and returns failure for nonexistent updates/deletes.

```kotlin
interface WorkoutRepository {
    fun observe(userId: String): Flow<List<WorkoutRecord>>
    suspend fun get(userId: String, id: Int): Result<WorkoutRecord?>
    suspend fun upsert(record: WorkoutRecord): Result<Int>
    suspend fun delete(userId: String, id: Int): Result<Unit>
}
```

- [x] Replace the failing stubs with validated mapping and DAO calls; cancellation propagates.
- [x] Database and validation suite GREEN: 11 passed; generated v1 schema included.

## Task 4 ViewModel tests and implementation

Create workout/WorkoutRecordsViewModel.kt and test/WorkoutRecordsViewModelTest.kt. Inject a WorkoutRepository and userId. Test real ViewModel behavior with a controlled repository at this boundary, not pretend it is a database test.

```kotlin
assertTrue(viewModel.state.value.loading)
assertFalse(viewModel.save(foreignUserRecord).isSuccess)
assertFalse(viewModel.isSaving.value)
```

- [x] Write tests for observed records, read errors and retry, successful/failed writes, cancellation and duplicate-save gating.
- [x] Verify RED (8 tests, 7 failed), implement and verify GREEN (8 passed).

## Task 5 Existing screen integration

Modify navigation/MoveSmartApp.kt and ui/screens/WorkoutScreens.kt; change StatisticsScreen labels without changing D's chart implementation. Use lifecycle-aware state collection and a shared-record-to-legacy adapter. History and Search get the observed list; loading/error views have retry. Editor remains open on failure. Live saves explicitly DEMO data. Stop using PrototypeState.records and remove workout serialization from its saver.

- [x] Host Compose regressions cover empty History, cancel, CRUD/search/statistics, read retry, failed save, duration boundaries and pending-write system Back. Device reopen/isolation test added.
- [x] Prototype UI tests now use a debug-only Activity with a unique retained test database and asynchronous waits; no production workout test pollution.
- [x] All 47 host tests, APK builds and lint verified; default configuration-cache build also verified. Device availability checked: no connected device, device execution pending. No existing emulator data was deleted.

## Task 6 Review and local handoff

- [x] Independent read-only review completed; relevant findings repaired and regression-tested.
- [x] Handoff/learning map and honest English AI-usage fragment written with actual verification limits.
- [x] Stage only feature/test/schema/build files and handoff docs; preserve .idea changes and ignore local.properties.
- [x] Local design/feature/handoff commits use the existing Git identity. Preserve member-c-dev at the ready-to-push checkpoint; no remote push or main merge performed. Device execution remains a pre-merge acceptance item.
