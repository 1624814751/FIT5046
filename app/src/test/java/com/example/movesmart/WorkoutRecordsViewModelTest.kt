package com.example.movesmart

import androidx.lifecycle.ViewModelStore
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WorkoutRecord
import com.example.movesmart.shared.WorkoutRepository
import com.example.movesmart.workout.WorkoutRecordsViewModel
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Controlled repository tests the ViewModel boundary; Room SQL is tested separately. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRecordsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val row = WorkoutRecord(1, "user-a", "Walk", 30, LocalDate.of(2026, 10, 11), null, DataSource.LIVE)
    private val repository = ControlledRepository()
    private fun model() = WorkoutRecordsViewModel(repository, "user-a").also { store.put("records", it) }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun observesRepositoryUpdatesIncludingAnEmptyList() = runTest(dispatcher) {
        val model = model()
        assertTrue(model.state.value.loading)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.records.isEmpty())
        repository.rows.value = listOf(row)
        runCurrent()
        assertEquals(listOf(row), model.state.value.records)
        repository.rows.value = emptyList()
        runCurrent()
        assertTrue(model.state.value.records.isEmpty())
    }

    @Test fun readFailureIsVisibleAndRetryCanRecover() = runTest(dispatcher) {
        repository.readFailure = true
        val model = model()
        runCurrent()
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.loading)
        repository.readFailure = false
        repository.rows.value = listOf(row)
        model.retry()
        runCurrent()
        assertNull(model.state.value.error)
        assertEquals(listOf(row), model.state.value.records)
    }

    @Test fun failedWritesReturnFailureAndReleaseBusyFlag() = runTest(dispatcher) {
        repository.writeResult = Result.failure(IllegalStateException("Disk full"))
        val model = model()
        assertTrue(model.save(row.copy(id = 0)).isFailure)
        assertFalse(model.isSaving.value)
        repository.writeResult = Result.success(2)
        assertEquals(2, model.save(row.copy(id = 0)).getOrThrow())
        assertFalse(model.isSaving.value)
    }

    @Test fun rejectsForeignUserBeforeCallingRepository() = runTest(dispatcher) {
        val model = model()
        assertTrue(model.save(row.copy(userId = "user-b")).isFailure)
        assertEquals(0, repository.writes)
    }

    @Test fun overlappingSaveAndDeleteAreRejectedWhileFirstWriteRuns() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val model = model()
        val pending = async { model.save(row.copy(id = 0)) }
        runCurrent()
        assertTrue(model.isSaving.value)
        assertTrue(model.save(row.copy(id = 0)).isFailure)
        assertTrue(model.delete(row.id).isFailure)
        assertEquals(1, repository.writes)
        repository.gate!!.complete(Unit)
        assertTrue(pending.await().isSuccess)
        assertFalse(model.isSaving.value)
    }

    @Test fun cancellationPropagatesAndClearsBusyFlag() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val model = model()
        val pending = async { model.save(row.copy(id = 0)) }
        runCurrent()
        pending.cancel()
        runCurrent()
        assertTrue(pending.isCancelled)
        assertFalse(model.isSaving.value)
    }

    @Test fun deleteUsesCurrentUserAndReturnsFailureWithoutFakeRemoval() = runTest(dispatcher) {
        repository.rows.value = listOf(row)
        val model = model()
        runCurrent()
        assertTrue(model.delete(1).isSuccess)
        assertEquals("user-a", repository.deletedUser)
        repository.deleteResult = Result.failure(IllegalStateException("Disk error"))
        assertTrue(model.delete(1).isFailure)
        assertEquals(listOf(row), model.state.value.records)
        assertFalse(model.isSaving.value)
    }

    @Test fun repositoryCancellationIsNotConvertedToNormalFailure() = runTest(dispatcher) {
        repository.cancelWrite = true
        val model = model()
        try {
            model.save(row.copy(id = 0))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(model.isSaving.value)
        }
    }

    private class ControlledRepository : WorkoutRepository {
        val rows = MutableStateFlow<List<WorkoutRecord>>(emptyList())
        var readFailure = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        var cancelWrite = false
        var writeResult = Result.success(1)
        var deleteResult = Result.success(Unit)
        var deletedUser: String? = null
        override fun observe(userId: String): Flow<List<WorkoutRecord>> = flow {
            if (readFailure) throw IllegalStateException("Unable to read database")
            rows.collect { emit(it) }
        }
        override suspend fun get(userId: String, id: Int) = Result.success(rows.value.find { it.id == id && it.userId == userId })
        override suspend fun upsert(record: WorkoutRecord): Result<Int> {
            writes++
            if (cancelWrite) throw CancellationException("Cancelled write")
            gate?.await()
            return writeResult
        }
        override suspend fun delete(userId: String, id: Int): Result<Unit> {
            deletedUser = userId
            return deleteResult
        }
    }
}
