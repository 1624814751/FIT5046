# Member C Room Implementation Plan

> For agentic workers: execute inline using executing-plans and test-driven-development. Use requesting-code-review for independent read-only review. No automatic remote writes.

**Goal:** Persist user-scoped workout CRUD and feed the existing History, Search and Statistics screens from the same Room database.

**Architecture:** Room Entity and DAO are private storage details. RoomWorkoutRepository implements the shared contract. WorkoutRecordsViewModel exposes observable state and serializes writes. Existing screens use a documented legacy adapter rather than a second record store.

**Tech Stack:** Existing Kotlin 2.2.10 / AGP 9.3.2 / Compose; Room 2.8.x; KSP; coroutines Flow; JUnit and a host Android runtime, plus Android instrumentation tests.

## Task 1 Baseline and configuration

- [ ] Run `gradlew.bat --offline --no-daemon --no-configuration-cache :app:testDebugUnitTest` using Android Studio JBR 21. Expected: existing 22 tests pass.
- [ ] Add pinned Room/KSP dependencies and test runtime to gradle/libs.versions.toml, root build.gradle.kts and app/build.gradle.kts. Enable Android resources for host tests; export schema under app/schemas. Keep IDE changes out of staged files.

## Task 2 Database contract tests before implementation

Create app/src/test/java/com/example/movesmart/RoomWorkoutRepositoryTest.kt and its validation tests. Use a fixed clock and a real Room database; each test creates its own database, never deletes application production data.

```kotlin
val result = repository.upsert(record.copy(id = 0))
assertTrue("Insert must succeed", result.isSuccess)
assertTrue(result.getOrThrow() > 0)
assertEquals("Walk", repository.get("user-a", result.getOrThrow()).getOrThrow()!!.title)
```

- [ ] Write cases for empty startup, persistent reopen, Flow ordering, update/delete, invalid records and foreign IDs.
- [ ] Add minimal declarations and failing repository/validation stubs under data/local so tests compile.
- [ ] Run the new tests and verify expected assertion failures for missing validation and writes. Keep this RED evidence.

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

- [ ] Replace the failing stubs with validated mapping and DAO calls; cancellation propagates.
- [ ] Run the database and validation suite GREEN. Add the generated v1 schema to source control.

## Task 4 ViewModel tests and implementation

Create workout/WorkoutRecordsViewModel.kt and test/WorkoutRecordsViewModelTest.kt. Inject a WorkoutRepository and userId. Test real ViewModel behavior with a controlled repository at this boundary, not pretend it is a database test.

```kotlin
assertTrue(viewModel.state.value.loading)
assertFalse(viewModel.save(foreignUserRecord).isSuccess)
assertFalse(viewModel.isSaving.value)
```

- [ ] Write tests for observed records, read errors and retry, successful/failed writes, cancellation and duplicate-save gating.
- [ ] Run tests RED with minimal state/write stubs, implement coroutine observation and finally-based busy clearing, then run GREEN.

## Task 5 Existing screen integration

Modify navigation/MoveSmartApp.kt and ui/screens/WorkoutScreens.kt; change StatisticsScreen labels without changing D's chart implementation. Use lifecycle-aware state collection and a shared-record-to-legacy adapter. History and Search get the observed list; loading/error views have retry. Editor remains open on failure. Live saves explicitly DEMO data. Stop using PrototypeState.records and remove workout serialization from its saver.

- [ ] Add a host/device regression for empty History and CRUD. Add Android database reopen and user isolation tests under androidTest.
- [ ] Update prototype UI tests so their baseline is empty, not sampleRecords; use isolated test data for assertions.
- [ ] Run build, all JVM tests, lint and Android test APK compilation. Attempt device checks safely without changing another project or deleting existing user data; report a blocked emulator truthfully.

## Task 6 Review and local handoff

- [ ] Request independent read-only code review, repair significant findings and rerun checks.
- [ ] Write docs/A4_MEMBER_C_ROOM.md and docs/AI_USAGE_MEMBER_C_ROOM.md with actual verification and integration limits. Include Member C's file-by-file learning map.
- [ ] Stage only the feature, tests, schema, build configuration and docs; preserve .idea changes.
- [ ] Create local commits using the existing Git identity. Verify `git status`, commit diff and `origin/member-c-dev` relationship. Stop ready for `git push origin member-c-dev`; do not perform it without a direct request.
