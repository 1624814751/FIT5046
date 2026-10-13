package com.example.movesmart.data.local

import com.example.movesmart.shared.WorkoutRecord
import com.example.movesmart.shared.WorkoutRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import com.example.movesmart.shared.DataSource
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWorkoutRepository(private val database: MoveSmartDatabase, private val clock: Clock = Clock.systemDefaultZone()) : WorkoutRepository {
    private val dao = database.workouts()

    override fun observe(userId: String): Flow<List<WorkoutRecord>> {
        require(userId.isNotBlank()) { "A user ID is required." }
        return dao.observe(userId).map { rows -> rows.map { it.toRecord() } }
    }

    override suspend fun get(userId: String, id: Int): Result<WorkoutRecord?> = databaseResult {
        requireIdentity(userId, id)
        dao.get(userId, id)?.toRecord()
    }

    override suspend fun upsert(record: WorkoutRecord): Result<Int> = databaseResult {
        workoutValidationError(record, LocalDate.now(clock))?.let { throw IllegalArgumentException(it) }
        val entity = record.copy(title = record.title.trim()).toEntity()
        database.withTransaction {
            if (entity.id == 0) {
                val id = dao.insert(entity)
                check(id in 1..Int.MAX_VALUE.toLong()) { "Workout ID capacity exceeded." }
                id.toInt()
            } else {
                check(dao.update(entity.userId, entity.id, entity.title, entity.minutes, entity.dateEpochDay,
                    entity.startedAtEpochMillis, entity.source) == 1) { "This workout no longer exists for this user." }
                entity.id
            }
        }
    }

    override suspend fun delete(userId: String, id: Int): Result<Unit> = databaseResult {
        requireIdentity(userId, id)
        check(dao.delete(userId, id) == 1) { "This workout no longer exists for this user." }
    }

    private fun requireIdentity(userId: String, id: Int) {
        require(userId.isNotBlank()) { "A user ID is required." }
        require(id > 0) { "Invalid workout ID." }
    }

    // Cancellation belongs to the coroutine lifecycle, not an ordinary database error.
    private suspend fun <T> databaseResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private fun WorkoutRecord.toEntity() = WorkoutEntity(id, userId, title, minutes, date.toEpochDay(), startedAt?.toEpochMilli(), source.name)
private fun WorkoutEntity.toRecord() = WorkoutRecord(id, userId, title, minutes, LocalDate.ofEpochDay(dateEpochDay),
    startedAtEpochMillis?.let(Instant::ofEpochMilli), DataSource.valueOf(source))
