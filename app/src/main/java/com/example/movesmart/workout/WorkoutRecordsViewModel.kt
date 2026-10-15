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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class WorkoutRecordsState(val loading: Boolean = true, val records: List<WorkoutRecord> = emptyList(), val error: String? = null)

class WorkoutRecordsViewModel(
    private val repository: WorkoutRepository,
    val userId: String,
    private val currentUserId: () -> String?
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkoutRecordsState())
    val state = mutableState.asStateFlow()
    private val saving = MutableStateFlow(false)
    val isSaving = saving.asStateFlow()
    private val writes = Mutex()
    private var observation: Job? = null
    private var pendingWrite: Job? = null
    private var active = true

    init {
        require(userId.isNotBlank()) { "A user ID is required." }
        retry()
    }

    fun stopObserving() {
        active = false
        observation?.cancel()
        pendingWrite?.cancel()
        mutableState.value = WorkoutRecordsState()
    }

    fun retry() {
        observation?.cancel()
        mutableState.value = WorkoutRecordsState()
        active = currentUserId() == userId
        if (!active) return
        observation = viewModelScope.launch {
            try {
                repository.observe(userId).collect { rows ->
                    if (active && currentUserId() == userId) {
                        mutableState.value = WorkoutRecordsState(loading = false, records = rows.filter { it.userId == userId })
                    }
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
        if (!active || currentUserId() != userId) {
            return Result.failure(IllegalStateException("Sign in to this account before changing workouts."))
        }
        if (!writes.tryLock()) return Result.failure(IllegalStateException("A workout change is already being saved."))
        saving.value = true
        return try {
            coroutineScope {
                pendingWrite = currentCoroutineContext()[Job]
                block()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            pendingWrite = null
            saving.value = false
            writes.unlock()
        }
    }

    companion object {
        fun factory(repository: WorkoutRepository, userId: String, currentUserId: () -> String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(WorkoutRecordsViewModel::class.java))
                    return WorkoutRecordsViewModel(repository, userId, currentUserId) as T
                }
            }
    }
}
