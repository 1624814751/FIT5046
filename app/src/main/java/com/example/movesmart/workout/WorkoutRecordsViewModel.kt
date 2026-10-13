package com.example.movesmart.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.shared.WorkoutRecord
import com.example.movesmart.shared.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class WorkoutRecordsState(val loading: Boolean = true, val records: List<WorkoutRecord> = emptyList(), val error: String? = null)

class WorkoutRecordsViewModel(private val repository: WorkoutRepository, val userId: String) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkoutRecordsState())
    val state = mutableState.asStateFlow()
    private val saving = MutableStateFlow(false)
    val isSaving = saving.asStateFlow()
    private val writes = Mutex()
    private var observation: Job? = null

    init {
        require(userId.isNotBlank()) { "A user ID is required." }
        retry()
    }

    fun retry() {
        observation?.cancel()
        mutableState.value = WorkoutRecordsState()
        observation = viewModelScope.launch {
            try {
                repository.observe(userId).collect { rows ->
                    mutableState.value = WorkoutRecordsState(loading = false, records = rows)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = WorkoutRecordsState(loading = false, error = "Could not load workouts. Please retry.")
            }
        }
    }

    suspend fun save(record: WorkoutRecord): Result<Int> {
        if (record.userId != userId) return Result.failure(IllegalArgumentException("This workout belongs to another user."))
        return write { repository.upsert(record) }
    }

    suspend fun delete(id: Int): Result<Unit> = write { repository.delete(userId, id) }

    private suspend fun <T> write(block: suspend () -> Result<T>): Result<T> {
        if (!writes.tryLock()) return Result.failure(IllegalStateException("A workout change is already being saved."))
        saving.value = true
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            saving.value = false
            writes.unlock()
        }
    }

    companion object {
        fun factory(repository: WorkoutRepository, userId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(WorkoutRecordsViewModel::class.java))
                    return WorkoutRecordsViewModel(repository, userId) as T
                }
            }
    }
}
